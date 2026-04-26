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

import io.sapl.api.model.Value;
import io.sapl.api.model.jackson.SaplJacksonModule;
import org.springframework.security.access.AccessDeniedException;
import io.sapl.spring.pep.constraints.ConstraintHandler.Mapper;
import io.sapl.spring.pep.constraints.Signal;
import io.sapl.spring.pep.constraints.SignalType;
import lombok.val;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("SqlQueryManipulationProvider")
class SqlQueryManipulationProviderTests {

    private static final ObjectMapper MAPPER          = JsonMapper.builder().addModule(new SaplJacksonModule()).build();
    private static final SignalType   SQL_SIGNAL      = Signal.SqlShimSignal.TYPE;
    private static final SignalType   DECISION_SIGNAL = Signal.DecisionSignal.TYPE;

    private final SqlQueryManipulationProvider provider = new SqlQueryManipulationProvider();

    private static Value v(String json) {
        return MAPPER.readValue(json, Value.class);
    }

    @SuppressWarnings("unchecked")
    private Mapper<String> mapperFor(String constraintJson) {
        return (Mapper<String>) provider.getConstraintHandler(v(constraintJson), Set.of(SQL_SIGNAL)).orElseThrow()
                .handler();
    }

    @Nested
    @DisplayName("Responsibility")
    class Responsibility {

