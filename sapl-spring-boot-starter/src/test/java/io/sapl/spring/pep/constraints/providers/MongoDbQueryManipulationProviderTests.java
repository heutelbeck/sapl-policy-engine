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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("MongoDbQueryManipulationProvider")
class MongoDbQueryManipulationProviderTests {

    private static final ObjectMapper MAPPER          = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
    private static final SignalType   MONGO_SIGNAL    = Signal.MongoDbQueryShimSignal.TYPE;
    private static final SignalType   DECISION_SIGNAL = Signal.DecisionSignal.TYPE;

    private final MongoDbQueryManipulationProvider provider = new MongoDbQueryManipulationProvider();

    private static Value v(String json) {
        return MAPPER.readValue(json, Value.class);
    }

    @SuppressWarnings("unchecked")
    private Mapper<Query> mapperFor(String constraintJson) {
        return (Mapper<Query>) provider.getConstraintHandler(v(constraintJson), Set.of(MONGO_SIGNAL)).orElseThrow()
                .handler();
    }

    private static String renderQueryDocument(Query query) {
        return query.getQueryObject().toJson();
    }

    @Nested
    @DisplayName("Responsibility")
    class Responsibility {

        @Test
        @DisplayName("non-matching constraint type yields empty Optional")
        void givenWrongConstraintTypeThenEmpty() {
            assertThat(provider.getConstraintHandler(v("""
                    {"type": "somethingElse"}
                    """), Set.of(MONGO_SIGNAL))).isEmpty();
        }

        @Test
        @DisplayName("non-object constraint yields empty Optional")
        void givenNonObjectConstraintThenEmpty() {
            assertThat(provider.getConstraintHandler(v("\"plain\""), Set.of(MONGO_SIGNAL))).isEmpty();
        }

