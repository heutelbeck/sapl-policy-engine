/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static io.sapl.assertj.SaplAssertions.assertThatVal;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprehensive test suite for GraphQLFunctionLibrary.
 * <p/>
 * Tests cover query parsing, validation, security analysis, and edge cases
 * using Lovecraftian-themed test data.
 */
class GraphQLFunctionLibraryTests {

    private static final String BASIC_SCHEMA = """
            type Query {
              investigator(id: ID!): Investigator
              investigators(first: Int, last: Int, limit: Int): [Investigator]
              tome(title: String!): Tome
              tomes(first: Int, last: Int, limit: Int): [Tome]
              cultist(name: String!): Cultist
              ritual(name: String!): Ritual
            }

            type Mutation {
              performRitual(name: String!, participants: Int!): RitualResult
              banishEntity(name: String!): Boolean
              sealTome(title: String!): Tome
            }

            type Subscription {
              madnessIncreased: MadnessEvent
              entitySummoned: Entity
            }

            type Investigator {
              id: ID!
              name: String!
              sanity: Int!
              forbiddenKnowledge: String
              tomes(first: Int, last: Int, limit: Int): [Tome]
              encounteredEntities(first: Int, last: Int, limit: Int): [Entity]
            }

            type Tome {
              title: String!
              author: String
              dangerLevel: Int!
              contents: String
              rituals: [Ritual]
            }

            type Cultist {
              name: String!
              allegiance: String!
              rituals: [Ritual]
            }

            type Ritual {
              name: String!
              requirements: String!
              consequences: String
            }

            type Entity {
              name: String!
              realm: String!
              power: Int!
            }

            type RitualResult {
              success: Boolean!
              message: String!
            }

            type MadnessEvent {
              investigatorId: ID!
              newSanity: Int!
            }
            """;

    /* Basic Parsing Tests */

    @ParameterizedTest
    @MethodSource("provideBasicParsingTestCases")
    void when_parseQuery_then_returnsExpectedResult(String query, String schema, boolean useSchema,
            boolean expectedValid, String expectedOperation, int minFieldCount, String scenario) {
        val result = useSchema ? GraphQLFunctionLibrary.validateQuery(Val.of(query), Val.of(schema))
                : GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val parsed = result.get();
        assertThat(parsed.get("valid").asBoolean()).isEqualTo(expectedValid);

        if (expectedValid) {
            assertThat(parsed.get("operation").asText()).isEqualTo(expectedOperation);
            assertThat(parsed.get("fields").size()).isGreaterThanOrEqualTo(minFieldCount);
        } else {
            assertThat(parsed.get("errors").size()).isGreaterThan(0);
        }
    }

    static Stream<Arguments> provideBasicParsingTestCases() {
        return Stream.of(
                // Valid queries with schema
                Arguments.of("query { investigator(id: \"1\") { name sanity } }", BASIC_SCHEMA, true, true, "query", 3,
                        "valid query with schema"),
                Arguments.of("mutation { performRitual(name: \"Summon\", participants: 13) { success } }", BASIC_SCHEMA,
                        true, true, "mutation", 2, "valid mutation with schema"),
                Arguments.of("subscription { madnessIncreased { investigatorId } }", BASIC_SCHEMA, true, true,
                        "subscription", 2, "valid subscription with schema"),

                // Valid queries without schema
                Arguments.of("query { investigator(id: \"1\") { name sanity } }", BASIC_SCHEMA, false, true, "query", 3,
                        "valid query without schema"),
                Arguments.of("{ investigator(id: \"1\") { name } }", BASIC_SCHEMA, false, true, "query", 2,
                        "shorthand query syntax"),

                // Invalid queries
                Arguments.of("query { investigator(id: ", BASIC_SCHEMA, true, false, "", 0, "incomplete query"),
                Arguments.of("query { investigator(id: \"1\") { nonExistentField } }", BASIC_SCHEMA, true, false, "", 0,
                        "invalid field"),
                Arguments.of("type Query { invalid syntax }", BASIC_SCHEMA, true, false, "", 0,
                        "invalid schema syntax"));
    }

