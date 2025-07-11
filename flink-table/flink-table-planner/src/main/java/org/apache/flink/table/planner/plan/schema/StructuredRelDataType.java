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

package org.apache.flink.table.planner.plan.schema;

import org.apache.flink.annotation.Internal;
import org.apache.flink.table.planner.calcite.FlinkTypeFactory;
import org.apache.flink.table.types.logical.StructuredType;
import org.apache.flink.table.types.logical.StructuredType.StructuredAttribute;

import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeComparability;
import org.apache.calcite.rel.type.RelDataTypeFamily;
import org.apache.calcite.rel.type.RelDataTypeField;
import org.apache.calcite.rel.type.RelDataTypeFieldImpl;
import org.apache.calcite.rel.type.StructKind;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.ObjectSqlType;
import org.apache.calcite.sql.type.SqlTypeName;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link RelDataType} representation of a {@link StructuredType}.
 *
 * <p>It extends {@link ObjectSqlType} for by inline structured types and an optional implementation
 * class.
 */
@Internal
public final class StructuredRelDataType extends ObjectSqlType {

    private static final String IDENTIFIER_FORMAT = "*%s*";

    private final StructuredType structuredType;

    public StructuredRelDataType(StructuredType structuredType, List<RelDataTypeField> fields) {
        super(
                SqlTypeName.STRUCTURED,
                createSqlIdentifier(structuredType),
                structuredType.isNullable(),
                fields,
                createRelDataTypeComparability(structuredType));
        this.structuredType = structuredType;
        computeDigest(); // recompute digest
    }

    public static StructuredRelDataType create(
            FlinkTypeFactory factory, StructuredType structuredType) {
        final List<RelDataTypeField> fields = new ArrayList<>();
        for (int i = 0; i < structuredType.getAttributes().size(); i++) {
            final StructuredAttribute attribute = structuredType.getAttributes().get(i);
            final RelDataTypeField field =
                    new RelDataTypeFieldImpl(
                            attribute.getName(),
                            i,
                            factory.createFieldTypeFromLogicalType(attribute.getType()));
            fields.add(field);
        }
        return new StructuredRelDataType(structuredType, fields);
    }

    public StructuredType getStructuredType() {
        return structuredType;
    }

    public StructuredRelDataType createWithNullability(boolean nullable) {
        if (nullable == isNullable()) {
            return this;
        }
        return new StructuredRelDataType((StructuredType) structuredType.copy(nullable), fieldList);
    }

    @Override
    public StructKind getStructKind() {
        return StructKind.PEEK_FIELDS_NO_EXPAND;
    }

    @Override
    public RelDataTypeFamily getFamily() {
        return this; // every user-defined type is its own family
    }

    @Override
    protected void generateTypeString(StringBuilder sb, boolean withDetail) {
        // called by super constructor
        if (structuredType == null) {
            return;
        }
        if (withDetail) {
            sb.append(structuredType.asSerializableString());
        } else {
            sb.append(structuredType.asSummaryString());
        }
    }

    @Override
    protected void computeDigest() {
        final StringBuilder sb = new StringBuilder();
        generateTypeString(sb, true);
        digest = sb.toString();
    }

    private static SqlIdentifier createSqlIdentifier(StructuredType structuredType) {
        return structuredType
                .getObjectIdentifier()
                .map(i -> new SqlIdentifier(i.toList(), SqlParserPos.ZERO))
                .orElseGet(
                        () ->
                                new SqlIdentifier(
                                        String.format(
                                                IDENTIFIER_FORMAT,
                                                structuredType
                                                        .getClassName()
                                                        .orElseThrow(IllegalStateException::new)),
                                        SqlParserPos.ZERO));
    }

    private static RelDataTypeComparability createRelDataTypeComparability(
            StructuredType structuredType) {
        switch (structuredType.getComparison()) {
            case EQUALS:
                return RelDataTypeComparability.UNORDERED;
            case FULL:
                return RelDataTypeComparability.ALL;
            case NONE:
                return RelDataTypeComparability.NONE;
            default:
                throw new IllegalArgumentException("Unsupported structured type comparison.");
        }
    }
}