        @Test
        @DisplayName("matching constraint without MongoDbQueryShimSignal yields empty Optional")
        void givenMatchingConstraintWithoutSignalThenEmpty() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "mongo:queryManipulation",
                     "conditions": ["{'tenantId': 7}"]}
                    """), Set.of(DECISION_SIGNAL));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("matching constraint with neither criteria nor conditions yields empty Optional")
        void givenMatchingConstraintWithEmptyPayloadThenEmpty() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "mongo:queryManipulation"}
                    """), Set.of(MONGO_SIGNAL));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns Mapper at MongoDbQueryShimSignal with default priority")
        void givenMatchingConstraintAndSignalThenReturnsMapper() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "mongo:queryManipulation",
                     "conditions": ["{'tenantId': 7}"]}
                    """), Set.of(MONGO_SIGNAL));
            assertThat(result).hasValueSatisfying(scoped -> assertThat(scoped).satisfies(s -> {
                assertThat(s.signalType()).isEqualTo(MONGO_SIGNAL);
                assertThat(s.priority()).isEqualTo(30);
                assertThat(s.handler()).isInstanceOf(Mapper.class);
            }));
        }
    }

    @Nested
    @DisplayName("Typed criteria path")
    class TypedCriteria {

        @ParameterizedTest(name = "{0}")
        @MethodSource("binaryOpScenarios")
        @DisplayName("Binary operator produces the expected BSON fragment")
        void givenBinaryOpThenBsonContainsExpectedFragment(String name, String op, String valueJson, String fragment) {
            val mapper   = mapperFor("""
                    {"type": "mongo:queryManipulation",
                     "criteria": [{"column": "tenantId", "op": "%s", "value": %s}]}
                    """.formatted(op, valueJson));
            val rendered = renderQueryDocument(mapper.apply(new Query()));
            assertThat(rendered).contains(fragment);
        }

        static Stream<Arguments> binaryOpScenarios() {
            return Stream.of(arguments("equals number", "=", "7", "\"tenantId\": 7"),
                    arguments("not equals", "!=", "7", "\"$ne\": 7"), arguments("greater than", ">", "7", "\"$gt\": 7"),
                    arguments("greater or equal", ">=", "7", "\"$gte\": 7"),
                    arguments("less than", "<", "7", "\"$lt\": 7"),
                    arguments("less or equal", "<=", "7", "\"$lte\": 7"),
                    arguments("in list", "in", "[1, 2]", "\"$in\""));
        }

        @Test
        @DisplayName("Multiple top-level criteria are AND-combined")
        void givenMultipleCriteriaThenAndCombined() {
            val mapper   = mapperFor("""
                    {"type": "mongo:queryManipulation",
                     "criteria": [
                       {"column": "tenantId", "op": "=", "value": 7},
                       {"column": "active", "op": "=", "value": true}
                     ]}
                    """);
            val rendered = renderQueryDocument(mapper.apply(new Query()));
            assertThat(rendered).contains("$and").contains("tenantId").contains("active");
        }

        @Test
        @DisplayName("OR group within criteria array combines its children with $or")
        void givenOrGroupThenChildrenAreOrCombined() {
            val mapper   = mapperFor("""
                    {"type": "mongo:queryManipulation",
                     "criteria": [
                       {"or": [
                         {"column": "ownerId", "op": "=", "value": "alice"},
                         {"column": "public", "op": "=", "value": true}
                       ]}
                     ]}
                    """);
            val rendered = renderQueryDocument(mapper.apply(new Query()));
            assertThat(rendered).contains("$or").contains("ownerId").contains("public");
        }

        @Test
        @DisplayName("isNull operator produces deletedAt: null fragment")
        void givenIsNullThenEqualsNullFragment() {
            val mapper   = mapperFor("""
                    {"type": "mongo:queryManipulation",
                     "criteria": [{"column": "deletedAt", "op": "isNull"}]}
                    """);
            val rendered = renderQueryDocument(mapper.apply(new Query()));
            assertThat(rendered).contains("\"deletedAt\": null");
        }
    }

    @Nested
    @DisplayName("String conditions path (escape hatch)")
    class StringConditions {

        @Test
        @DisplayName("Single string condition is appended into the BSON document")
        void givenOneStringConditionThenAppendedToBson() {
            val mapper   = mapperFor("""
                    {"type": "mongo:queryManipulation",
                     "conditions": ["{'tenantId': 7}"]}
                    """);
            val rendered = renderQueryDocument(mapper.apply(new Query()));
            assertThat(rendered).contains("tenantId").contains("7");
        }

        @Test
        @DisplayName("Multiple string conditions are appended together")
        void givenMultipleStringConditionsThenAllAppended() {
            val mapper   = mapperFor("""
                    {"type": "mongo:queryManipulation",
                     "conditions": ["{'tenantId': 7}", "{'age': {'$gte': 18}}"]}
                    """);
            val rendered = renderQueryDocument(mapper.apply(new Query()));
            assertThat(rendered).contains("tenantId").contains("age").contains("$gte").contains("18");
        }

        @Test
        @DisplayName("Obligation condition is intersected with the original predicate on the same field via $and (never widens)")
        void givenSameFieldInOriginalAndObligationThenIntersectedViaAnd() {
            val mapper   = mapperFor("""
                    {"type": "mongo:queryManipulation",
                     "conditions": ["{'tenantId': 7}"]}
                    """);
            val original = new Query(Criteria.where("tenantId").is(99));
            val rendered = renderQueryDocument(mapper.apply(original));
            assertThat(rendered).contains("$and").contains("99").contains("\"tenantId\": 7");
        }

        @Test
        @DisplayName("Malformed JSON in a condition raises a parse exception that the planner treats as obligation failure")
        void givenMalformedJsonInConditionThenMapperThrows() {
            val mapper = mapperFor("""
                    {"type": "mongo:queryManipulation",
                     "conditions": ["this is not bson"]}
                    """);
            val query  = new Query();
            assertThatThrownBy(() -> mapper.apply(query)).isInstanceOf(org.bson.json.JsonParseException.class)
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("Sort, limit, and skip preservation")
    class PagingPreservation {

        @Test
        @DisplayName("Sort is preserved when string conditions are merged into the BSON document")
        void givenOriginalSortWhenStringConditionsAppliedThenSortPreserved() {
            val mapper   = mapperFor("""
                    {"type": "mongo:queryManipulation",
                     "conditions": ["{'tenantId': 7}"]}
                    """);
            val original = new Query().with(org.springframework.data.domain.Sort.by("name").ascending());
            val result   = mapper.apply(original);
            assertThat(result.getSortObject()).isEqualTo(original.getSortObject());
        }

        @Test
        @DisplayName("Limit is preserved when string conditions are merged into the BSON document")
        void givenOriginalLimitWhenStringConditionsAppliedThenLimitPreserved() {
            val mapper   = mapperFor("""
                    {"type": "mongo:queryManipulation",
                     "conditions": ["{'tenantId': 7}"]}
                    """);
            val original = new Query().limit(25);
            val result   = mapper.apply(original);
            assertThat(result.getLimit()).isEqualTo(25);
        }

        @Test
        @DisplayName("Skip is preserved when string conditions are merged into the BSON document")
        void givenOriginalSkipWhenStringConditionsAppliedThenSkipPreserved() {
            val mapper   = mapperFor("""
                    {"type": "mongo:queryManipulation",
                     "conditions": ["{'tenantId': 7}"]}
                    """);
            val original = new Query().skip(50L);
            val result   = mapper.apply(original);
            assertThat(result.getSkip()).isEqualTo(50L);
        }

        @Test
        @DisplayName("Sort, limit, and skip all preserved when typed criteria are added")
        void givenTypedCriteriaObligationWhenAppliedThenPagingPreserved() {
            val mapper   = mapperFor("""
                    {"type": "mongo:queryManipulation",
                     "criteria": [{"column": "tenantId", "op": "=", "value": 7}]}
                    """);
            val original = new Query().with(org.springframework.data.domain.Sort.by("name").ascending()).limit(10)
                    .skip(20L);
            val result   = mapper.apply(original);
            assertThat(result).satisfies(r -> {
                assertThat(r.getSortObject()).isEqualTo(original.getSortObject());
                assertThat(r.getLimit()).isEqualTo(10);
                assertThat(r.getSkip()).isEqualTo(20L);
            });
        }
    }
}
