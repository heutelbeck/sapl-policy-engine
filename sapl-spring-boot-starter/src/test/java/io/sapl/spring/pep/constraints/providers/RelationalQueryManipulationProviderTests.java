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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.sql.SqlIdentifier;

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("RelationalQueryManipulationProvider")
class RelationalQueryManipulationProviderTests {

    private static final ObjectMapper MAPPER          = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
    private static final SignalType   REL_SIGNAL      = Signal.RelationalQueryShimSignal.TYPE;
    private static final SignalType   DECISION_SIGNAL = Signal.DecisionSignal.TYPE;

    private final RelationalQueryManipulationProvider provider = new RelationalQueryManipulationProvider();

    private static Value v(String json) {
        return MAPPER.readValue(json, Value.class);
    }

    @SuppressWarnings("unchecked")
    private Mapper<Query> mapperFor(String constraintJson) {
        return (Mapper<Query>) provider.getConstraintHandler(v(constraintJson), Set.of(REL_SIGNAL)).orElseThrow()
                .handler();
    }

    private static String renderCriteria(Query query) {
        return query.getCriteria().map(Object::toString).orElse("");
    }

    @Nested
    @DisplayName("Responsibility")
    class Responsibility {

        @Test
        @DisplayName("non-matching constraint type yields empty Optional")
        void givenWrongConstraintTypeThenEmpty() {
            assertThat(provider.getConstraintHandler(v("""
                    {"type": "somethingElse"}
                    """), Set.of(REL_SIGNAL))).isEmpty();
        }

        @Test
        @DisplayName("non-object constraint yields empty Optional")
        void givenNonObjectConstraintThenEmpty() {
            assertThat(provider.getConstraintHandler(v("\"plain\""), Set.of(REL_SIGNAL))).isEmpty();
        }