        @Test
        @DisplayName("non-matching constraint type yields empty Optional")
        void givenWrongConstraintTypeThenEmpty() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "somethingElse"}
                    """), Set.of(SQL_SIGNAL));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("non-object constraint yields empty Optional")
        void givenNonObjectConstraintThenEmpty() {
            assertThat(provider.getConstraintHandler(v("\"plain string\""), Set.of(SQL_SIGNAL))).isEmpty();
        }

        @Test
        @DisplayName("matching constraint without SqlShimSignal supported yields empty Optional")
        void givenMatchingConstraintWithoutSqlSignalThenEmpty() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "sql:queryManipulation", "conditions": ["x = 1"]}
                    """), Set.of(DECISION_SIGNAL));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("matching constraint with neither conditions nor columns yields empty Optional")
        void givenMatchingConstraintWithEmptyPayloadThenEmpty() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "sql:queryManipulation"}
                    """), Set.of(SQL_SIGNAL));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns Mapper at SqlShimSignal with default priority")
        void givenMatchingConstraintAndSignalThenReturnsMapper() {
            val result = provider.getConstraintHandler(v("""
                    {"type": "sql:queryManipulation", "conditions": ["x = 1"]}
                    """), Set.of(SQL_SIGNAL));
            assertThat(result).hasValueSatisfying(scoped -> assertThat(scoped).satisfies(s -> {
                assertThat(s.signalType()).isEqualTo(SQL_SIGNAL);
                assertThat(s.priority()).isEqualTo(30);
                assertThat(s.handler()).isInstanceOf(Mapper.class);
            }));
        }
    }

    @Nested
    @DisplayName("WHERE injection on SELECT")
    class WhereInjectionOnSelect {

        @Test
        @DisplayName("Adds WHERE clause when original SELECT has no WHERE (closes the silent-bypass bug)")
        void givenSelectWithoutWhereThenWhereIsAdded() {
            val mapper    = mapperFor("""
                    {"type": "sql:queryManipulation", "conditions": ["tenant_id = 7"]}
                    """);
            val rewritten = mapper.apply("SELECT * FROM users");
            assertThat(rewritten).containsIgnoringCase("WHERE").contains("tenant_id = 7");
        }

        @Test
        @DisplayName("Preserves OR precedence when original WHERE contains OR (closes the precedence-inversion bug)")
        void givenSelectWithOrWhenObligationAddsAndThenOrPrecedenceIsPreserved() {
            val mapper    = mapperFor("""
                    {"type": "sql:queryManipulation", "conditions": ["tenant_id = 7"]}
                    """);
            val rewritten = mapper.apply("SELECT * FROM users WHERE a = 1 OR b = 2");
            assertThat(rewritten).contains("(a = 1 OR b = 2)").contains("tenant_id = 7");
        }

        @Test
        @DisplayName("Multiple obligation conditions are AND-combined and parenthesized individually")
        void givenMultipleConditionsThenAllAreAndCombined() {
            val mapper    = mapperFor("""
                    {"type": "sql:queryManipulation",
                     "conditions": ["tenant_id = 7", "status = 'active'"]}
                    """);
            val rewritten = mapper.apply("SELECT * FROM users");
            assertThat(rewritten).contains("tenant_id = 7").contains("status = 'active'").contains("AND");
        }

        @Test
        @DisplayName("String literals containing the word 'where' do not confuse the rewriter")
        void givenWhereInsideStringLiteralThenRewriteIsCorrect() {
            val mapper    = mapperFor("""
                    {"type": "sql:queryManipulation", "conditions": ["tenant_id = 7"]}
                    """);
            val rewritten = mapper.apply("SELECT * FROM logs WHERE message = 'something where stuff happened'");
            assertThat(rewritten).contains("'something where stuff happened'").contains("tenant_id = 7");
        }
    }

    @Nested
    @DisplayName("WHERE injection on UPDATE and DELETE")
    class WhereInjectionOnUpdateAndDelete {

        @Test
        @DisplayName("UPDATE statement gets the obligation injected into WHERE")
        void givenUpdateThenWhereIsAddedOrCombined() {
            val mapper    = mapperFor("""
                    {"type": "sql:queryManipulation", "conditions": ["tenant_id = 7"]}
                    """);
            val rewritten = mapper.apply("UPDATE users SET name = 'x' WHERE id = 1");
            assertThat(rewritten).contains("UPDATE").contains("tenant_id = 7").contains("id = 1");
        }

        @Test
        @DisplayName("DELETE statement gets the obligation injected into WHERE")
        void givenDeleteThenWhereIsAddedOrCombined() {
            val mapper    = mapperFor("""
                    {"type": "sql:queryManipulation", "conditions": ["tenant_id = 7"]}
                    """);
            val rewritten = mapper.apply("DELETE FROM users WHERE id = 1");
            assertThat(rewritten).contains("DELETE").contains("tenant_id = 7").contains("id = 1");
        }
    }

    @Nested
    @DisplayName("Fail-closed behaviour")
    class FailClosed {

        @Test
        @DisplayName("Plain INSERT statement throws (no WHERE to inject into)")
        void givenInsertThenThrows() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation", "conditions": ["tenant_id = 7"]}
                    """);
            assertThatThrownBy(() -> mapper.apply("INSERT INTO users (id, name) VALUES (1, 'x')"))
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining("does not support");
        }

        @Test
        @DisplayName("Malformed original SQL throws")
        void givenMalformedSqlThenThrows() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation", "conditions": ["tenant_id = 7"]}
                    """);
            assertThatThrownBy(() -> mapper.apply("SELEKT * FROM users")).isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Cannot parse SQL");
        }

        @Test
        @DisplayName("Malformed obligation condition throws")
        void givenMalformedObligationConditionThenThrows() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation", "conditions": ["this is not sql"]}
                    """);
            assertThatThrownBy(() -> mapper.apply("SELECT * FROM users")).isInstanceOf(AccessDeniedException.class)
                    .hasMessageContaining("Cannot parse obligation condition");
        }
    }

    @Nested
    @DisplayName("Column projection narrowing")
    class ColumnProjection {

        @Test
        @DisplayName("SELECT * is narrowed to obligation columns")
        void givenSelectStarThenColumnsReplaceStar() {
            val mapper    = mapperFor("""
                    {"type": "sql:queryManipulation", "columns": ["id", "name"]}
                    """);
            val rewritten = mapper.apply("SELECT * FROM users");
            assertThat(rewritten).contains("id").contains("name").doesNotContain("*");
        }

        @Test
        @DisplayName("Obligation cannot widen original projection (security: intersect not replace)")
        void givenOriginalNarrowsAndObligationWidensThenIntersectionWins() {
            val mapper    = mapperFor("""
                    {"type": "sql:queryManipulation", "columns": ["id", "name", "ssn"]}
                    """);
            val rewritten = mapper.apply("SELECT id, name FROM users");
            assertThat(rewritten).contains("id").contains("name").doesNotContain("ssn");
        }

        @Test
        @DisplayName("Columns directive is silently ignored on UPDATE")
        void givenUpdateWithColumnsThenColumnsAreIgnored() {
            val mapper    = mapperFor("""
                    {"type": "sql:queryManipulation",
                     "conditions": ["tenant_id = 7"], "columns": ["id"]}
                    """);
            val rewritten = mapper.apply("UPDATE users SET name = 'x' WHERE id = 1");
            assertThat(rewritten).contains("UPDATE").contains("tenant_id = 7");
        }
    }

    @Nested
    @DisplayName("Combined conditions and columns")
    class Combined {

        @ParameterizedTest(name = "{0}")
        @MethodSource("combinedScenarios")
        @DisplayName("Conditions and columns are both applied")
        void givenConditionsAndColumnsThenBothApplied(String scenarioName, String constraintJson, String inputSql,
                String[] mustContain, String[] mustNotContain) {
            val mapper    = mapperFor(constraintJson);
            val rewritten = mapper.apply(inputSql);
            assertThat(rewritten).satisfies(r -> {
                for (val needle : mustContain) {
                    assertThat(r).contains(needle);
                }
                for (val needle : mustNotContain) {
                    assertThat(r).doesNotContain(needle);
                }
            });
        }

        static Stream<Arguments> combinedScenarios() {
            return Stream.of(arguments("conditions plus column narrowing on SELECT *", """
                    {"type": "sql:queryManipulation",
                     "conditions": ["tenant_id = 7"],
                     "columns": ["id", "name"]}
                    """, "SELECT * FROM users", new String[] { "tenant_id = 7", "id", "name" }, new String[] { "*" }),
                    arguments("conditions plus column intersection on narrow SELECT", """
                            {"type": "sql:queryManipulation",
                             "conditions": ["tenant_id = 7"],
                             "columns": ["id", "name", "ssn"]}
                            """, "SELECT id, name FROM users WHERE active = true",
                            new String[] { "tenant_id = 7", "id", "name", "active" }, new String[] { "ssn" }));
        }
    }

    @Nested
    @DisplayName("Typed criteria input (cross-backend symmetry with Relational and Mongo providers)")
    class TypedCriteriaInput {

        @Test
        @DisplayName("Single equality criterion is rendered to a SQL fragment and AND-injected into the WHERE")
        void whenSingleEqualityCriterionThenInjectedAsSqlFragment() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation",
                     "criteria": [{"column": "tenant_id", "op": "=", "value": 7}]}
                    """);

            val rewritten = mapper.apply("SELECT * FROM users");

            assertThat(rewritten).contains("tenant_id = 7").contains("WHERE");
        }

        @Test
        @DisplayName("Text value is single-quoted and embedded single quotes are doubled")
        void whenTextValueThenSingleQuotedAndEscaped() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation",
                     "criteria": [{"column": "name", "op": "=", "value": "O'Brien"}]}
                    """);

            val rewritten = mapper.apply("SELECT * FROM users");

            assertThat(rewritten).contains("name = 'O''Brien'");
        }

        @Test
        @DisplayName("Boolean value renders as TRUE / FALSE literal")
        void whenBooleanValueThenLiteralTrueOrFalse() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation",
                     "criteria": [{"column": "active", "op": "=", "value": true}]}
                    """);

            val rewritten = mapper.apply("SELECT * FROM users");

            assertThat(rewritten).contains("active = TRUE");
        }

        @Test
        @DisplayName("Null value as = renders to IS NULL idiom (semantically correct SQL)")
        void whenIsNullOperatorThenRendersIsNull() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation",
                     "criteria": [{"column": "deleted_at", "op": "isNull"}]}
                    """);

            val rewritten = mapper.apply("SELECT * FROM users");

            assertThat(rewritten).contains("deleted_at IS NULL");
        }

        @Test
        @DisplayName("isNotNull operator renders to IS NOT NULL")
        void whenIsNotNullOperatorThenRendersIsNotNull() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation",
                     "criteria": [{"column": "verified_at", "op": "isNotNull"}]}
                    """);

            val rewritten = mapper.apply("SELECT * FROM users");

            assertThat(rewritten).contains("verified_at IS NOT NULL");
        }

        @Test
        @DisplayName("In operator with array value renders as IN (...)")
        void whenInOperatorThenRendersAsInList() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation",
                     "criteria": [{"column": "category", "op": "in", "value": [1, 2, 3]}]}
                    """);

            val rewritten = mapper.apply("SELECT * FROM books");

            assertThat(rewritten).contains("category IN (1, 2, 3)");
        }

        @Test
        @DisplayName("Like operator with text value renders as LIKE 'pattern'")
        void whenLikeOperatorThenRendersAsLike() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation",
                     "criteria": [{"column": "title", "op": "like", "value": "%Krynn%"}]}
                    """);

            val rewritten = mapper.apply("SELECT * FROM books");

            assertThat(rewritten).contains("title LIKE '%Krynn%'");
        }

        @Test
        @DisplayName("Multiple top-level criteria are AND-combined")
        void whenMultipleTopLevelCriteriaThenAndCombined() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation",
                     "criteria": [
                       {"column": "tenant_id", "op": "=", "value": 7},
                       {"column": "deleted_at", "op": "isNull"}
                     ]}
                    """);

            val rewritten = mapper.apply("SELECT * FROM users");

            assertThat(rewritten).contains("tenant_id = 7").contains("deleted_at IS NULL").contains("AND");
        }

        @Test
        @DisplayName("OR group within criteria array renders as parenthesised OR-expression")
        void whenOrGroupThenRendersAsParenthesisedOr() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation",
                     "criteria": [{"or": [
                       {"column": "owner_id", "op": "=", "value": "alice"},
                       {"column": "is_public", "op": "=", "value": true}
                     ]}]}
                    """);

            val rewritten = mapper.apply("SELECT * FROM resources");

            assertThat(rewritten).contains("owner_id = 'alice'").contains("is_public = TRUE").contains("OR");
        }

        @Test
        @DisplayName("Typed criteria + string conditions can coexist on one obligation; both contribute")
        void whenTypedCriteriaAndStringConditionsCoexistThenBothApplied() {
            val mapper = mapperFor("""
                    {"type": "sql:queryManipulation",
                     "criteria": [{"column": "tenant_id", "op": "=", "value": 7}],
                     "conditions": ["status = 'active'"]}
                    """);

            val rewritten = mapper.apply("SELECT * FROM users");

            assertThat(rewritten).contains("tenant_id = 7").contains("status = 'active'");
        }

        @Test
        @DisplayName("Unsupported operator on a typed criterion fails closed at planning time")
        void whenUnsupportedOperatorOnTypedCriterionThenThrowsAccessDeniedException() {
            val constraint = v("""
                    {"type": "sql:queryManipulation",
                     "criteria": [{"column": "tenant_id", "op": "is_secretly_equal", "value": 7}]}
                    """);

            assertThatThrownBy(() -> provider.getConstraintHandler(constraint, Set.of(SQL_SIGNAL)))
                    .isInstanceOf(AccessDeniedException.class).hasMessageContaining("Unsupported operator");
        }

        @Test
        @DisplayName("Obligation type 'relational:queryManipulation' is accepted as alias")
        void whenRelationalTypeAliasThenProviderClaimsObligation() {
            val mapper = mapperFor("""
                    {"type": "relational:queryManipulation",
                     "criteria": [{"column": "tenant_id", "op": "=", "value": 7}]}
                    """);

            val rewritten = mapper.apply("SELECT * FROM users");

            assertThat(rewritten).contains("tenant_id = 7");
        }
    }
}
