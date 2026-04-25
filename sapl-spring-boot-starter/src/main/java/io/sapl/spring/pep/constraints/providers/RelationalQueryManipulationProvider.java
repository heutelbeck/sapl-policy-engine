/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.pep.constraints.providers;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.CriteriaDefinition;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.NullValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.RelationalQueryShimSignal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.val;

/**
 * Translates a {@code relational:queryManipulation} constraint into a
 * {@link Mapper} attached to the PEP's {@link RelationalQueryShimSignal}. The
 * mapper appends Spring Data Relational {@link Criteria} predicates to the
 * structured {@link Query} before driver dispatch and optionally narrows the
 * projected columns. Operates on R2DBC and Spring Data JDBC alike since both
 * use the same {@link Query} class from {@code spring-data-relational}.
 * </p>
 * The obligation shape is
 * </p>
 *
 * <pre>{@code
 * {
 *   "type": "relational:queryManipulation",
 *   "criteria": [
 *     {"column": "tenant_id", "op": "=", "value": 7},
 *     {"or": [
 *       {"column": "owner_id", "op": "=", "value": "alice"},
 *       {"column": "public", "op": "=", "value": true}
 *     ]},
 *     {"column": "deleted_at", "op": "isNull"}
 *   ],
 *   "columns": ["id", "name", "email"]
 * }
 * }</pre>
 *
 * The {@code criteria} array is AND-combined and conjoined with any
 * pre-existing criteria on the original query. Each entry is either a
 * binary criterion {@code {column, op, value}}, a unary criterion
 * {@code {column, op}} ({@code isNull}, {@code isNotNull}), or an OR-group
 * {@code {or: [...]}} whose children are AND-combined within the group.
 * </p>
 * The {@code columns} entry, if present, narrows the projection by
 * intersecting with any pre-existing column list on the original query
 * (least-privilege: an obligation cannot widen what the original query
 * already restricted). When the original has no projection, the obligation
 * defines it.
 * </p>
 * Supported {@code op} values: {@code =}, {@code !=}, {@code >}, {@code >=},
 * {@code <}, {@code <=}, {@code in}, {@code like}, {@code notLike},
 * {@code isNull}, {@code isNotNull}.
 */