    @ParameterizedTest
    @CsvSource({ "query InvestigateArkham { investigator(id: \"1\") { name } }, InvestigateArkham",
            "query { investigator(id: \"1\") { name } }, ''" })
    void when_parseOperationWithName_then_extractsCorrectName(String query, String expectedName) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("ast").get("operationName").asText()).isEqualTo(expectedName);
    }

    /* Field Analysis Tests */

    @Test
    void when_parseNestedFields_then_extractsAllFields() {
        val query  = """
                query {
                  investigator(id: "1") {
                    name
                    sanity
                    tomes {
                      title
                      rituals {
                        name
                      }
                    }
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val fields = result.get().get("fields");
        assertThat(fields).hasSize(7);

        val fieldNames = new java.util.ArrayList<String>();
        fields.forEach(node -> fieldNames.add(node.asText()));

        assertThat(fieldNames).containsExactlyInAnyOrder("investigator", "name", "sanity", "tomes", "title", "rituals",
                "name");
    }

    @ParameterizedTest
    @MethodSource("provideMetricCalculationTestCases")
    void when_parseQuery_then_calculatesMetricsCorrectly(String query, String metricName, int expectedValue,
            String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();

        // Handle nested paths like "security.aliasCount" or "ast.fragmentCount"
        val parts = metricName.split("\\.");
        var node  = result.get();
        for (String part : parts) {
            node = node.get(part);
        }
        assertThat(node.asInt()).isEqualTo(expectedValue);
    }

    static Stream<Arguments> provideMetricCalculationTestCases() {
        return Stream.of(
                Arguments.of("query { investigator(id: \"1\") { name sanity forbiddenKnowledge } }", "fieldCount", 4,
                        "field count"),
                Arguments.of("query { investigator(id: \"1\") { name } tomes { title } }", "security.rootFieldCount", 2,
                        "root field count"),
                Arguments.of(
                        "fragment Details on Investigator { name }\nfragment Info on Tome { title }\nquery { investigator(id: \"1\") { ...Details } }",
                        "ast.fragmentCount", 2, "fragment count"),
                Arguments.of(
                        "query { investigator(id: \"1\") { name @include(if: true) forbiddenKnowledge @skip(if: false) sanity @deprecated(reason: \"test\") } }",
                        "security.directiveCount", 3, "directive count"),
                Arguments.of("query { investigator(id: \"1\") { name } }", "security.aliasCount", 0, "no aliases"),
                Arguments.of(
                        "query { first: investigator(id: \"1\") { name } second: investigator(id: \"2\") { name } third: investigator(id: \"3\") { name } }",
                        "security.aliasCount", 3, "multiple aliases"));
    }

    /* Depth Analysis Tests */

    @ParameterizedTest
    @MethodSource("provideDepthTestCases")
    void when_parseQuery_then_calculatesDepth(String query, int expectedDepth, String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("depth").asInt()).isEqualTo(expectedDepth);
    }

    static Stream<Arguments> provideDepthTestCases() {
        return Stream.of(
                // Simple queries
                Arguments.of("query { investigator(id: \"1\") { name } }", 2, "simple query"),
                Arguments.of("query { investigator(id: \"1\") { tomes { rituals { name } } } }", 4, "nested query"),

                // Extreme depth (capped at 100)
                Arguments.of(buildDeeplyNestedQuery(150), 100, "extreme depth 150 capped"),
                Arguments.of(buildDeeplyNestedQuery(120), 100, "extreme depth 120 capped"),
                Arguments.of(buildDeeplyNestedQuery(105), 100, "extreme depth 105 capped"),

                // Multiple branches with different depths
                Arguments.of(buildMultiBranchQuery(), 4, "mixed branch depths"));
    }

    private static String buildDeeplyNestedQuery(int nestingLevel) {
        return "query { investigator(id: \"1\") { " + "tomes { ".repeat(nestingLevel) + "title"
                + " }".repeat(nestingLevel) + " } }";
    }

    private static String buildMultiBranchQuery() {
        return """
                query {
                  shallowBranch: investigator(id: "1") {
                    name
                    sanity
                  }
                  deepBranch: investigator(id: "2") {
                    tomes {
                      rituals {
                        name
                        consequences
                      }
                    }
                  }
                  mediumBranch: cultist(name: "Cthulhu") {
                    rituals {
                      name
                    }
                  }
                }
                """;
    }

    /* Introspection Detection Tests */

    @ParameterizedTest
    @MethodSource("provideIntrospectionTestCases")
    void when_parseQuery_then_detectsIntrospectionCorrectly(String query, boolean expectedIntrospection,
            String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("security").get("isIntrospection").asBoolean()).isEqualTo(expectedIntrospection);
    }

    static Stream<Arguments> provideIntrospectionTestCases() {
        return Stream.of(Arguments.of("query { __schema { types { name } } }", true, "schema introspection"),
                Arguments.of("query { investigator(id: \"1\") { __typename name } }", true, "typename introspection"),
                Arguments.of("query { investigator(id: \"1\") { name } }", false, "no introspection"));
    }

    /* Complexity Tests */

    @Test
    void when_parseQuery_then_calculatesBasicComplexity() {
        val query  = """
                query {
                  investigator(id: "1") {
                    name
                    tomes {
                      title
                    }
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val fieldCount         = result.get().get("fieldCount").asInt();
        val depth              = result.get().get("depth").asInt();
        val expectedComplexity = fieldCount + (depth * GraphQLFunctionLibrary.DEPTH_COMPLEXITY_FACTOR);

        assertThat(result.get().get("complexity").asInt()).isEqualTo(expectedComplexity);
    }

    @ParameterizedTest
    @MethodSource("provideComplexityTestCases")
    void when_calculateWeightedComplexity_then_appliesWeightsCorrectly(String query, String weightsJson,
            int minExpectedComplexity, String scenario) throws JsonProcessingException {
        val parsed     = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));
        val weights    = Val.ofJson(weightsJson);
        val complexity = GraphQLFunctionLibrary.complexity(parsed, weights);

        assertThatVal(complexity).hasValue();
        assertThat(complexity.get().asInt()).isGreaterThanOrEqualTo(minExpectedComplexity);
    }

    static Stream<Arguments> provideComplexityTestCases() {
        return Stream.of(Arguments.of("""
                query {
                  investigator(id: "1") {
                    name
                    tomes {
                      title
                    }
                  }
                }
                """, """
                {
                  "tomes": 10,
                  "name": 1,
                  "title": 2
                }
                """, 10, "weighted fields"),

                Arguments.of("query { investigator(id: \"1\") { name sanity } }", """
                        {
                          "name": 5
                        }
                        """, 5, "partial weights"),

                Arguments.of("query { investigator(id: \"1\") { name sanity } }", "{}", 2, "no weights"));
    }

    @ParameterizedTest
    @MethodSource("provideComplexityEdgeCases")
    void when_calculateComplexity_withEdgeCases_then_handlesGracefully(String parsedJson, String weightsJson,
            int expectedComplexity) throws JsonProcessingException {
        val parsed     = Val.ofJson(parsedJson);
        val weights    = Val.ofJson(weightsJson);
        val complexity = GraphQLFunctionLibrary.complexity(parsed, weights);

        assertThatVal(complexity).hasValue();
        assertThat(complexity.get().asInt()).isEqualTo(expectedComplexity);
    }

    static Stream<Arguments> provideComplexityEdgeCases() {
        return Stream.of(Arguments.of("{\"fields\": [], \"depth\": 0}", "{}", 0), Arguments.of("{}", "{}", 0),
                Arguments.of("{\"fields\": \"not-an-array\"}", "{}", 0),
                Arguments.of("{\"fields\": [\"name\"]}", "{}", 3),
                Arguments.of("{\"fields\": [\"name\", 42, \"sanity\", null], \"depth\": 2}", "{}", 6));
    }

    /* Alias and Batching Tests */

    @ParameterizedTest
    @MethodSource("provideAliasBatchingTestCases")
    void when_parseQueryWithAliases_then_calculatesBatchingMetrics(String query, int expectedAliasCount,
            int expectedBatchingScore, String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("security").get("aliasCount").asInt()).isEqualTo(expectedAliasCount);
        assertThat(result.get().get("security").get("batchingScore").asInt()).isEqualTo(expectedBatchingScore);
    }

    static Stream<Arguments> provideAliasBatchingTestCases() {
        return Stream.of(Arguments.of("""
                query {
                  inv1: investigator(id: "1") { name }
                  inv2: investigator(id: "2") { name }
                }
                """, 2, 12, "two aliases"),

                Arguments.of("""
                        query {
                          investigator(id: "1") { name }
                          tomes { title }
                        }
                        """, 0, 2, "no aliases"));
    }

    /* Argument Analysis Tests */

    @Test
    void when_parseQueryWithArguments_then_extractsArguments() {
        val query  = """
                query {
                  investigator(id: "cthulhu-cultist-42") {
                    name
                    tomes(first: 10) {
                      title
                    }
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val args = result.get().get("ast").get("arguments");
        assertThat(args.has("investigator")).isTrue();
        assertThat(args.get("investigator").get("id").asText()).contains("cthulhu-cultist-42");
    }

    @ParameterizedTest
    @MethodSource("providePaginationTestCases")
    void when_parseQueryWithPagination_then_tracksMaxLimit(String query, int expectedMax, String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("security").get("maxPaginationLimit").asInt()).isEqualTo(expectedMax);
    }

    static Stream<Arguments> providePaginationTestCases() {
        return Stream.of(
                // Basic pagination
                Arguments.of("query { investigators(first: 50) { name } tomes(limit: 100) { title } }", 100,
                        "mixed limits"),
                Arguments.of("query { investigator(id: \"1\") { name } }", 0, "no pagination"),

                // Multiple pagination args
                Arguments.of("query { investigators(first: 10, last: 20, limit: 50) { name } }", 50, "multiple args"),

                // Case insensitive
                Arguments.of("query { investigators(FIRST: 25) { name } tomes(First: 30) { title } }", 30,
                        "case insensitive"),

                // All pagination arg types
                Arguments.of("query { investigators(first: 42) { name } }", 42, "first arg"),
                Arguments.of("query { investigators(last: 42) { name } }", 42, "last arg"),
                Arguments.of("query { investigators(limit: 42) { name } }", 42, "limit arg"),
                Arguments.of("query { investigators(offset: 42) { name } }", 42, "offset arg"),
                Arguments.of("query { investigators(skip: 42) { name } }", 42, "skip arg"),
                Arguments.of("query { investigators(take: 42) { name } }", 42, "take arg"),

                // Edge cases
                Arguments.of("query { investigators(first: \"not-a-number\") { name } }", 0, "invalid type"),
                Arguments.of("query { investigators(limit: 2147483647) { name } }", Integer.MAX_VALUE, "max int"),
                Arguments.of("query { investigators(first: 999999) { name } }", 999999, "attack scenario"));
    }

    @Test
    void when_parseArgumentsWithComplexTypes_then_preservesStructure() {
        val query  = """
                query {
                  investigator(
                    filters: {
                      sanityMin: 50,
                      hasKnowledge: true,
                      names: ["Carter", "Ward", "Armitage"]
                    }
                  ) {
                    name
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val args = result.get().get("ast").get("arguments").get("investigator");
        assertThat(args).isNotNull();

        val filters = args.get("filters");
        assertThat(filters.isObject()).isTrue();
        assertThat(filters.get("sanityMin").asInt()).isEqualTo(50);
        assertThat(filters.get("hasKnowledge").asBoolean()).isTrue();
        assertThat(filters.get("names").isArray()).isTrue();
        assertThat(filters.get("names")).hasSize(3);
        assertThat(filters.get("names").get(0).asText()).isEqualTo("Carter");
    }

    @ParameterizedTest
    @ValueSource(strings = { "query { investigator(id: \"1\", filter: null) { name } }",
            "query { investigator(status: ACTIVE) { name } }",
            "query { investigator(coordinates: [[1, 2], [3, 4]]) { name } }",
            "query { investigator(id: $investigatorId) { name } }" })
    void when_parseArgumentsWithEdgeCases_then_handlesCorrectly(String query) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isTrue();
    }

    /* Fragment Analysis Tests */

    @ParameterizedTest
    @MethodSource("provideFragmentCircularityTestCases")
    void when_parseQueryWithFragments_then_detectsCircularityCorrectly(String query, boolean expectedCircular,
            String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("security").get("hasCircularFragments").asBoolean()).isEqualTo(expectedCircular);
    }

    static Stream<Arguments> provideFragmentCircularityTestCases() {
        return Stream.of(Arguments.of("""
                fragment InvestigatorWithTomes on Investigator {
                  name
                  tomes {
                    ...TomeWithRituals
                  }
                }

                fragment TomeWithRituals on Tome {
                  title
                  rituals {
                    name
                    investigator {
                      ...InvestigatorWithTomes
                    }
                  }
                }

                query {
                  investigator(id: "1") {
                    ...InvestigatorWithTomes
                  }
                }
                """, true, "circular fragments"),

                Arguments.of("""
                        fragment SelfRef on Investigator {
                          name
                          ... SelfRef
                        }

                        query {
                          investigator(id: "1") {
                            ...SelfRef
                          }
                        }
                        """, true, "self-referencing fragment"),

                Arguments.of("""
                        fragment BasicInfo on Investigator {
                          name
                          sanity
                        }

                        query {
                          investigator(id: "1") {
                            ...BasicInfo
                          }
                        }
                        """, false, "non-circular fragments"),

                Arguments.of("query { investigator(id: \"1\") { name } }", false, "no fragments"));
    }

    @Test
    void when_parseQueryWithFragments_then_extractsFragmentDetails() {
        val query  = """
                fragment InvestigatorDetails on Investigator {
                  name
                  sanity
                }

                query {
                  investigator(id: "1") {
                    ...InvestigatorDetails
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val fragments = result.get().get("ast").get("fragments");
        assertThat(fragments.has("InvestigatorDetails")).isTrue();
        assertThat(fragments.get("InvestigatorDetails").get("typeName").asText()).isEqualTo("Investigator");
        assertThat(fragments.get("InvestigatorDetails").get("fields")).hasSize(2);
    }

    /* Directive Analysis Tests */

    @ParameterizedTest
    @MethodSource("provideDirectiveTestCases")
    void when_parseQueryWithDirectives_then_analyzesCorrectly(String query, int expectedCount, double minRatio,
            String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("security").get("directiveCount").asInt()).isEqualTo(expectedCount);
        assertThat(result.get().get("security").get("directivesPerField").asDouble()).isGreaterThanOrEqualTo(minRatio);
    }

    static Stream<Arguments> provideDirectiveTestCases() {
        return Stream.of(
                // No directives
                Arguments.of("query { investigator(id: \"1\") { name } }", 0, 0.0, "no directives"),

                // Multiple directives on fields
                Arguments.of("""
                        query {
                          investigator(id: "1") {
                            name @include(if: true)
                            sanity @include(if: true) @skip(if: false)
                          }
                        }
                        """, 3, 1.0, "multiple directives on fields"),

                // Directives on inline fragments
                Arguments.of("""
                        query {
                          investigator(id: "1") {
                            ... @include(if: true) @skip(if: false) {
                              name
                              sanity
                            }
                          }
                        }
                        """, 2, 0.5, "directives on inline fragments"),

                // Directive abuse (attack scenario)
                Arguments.of("query { investigator(id: \"1\") { " + "name " + "@include(if: true) ".repeat(50) + "} }",
                        50, 10.0, "directive abuse attack"));
    }

    /* Variable Tests */

    @ParameterizedTest
    @MethodSource("provideVariableTestCases")
    void when_parseQueryWithVariables_then_extractsDefaults(String query, Map<String, Object> expectedVariables,
            String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val variables = result.get().get("ast").get("variables");
        assertThat(variables).hasSize(expectedVariables.size());

        expectedVariables.forEach((name, value) -> {
            assertThat(variables.has(name)).isTrue();
            switch (value) {
            case Integer intVal   -> assertThat(variables.get(name).asInt()).isEqualTo(intVal);
            case String strVal    -> assertThat(variables.get(name).asText()).isEqualTo(strVal);
            case Boolean boolVal  -> assertThat(variables.get(name).asBoolean()).isEqualTo(boolVal);
            case Double doubleVal -> assertThat(variables.get(name).asDouble()).isEqualTo(doubleVal);
            default               -> {}
            }
        });
    }

    static Stream<Arguments> provideVariableTestCases() {
        return Stream.of(
                // With defaults
                Arguments.of(
                        "query($id: ID = \"default-investigator\", $includeDetails: Boolean = true) { investigator(id: $id) { name } }",
                        Map.of("id", "default-investigator", "includeDetails", true), "with defaults"),

                // Without defaults (not included)
                Arguments.of("query($id: ID!, $name: String!) { investigator(id: $id) { name } }", Map.of(),
                        "without defaults"),

                // Mixed types
                Arguments.of(
                        "query($intVar: Int = 42, $strVar: String = \"test\", $boolVar: Boolean = true, $floatVar: Float = 2.5) { investigator(id: \"1\") { name } }",
                        Map.of("intVar", 42, "strVar", "test", "boolVar", true, "floatVar", 2.5), "mixed types"),

                // Partial defaults
                Arguments.of(
                        "query($limit: Int, $offset: Int = 10, $nameFilter: String = \"default\") { investigators(limit: $limit, offset: $offset) { name } }",
                        Map.of("offset", 10, "nameFilter", "default"), "partial defaults"),

                // No variables
                Arguments.of("query { investigator(id: \"1\") { name } }", Map.of(), "no variables"));
    }

    /* Type Information Tests */

    @Test
    void when_parseQueryWithInlineFragments_then_extractsTypes() {
        val query  = """
                query {
                  investigator(id: "1") {
                    ... on Investigator {
                      name
                      sanity
                    }
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val types = result.get().get("ast").get("types");
        assertThat(types.size()).isGreaterThan(0);
    }

    /* Realistic Use Case Tests */

    @Test
    void when_parseTypicalInvestigationQuery_then_analyzesCorrectly() {
        val query  = """
                query InvestigateArkham($investigatorId: ID!) {
                  investigator(id: $investigatorId) {
                    name
                    sanity
                    tomes(first: 10) {
                      title
                      dangerLevel
                      author
                    }
                    encounteredEntities(limit: 5) {
                      name
                      realm
                      power
                    }
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.validateQuery(Val.of(query), Val.of(BASIC_SCHEMA));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isTrue();
        assertThat(result.get().get("operation").asText()).isEqualTo("query");
        assertThat(result.get().get("depth").asInt()).isLessThanOrEqualTo(5);
        assertThat(result.get().get("security").get("maxPaginationLimit").asInt()).isEqualTo(10);
        assertThat(result.get().get("security").get("isIntrospection").asBoolean()).isFalse();
    }

    @Test
    void when_analyzeQueryForSensitiveFields_then_detectsForbiddenKnowledge() {
        val query  = """
                query {
                  investigator(id: "1") {
                    name
                    forbiddenKnowledge
                    sanity
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.validateQuery(Val.of(query), Val.of(BASIC_SCHEMA));

        assertThatVal(result).hasValue();
        val fields = result.get().get("fields");
        assertThat(fields).extracting(JsonNode::asText).contains("forbiddenKnowledge");
    }

    @Test
    void when_enforceComplexityLimit_then_detectsViolation() throws JsonProcessingException {
        val query   = """
                query {
                  investigator(id: "1") {
                    tomes {
                      title
                      rituals {
                        name
                      }
                    }
                  }
                }
                """;
        val parsed  = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));
        val weights = Val.ofJson("""
                {
                  "tomes": 50,
                  "rituals": 30
                }
                """);

        val complexity = GraphQLFunctionLibrary.complexity(parsed, weights);

        assertThatVal(complexity).hasValue();
        assertThat(complexity.get().asInt()).isGreaterThan(50);
    }

    /* Security Tests - Malicious Queries */

    @ParameterizedTest
    @MethodSource("provideMaliciousQueryTestCases")
    void when_parseMaliciousQuery_then_detectsThreatIndicators(String queryTemplate, int repetitions, String metric,
            int minExpectedValue, String scenario) {
        val query  = buildRepeatedQuery(queryTemplate, repetitions);
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        // Handle nested paths
        val parts = metric.split("\\.");
        var node  = result.get();
        for (String part : parts) {
            node = node.get(part);
        }
        assertThat(node.asInt()).isGreaterThanOrEqualTo(minExpectedValue);
    }

    static Stream<Arguments> provideMaliciousQueryTestCases() {
        return Stream.of(
                Arguments.of("investigator%d: investigator(id: \"%d\") { name sanity }", 100, "security.aliasCount",
                        100, "alias batching attack"),
                Arguments.of("investigator%d: investigator(id: \"%d\") { name sanity }", 100, "security.batchingScore",
                        500, "batching score attack"),
                Arguments.of("name ", 120, "fieldCount", 100, "field repetition attack"),
                Arguments.of("tomes { ", 150, "depth", 100, "depth bomb attack"));
    }

    private static String buildRepeatedQuery(String template, int repetitions) {
        val queryBuilder = new StringBuilder("query {\n");

        if (template.contains("%d")) {
            for (int i = 1; i <= repetitions; i++) {
                queryBuilder.append("  ").append(String.format(template, i, i)).append('\n');
            }
        } else if (template.startsWith("tomes")) {
            queryBuilder.append("investigator(id: \"1\") { ");
            queryBuilder.append(template.repeat(repetitions));
            queryBuilder.append("title");
            queryBuilder.append(" }".repeat(repetitions));
            queryBuilder.append(" }");
        } else {
            queryBuilder.append("investigator(id: \"1\") { ");
            queryBuilder.append(template.repeat(repetitions));
            queryBuilder.append('}');
        }

        queryBuilder.append('}');
        return queryBuilder.toString();
    }

    @Test
    void when_parseCombinedAttackQuery_then_detectsMultipleThreatIndicators() {
        val query  = """
                query MaliciousQuery {
                  inv1: investigator(id: "1") {
                    name @include(if: true) @skip(if: false)
                    tomes(first: 999999) {
                      title
                      rituals {
                        name
                        cultist {
                          name
                          rituals {
                            name
                          }
                        }
                      }
                    }
                  }
                  inv2: investigator(id: "2") {
                    name @include(if: true)
                    tomes(limit: 888888) {
                      title
                    }
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("depth").asInt()).isGreaterThan(4);
        assertThat(result.get().get("security").get("aliasCount").asInt()).isGreaterThan(0);
        assertThat(result.get().get("security").get("directiveCount").asInt()).isGreaterThan(0);
        assertThat(result.get().get("security").get("maxPaginationLimit").asInt()).isGreaterThan(800000);
    }

    /* Edge Case Tests */

    @ParameterizedTest
    @MethodSource("provideEdgeCaseTestCases")
    void when_parseEdgeCases_then_handlesCorrectly(String query, boolean shouldBeValid, String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isEqualTo(shouldBeValid);
    }

    static Stream<Arguments> provideEdgeCaseTestCases() {
        return Stream.of(
                // Comments
                Arguments.of("""
                        # This is a comment about Cthulhu
                        query {
                          # Query the investigator
                          investigator(id: "1") {
                            name # The investigator's name
                            sanity
                          }
                        }
                        """, true, "query with comments"),

                // Special characters in strings
                Arguments.of("query { investigator(id: \"The \\\"Dreamer\\\" of R'lyeh\") { name } }", true,
                        "escaped quotes"),
                Arguments.of("query { investigator(id: \"克苏鲁\") { name } }", true, "Chinese characters in string"),
                Arguments.of("query { investigator(id: \"ℵ查询א\") { name } }", true, "unicode in string"),
                Arguments.of("query { investigator(id: \"Поиск\") { name } }", true, "Cyrillic in string"),
                Arguments.of("query { investigator(id: \"🔮\") { name } }", true, "emoji in string"),

                // Invalid unicode in operation names (per GraphQL spec)
                Arguments.of("query ℵ查询 { investigator(id: \"1\") { name } }", false,
                        "unicode in operation name (invalid)"),
                Arguments.of("query Поиск { investigator(id: \"1\") { name } }", false,
                        "Cyrillic in operation name (invalid)"));
    }

    /* Error Handling Tests */

    @ParameterizedTest
    @MethodSource("provideInvalidInputTestCases")
    void when_parseWithInvalidInput_then_returnsError(Val input, String description) {
        val result = GraphQLFunctionLibrary.analyzeQuery(input);

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isFalse();
        assertThat(result.get().get("errors").size()).isGreaterThan(0);
    }

    static Stream<Arguments> provideInvalidInputTestCases() {
        return Stream.of(Arguments.of(Val.of(42), "numeric input"), Arguments.of(Val.NULL, "null input"),
                Arguments.of(Val.UNDEFINED, "undefined input"));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidSchemaTestCases")
    void when_parseWithInvalidSchema_then_returnsError(String query, Val schema) {
        val result = GraphQLFunctionLibrary.validateQuery(Val.of(query), schema);

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isFalse();
    }

    static Stream<Arguments> provideInvalidSchemaTestCases() {
        return Stream.of(Arguments.of("query { investigator(id: \"1\") { name } }", Val.of(123)),
                Arguments.of("query { investigator(id: \"1\") { name } }", Val.NULL),
                Arguments.of("query { investigator(id: \"1\") { name } }", Val.of("")), Arguments.of(
                        "query { investigator(id: \"1\") { name } }", Val.of("type Query { investigator(id: ID! }")));
    }

    /* Schema Parsing Tests */

    @Test
    void when_parseValidSchema_then_returnsValidResult() {
        val result = GraphQLFunctionLibrary.parseSchema(Val.of(BASIC_SCHEMA));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isTrue();
        assertThat(result.get().get("ast")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "type Query { invalid syntax }", "type Query {", "", "   \n\t  " })
    void when_parseInvalidSchema_then_returnsError(String schema) {
        val result = GraphQLFunctionLibrary.parseSchema(Val.of(schema));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isFalse();
        assertThat(result.get().get("errors").size()).isGreaterThan(0);
    }

    @Test
    void when_parseSchema_then_extractsTypes() {
        val result = GraphQLFunctionLibrary.parseSchema(Val.of(BASIC_SCHEMA));

        assertThatVal(result).hasValue();
        val ast = result.get().get("ast");
        assertThat(ast.has("types")).isTrue();
        assertThat(ast.get("types").size()).isGreaterThan(0);
    }

    @Test
    void when_parseSchemaWithDirectives_then_extractsDirectives() {
        val schemaWithDirectives = """
                directive @auth(requires: String!) on FIELD_DEFINITION

                type Query {
                  investigator(id: ID!): Investigator
                }

                type Investigator {
                  id: ID!
                  name: String!
                }
                """;
        val result               = GraphQLFunctionLibrary.parseSchema(Val.of(schemaWithDirectives));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isTrue();
        val ast = result.get().get("ast");
        assertThat(ast.has("directives")).isTrue();
    }

    @Test
    void when_parseSchemaWithScalars_then_includesScalars() {
        val schemaWithScalars = """
                scalar DateTime
                scalar JSON

                type Query {
                  investigator(id: ID!): Investigator
                }

                type Investigator {
                  id: ID!
                  lastSeen: DateTime
                  metadata: JSON
                }
                """;
        val result            = GraphQLFunctionLibrary.parseSchema(Val.of(schemaWithScalars));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isTrue();
    }

    /* Performance Tests */

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void when_parseComplexQuery_then_completesQuickly() {
        val queryBuilder = new StringBuilder("query ComplexQuery {\n");
        for (int i = 0; i < 20; i++) {
            queryBuilder.append(String.format("""
                    investigator%d: investigator(id: "%d") {
                      name
                      sanity
                      tomes(first: 10) {
                        title
                        dangerLevel
                        rituals {
                          name
                        }
                      }
                    }
                    """, i, i));
        }
        queryBuilder.append('}');

        val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(queryBuilder.toString()));

        assertThatVal(result).hasValue();
    }

    @Test
    @Timeout(value = 1, unit = TimeUnit.SECONDS)
    void when_parseMultipleTimes_then_completesQuickly() {
        val query = """
                query {
                  investigator(id: "1") {
                    name
                    sanity
                    tomes {
                      title
                    }
                  }
                }
                """;

        for (int i = 0; i < 100; i++) {
            val result = GraphQLFunctionLibrary.analyzeQuery(Val.of(query));
            assertThatVal(result).hasValue();
        }
    }
}