        @Test
        @DisplayName("matching constraint without RelationalQueryShimSignal yields empty Optional")
        void givenMatchingConstraintWithoutSignalThenEmpty() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "relational:queryManipulation",
                     "criteria": [{"column": "x", "op": "=", "value": 1}]}
                    """), Set.of(DECISION_SIGNAL));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("matching constraint with neither criteria nor columns yields empty Optional")
        void givenMatchingConstraintWithEmptyPayloadThenEmpty() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "relational:queryManipulation"}
                    """), Set.of(REL_SIGNAL));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns Mapper at RelationalQueryShimSignal with default priority")
        void givenMatchingConstraintAndSignalThenReturnsMapper() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "relational:queryManipulation",
                     "criteria": [{"column": "x", "op": "=", "value": 1}]}
                    """), Set.of(REL_SIGNAL));
            assertThat(result).hasValueSatisfying(scoped -> assertThat(scoped).satisfies(s -> {
                assertThat(s.signalType()).isEqualTo(REL_SIGNAL);
                assertThat(s.priority()).isEqualTo(30);
                assertThat(s.handler()).isInstanceOf(Mapper.class);
            }));
        }
    }

    @Nested
    @DisplayName("Binary operators")
    class BinaryOperators {

        @ParameterizedTest(name = "{0}")
        @MethodSource("binaryOpScenarios")
        @DisplayName("Binary operator produces the expected criteria string")
        void givenBinaryOpThenCriteriaMatches(String name, String op, String valueJson, String expectedFragment) {
            val mapper   = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "criteria": [{"column": "tenant_id", "op": "%s", "value": %s}]}
                    """.formatted(op, valueJson));
            val rendered = renderCriteria(mapper.apply(Query.empty()));
            assertThat(rendered).contains(expectedFragment);
        }

        static Stream<Arguments> binaryOpScenarios() {
            return Stream.of(arguments("equals number", "=", "7", "tenant_id = 7"),
                    arguments("not equals number", "!=", "7", "tenant_id != 7"),
                    arguments("greater than", ">", "7", "tenant_id > 7"),
                    arguments("greater than or eq", ">=", "7", "tenant_id >= 7"),
                    arguments("less than", "<", "7", "tenant_id < 7"),
                    arguments("less than or eq", "<=", "7", "tenant_id <= 7"),
                    arguments("equals string", "=", "\"a\"", "tenant_id = 'a'"),
                    arguments("equals boolean true", "=", "true", "tenant_id = 'true'"),
                    arguments("equals boolean false", "=", "false", "tenant_id = 'false'"),
                    arguments("equals null", "=", "null", "tenant_id IS NULL"),
                    arguments("in list", "in", "[1, 2]", "tenant_id IN"),
                    arguments("like pattern", "like", "\"a%\"", "tenant_id LIKE"),
                    arguments("not like pattern", "notLike", "\"a%\"", "tenant_id NOT LIKE"));
        }
    }

    @Nested
    @DisplayName("Unary operators")
    class UnaryOperators {

        @ParameterizedTest(name = "{0}")
        @MethodSource("unaryOpScenarios")
        @DisplayName("Unary operator produces the expected criteria string")
        void givenUnaryOpThenCriteriaMatches(String name, String op, String expectedFragment) {
            val mapper   = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "criteria": [{"column": "deleted_at", "op": "%s"}]}
                    """.formatted(op));
            val rendered = renderCriteria(mapper.apply(Query.empty()));
            assertThat(rendered).contains(expectedFragment);
        }

        static Stream<Arguments> unaryOpScenarios() {
            return Stream.of(arguments("isNull", "isNull", "deleted_at IS NULL"),
                    arguments("isNotNull", "isNotNull", "deleted_at IS NOT NULL"));
        }
    }

    @Nested
    @DisplayName("Composition")
    class Composition {

        @Test
        @DisplayName("Multiple top-level criteria are AND-combined")
        void givenMultipleTopLevelCriteriaThenAllAreAndCombined() {
            val mapper   = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "criteria": [
                       {"column": "tenant_id", "op": "=", "value": 7},
                       {"column": "status", "op": "=", "value": "active"}
                     ]}
                    """);
            val rendered = renderCriteria(mapper.apply(Query.empty()));
            assertThat(rendered).contains("tenant_id = 7").contains("status = 'active'").contains("AND");
        }

        @Test
        @DisplayName("Obligation criteria are AND-combined with existing query criteria")
        void givenExistingCriteriaThenObligationIsAndCombined() {
            val mapper   = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "criteria": [{"column": "tenant_id", "op": "=", "value": 7}]}
                    """);
            val original = Query.query(Criteria.where("name").is("alice"));
            val rendered = renderCriteria(mapper.apply(original));
            assertThat(rendered).contains("name = 'alice'").contains("tenant_id = 7").contains("AND");
        }

        @Test
        @DisplayName("OR group within criteria array combines its children with OR")
        void givenOrGroupThenChildrenAreOrCombined() {
            val mapper   = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "criteria": [
                       {"or": [
                         {"column": "owner_id", "op": "=", "value": "alice"},
                         {"column": "public", "op": "=", "value": true}
                       ]}
                     ]}
                    """);
            val rendered = renderCriteria(mapper.apply(Query.empty()));
            assertThat(rendered).contains("owner_id = 'alice'").contains("public = 'true'").contains("OR");
        }
    }

    @Nested
    @DisplayName("Column projection")
    class ColumnProjection {

        @Test
        @DisplayName("Obligation columns define projection when original has none")
        void givenNoOriginalColumnsThenObligationDefinesProjection() {
            val mapper = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "columns": ["id", "name"]}
                    """);
            val result = mapper.apply(Query.empty());
            assertThat(result.getColumns()).containsExactly(SqlIdentifier.unquoted("id"),
                    SqlIdentifier.unquoted("name"));
        }

        @Test
        @DisplayName("Obligation cannot widen original projection (security: intersect not replace)")
        void givenOriginalNarrowsAndObligationWidensThenIntersectionWins() {
            val mapper   = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "columns": ["id", "name", "ssn"]}
                    """);
            val original = Query.empty().columns(SqlIdentifier.unquoted("id"), SqlIdentifier.unquoted("name"));
            val result   = mapper.apply(original);
            assertThat(result.getColumns())
                    .containsExactly(SqlIdentifier.unquoted("id"), SqlIdentifier.unquoted("name"))
                    .doesNotContain(SqlIdentifier.unquoted("ssn"));
        }

        @Test
        @DisplayName("Empty columns array is treated as no projection narrowing")
        void givenEmptyColumnsArrayThenOriginalProjectionIsPreserved() {
            val mapper   = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "criteria": [{"column": "tenant_id", "op": "=", "value": 7}]}
                    """);
            val original = Query.empty().columns(SqlIdentifier.unquoted("id"));
            val result   = mapper.apply(original);
            assertThat(result.getColumns()).containsExactly(SqlIdentifier.unquoted("id"));
        }
    }

    @Nested
    @DisplayName("Sort, limit, and offset preservation")
    class PagingPreservation {

        @Test
        @DisplayName("Sort is preserved when criteria are AND-combined into the query")
        void givenOriginalSortWhenObligationAppliesThenSortPreserved() {
            val mapper   = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "criteria": [{"column": "tenant_id", "op": "=", "value": 7}]}
                    """);
            val original = Query.empty().sort(org.springframework.data.domain.Sort.by("name").ascending());
            val result   = mapper.apply(original);
            assertThat(result.getSort()).isEqualTo(original.getSort());
        }

        @Test
        @DisplayName("Limit is preserved when criteria are AND-combined into the query")
        void givenOriginalLimitWhenObligationAppliesThenLimitPreserved() {
            val mapper   = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "criteria": [{"column": "tenant_id", "op": "=", "value": 7}]}
                    """);
            val original = Query.empty().limit(25);
            val result   = mapper.apply(original);
            assertThat(result.getLimit()).isEqualTo(25);
        }

        @Test
        @DisplayName("Offset is preserved when criteria are AND-combined into the query")
        void givenOriginalOffsetWhenObligationAppliesThenOffsetPreserved() {
            val mapper   = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "criteria": [{"column": "tenant_id", "op": "=", "value": 7}]}
                    """);
            val original = Query.empty().offset(50L);
            val result   = mapper.apply(original);
            assertThat(result.getOffset()).isEqualTo(50L);
        }

        @Test
        @DisplayName("Sort, limit, and offset all preserved when only column projection is narrowed")
        void givenColumnsObligationWhenAppliedThenPagingPreserved() {
            val mapper   = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "columns": ["id"]}
                    """);
            val original = Query.empty().columns(SqlIdentifier.unquoted("id"))
                    .sort(org.springframework.data.domain.Sort.by("name").ascending()).limit(10).offset(20L);
            val result   = mapper.apply(original);
            assertThat(result).satisfies(r -> {
                assertThat(r.getSort()).isEqualTo(original.getSort());
                assertThat(r.getLimit()).isEqualTo(10);
                assertThat(r.getOffset()).isEqualTo(20L);
            });
        }
    }

    @Nested
    @DisplayName("Malformed input handling")
    class MalformedInput {

        @ParameterizedTest(name = "{0}")
        @MethodSource("malformedEntries")
        @DisplayName("Malformed criterion entry is silently dropped without affecting valid entries")
        void givenMalformedEntryThenItIsSilentlyDropped(String name, String malformedJson) {
            val mapper   = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "criteria": [
                       %s,
                       {"column": "tenant_id", "op": "=", "value": 7}
                     ]}
                    """.formatted(malformedJson));
            val rendered = renderCriteria(mapper.apply(Query.empty()));
            assertThat(rendered).contains("tenant_id = 7");
        }

        static Stream<Arguments> malformedEntries() {
            return Stream.of(arguments("missing column", "{\"op\": \"=\", \"value\": 1}"),
                    arguments("missing op", "{\"column\": \"x\", \"value\": 1}"),
                    arguments("unknown op", "{\"column\": \"x\", \"op\": \"matches\", \"value\": 1}"),
                    arguments("non-list value for in", "{\"column\": \"x\", \"op\": \"in\", \"value\": 1}"),
                    arguments("non-string for like", "{\"column\": \"x\", \"op\": \"like\", \"value\": 1}"),
                    arguments("missing value for =", "{\"column\": \"x\", \"op\": \"=\"}"));
        }
    }
}
