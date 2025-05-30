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

package org.apache.flink.state.api.runtime;

import org.apache.flink.annotation.Internal;
import org.apache.flink.api.common.JobInfo;
import org.apache.flink.api.common.TaskInfo;
import org.apache.flink.api.common.accumulators.Accumulator;
import org.apache.flink.api.common.accumulators.DoubleCounter;
import org.apache.flink.api.common.accumulators.Histogram;
import org.apache.flink.api.common.accumulators.IntCounter;
import org.apache.flink.api.common.accumulators.LongCounter;
import org.apache.flink.api.common.cache.DistributedCache;
import org.apache.flink.api.common.externalresource.ExternalResourceInfo;
import org.apache.flink.api.common.functions.BroadcastVariableInitializer;
import org.apache.flink.api.common.functions.RuntimeContext;
import org.apache.flink.api.common.state.AggregatingState;
import org.apache.flink.api.common.state.AggregatingStateDescriptor;
import org.apache.flink.api.common.state.KeyedStateStore;
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.ReducingState;
import org.apache.flink.api.common.state.ReducingStateDescriptor;
import org.apache.flink.api.common.state.StateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.TypeSerializer;
import org.apache.flink.metrics.groups.OperatorMetricGroup;
import org.apache.flink.util.Preconditions;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A streaming {@link RuntimeContext} which delegates to the underlying batch {@code RuntimeContext}
 * along with a specified {@link KeyedStateStore}.
 *
 * <p>This {@code RuntimeContext} has the ability to force eager state registration by throwing an
 * exception if state is registered outside of open.
 */
@Internal
public final class SavepointRuntimeContext implements RuntimeContext {
    private static final String REGISTRATION_EXCEPTION_MSG =
            "State Descriptors may only be registered inside of open";

    private final RuntimeContext ctx;

    private final KeyedStateStore keyedStateStore;

    private final List<StateDescriptor<?, ?>> registeredDescriptors;

    private boolean stateRegistrationAllowed;

    public SavepointRuntimeContext(RuntimeContext ctx, KeyedStateStore keyedStateStore) {
        this.ctx = Preconditions.checkNotNull(ctx);
        this.keyedStateStore = Preconditions.checkNotNull(keyedStateStore);
        this.stateRegistrationAllowed = true;

        this.registeredDescriptors = new ArrayList<>();
    }

    @Override
    public JobInfo getJobInfo() {
        return ctx.getJobInfo();
    }

    @Override
    public TaskInfo getTaskInfo() {
        return ctx.getTaskInfo();
    }

    @Override
    public OperatorMetricGroup getMetricGroup() {
        return ctx.getMetricGroup();
    }

    @Override
    public <T> TypeSerializer<T> createSerializer(TypeInformation<T> typeInformation) {
        return ctx.createSerializer(typeInformation);
    }

    @Override
    public Map<String, String> getGlobalJobParameters() {
        return ctx.getGlobalJobParameters();
    }

    @Override
    public boolean isObjectReuseEnabled() {
        return ctx.isObjectReuseEnabled();
    }

    @Override
    public ClassLoader getUserCodeClassLoader() {
        return ctx.getUserCodeClassLoader();
    }

    @Override
    public void registerUserCodeClassLoaderReleaseHookIfAbsent(
            String releaseHookName, Runnable releaseHook) {
        ctx.registerUserCodeClassLoaderReleaseHookIfAbsent(releaseHookName, releaseHook);
    }

    @Override
    public <V, A extends Serializable> void addAccumulator(
            String name, Accumulator<V, A> accumulator) {
        ctx.addAccumulator(name, accumulator);
    }

    @Override
    public <V, A extends Serializable> Accumulator<V, A> getAccumulator(String name) {
        return ctx.getAccumulator(name);
    }

    @Override
    public IntCounter getIntCounter(String name) {
        return ctx.getIntCounter(name);
    }

    @Override
    public LongCounter getLongCounter(String name) {
        return ctx.getLongCounter(name);
    }

    @Override
    public DoubleCounter getDoubleCounter(String name) {
        return ctx.getDoubleCounter(name);
    }

    @Override
    public Histogram getHistogram(String name) {
        return ctx.getHistogram(name);
    }

