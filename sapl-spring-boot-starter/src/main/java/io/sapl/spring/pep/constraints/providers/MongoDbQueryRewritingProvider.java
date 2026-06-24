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
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.NullValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.ScopedConstraintHandler;
import io.sapl.spring.pep.constraints.Signal.MongoDbQueryShimSignal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Translates a {@code mongo:queryRewriting} constraint into a {@link Mapper}
 * attached to the PEP's
 * {@link MongoDbQueryShimSignal}. The mapper appends MongoDB {@link Criteria}
 * predicates to the original {@link Query}
 * before driver dispatch.
 * </p>
 * Two obligation shapes are supported. The typed shape mirrors the relational
 * provider for cross-backend symmetry:
 * </p>
 *
 * <pre>{@code
 * {
 *   "type": "mongo:queryRewriting",
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
 * The string-conditions shape remains as an escape hatch for MongoDB-specific
 * operators (e.g. {@code $exists},
 * {@code $regex}, {@code $geoWithin}) that are not exposed via the typed form:
 * </p>
 *
 * <pre>{@code
 * {
 *   "type": "mongo:queryRewriting",
 *   "conditions": ["{\"tenantId\": 7}", "{\"age\": {\"$gte\": 18}}"]
 * }
 * }</pre>
 *
 * Condition strings must be valid JSON (double-quoted), not MongoDB shell
 * syntax, so the same obligation parses
 * identically on every SAPL Mongo PEP. The Python sapl-pymongo shim parses them
 * with bson.json_util, which accepts JSON
 * only.
 * </p>
 * Both forms may appear together. Typed criteria are wrapped in
 * {@link Criteria#andOperator(Criteria...)} before being
 * added to the query (so the wrapper has a null root key and never collides
 * with a field the user query is already
 * filtering on). String-condition fragments are then intersected with the
 * resulting query by AND-ing the original BSON
 * document and each condition document inside a top-level {@code $and} array.
 * The obligation never replaces or widens
 * the user's filter. It can only narrow it.
 * </p>
 * Supported {@code op} values for typed criteria: {@code =}, {@code !=},
 * {@code >}, {@code >=}, {@code <}, {@code <=},
 * {@code in}, {@code isNull}, {@code isNotNull}. For LIKE-style matching use
 * the string-conditions form with
 * {@code $regex}.
 */
public class MongoDbQueryRewritingProvider implements ConstraintHandlerProvider {

    private static final String CONSTRAINT_TYPE  = "mongo:queryRewriting";
    private static final String FIELD_AND        = "and";
    private static final String FIELD_COLUMN     = "column";
    private static final String FIELD_CONDITIONS = "conditions";
    private static final String FIELD_CRITERIA   = "criteria";
    private static final String FIELD_OP         = "op";
    private static final String FIELD_OR         = "or";
    private static final String FIELD_VALUE      = "value";
    private static final int    DEFAULT_PRIORITY = 30;

    private static final JsonMapper STRICT_JSON = JsonMapper.builder().build();

    private static final String ERROR_NON_JSON_CONDITION    = "mongo:queryRewriting condition is not strict JSON: ";
    private static final String ERROR_UNBUILDABLE_CRITERION = "mongo:queryRewriting typed criterion is malformed and cannot be built: ";

    @Override
    public List<ScopedConstraintHandler> getConstraintHandlers(Value constraint, Set<SignalType> supportedSignals) {
        var signalOpt = ConstraintHandlerProvider.constraintTypeAndSignal(constraint, CONSTRAINT_TYPE, supportedSignals,
                MongoDbQueryShimSignal.SIGNAL_TYPE);
        if (signalOpt.isEmpty()) {
            return List.of();
        }
        val criterionEntries = extractTopLevelCriterionEntries(constraint);
        val conditions       = extractConditions(constraint);
        if (criterionEntries.isEmpty() && conditions.isEmpty()) {
            return List.of();
        }
        Mapper<Query> mapper = query -> applyToQuery(query, criterionEntries, conditions);
        return List.of(new ScopedConstraintHandler(mapper, signalOpt.get(), DEFAULT_PRIORITY));
    }

    private static Query applyToQuery(Query query, List<Value> criterionEntries, List<String> conditions) {
        Query working = query;
        if (!criterionEntries.isEmpty()) {
            // Defensive copy: addCriteria mutates in place, and a reused caller Query must
            // not
            // accumulate obligation criteria across calls (which would eventually
            // over-restrict).
            // Query.of drops the read preference, so carry it over explicitly.
            working = Query.of(query);
            if (query.getReadPreference() != null) {
                working.withReadPreference(query.getReadPreference());
            }
            val criteria = new ArrayList<Criteria>(criterionEntries.size());
            for (val entry : criterionEntries) {
                criteria.add(buildCriterionNode(entry));
            }
            // Wrap obligation criteria in $and so the resulting Criteria has a
            // null root key. Without this wrapping, addCriteria fails with
            // InvalidMongoDbApiUsageException whenever an obligation field
            // collides with a field already present in the user's query
            // (e.g. obligation requires moon IN [...] and the user query
            // already filters on moon).
            working.addCriteria(new Criteria().andOperator(criteria.toArray(new Criteria[0])));
        }
        if (conditions.isEmpty()) {
            return working;
        }
        return rebuildWithMergedBson(working, conditions);
    }

    @FunctionalInterface
    private interface CriteriaCombiner {
        Criteria apply(Criteria root, Criteria[] more);
    }

    private static Criteria combine(List<Criteria> parts, CriteriaCombiner combiner) {
        if (parts.size() == 1) {
            return parts.getFirst();
        }
        // Use a fresh host Criteria so the resulting document is exactly
        // {$and: [c1, c2, ...]} or {$or: [c1, c2, ...]}. Combining onto
        // parts.getFirst() would leak its content as a top-level AND
        // peer of the group operator, which silently breaks OR semantics
        // (c1 OR c2 OR c3 becomes c1 AND ($or: [c2, c3])).
        return combiner.apply(new Criteria(), parts.toArray(new Criteria[0]));
    }

    /**
     * Builds a {@code $and} array of the original query's BSON document and each
     * obligation condition fragment, then
     * constructs a fresh {@link BasicQuery} carrying the merged document. Sort,
     * limit, skip, projection fields, collation, hint, read preference, read
     * concern, and meta are transferred from the original query so the non-filter
     * parts of the query remain intact, matching the typed-criteria path which
     * mutates the original {@link Query} in place.
     * </p>
     * The original-query document is included as the first element of the
     * {@code $and} so the user's filter still
     * applies. The obligation conditions are AND-ed onto it, never replacing it.
     * This matches the intersection semantic
     * of the typed-criteria path: an obligation can only narrow access, never widen
     * it.
     * </p>
     * Rebuilding (rather than mutating {@code getQueryObject()}) is required
     * because for queries built from typed
     * {@link Criteria} the document is a fresh snapshot computed from the criteria
     * tree on each call. In-place
     * mutations there are dropped on the next access.
     */
    private static Query rebuildWithMergedBson(Query query, List<String> conditions) {
        val original = query.getQueryObject();
        val parts    = new ArrayList<org.bson.Document>(conditions.size() + 1);
        if (!original.isEmpty()) {
            parts.add(new org.bson.Document(original));
        }
        for (val condition : conditions) {
            val parsed = parseStrictCondition(condition);
            if (!parsed.isEmpty()) {
                parts.add(parsed);
            }
        }
        val merged  = parts.size() == 1 ? parts.getFirst() : new org.bson.Document("$and", parts);
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
        query.getCollation().ifPresent(rebuilt::collation);
        if (query.getHint() != null) {
            rebuilt.withHint(query.getHint());
        }
        if (query.getReadPreference() != null) {
            rebuilt.withReadPreference(query.getReadPreference());
        }
        if (query.getReadConcern() != null) {
            rebuilt.withReadConcern(query.getReadConcern());
        }
        rebuilt.setMeta(query.getMeta());
        return rebuilt;
    }

    /**
     * Parses one condition fragment, rejecting MongoDB shell syntax (single quotes,
     * unquoted keys).
     * The fragment must be strict JSON so the same condition string parses
     * identically on every
     * SAPL Mongo PEP. The Python sapl-pymongo shim parses conditions with
     * bson.json_util, which
     * accepts strict JSON only. A non-JSON fragment raises, so the planner fails
     * closed.
     */
    private static org.bson.Document parseStrictCondition(String condition) {
        try {
            STRICT_JSON.readTree(condition);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(ERROR_NON_JSON_CONDITION + condition, e);
        }
        return new BasicQuery(condition).getQueryObject();
    }

    private static List<Value> extractTopLevelCriterionEntries(Value constraint) {
        if (!(constraint instanceof ObjectValue object)) {
            return List.of();
        }
        if (!(object.get(FIELD_CRITERIA) instanceof ArrayValue criteriaArray)) {
            return List.of();
        }
        return List.copyOf(criteriaArray);
    }

    private static Criteria buildCriterionNode(Value entry) {
        if (!(entry instanceof ObjectValue object)) {
            throw new AccessDeniedException(ERROR_UNBUILDABLE_CRITERION + entry);
        }
        if (object.get(FIELD_OR) instanceof ArrayValue orChildren) {
            return buildGroup(orChildren, Criteria::orOperator);
        }
        if (object.get(FIELD_AND) instanceof ArrayValue andChildren) {
            return buildGroup(andChildren, Criteria::andOperator);
        }
        return buildLeaf(object);
    }

    private static Criteria buildGroup(ArrayValue children, CriteriaCombiner combiner) {
        if (children.isEmpty()) {
            throw new AccessDeniedException(ERROR_UNBUILDABLE_CRITERION + children);
        }
        val parts = new ArrayList<Criteria>(children.size());
        for (val child : children) {
            parts.add(buildCriterionNode(child));
        }
        return combine(parts, combiner);
    }

    private static Criteria buildLeaf(ObjectValue object) {
        if (!(object.get(FIELD_COLUMN) instanceof TextValue(String column))) {
            throw new AccessDeniedException(ERROR_UNBUILDABLE_CRITERION + object);
        }
        if (!(object.get(FIELD_OP) instanceof TextValue(String op))) {
            throw new AccessDeniedException(ERROR_UNBUILDABLE_CRITERION + object);
        }
        val builder = Criteria.where(column);
        if ("isNull".equals(op)) {
            return builder.is(null);
        }
        if ("isNotNull".equals(op)) {
            return builder.ne(null);
        }
        val valueNode = object.get(FIELD_VALUE);
        if (valueNode == null || valueNode instanceof UndefinedValue) {
            throw new AccessDeniedException(ERROR_UNBUILDABLE_CRITERION + object);
        }
        return applyBinaryOp(builder, op, valueNode, object);
    }

    private static Criteria applyBinaryOp(Criteria builder, String op, Value valueNode, ObjectValue object) {
        val javaValue = unwrap(valueNode);
        return switch (op) {
        case "="  -> builder.is(javaValue);
        case "!=" -> builder.ne(javaValue);
        case ">"  -> builder.gt(javaValue);
        case ">=" -> builder.gte(javaValue);
        case "<"  -> builder.lt(javaValue);
        case "<=" -> builder.lte(javaValue);
        case "in" -> {
            if (javaValue instanceof Collection<?> c) {
                yield builder.in(c);
            }
            throw new AccessDeniedException(ERROR_UNBUILDABLE_CRITERION + object);
        }
        default   -> throw new AccessDeniedException(ERROR_UNBUILDABLE_CRITERION + object);
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
     * Reduces a {@link java.math.BigDecimal} to the narrowest Java number type that
     * fits losslessly so MongoDB BSON
     * renders it compactly (plain {@code 7} instead of the
     * {@code {"$numberDecimal": "7"}} extended-JSON wrapper). For
     * non-integral values the result falls back to {@code double} which accepts the
     * precision loss inherent to JSON
     * numerics in the obligation payload.
     */
    private static Number compactNumber(java.math.BigDecimal n) {
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
