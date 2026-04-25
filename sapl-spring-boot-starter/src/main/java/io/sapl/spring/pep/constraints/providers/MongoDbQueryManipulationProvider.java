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

import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

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
import io.sapl.spring.pep.constraints.Signal.MongoDbQueryShimSignal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.val;

/**
 * Translates a {@code mongo:queryManipulation} constraint into a
 * {@link Mapper} attached to the PEP's {@link MongoDbQueryShimSignal}. The
 * mapper appends MongoDB {@link Criteria} predicates to the original
 * {@link Query} before driver dispatch.
 * </p>
 * Two obligation shapes are supported. The typed shape mirrors the
 * relational provider for cross-backend symmetry:
 * </p>
 *
 * <pre>{@code
 * {
 *   "type": "mongo:queryManipulation",
 *   "criteria": [
 *     {"column": "tenantId", "op": "=", "value": 7},
 *     {"or": [
 *       {"column": "ownerId", "op": "=", "value": "alice"},
 *       {"column": "public", "op": "=", "value": true}
 *     ]},
 *     {"column": "deletedAt", "op": "isNull"}
 *   ]
 * }
 * }</pre>
 *
 * The string-conditions shape remains as an escape hatch for
 * MongoDB-specific operators (e.g. {@code $exists}, {@code $regex},
 * {@code $geoWithin}) that are not exposed via the typed form:
 * </p>
 *
 * <pre>{@code
 * {
 *   "type": "mongo:queryManipulation",
 *   "conditions": ["{'tenantId': 7}", "{'age': {'$gte': 18}}"]
 * }
 * }</pre>
 *
 * Both forms may appear together; typed criteria are applied first via
 * {@link Criteria#andOperator(Criteria...)}, then string-condition fragments
 * are merged into the BSON document. String-condition fragments overwrite
 * any field they share with the original query (mirrors MongoDB document-
 * append semantics; for obligation enforcement this is the safer default
 * since the obligation always wins over the original).
 * </p>
 * Supported {@code op} values for typed criteria: {@code =}, {@code !=},
 * {@code >}, {@code >=}, {@code <}, {@code <=}, {@code in}, {@code isNull},
 * {@code isNotNull}. For LIKE-style matching use the string-conditions form
 * with {@code $regex}.
 */