    @Override
    public Set<ExternalResourceInfo> getExternalResourceInfos(String resourceName) {
        throw new UnsupportedOperationException(
                "Do not support external resource in current environment");
    }

    @Override
    public boolean hasBroadcastVariable(String name) {
        return ctx.hasBroadcastVariable(name);
    }

    @Override
    public <RT> List<RT> getBroadcastVariable(String name) {
        return ctx.getBroadcastVariable(name);
    }

    @Override
    public <T, C> C getBroadcastVariableWithInitializer(
            String name, BroadcastVariableInitializer<T, C> initializer) {
        return ctx.getBroadcastVariableWithInitializer(name, initializer);
    }

    @Override
    public DistributedCache getDistributedCache() {
        return ctx.getDistributedCache();
    }

    @Override
    public <T> ValueState<T> getState(ValueStateDescriptor<T> stateProperties) {
        if (!stateRegistrationAllowed) {
            throw new RuntimeException(REGISTRATION_EXCEPTION_MSG);
        }

        registeredDescriptors.add(stateProperties);
        return keyedStateStore.getState(stateProperties);
    }

    @Override
    public <T> ListState<T> getListState(ListStateDescriptor<T> stateProperties) {
        if (!stateRegistrationAllowed) {
            throw new RuntimeException(REGISTRATION_EXCEPTION_MSG);
        }

        registeredDescriptors.add(stateProperties);
        return keyedStateStore.getListState(stateProperties);
    }

    @Override
    public <T> ReducingState<T> getReducingState(ReducingStateDescriptor<T> stateProperties) {
        if (!stateRegistrationAllowed) {
            throw new RuntimeException(REGISTRATION_EXCEPTION_MSG);
        }

        registeredDescriptors.add(stateProperties);
        return keyedStateStore.getReducingState(stateProperties);
    }

    @Override
    public <IN, ACC, OUT> AggregatingState<IN, OUT> getAggregatingState(
            AggregatingStateDescriptor<IN, ACC, OUT> stateProperties) {
        if (!stateRegistrationAllowed) {
            throw new RuntimeException(REGISTRATION_EXCEPTION_MSG);
        }

        registeredDescriptors.add(stateProperties);
        return keyedStateStore.getAggregatingState(stateProperties);
    }

    @Override
    public <UK, UV> MapState<UK, UV> getMapState(MapStateDescriptor<UK, UV> stateProperties) {
        if (!stateRegistrationAllowed) {
            throw new RuntimeException(REGISTRATION_EXCEPTION_MSG);
        }

        registeredDescriptors.add(stateProperties);
        return keyedStateStore.getMapState(stateProperties);
    }

    @Override
    public <T> org.apache.flink.api.common.state.v2.ValueState<T> getState(
            org.apache.flink.api.common.state.v2.ValueStateDescriptor<T> stateProperties) {
        throw new UnsupportedOperationException("State processor api does not support state v2.");
    }

    @Override
    public <T> org.apache.flink.api.common.state.v2.ListState<T> getListState(
            org.apache.flink.api.common.state.v2.ListStateDescriptor<T> stateProperties) {
        throw new UnsupportedOperationException("State processor api does not support state v2.");
    }

    @Override
    public <T> org.apache.flink.api.common.state.v2.ReducingState<T> getReducingState(
            org.apache.flink.api.common.state.v2.ReducingStateDescriptor<T> stateProperties) {
        throw new UnsupportedOperationException("State processor api does not support state v2.");
    }

    @Override
    public <IN, ACC, OUT>
            org.apache.flink.api.common.state.v2.AggregatingState<IN, OUT> getAggregatingState(
                    org.apache.flink.api.common.state.v2.AggregatingStateDescriptor<IN, ACC, OUT>
                            stateProperties) {
        throw new UnsupportedOperationException("State processor api does not support state v2.");
    }

    @Override
    public <UK, UV> org.apache.flink.api.common.state.v2.MapState<UK, UV> getMapState(
            org.apache.flink.api.common.state.v2.MapStateDescriptor<UK, UV> stateProperties) {
        throw new UnsupportedOperationException("State processor api does not support state v2.");
    }

    public List<StateDescriptor<?, ?>> getStateDescriptors() {
        if (registeredDescriptors.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(registeredDescriptors);
    }

    public void disableStateRegistration() throws Exception {
        stateRegistrationAllowed = false;
    }
}
