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
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.CriteriaDefinition;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.spring.constraints.providers.ConstraintResponsibility;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.Signal.RelationalQueryShimSignal;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.val;

/**
 * Translates a {@code relational:queryManipulation} constraint into a
 * {@link Mapper} attached to the PEP's {@link RelationalQueryShimSignal}. The
 * mapper appends Spring Data Relational {@link Criteria} predicates to the
 * structured {@link Query} before driver dispatch, and optionally restricts
 * the projected columns. Operates on R2DBC and Spring Data JDBC alike since
 * both use the same {@link Query} class from {@code spring-data-relational}.
 *
 * The obligation shape is
 *
 * <pre>{@code
 * {
 *   "type": "relational:queryManipulation",
 *   "criteria": [
 *     {"column": "tenant_id", "op": "=", "value": 7},
 *     {"column": "status", "op": "=", "value": "active"}
 *   ],
 *   "columns": ["id", "name", "email"]
 * }
 * }</pre>
 *
 * The {@code criteria} entries are AND-combined and conjoined with any
 * pre-existing criteria via {@link Criteria#and(Criteria)}. The {@code
 * columns} entry, if present, restricts the SELECT projection via
 * {@link Query#columns(SqlIdentifier...)}.
 *
 * Supported {@code op} values: {@code "="}, {@code "!="}, {@code ">"},
 * {@code ">="}, {@code "<"}, {@code "<="}, {@code "in"}, {@code "like"}.
 */
public class RelationalQueryManipulationProvider implements ConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE  = "relational:queryManipulation";
    private static final String FIELD_COLUMN     = "column";
    private static final String FIELD_COLUMNS    = "columns";
    private static final String FIELD_CRITERIA   = "criteria";
    private static final String FIELD_OP         = "op";
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
        val criteria = extractCriteria(constraint);
        val columns  = extractColumns(constraint);
        if (criteria.isEmpty() && columns.isEmpty()) {
            return Optional.empty();
        }
        Mapper<Query> mapper = query -> applyCriteriaAndColumns(query, criteria, columns);
        return Optional.of(new ScopedConstraintHandler(mapper, RelationalQueryShimSignal.TYPE, DEFAULT_PRIORITY));
    }

    private static Query applyCriteriaAndColumns(Query query, List<Criteria> criteria, List<String> columns) {
        var result = query;
        if (!criteria.isEmpty()) {
            val combined = combineWithExisting(query.getCriteria().orElse(null), criteria);
            result = Query.query(combined).sort(query.getSort()).limit(query.getLimit()).offset(query.getOffset());
            if (!query.getColumns().isEmpty()) {
                result = result.columns(query.getColumns().toArray(new SqlIdentifier[0]));
            }
        }
        if (!columns.isEmpty()) {
            val identifiers = columns.stream().map(SqlIdentifier::unquoted).toArray(SqlIdentifier[]::new);
            result = result.columns(identifiers);
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

    private static List<Criteria> extractCriteria(Value constraint) {
        if (!(constraint instanceof ObjectValue object)) {
            return List.of();
        }
        if (!(object.get(FIELD_CRITERIA) instanceof ArrayValue criteriaArray)) {
            return List.of();
        }
        val result = new ArrayList<Criteria>(criteriaArray.size());
        for (val element : criteriaArray) {
            buildCriterion(element).ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private static Optional<Criteria> buildCriterion(Value entry) {
        if (!(entry instanceof ObjectValue object)) {
            return Optional.empty();
        }
        if (!(object.get(FIELD_COLUMN) instanceof TextValue(String column))) {
            return Optional.empty();
        }
        if (!(object.get(FIELD_OP) instanceof TextValue(String op))) {
            return Optional.empty();
        }
        val rawValue  = object.get(FIELD_VALUE);
        val javaValue = unwrap(rawValue);
        val column0   = Criteria.where(column);
        return switch (op) {
        case "="    -> Optional.of(column0.is(javaValue));
        case "!="   -> Optional.of(column0.not(javaValue));
        case ">"    -> Optional.of(column0.greaterThan(javaValue));
        case ">="   -> Optional.of(column0.greaterThanOrEquals(javaValue));
        case "<"    -> Optional.of(column0.lessThan(javaValue));
        case "<="   -> Optional.of(column0.lessThanOrEquals(javaValue));
        case "in"   -> javaValue instanceof List<?> list ? Optional.of(column0.in(list)) : Optional.empty();
        case "like" -> javaValue instanceof String s ? Optional.of(column0.like(s)) : Optional.empty();
        default     -> Optional.empty();
        };
    }

    private static Object unwrap(Value value) {
        return switch (value) {
        case TextValue(String text)              -> text;
        case NumberValue(java.math.BigDecimal n) -> n;
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