public class RelationalQueryManipulationProvider implements ConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE  = "relational:queryManipulation";
    private static final String FIELD_AND        = "and";
    private static final String FIELD_COLUMN     = "column";
    private static final String FIELD_COLUMNS    = "columns";
    private static final String FIELD_CRITERIA   = "criteria";
    private static final String FIELD_OP         = "op";
    private static final String FIELD_OR         = "or";
    private static final String FIELD_VALUE      = "value";
    private static final int    DEFAULT_PRIORITY = 30;

    @Override
    public Optional<ScopedConstraintHandler> getConstraintHandler(Value constraint, Set<SignalType> supportedSignals) {
        if (!ConstraintResponsibility.isResponsible(constraint, CONSTRAINT_TYPE)) {
            return Optional.empty();
        }
        if (!supportedSignals.contains(RelationalQueryShimSignal.TYPE)) {
            return Optional.empty();
        }
        val criteria = extractTopLevelCriteria(constraint);
        val columns  = extractColumns(constraint);
        if (criteria.isEmpty() && columns.isEmpty()) {
            return Optional.empty();
        }
        Mapper<Query> mapper = query -> applyCriteriaAndColumns(query, criteria, columns);
        return Optional.of(new ScopedConstraintHandler(mapper, RelationalQueryShimSignal.TYPE, DEFAULT_PRIORITY));
    }

    private static Query applyCriteriaAndColumns(Query query, List<Criteria> criteria, List<String> columns) {
        val combined        = criteria.isEmpty() ? query.getCriteria().orElse(null)
                : combineWithExisting(query.getCriteria().orElse(null), criteria);
        val originalColumns = query.getColumns();
        val intersected     = intersectColumns(originalColumns, columns);
        val finalColumns    = intersected.isEmpty() ? List.copyOf(originalColumns) : intersected;
        var result          = (combined instanceof Criteria c) ? Query.query(c) : Query.empty();
        result = result.sort(query.getSort()).limit(query.getLimit()).offset(query.getOffset());
        if (!finalColumns.isEmpty()) {
            result = result.columns(finalColumns.toArray(new SqlIdentifier[0]));
        }
        return result;
    }

    private static Criteria combineWithExisting(CriteriaDefinition existing, List<Criteria> additions) {
        val additional = additions.stream().reduce(Criteria.empty(), Criteria::and);
        if (existing instanceof Criteria existingCriteria) {
            return existingCriteria.and(additional);
        }
        return additional;
    }

    /**
     * Intersects the original projection with the obligation's column list. An
     * empty obligation list means no projection narrowing was requested. An
     * empty original list means the obligation defines the projection. When
     * both are present, the result is their set intersection ordered by the
     * obligation's order so policy authors retain control over column order.
     */
    private static List<SqlIdentifier> intersectColumns(java.util.Collection<SqlIdentifier> originalColumns,
            List<String> obligationColumns) {
        if (obligationColumns.isEmpty()) {
            return List.of();
        }
        val obligationIdentifiers = obligationColumns.stream().map(SqlIdentifier::unquoted).toList();
        if (originalColumns.isEmpty()) {
            return obligationIdentifiers;
        }
        val originalSet = new LinkedHashSet<>(originalColumns);
        return obligationIdentifiers.stream().filter(originalSet::contains).toList();
    }

    private static List<Criteria> extractTopLevelCriteria(Value constraint) {
        if (!(constraint instanceof ObjectValue object)) {
            return List.of();
        }
        if (!(object.get(FIELD_CRITERIA) instanceof ArrayValue criteriaArray)) {
            return List.of();
        }
        val result = new ArrayList<Criteria>(criteriaArray.size());
        for (val element : criteriaArray) {
            buildCriterionNode(element).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private static Optional<Criteria> buildCriterionNode(Value entry) {
        if (!(entry instanceof ObjectValue object)) {
            return Optional.empty();
        }
        if (object.get(FIELD_OR) instanceof ArrayValue orChildren) {
            return buildGroup(orChildren, Criteria::or);
        }
        if (object.get(FIELD_AND) instanceof ArrayValue andChildren) {
            return buildGroup(andChildren, Criteria::and);
        }
        return buildLeaf(object);
    }

    private static Optional<Criteria> buildGroup(ArrayValue children,
            java.util.function.BinaryOperator<Criteria> combine) {
        val parts = new ArrayList<Criteria>(children.size());
        for (val child : children) {
            buildCriterionNode(child).ifPresent(parts::add);
        }
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(parts.stream().reduce(Criteria.empty(), combine));
    }

    private static Optional<Criteria> buildLeaf(ObjectValue object) {
        if (!(object.get(FIELD_COLUMN) instanceof TextValue(String column))) {
            return Optional.empty();
        }
        if (!(object.get(FIELD_OP) instanceof TextValue(String op))) {
            return Optional.empty();
        }
        val builder = Criteria.where(column);
        if ("isNull".equals(op)) {
            return Optional.of(builder.isNull());
        }
        if ("isNotNull".equals(op)) {
            return Optional.of(builder.isNotNull());
        }
        if (!(object.get(FIELD_VALUE) instanceof Value valueNode) || valueNode instanceof UndefinedValue) {
            return Optional.empty();
        }
        return applyBinaryOp(builder, op, valueNode);
    }

    private static Optional<Criteria> applyBinaryOp(Criteria.CriteriaStep builder, String op, Value valueNode) {
        val javaValue = unwrap(valueNode);
        if (javaValue == null) {
            return switch (op) {
            case "="  -> Optional.of(builder.isNull());
            case "!=" -> Optional.of(builder.isNotNull());
            default   -> Optional.empty();
            };
        }
        return switch (op) {
        case "="       -> Optional.of(builder.is(javaValue));
        case "!="      -> Optional.of(builder.not(javaValue));
        case ">"       -> Optional.of(builder.greaterThan(javaValue));
        case ">="      -> Optional.of(builder.greaterThanOrEquals(javaValue));
        case "<"       -> Optional.of(builder.lessThan(javaValue));
        case "<="      -> Optional.of(builder.lessThanOrEquals(javaValue));
        case "in"      -> javaValue instanceof List<?> list ? Optional.of(builder.in(list)) : Optional.empty();
        case "like"    -> javaValue instanceof String s ? Optional.of(builder.like(s)) : Optional.empty();
        case "notLike" -> javaValue instanceof String s ? Optional.of(builder.notLike(s)) : Optional.empty();
        default        -> Optional.empty();
        };
    }

    /**
     * Unwraps a SAPL {@link Value} into the corresponding Java type that
     * Spring Data Relational's {@link Criteria} accepts. Returns {@code null}
     * only for explicit {@link NullValue}; all other unhandled value kinds
     * (including {@link UndefinedValue}) are filtered out earlier so they
     * never reach this method.
     */
    private static Object unwrap(Value value) {
        return switch (value) {
        case TextValue(String text)              -> text;
        case NumberValue(java.math.BigDecimal n) -> n;
        case BooleanValue(boolean b)             -> b;
        case NullValue ignored                   -> null;
        case ArrayValue array                    -> {
            val list = new ArrayList<Object>(array.size());
            for (val element : array) {
                list.add(unwrap(element));
            }
            yield list;
        }
        default                                  -> null;
        };
    }

    private static List<String> extractColumns(Value constraint) {
        if (!(constraint instanceof ObjectValue object)) {
            return List.of();
        }
        if (!(object.get(FIELD_COLUMNS) instanceof ArrayValue columnsArray)) {
            return List.of();
        }
        val result = new ArrayList<String>(columnsArray.size());
        for (val element : columnsArray) {
            if (element instanceof TextValue(String text)) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }
}
