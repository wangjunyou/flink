/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.table.planner.codegen

import org.apache.flink.api.common.eventtime.WatermarkGeneratorSupplier
import org.apache.flink.api.common.functions.DefaultOpenContext
import org.apache.flink.configuration.Configuration
import org.apache.flink.metrics.MetricGroup
import org.apache.flink.streaming.util.MockStreamingRuntimeContext
import org.apache.flink.table.catalog.{ObjectIdentifier, UnresolvedIdentifier}
import org.apache.flink.table.data.{GenericRowData, TimestampData}
import org.apache.flink.table.planner.calcite.{FlinkContext, FlinkPlannerImpl, FlinkTypeFactory}
import org.apache.flink.table.planner.runtime.utils.JavaUserDefinedScalarFunctions.JavaFunc5
import org.apache.flink.table.planner.utils.PlannerMocks
import org.apache.flink.table.runtime.generated.WatermarkGenerator
import org.apache.flink.table.types.logical.{IntType, TimestampType}
import org.apache.flink.table.utils.CatalogManagerMocks
import org.apache.flink.testutils.junit.extensions.parameterized.{ParameterizedTestExtension, Parameters}
import org.apache.flink.util.clock.{RelativeClock, SystemClock}

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.TestTemplate
import org.junit.jupiter.api.extension.ExtendWith

import java.lang.{Integer => JInt, Long => JLong}
import java.util

/** Tests the generated [[WatermarkGenerator]] from [[WatermarkGeneratorCodeGenerator]]. */
@ExtendWith(Array(classOf[ParameterizedTestExtension]))
class WatermarkGeneratorCodeGenTest(useDefinedConstructor: Boolean) {
  val plannerMocks = PlannerMocks.create()

  def getPlanner: FlinkPlannerImpl = plannerMocks.getPlanner

  val data = List(
    GenericRowData.of(TimestampData.fromEpochMillis(1000L), JInt.valueOf(5)),
    GenericRowData.of(null, JInt.valueOf(4)),
    GenericRowData.of(TimestampData.fromEpochMillis(3000L), null),
    GenericRowData.of(TimestampData.fromEpochMillis(5000L), JInt.valueOf(3)),
    GenericRowData.of(TimestampData.fromEpochMillis(4000L), JInt.valueOf(10)),
    GenericRowData.of(TimestampData.fromEpochMillis(6000L), JInt.valueOf(8))
  )

  @TestTemplate
  def testAscendingWatermark(): Unit = {
    val generator =
      generateWatermarkGenerator("ts - INTERVAL '0.001' SECOND", useDefinedConstructor)
    val results = data.map(d => generator.currentWatermark(d))
    val expected = List(
      JLong.valueOf(999L),
      null,
      JLong.valueOf(2999),
      JLong.valueOf(4999),
      JLong.valueOf(3999),
      JLong.valueOf(5999))
    assertEquals(expected, results)
  }

  @TestTemplate
  def testBoundedOutOfOrderWatermark(): Unit = {
    val generator = generateWatermarkGenerator("ts - INTERVAL '5' SECOND", useDefinedConstructor)
    val results = data.map(d => generator.currentWatermark(d))
    val expected = List(
      JLong.valueOf(-4000L),
      null,
      JLong.valueOf(-2000L),
      JLong.valueOf(0L),
      JLong.valueOf(-1000L),
      JLong.valueOf(1000L))
    assertEquals(expected, results)
  }

  @TestTemplate
  def testLegacyCustomizedWatermark(): Unit = {
    testCustomizedWatermark(true)
  }

  @TestTemplate
  def testCustomizedWatermark(): Unit = {
    testCustomizedWatermark(false)
  }

  private def testCustomizedWatermark(isLegacy: Boolean): Unit = {
    JavaFunc5.openCalled = false
    JavaFunc5.closeCalled = false
    if (isLegacy) {
      plannerMocks.getFunctionCatalog.registerTempCatalogScalarFunction(
        ObjectIdentifier.of(
          CatalogManagerMocks.DEFAULT_CATALOG,
          CatalogManagerMocks.DEFAULT_DATABASE,
          "myFunc"),
        new JavaFunc5
      )
    } else {
      plannerMocks.getFunctionCatalog.registerTemporaryCatalogFunction(
        UnresolvedIdentifier.of(
          CatalogManagerMocks.DEFAULT_CATALOG,
          CatalogManagerMocks.DEFAULT_DATABASE,
          "myFunc"),
        new JavaFunc5,
        false
      )
    }

    val generator = generateWatermarkGenerator("myFunc(ts, `offset`)", useDefinedConstructor)
    if (!useDefinedConstructor) {
      // mock open and close invoking
      generator.setRuntimeContext(new MockStreamingRuntimeContext(1, 0))
    }
    generator.open(DefaultOpenContext.INSTANCE)
    val results = data.map(d => generator.currentWatermark(d))
    generator.close()
    val expected = List(
      JLong.valueOf(995L),
      null,
      null,
      JLong.valueOf(4997L),
      JLong.valueOf(3990L),
      JLong.valueOf(5992L))
    assertEquals(expected, results)
    assertTrue(JavaFunc5.openCalled)
    assertTrue(JavaFunc5.closeCalled)
  }

  private def generateWatermarkGenerator(
      expr: String,
      useDefinedConstructor: Boolean): WatermarkGenerator = {
    val tableRowType = plannerMocks.getPlannerContext.getTypeFactory.buildRelNodeRowType(
      Seq("ts", "offset"),
      Seq(
        new TimestampType(3),
        new IntType()
      ))
    val rowType = FlinkTypeFactory.toLogicalRowType(tableRowType)
    val converter = plannerMocks.getPlanner
      .createToRelContext()
      .getCluster
      .getPlanner
      .getContext
      .unwrap(classOf[FlinkContext])
      .getRexFactory
      .createSqlToRexConverter(tableRowType, null)
    val rexNode = converter.convertToRexNode(expr)

    if (useDefinedConstructor) {
      val generated = WatermarkGeneratorCodeGenerator
        .generateWatermarkGenerator(
          new Configuration,
          Thread.currentThread().getContextClassLoader,
          rowType,
          rexNode,
          Option.apply("context"))
      val newReferences = generated.getReferences :+
        new WatermarkGeneratorSupplier.Context {
          override def getMetricGroup: MetricGroup = null

          override def getInputActivityClock: RelativeClock = SystemClock.getInstance()
        }
      generated.newInstance(Thread.currentThread().getContextClassLoader, newReferences)
    } else {
      val generated = WatermarkGeneratorCodeGenerator
        .generateWatermarkGenerator(
          new Configuration,
          Thread.currentThread().getContextClassLoader,
          rowType,
          rexNode)
      generated.newInstance(Thread.currentThread().getContextClassLoader)
    }
  }
}

object WatermarkGeneratorCodeGenTest {
  @Parameters(name = "useDefinedConstructor={0}")
  def parameters(): util.Collection[Boolean] = {
    util.Arrays.asList(
      true,
      false
    )
  }
}