public class MongoDbQueryManipulationProvider implements ConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE  = "mongo:queryManipulation";
    private static final String FIELD_AND        = "and";
    private static final String FIELD_COLUMN     = "column";
    private static final String FIELD_CONDITIONS = "conditions";
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
        if (!supportedSignals.contains(MongoDbQueryShimSignal.TYPE)) {
            return Optional.empty();
        }
        val criteria   = extractTopLevelCriteria(constraint);
        val conditions = extractConditions(constraint);
        if (criteria.isEmpty() && conditions.isEmpty()) {
            return Optional.empty();
        }
        Mapper<Query> mapper = query -> applyToQuery(query, criteria, conditions);
        return Optional.of(new ScopedConstraintHandler(mapper, MongoDbQueryShimSignal.TYPE, DEFAULT_PRIORITY));
    }

    private static Query applyToQuery(Query query, List<Criteria> criteria, List<String> conditions) {
        if (!criteria.isEmpty()) {
            query.addCriteria(combine(criteria, Criteria::andOperator));
        }
        if (conditions.isEmpty()) {
            return query;
        }
        return rebuildWithMergedBson(query, conditions);
    }

    @FunctionalInterface
    private interface CriteriaCombiner {
        Criteria apply(Criteria root, Criteria[] more);
    }

    private static Criteria combine(List<Criteria> parts, CriteriaCombiner combiner) {
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        val first = parts.getFirst();
        val rest  = parts.subList(1, parts.size()).toArray(new Criteria[0]);
        return combiner.apply(first, rest);
    }

    /**
     * Snapshots the query's BSON document, merges the obligation conditions on
     * top (obligation overrides existing keys), and constructs a fresh
     * {@link BasicQuery} carrying the merged document. Sort, limit, skip, and
     * projection fields are transferred from the original query so the
     * non-filter parts of the query remain intact.
     * </p>
     * Rebuilding (rather than mutating {@code getQueryObject()}) is required
     * because for queries built from typed {@link Criteria} the document is a
     * fresh snapshot computed from the criteria tree on each call; in-place
     * mutations there are dropped on the next access.
     */
    private static Query rebuildWithMergedBson(Query query, List<String> conditions) {
        val merged = new org.bson.Document();
        merged.putAll(query.getQueryObject());
        for (val condition : conditions) {
            val parsed = new BasicQuery(condition).getQueryObject();
            parsed.forEach(merged::append);
        }
        val rebuilt = new BasicQuery(merged, query.getFieldsObject());
        if (query.getSkip() > 0) {
            rebuilt.skip(query.getSkip());
        }
        if (query.getLimit() > 0) {
            rebuilt.limit(query.getLimit());
        }
        if (query.isSorted()) {
            rebuilt.setSortObject(query.getSortObject());
        }
        return rebuilt;
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
            return buildGroup(orChildren, Criteria::orOperator);
        }
        if (object.get(FIELD_AND) instanceof ArrayValue andChildren) {
            return buildGroup(andChildren, Criteria::andOperator);
        }
        return buildLeaf(object);
    }

    private static Optional<Criteria> buildGroup(ArrayValue children, CriteriaCombiner combiner) {
        val parts = new ArrayList<Criteria>(children.size());
        for (val child : children) {
            buildCriterionNode(child).ifPresent(parts::add);
        }
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(combine(parts, combiner));
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
            return Optional.of(builder.is(null));
        }
        if ("isNotNull".equals(op)) {
            return Optional.of(builder.ne(null));
        }
        if (!(object.get(FIELD_VALUE) instanceof Value valueNode) || valueNode instanceof UndefinedValue) {
            return Optional.empty();
        }
        return applyBinaryOp(builder, op, valueNode);
    }

    private static Optional<Criteria> applyBinaryOp(Criteria builder, String op, Value valueNode) {
        val javaValue = unwrap(valueNode);
        return switch (op) {
        case "="  -> Optional.of(builder.is(javaValue));
        case "!=" -> Optional.of(builder.ne(javaValue));
        case ">"  -> Optional.of(builder.gt(javaValue));
        case ">=" -> Optional.of(builder.gte(javaValue));
        case "<"  -> Optional.of(builder.lt(javaValue));
        case "<=" -> Optional.of(builder.lte(javaValue));
        case "in" -> javaValue instanceof java.util.Collection<?> c ? Optional.of(builder.in(c)) : Optional.empty();
        default   -> Optional.empty();
        };
    }

    private static Object unwrap(Value value) {
        return switch (value) {
        case TextValue(String text)              -> text;
        case NumberValue(java.math.BigDecimal n) -> compactNumber(n);
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

    /**
     * Reduces a {@link java.math.BigDecimal} to the narrowest Java number type
     * that fits losslessly so MongoDB BSON renders it compactly (plain
     * {@code 7} instead of the {@code {"$numberDecimal": "7"}} extended-JSON
     * wrapper). For non-integral values the result falls back to
     * {@code double} which accepts the precision loss inherent to JSON
     * numerics in the obligation payload.
     */
    private static Object compactNumber(java.math.BigDecimal n) {
        if (n.scale() <= 0) {
            try {
                return n.intValueExact();
            } catch (ArithmeticException ignoredInt) {
                try {
                    return n.longValueExact();
                } catch (ArithmeticException ignoredLong) {
                    return n;
                }
            }
        }
        return n.doubleValue();
    }

    private static List<String> extractConditions(Value constraint) {
        if (!(constraint instanceof ObjectValue object)) {
            return List.of();
        }
        if (!(object.get(FIELD_CONDITIONS) instanceof ArrayValue conditionsArray)) {
            return List.of();
        }
        val result = new ArrayList<String>(conditionsArray.size());
        for (val element : conditionsArray) {
            if (element instanceof TextValue(String text)) {
                result.add(text);
            }
        }
        return List.copyOf(result);
    }
}
