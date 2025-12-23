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
package io.sapl.functions.libraries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.functions.DefaultFunctionBroker;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * Comprehensive test suite for GraphQLFunctionLibrary.
 * <p/>
 * Tests cover query parsing, validation, security analysis, and edge cases
 * using Lovecraftian-themed test data.
 */
class GraphQLFunctionLibraryTests {

    @Test
    void when_loadedIntoBroker_then_noError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(GraphQLFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    public static final String  MULTI_QUERY  = """
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
        val result = useSchema ? GraphQLFunctionLibrary.validateQuery(Value.of(query), Value.of(schema))
                : GraphQLFunctionLibrary.analyzeQuery(Value.of(query));

        assertThat(result).isNotNull();
        val parsed = ValueJsonMarshaller.toJsonNode(result);
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
                arguments("query { investigator(id: \"1\") { name sanity } }", BASIC_SCHEMA, true, true, "query", 3,
                        "valid query with schema"),
                arguments("mutation { performRitual(name: \"Summon\", participants: 13) { success } }", BASIC_SCHEMA,
                        true, true, "mutation", 2, "valid mutation with schema"),
                arguments("subscription { madnessIncreased { investigatorId } }", BASIC_SCHEMA, true, true,
                        "subscription", 2, "valid subscription with schema"),

                // Valid queries without schema
                arguments("query { investigator(id: \"1\") { name sanity } }", BASIC_SCHEMA, false, true, "query", 3,
                        "valid query without schema"),
                arguments("{ investigator(id: \"1\") { name } }", BASIC_SCHEMA, false, true, "query", 2,
                        "shorthand query syntax"),

                // Invalid queries
                arguments("query { investigator(id: ", BASIC_SCHEMA, true, false, "", 0, "incomplete query"),
                arguments("query { investigator(id: \"1\") { nonExistentField } }", BASIC_SCHEMA, true, false, "", 0,
                        "invalid field"),
                arguments("type Query { invalid syntax }", BASIC_SCHEMA, true, false, "", 0, "invalid schema syntax"));
    }

    @ParameterizedTest
    @CsvSource({ "query InvestigateArkham { investigator(id: \"1\") { name } }, InvestigateArkham",
            "query { investigator(id: \"1\") { name } }, ''" })
    void when_parseOperationWithName_then_extractsCorrectName(String query, String expectedName) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("ast").get("operationName").asText()).isEqualTo(expectedName);
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
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        val fields = parsed.get("fields");
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
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();

        // Handle nested paths like "security.aliasCount" or "ast.fragmentCount"
        val parts = metricName.split("\\.");
        var node  = parsed;
        for (String part : parts) {
            node = node.get(part);
        }
        assertThat(node.asInt()).isEqualTo(expectedValue);
    }

    static Stream<Arguments> provideMetricCalculationTestCases() {
        return Stream.of(
                arguments("query { investigator(id: \"1\") { name sanity forbiddenKnowledge } }", "fieldCount", 4,
                        "field count"),
                arguments("query { investigator(id: \"1\") { name } tomes { title } }", "security.rootFieldCount", 2,
                        "root field count"),
                arguments(
                        "fragment Details on Investigator { name }\nfragment Info on Tome { title }\nquery { investigator(id: \"1\") { ...Details } }",
                        "ast.fragmentCount", 2, "fragment count"),
                arguments(
                        "query { investigator(id: \"1\") { name @include(if: true) forbiddenKnowledge @skip(if: false) sanity @deprecated(reason: \"test\") } }",
                        "security.directiveCount", 3, "directive count"),
                arguments("query { investigator(id: \"1\") { name } }", "security.aliasCount", 0, "no aliases"),
                arguments(
                        "query { first: investigator(id: \"1\") { name } second: investigator(id: \"2\") { name } third: investigator(id: \"3\") { name } }",
                        "security.aliasCount", 3, "multiple aliases"));
    }

    /* Depth Analysis Tests */

    @ParameterizedTest
    @MethodSource("provideDepthTestCases")
    void when_parseQuery_then_calculatesDepth(String query, int expectedDepth, String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("depth").asInt()).isEqualTo(expectedDepth);
    }

    static Stream<Arguments> provideDepthTestCases() {
        return Stream.of(
                // Simple queries
                arguments("query { investigator(id: \"1\") { name } }", 2, "simple query"),
                arguments("query { investigator(id: \"1\") { tomes { rituals { name } } } }", 4, "nested query"),

                // Extreme depth (capped at 100)
                arguments(buildDeeplyNestedQuery(150), 100, "extreme depth 150 capped"),
                arguments(buildDeeplyNestedQuery(120), 100, "extreme depth 120 capped"),
                arguments(buildDeeplyNestedQuery(105), 100, "extreme depth 105 capped"),

                // Multiple branches with different depths
                arguments(MULTI_QUERY, 4, "mixed branch depths"));
    }

    private static String buildDeeplyNestedQuery(int nestingLevel) {
        return "query { investigator(id: \"1\") { " + "tomes { ".repeat(nestingLevel) + "title"
                + " }".repeat(nestingLevel) + " } }";
    }

    /* Introspection Detection Tests */

    @ParameterizedTest
    @MethodSource("provideIntrospectionTestCases")
    void when_parseQuery_then_detectsIntrospectionCorrectly(String query, boolean expectedIntrospection,
            String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("security").get("isIntrospection").asBoolean()).isEqualTo(expectedIntrospection);
    }

    static Stream<Arguments> provideIntrospectionTestCases() {
        return Stream.of(arguments("query { __schema { types { name } } }", true, "schema introspection"),
                arguments("query { investigator(id: \"1\") { __typename name } }", true, "typename introspection"),
                arguments("query { investigator(id: \"1\") { name } }", false, "no introspection"));
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
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        val fieldCount         = parsed.get("fieldCount").asInt();
        val depth              = parsed.get("depth").asInt();
        val expectedComplexity = fieldCount + (depth * GraphQLFunctionLibrary.DEPTH_COMPLEXITY_FACTOR);

        assertThat(parsed.get("complexity").asInt()).isEqualTo(expectedComplexity);
    }

    @ParameterizedTest
    @MethodSource("provideComplexityTestCases")
    void when_calculateWeightedComplexity_then_appliesWeightsCorrectly(String query, String weightsJson,
            int minExpectedComplexity, String scenario) throws JsonProcessingException {
        val parsed     = (ObjectValue) GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val weights    = (ObjectValue) ValueJsonMarshaller.fromJsonNode(new ObjectMapper().readTree(weightsJson));
        val complexity = GraphQLFunctionLibrary.complexity(parsed, weights);

        assertThat(complexity).isNotNull();
        assertThat(((NumberValue) complexity).value().intValue()).isGreaterThanOrEqualTo(minExpectedComplexity);
    }

    static Stream<Arguments> provideComplexityTestCases() {
        return Stream.of(arguments("""
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

                arguments("query { investigator(id: \"1\") { name sanity } }", """
                        {
                          "name": 5
                        }
                        """, 5, "partial weights"),

                arguments("query { investigator(id: \"1\") { name sanity } }", "{}", 2, "no weights"));
    }

    @ParameterizedTest
    @MethodSource("provideComplexityEdgeCases")
    void when_calculateComplexity_withEdgeCases_then_handlesGracefully(String parsedJson, String weightsJson,
            int expectedComplexity) throws JsonProcessingException {
        val parsed     = (ObjectValue) ValueJsonMarshaller.fromJsonNode(new ObjectMapper().readTree(parsedJson));
        val weights    = (ObjectValue) ValueJsonMarshaller.fromJsonNode(new ObjectMapper().readTree(weightsJson));
        val complexity = GraphQLFunctionLibrary.complexity(parsed, weights);

        assertThat(complexity).isNotNull().isEqualTo(Value.of(expectedComplexity));
    }

    static Stream<Arguments> provideComplexityEdgeCases() {
        return Stream.of(arguments("{\"fields\": [], \"depth\": 0}", "{}", 0), arguments("{}", "{}", 0),
                arguments("{\"fields\": \"not-an-array\"}", "{}", 0), arguments("{\"fields\": [\"name\"]}", "{}", 3),
                arguments("{\"fields\": [\"name\", 42, \"sanity\", null], \"depth\": 2}", "{}", 6));
    }

    /* Alias and Batching Tests */

    @ParameterizedTest
    @MethodSource("provideAliasBatchingTestCases")
    void when_parseQueryWithAliases_then_calculatesBatchingMetrics(String query, int expectedAliasCount,
            int expectedBatchingScore, String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("security").get("aliasCount").asInt()).isEqualTo(expectedAliasCount);
        assertThat(parsed.get("security").get("batchingScore").asInt()).isEqualTo(expectedBatchingScore);
    }

    static Stream<Arguments> provideAliasBatchingTestCases() {
        return Stream.of(arguments("""
                query {
                  inv1: investigator(id: "1") { name }
                  inv2: investigator(id: "2") { name }
                }
                """, 2, 12, "two aliases"),

                arguments("""
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
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        val args = parsed.get("ast").get("arguments");
        assertThat(args.has("investigator")).isTrue();
        assertThat(args.get("investigator").get("id").asText()).contains("cthulhu-cultist-42");
    }

    @ParameterizedTest
    @MethodSource("providePaginationTestCases")
    void when_parseQueryWithPagination_then_tracksMaxLimit(String query, int expectedMax, String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("security").get("maxPaginationLimit").asInt()).isEqualTo(expectedMax);
    }

    static Stream<Arguments> providePaginationTestCases() {
        return Stream.of(
                // Basic pagination
                arguments("query { investigators(first: 50) { name } tomes(limit: 100) { title } }", 100,
                        "mixed limits"),
                arguments("query { investigator(id: \"1\") { name } }", 0, "no pagination"),

                // Multiple pagination args
                arguments("query { investigators(first: 10, last: 20, limit: 50) { name } }", 50, "multiple args"),

                // Case-insensitive
                arguments("query { investigators(FIRST: 25) { name } tomes(First: 30) { title } }", 30,
                        "case insensitive"),

                // All pagination arg types
                arguments("query { investigators(first: 42) { name } }", 42, "first arg"),
                arguments("query { investigators(last: 42) { name } }", 42, "last arg"),
                arguments("query { investigators(limit: 42) { name } }", 42, "limit arg"),
                arguments("query { investigators(offset: 42) { name } }", 42, "offset arg"),
                arguments("query { investigators(skip: 42) { name } }", 42, "skip arg"),
                arguments("query { investigators(take: 42) { name } }", 42, "take arg"),

                // Edge cases
                arguments("query { investigators(first: \"not-a-number\") { name } }", 0, "invalid type"),
                arguments("query { investigators(limit: 2147483647) { name } }", Integer.MAX_VALUE, "max int"),
                arguments("query { investigators(first: 999999) { name } }", 999999, "attack scenario"));
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
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        val args = parsed.get("ast").get("arguments").get("investigator");
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
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("valid").asBoolean()).isTrue();
    }

    /* Fragment Analysis Tests */

    @ParameterizedTest
    @MethodSource("provideFragmentCircularityTestCases")
    void when_parseQueryWithFragments_then_detectsCircularityCorrectly(String query, boolean expectedCircular,
            String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("security").get("hasCircularFragments").asBoolean()).isEqualTo(expectedCircular);
    }

    static Stream<Arguments> provideFragmentCircularityTestCases() {
        return Stream.of(arguments("""
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

                arguments("""
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

                arguments("""
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

                arguments("query { investigator(id: \"1\") { name } }", false, "no fragments"));
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
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        val fragments = parsed.get("ast").get("fragments");
        assertThat(fragments.has("InvestigatorDetails")).isTrue();
        assertThat(fragments.get("InvestigatorDetails").get("typeName").asText()).isEqualTo("Investigator");
        assertThat(fragments.get("InvestigatorDetails").get("fields")).hasSize(2);
    }

    /* Directive Analysis Tests */

    @ParameterizedTest
    @MethodSource("provideDirectiveTestCases")
    void when_parseQueryWithDirectives_then_analyzesCorrectly(String query, int expectedCount, double minRatio,
            String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("security").get("directiveCount").asInt()).isEqualTo(expectedCount);
        assertThat(parsed.get("security").get("directivesPerField").asDouble()).isGreaterThanOrEqualTo(minRatio);
    }

    static Stream<Arguments> provideDirectiveTestCases() {
        return Stream.of(
                // No directives
                arguments("query { investigator(id: \"1\") { name } }", 0, 0.0, "no directives"),

                // Multiple directives on fields
                arguments("""
                        query {
                          investigator(id: "1") {
                            name @include(if: true)
                            sanity @include(if: true) @skip(if: false)
                          }
                        }
                        """, 3, 1.0, "multiple directives on fields"),

                // Directives on inline fragments
                arguments("""
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
                arguments("query { investigator(id: \"1\") { " + "name " + "@include(if: true) ".repeat(50) + "} }", 50,
                        10.0, "directive abuse attack"));
    }

    /* Variable Tests */

    @ParameterizedTest
    @MethodSource("provideVariableTestCases")
    void when_parseQueryWithVariables_then_extractsDefaults(String query, Map<String, Object> expectedVariables,
            String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        val variables = parsed.get("ast").get("variables");
        assertThat(variables).hasSize(expectedVariables.size());

        expectedVariables.forEach((name, value) -> {
            assertThat(variables.has(name)).isTrue();
            switch (value) {
            case Integer intVal   -> assertThat(variables.get(name).asInt()).isEqualTo(intVal);
            case String strVal    -> assertThat(variables.get(name).asText()).isEqualTo(strVal);
            case Boolean boolVal  -> assertThat(variables.get(name).asBoolean()).isEqualTo(boolVal);
            case Double doubleVal -> assertThat(variables.get(name).asDouble()).isEqualTo(doubleVal);
            default               -> {
                /* no-op */}
            }
        });
    }

    static Stream<Arguments> provideVariableTestCases() {
        return Stream.of(
                // With defaults
                arguments(
                        "query($id: ID = \"default-investigator\", $includeDetails: Boolean = true) { investigator(id: $id) { name } }",
                        Map.of("id", "default-investigator", "includeDetails", true), "with defaults"),

                // Without defaults (not included)
                arguments("query($id: ID!, $name: String!) { investigator(id: $id) { name } }", Map.of(),
                        "without defaults"),

                // Mixed types
                arguments(
                        "query($intVar: Int = 42, $strVar: String = \"test\", $boolVar: Boolean = true, $floatVar: Float = 2.5) { investigator(id: \"1\") { name } }",
                        Map.of("intVar", 42, "strVar", "test", "boolVar", true, "floatVar", 2.5), "mixed types"),

                // Partial defaults
                arguments(
                        "query($limit: Int, $offset: Int = 10, $nameFilter: String = \"default\") { investigators(limit: $limit, offset: $offset) { name } }",
                        Map.of("offset", 10, "nameFilter", "default"), "partial defaults"),

                // No variables
                arguments("query { investigator(id: \"1\") { name } }", Map.of(), "no variables"));
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
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        val types = parsed.get("ast").get("types");
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
        val result = GraphQLFunctionLibrary.validateQuery(Value.of(query), Value.of(BASIC_SCHEMA));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("valid").asBoolean()).isTrue();
        assertThat(parsed.get("operation").asText()).isEqualTo("query");
        assertThat(parsed.get("depth").asInt()).isLessThanOrEqualTo(5);
        assertThat(parsed.get("security").get("maxPaginationLimit").asInt()).isEqualTo(10);
        assertThat(parsed.get("security").get("isIntrospection").asBoolean()).isFalse();
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
        val result = GraphQLFunctionLibrary.validateQuery(Value.of(query), Value.of(BASIC_SCHEMA));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        val fields = parsed.get("fields");
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
        val parsed  = (ObjectValue) GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val weights = (ObjectValue) ValueJsonMarshaller.fromJsonNode(new ObjectMapper().readTree("""
                {
                  "tomes": 50,
                  "rituals": 30
                }
                """));

        val complexity = GraphQLFunctionLibrary.complexity(parsed, weights);

        assertThat(complexity).isNotNull();
        assertThat(((NumberValue) complexity).value().intValue()).isGreaterThan(50);
    }

    /* Security Tests - Malicious Queries */

    @ParameterizedTest
    @MethodSource("provideMaliciousQueryTestCases")
    void when_parseMaliciousQuery_then_detectsThreatIndicators(String queryTemplate, int repetitions, String metric,
            int minExpectedValue, String scenario) {
        val query  = buildRepeatedQuery(queryTemplate, repetitions);
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        // Handle nested paths
        val parts = metric.split("\\.");
        var node  = parsed;
        for (String part : parts) {
            node = node.get(part);
        }
        assertThat(node.asInt()).isGreaterThanOrEqualTo(minExpectedValue);
    }

    static Stream<Arguments> provideMaliciousQueryTestCases() {
        return Stream.of(
                arguments("investigator%d: investigator(id: \"%d\") { name sanity }", 100, "security.aliasCount", 100,
                        "alias batching attack"),
                arguments("investigator%d: investigator(id: \"%d\") { name sanity }", 100, "security.batchingScore",
                        500, "batching score attack"),
                arguments("name ", 120, "fieldCount", 100, "field repetition attack"),
                arguments("tomes { ", 150, "depth", 100, "depth bomb attack"));
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
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("depth").asInt()).isGreaterThan(4);
        assertThat(parsed.get("security").get("aliasCount").asInt()).isGreaterThan(0);
        assertThat(parsed.get("security").get("directiveCount").asInt()).isGreaterThan(0);
        assertThat(parsed.get("security").get("maxPaginationLimit").asInt()).isGreaterThan(800000);
    }

    /* Edge Case Tests */

    @ParameterizedTest
    @MethodSource("provideEdgeCaseTestCases")
    void when_parseEdgeCases_then_handlesCorrectly(String query, boolean shouldBeValid, String scenario) {
        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("valid").asBoolean()).isEqualTo(shouldBeValid);
    }

    static Stream<Arguments> provideEdgeCaseTestCases() {
        return Stream.of(
                // Comments
                arguments("""
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
                arguments("query { investigator(id: \"The \\\"Dreamer\\\" of R'lyeh\") { name } }", true,
                        "escaped quotes"),
                arguments("query { investigator(id: \"克苏鲁\") { name } }", true, "Chinese characters in string"),
                arguments("query { investigator(id: \"ℵ查询א\") { name } }", true, "unicode in string"),
                arguments("query { investigator(id: \"Поиск\") { name } }", true, "Cyrillic in string"),
                arguments("query { investigator(id: \"🔮\") { name } }", true, "emoji in string"),

                // Invalid unicode in operation names (per GraphQL spec)
                arguments("query ℵ查询 { investigator(id: \"1\") { name } }", false,
                        "unicode in operation name (invalid)"),
                arguments("query Поиск { investigator(id: \"1\") { name } }", false,
                        "Cyrillic in operation name (invalid)"));
    }

    @ParameterizedTest
    @MethodSource("provideInvalidSchemaTestCases")
    void when_parseWithInvalidSchema_then_returnsError(String query, String schema) {
        val result = GraphQLFunctionLibrary.validateQuery(Value.of(query), Value.of(schema));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("valid").asBoolean()).isFalse();
    }

    static Stream<Arguments> provideInvalidSchemaTestCases() {
        // Note: Removed test cases with invalid types (number, null) as these are now
        // prevented by compile-time type checking
        return Stream.of(arguments("query { investigator(id: \"1\") { name } }", ""),
                arguments("query { investigator(id: \"1\") { name } }", "type Query { investigator(id: ID! }"));
    }

    /* Schema Parsing Tests */

    @Test
    void when_parseValidSchema_then_returnsValidResult() {
        val result = GraphQLFunctionLibrary.parseSchema(Value.of(BASIC_SCHEMA));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("valid").asBoolean()).isTrue();
        assertThat(parsed.get("ast")).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = { "type Query { invalid syntax }", "type Query {", "", "   \n\t  " })
    void when_parseInvalidSchema_then_returnsError(String schema) {
        val result = GraphQLFunctionLibrary.parseSchema(Value.of(schema));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        assertThat(parsed.get("valid").asBoolean()).isFalse();
        assertThat(parsed.get("errors").size()).isGreaterThan(0);
    }

    @Test
    void when_parseSchema_then_extractsTypes() {
        val result = GraphQLFunctionLibrary.parseSchema(Value.of(BASIC_SCHEMA));
        val parsed = ValueJsonMarshaller.toJsonNode(result);

        assertThat(result).isNotNull();
        val ast = parsed.get("ast");
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
        val result               = GraphQLFunctionLibrary.parseSchema(Value.of(schemaWithDirectives));

        assertThat(result).isNotNull();
        val parsed = ValueJsonMarshaller.toJsonNode(result);
        assertThat(parsed.get("valid").asBoolean()).isTrue();
        val ast = parsed.get("ast");
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
        val result            = GraphQLFunctionLibrary.parseSchema(Value.of(schemaWithScalars));

        assertThat(result).isNotNull();
        val parsed = ValueJsonMarshaller.toJsonNode(result);
        assertThat(parsed.get("valid").asBoolean()).isTrue();
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

        val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(queryBuilder.toString()));

        assertThat(result).isNotNull();
        assertThat(ValueJsonMarshaller.isJsonCompatible(result)).isTrue();
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
            val result = GraphQLFunctionLibrary.analyzeQuery(Value.of(query));

            assertThat(result).isNotNull();
            assertThat(ValueJsonMarshaller.isJsonCompatible(result)).isTrue();
        }
    }
}
