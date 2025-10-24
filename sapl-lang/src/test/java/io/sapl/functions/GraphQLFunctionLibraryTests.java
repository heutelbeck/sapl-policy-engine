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
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

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

    @Test
    void when_parseValidQuery_then_returnsValidResult() {
        val query  = "query { investigator(id: \"1\") { name sanity } }";
        val result = GraphQLFunctionLibrary.parse(Val.of(query), Val.of(BASIC_SCHEMA));

        assertThatVal(result).hasValue();
        val parsed = result.get();
        assertThat(parsed.get("valid").asBoolean()).isTrue();
        assertThat(parsed.get("operation").asText()).isEqualTo("query");
        assertThat(parsed.get("fields").size()).isEqualTo(3);
    }

    @Test
    void when_parseQueryWithoutSchema_then_returnsValidSyntaxResult() {
        val query  = "query { investigator(id: \"1\") { name sanity } }";
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isTrue();
        assertThat(result.get().get("operation").asText()).isEqualTo("query");
    }

    @ParameterizedTest
    @MethodSource("provideInvalidQueryTestCases")
    void when_parseInvalidQuery_then_returnsError(String query, String description) {
        val result = GraphQLFunctionLibrary.parse(Val.of(query), Val.of(BASIC_SCHEMA));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isFalse();
        assertThat(result.get().get("errors").size()).isGreaterThan(0);
    }

    static Stream<Arguments> provideInvalidQueryTestCases() {
        return Stream.of(Arguments.of("query { investigator(id: ", "incomplete query"),
                Arguments.of("query { investigator(id: \"1\") { nonExistentField } }", "invalid field"),
                Arguments.of("type Query { invalid syntax }", "invalid schema syntax"));
    }

    /* Operation Type Tests */

    @ParameterizedTest
    @MethodSource("provideOperationTypeTestCases")
    void when_parseOperation_then_detectsCorrectType(String query, String expectedOperation) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("operation").asText()).isEqualTo(expectedOperation);
    }

    static Stream<Arguments> provideOperationTypeTestCases() {
        return Stream.of(Arguments.of("query { investigator(id: \"1\") { name } }", "query"),
                Arguments.of("mutation { performRitual(name: \"Summon\", participants: 13) { success } }", "mutation"),
                Arguments.of("subscription { madnessIncreased { investigatorId } }", "subscription"),
                Arguments.of("{ investigator(id: \"1\") { name } }", "query"));
    }

    @ParameterizedTest
    @CsvSource({ "query InvestigateArkham { investigator(id: \"1\") { name } }, InvestigateArkham",
            "query { investigator(id: \"1\") { name } }, ''" })
    void when_parseOperationWithName_then_extractsCorrectName(String query, String expectedName) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("operationName").asText()).isEqualTo(expectedName);
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
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val fields = result.get().get("fields");
        assertThat(fields.size()).isEqualTo(7);

        val fieldNames = new java.util.ArrayList<String>();
        fields.forEach(node -> fieldNames.add(node.asText()));

        assertThat(fieldNames).containsExactlyInAnyOrder("investigator", "name", "sanity", "tomes", "title", "rituals",
                "name");
    }

    @ParameterizedTest
    @MethodSource("provideMetricCalculationTestCases")
    void when_parseQuery_then_calculatesMetricsCorrectly(String query, String metricName, int expectedValue) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get(metricName).asInt()).isEqualTo(expectedValue);
    }

    static Stream<Arguments> provideMetricCalculationTestCases() {
        return Stream.of(
                Arguments.of("query { investigator(id: \"1\") { name sanity forbiddenKnowledge } }", "fieldCount", 4),
                Arguments.of("query { investigator(id: \"1\") { name } tomes { title } }", "rootFieldCount", 2),
                Arguments.of(
                        "fragment Details on Investigator { name }\nfragment Info on Tome { title }\nquery { investigator(id: \"1\") { ...Details } }",
                        "fragmentCount", 2),
                Arguments.of(
                        "query { investigator(id: \"1\") { name @include(if: true) forbiddenKnowledge @skip(if: false) sanity @deprecated(reason: \"test\") } }",
                        "directiveCount", 3),
                Arguments.of("query { investigator(id: \"1\") { name } }", "aliasCount", 0),
                Arguments.of(
                        "query { first: investigator(id: \"1\") { name } second: investigator(id: \"2\") { name } third: investigator(id: \"3\") { name } }",
                        "aliasCount", 3));
    }

    /* Depth Analysis Tests */

    @ParameterizedTest
    @CsvSource({ "'query { investigator(id: \"1\") { name } }', 2",
            "'query { investigator(id: \"1\") { tomes { rituals { name } } } }', 4" })
    void when_parseQuery_then_calculatesCorrectDepth(String query, int expectedDepth) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("depth").asInt()).isEqualTo(expectedDepth);
    }

    @ParameterizedTest
    @CsvSource({ "150, 100", "120, 100", "105, 100" })
    void when_parseExtremelyDeepQuery_then_capsDepthAtMaximum(int nestingLevel, int expectedDepth) {
        String queryBuilder = "query { investigator(id: \"1\") { " + "tomes { ".repeat(nestingLevel) + "title"
                + " }".repeat(nestingLevel) + " } }";

        val result = GraphQLFunctionLibrary.parseQuery(Val.of(queryBuilder));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("depth").asInt()).isEqualTo(expectedDepth);
    }

    @Test
    void when_parseQueryWithDifferentBranchDepths_then_tracksMaxDepthCorrectly() {
        val query  = """
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
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("depth").asInt()).isEqualTo(4);
    }

    /* Introspection Detection Tests */

    @ParameterizedTest
    @MethodSource("provideIntrospectionTestCases")
    void when_parseQuery_then_detectsIntrospectionCorrectly(String query, boolean expectedIntrospection) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("isIntrospection").asBoolean()).isEqualTo(expectedIntrospection);
    }

    static Stream<Arguments> provideIntrospectionTestCases() {
        return Stream.of(Arguments.of("query { __schema { types { name } } }", true),
                Arguments.of("query { investigator(id: \"1\") { __typename name } }", true),
                Arguments.of("query { investigator(id: \"1\") { name } }", false));
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
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val fieldCount         = result.get().get("fieldCount").asInt();
        val depth              = result.get().get("depth").asInt();
        val expectedComplexity = fieldCount + (depth * 2);

        assertThat(result.get().get("complexity").asInt()).isEqualTo(expectedComplexity);
    }

    @Test
    void when_calculateWeightedComplexity_then_appliesWeights() throws JsonProcessingException {
        val query   = """
                query {
                  investigator(id: "1") {
                    name
                    tomes {
                      title
                    }
                  }
                }
                """;
        val parsed  = GraphQLFunctionLibrary.parseQuery(Val.of(query));
        val weights = Val.ofJson("""
                {
                  "tomes": 10,
                  "name": 1,
                  "title": 2
                }
                """);

        val complexity = GraphQLFunctionLibrary.complexity(parsed, weights);

        assertThatVal(complexity).hasValue();
        assertThat(complexity.get().asInt()).isGreaterThan(0);
    }

    @Test
    void when_calculateWeightedComplexity_withUnweightedFields_then_usesDefaultWeight() throws JsonProcessingException {
        val query   = "query { investigator(id: \"1\") { name sanity } }";
        val parsed  = GraphQLFunctionLibrary.parseQuery(Val.of(query));
        val weights = Val.ofJson("""
                {
                  "name": 5
                }
                """);

        val complexity = GraphQLFunctionLibrary.complexity(parsed, weights);

        assertThatVal(complexity).hasValue();
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

    @Test
    void when_calculateComplexityWithNullFragments_then_handlesGracefully() {
        val query   = "query { investigator(id: \"1\") { name sanity } }";
        val parsed  = GraphQLFunctionLibrary.parseQuery(Val.of(query));
        val weights = Val.JSON.objectNode();
        weights.put("name", 5);
        weights.put("sanity", 3);

        val complexity = GraphQLFunctionLibrary.complexity(parsed, Val.of(weights));

        assertThatVal(complexity).hasValue();
        assertThat(complexity.get().asInt()).isGreaterThan(0);
    }

    /* Alias Analysis Tests */

    @Test
    void when_parseQueryWithAliases_then_calculatesBatchingScore() {
        val query  = """
                query {
                  inv1: investigator(id: "1") { name }
                  inv2: investigator(id: "2") { name }
                }
                """;
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val aliasCount     = result.get().get("aliasCount").asInt();
        val rootFieldCount = result.get().get("rootFieldCount").asInt();
        val expectedScore  = (aliasCount * 5) + rootFieldCount;

        assertThat(result.get().get("batchingScore").asInt()).isEqualTo(expectedScore);
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
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val args = result.get().get("arguments");
        assertThat(args.has("investigator")).isTrue();
        assertThat(args.get("investigator").get("id").asText()).contains("cthulhu-cultist-42");
    }

    @ParameterizedTest
    @MethodSource("providePaginationLimitTestCases")
    void when_parseQueryWithPaginationArguments_then_extractsMaxLimit(String query, int expectedMaxLimit) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("maxPaginationLimit").asInt()).isEqualTo(expectedMaxLimit);
    }

    static Stream<Arguments> providePaginationLimitTestCases() {
        return Stream.of(Arguments.of("query { investigators(first: 50) { name } tomes(limit: 100) { title } }", 100),
                Arguments.of("query { investigator(id: \"1\") { name } }", 0),
                Arguments.of(
                        "query { investigators(first: 10, last: 20, limit: 50, offset: 5, skip: 3, take: 15) { name } }",
                        50),
                Arguments.of("query { investigators(FIRST: 25) { name } tomes(First: 30) { title } }", 30),
                Arguments.of("query { investigators(first: \"not-a-number\") { name } }", 0),
                Arguments.of("query { investigators(limit: 2147483647) { name } tomes(first: 999999999) { title } }",
                        Integer.MAX_VALUE));
    }

    @ParameterizedTest
    @ValueSource(strings = { "first", "last", "limit", "offset", "skip", "take" })
    void when_parseQueryWithPaginationArg_then_detectsAllPaginationTypes(String argName) {
        val query  = String.format("query { investigators(%s: 42) { name } }", argName);
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("maxPaginationLimit").asInt()).isEqualTo(42);
    }

    @ParameterizedTest
    @MethodSource("provideArgumentTypeTestCases")
    void when_parseQueryWithDifferentArgumentTypes_then_capturesArguments(String query, String fieldName) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val args = result.get().get("arguments");
        assertThat(args.has(fieldName)).isTrue();
    }

    static Stream<Arguments> provideArgumentTypeTestCases() {
        return Stream.of(
                Arguments.of("query { investigator(id: \"1\", includeForbidden: true) { name } }", "investigator"),
                Arguments.of("query { investigator(id: $investigatorId) { name } }", "investigator"),
                Arguments.of("query { investigator(id: \"1\", status: ACTIVE) { name } }", "investigator"));
    }

    @Test
    void when_parseArgumentsWithComplexTypes_then_preservesStructure() throws JsonProcessingException {
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
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val args = result.get().get("arguments").get("investigator");
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
    @MethodSource("provideArgumentEdgeCases")
    void when_parseArgumentsWithEdgeCases_then_handlesCorrectly(String query, String description) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isTrue();
    }

    static Stream<Arguments> provideArgumentEdgeCases() {
        return Stream.of(Arguments.of("query { investigator(id: \"1\", filter: null) { name } }", "null values"),
                Arguments.of("query { investigator(status: ACTIVE) { name } }", "enum values"),
                Arguments.of("query { investigator(coordinates: [[1, 2], [3, 4]]) { name } }", "nested arrays"));
    }

    /* Fragment Analysis Tests */

    @ParameterizedTest
    @MethodSource("provideFragmentCircularityTestCases")
    void when_parseQueryWithFragments_then_detectsCircularityCorrectly(String query, boolean expectedCircular) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("hasCircularFragments").asBoolean()).isEqualTo(expectedCircular);
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
                """, true), Arguments.of("""
                fragment SelfRef on Investigator {
                  name
                  ... SelfRef
                }

                query {
                  investigator(id: "1") {
                    ...SelfRef
                  }
                }
                """, true), Arguments.of("""
                fragment BaseDetails on Investigator {
                  name
                  ... on Investigator {
                    ...BaseDetails
                  }
                }

                query {
                  investigator(id: "1") {
                    ...BaseDetails
                  }
                }
                """, true), Arguments.of("""
                fragment A on Investigator {
                  name
                  ... on Investigator {
                    ...B
                  }
                }

                fragment B on Investigator {
                  sanity
                  ... on Investigator {
                    ...A
                  }
                }

                query {
                  investigator(id: "1") {
                    ...A
                  }
                }
                """, true), Arguments.of("""
                fragment BasicInfo on Investigator {
                  name
                  sanity
                }

                query {
                  investigator(id: "1") {
                    ...BasicInfo
                  }
                }
                """, false), Arguments.of("query { investigator(id: \"1\") { name } }", false));
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
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val fragments = result.get().get("fragments");
        assertThat(fragments.has("InvestigatorDetails")).isTrue();
        assertThat(fragments.get("InvestigatorDetails").get("typeName").asText()).isEqualTo("Investigator");
        assertThat(fragments.get("InvestigatorDetails").get("fields").size()).isEqualTo(2);
    }

    /* Directive Analysis Tests */

    @ParameterizedTest
    @MethodSource("provideDirectiveCountTestCases")
    void when_parseQueryWithDirectives_then_countsDirectivesCorrectly(String query, int expectedDirectiveCount) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("directiveCount").asInt()).isEqualTo(expectedDirectiveCount);
    }

    static Stream<Arguments> provideDirectiveCountTestCases() {
        return Stream.of(Arguments.of("query { investigator(id: \"1\") { name } }", 0), Arguments.of("""
                query {
                  investigator(id: "1") {
                    name @include(if: true)
                    sanity @include(if: true) @skip(if: false)
                  }
                }
                """, 3), Arguments.of("""
                query {
                  investigator(id: "1") {
                    ... @include(if: true) @skip(if: false) {
                      name
                      sanity
                    }
                  }
                }
                """, 2));
    }

    @Test
    void when_parseQueryWithDirectives_then_calculatesDirectivesPerField() {
        val query  = """
                query {
                  investigator(id: "1") {
                    name @include(if: true)
                    sanity @include(if: true) @skip(if: false)
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val directiveCount = result.get().get("directiveCount").asInt();
        val fieldCount     = result.get().get("fieldCount").asInt();
        val expectedRatio  = (double) directiveCount / fieldCount;

        assertThat(result.get().get("directivesPerField").asDouble()).isEqualTo(expectedRatio);
    }

    /* Variable Tests */

    @ParameterizedTest
    @MethodSource("provideVariableTestCases")
    void when_parseQueryWithVariables_then_extractsCorrectly(String query, int expectedVariableCount) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val variables = result.get().get("variables");
        assertThat(variables.size()).isEqualTo(expectedVariableCount);
    }

    static Stream<Arguments> provideVariableTestCases() {
        return Stream.of(Arguments.of("""
                query InvestigateWithDefaults($id: ID = "default-investigator", $includeDetails: Boolean = true) {
                  investigator(id: $id) {
                    name
                  }
                }
                """, 2), Arguments.of("""
                query InvestigateWithoutDefaults($id: ID!, $name: String!) {
                  investigator(id: $id) {
                    name
                  }
                }
                """, 0), Arguments.of("query { investigator(id: \"1\") { name } }", 0));
    }

    @Test
    void when_parseVariableDefaultValues_then_preservesProperTypes() {
        val query  = """
                query($intVar: Int = 42, $strVar: String = "test", $boolVar: Boolean = true, $floatVar: Float = 2.5) {
                  investigator(id: "1") { name }
                }
                """;
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val variables = result.get().get("variables");
        assertThat(variables.get("intVar").isInt()).isTrue();
        assertThat(variables.get("intVar").asInt()).isEqualTo(42);
        assertThat(variables.get("strVar").isTextual()).isTrue();
        assertThat(variables.get("strVar").asText()).isEqualTo("test");
        assertThat(variables.get("boolVar").isBoolean()).isTrue();
        assertThat(variables.get("boolVar").asBoolean()).isTrue();
        assertThat(variables.get("floatVar").isNumber()).isTrue();
        assertThat(variables.get("floatVar").asDouble()).isEqualTo(2.5);
    }

    @Test
    void when_parseQueryWithVariablesWithoutDefaults_then_excludesFromResult() {
        val query  = """
                query($limit: Int, $offset: Int = 10, $nameFilter: String = "default") {
                  investigators(limit: $limit, offset: $offset) {
                    name
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val variables = result.get().get("variables");
        assertThat(variables.has("offset")).isTrue();
        assertThat(variables.get("offset").asInt()).isEqualTo(10);
        assertThat(variables.has("nameFilter")).isTrue();
        assertThat(variables.get("nameFilter").asText()).isEqualTo("default");
        assertThat(variables.has("limit")).isFalse();
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
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        val types = result.get().get("types");
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
        val result = GraphQLFunctionLibrary.parse(Val.of(query), Val.of(BASIC_SCHEMA));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isTrue();
        assertThat(result.get().get("operation").asText()).isEqualTo("query");
        assertThat(result.get().get("depth").asInt()).isLessThanOrEqualTo(5);
        assertThat(result.get().get("maxPaginationLimit").asInt()).isEqualTo(10);
        assertThat(result.get().get("isIntrospection").asBoolean()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("provideRealisticOperationTestCases")
    void when_parseRealisticOperation_then_identifiesCorrectly(String query, String expectedOperation) {
        val result = GraphQLFunctionLibrary.parse(Val.of(query), Val.of(BASIC_SCHEMA));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isTrue();
        assertThat(result.get().get("operation").asText()).isEqualTo(expectedOperation);
    }

    static Stream<Arguments> provideRealisticOperationTestCases() {
        return Stream.of(Arguments.of("""
                mutation PerformDarkRitual($ritualName: String!, $participants: Int!) {
                  performRitual(name: $ritualName, participants: $participants) {
                    success
                    message
                  }
                }
                """, "mutation"), Arguments.of("""
                subscription WatchForMadness {
                  madnessIncreased {
                    investigatorId
                    newSanity
                  }
                }
                """, "subscription"));
    }

    /* Security Tests - Malicious Queries */

    @ParameterizedTest
    @MethodSource("provideMaliciousQueryTestCases")
    void when_parseMaliciousQuery_then_detectsThreatIndicators(String queryTemplate, int repetitions, String metric,
            int minExpectedValue) {
        val query  = buildRepeatedQuery(queryTemplate, repetitions);
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get(metric).asInt()).isGreaterThanOrEqualTo(minExpectedValue);
    }

    static Stream<Arguments> provideMaliciousQueryTestCases() {
        return Stream.of(
                Arguments.of("investigator%d: investigator(id: \"%d\") { name sanity }", 100, "aliasCount", 100),
                Arguments.of("investigator%d: investigator(id: \"%d\") { name sanity }", 100, "batchingScore", 500),
                Arguments.of("name ", 120, "fieldCount", 100), Arguments.of("tomes { ", 150, "depth", 100));
    }

    static String buildRepeatedQuery(String template, int repetitions) {
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
            queryBuilder.append("}");
        }

        queryBuilder.append('}');
        return queryBuilder.toString();
    }

    @Test
    void when_parseExcessivePaginationAttack_then_detectsLargeLimit() {
        val query  = """
                query {
                  investigators(first: 999999) {
                    name
                    tomes(limit: 999999) {
                      title
                    }
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("maxPaginationLimit").asInt()).isEqualTo(999999);
    }

    @Test
    void when_parseDirectiveAbuse_then_detectsExcessiveDirectives() {
        String queryBuilder = "query { investigator(id: \"1\") { " + "name " + "@include(if: true) ".repeat(50) + "} }";

        val result = GraphQLFunctionLibrary.parseQuery(Val.of(queryBuilder));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("directiveCount").asInt()).isGreaterThan(40);
        assertThat(result.get().get("directivesPerField").asDouble()).isGreaterThan(10);
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
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("depth").asInt()).isGreaterThan(4);
        assertThat(result.get().get("aliasCount").asInt()).isGreaterThan(0);
        assertThat(result.get().get("directiveCount").asInt()).isGreaterThan(0);
        assertThat(result.get().get("maxPaginationLimit").asInt()).isGreaterThan(800000);
    }

    /* Edge Case Tests */

    @Test
    void when_parseQueryWithComments_then_ignoresComments() {
        val query  = """
                # This is a comment about Cthulhu
                query {
                  # Query the investigator
                  investigator(id: "1") {
                    name # The investigator's name
                    sanity
                  }
                }
                """;
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("provideSpecialCharacterTestCases")
    void when_parseQueryWithSpecialCharacters_then_handlesCorrectly(String query) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isTrue();
    }

    static Stream<Arguments> provideSpecialCharacterTestCases() {
        return Stream.of(Arguments.of("query { investigator(id: \"The \\\"Dreamer\\\" of R'lyeh\") { name } }"),
                Arguments.of("query { investigator(id: \"克苏鲁\") { name } }") // Chinese "Cthulhu"
        );
    }

    @ParameterizedTest
    @MethodSource("provideUnicodeStringValueTestCases")
    void when_parseQueryWithUnicodeInStringValues_then_handlesCorrectly(String query, String expectedIdValue) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isTrue();
        val args = result.get().get("arguments").get("investigator");
        assertThat(args.get("id").asText()).isEqualTo(expectedIdValue);
    }

    static Stream<Arguments> provideUnicodeStringValueTestCases() {
        return Stream.of(Arguments.of("query { investigator(id: \"ℵ查询א\") { name } }", "ℵ查询א"), // Hebrew aleph +
                                                                                                // Chinese "query" +
                                                                                                // Hebrew aleph
                Arguments.of("query { investigator(id: \"Поиск\") { name } }", "Поиск"), // Russian "search"
                Arguments.of("query { investigator(id: \"調査١\") { name } }", "調査١"), // Japanese "investigation" +
                                                                                     // Arabic numeral 1
                Arguments.of("query { investigator(id: \"🔮\") { name } }", "🔮"), // Crystal ball emoji
                Arguments.of("query ValidName { investigator(id: \"混合ميكس\") { name } }", "混合ميكس") // Chinese "mixed" +
                                                                                                    // Arabic "mix"
        );
    }

    @ParameterizedTest
    @ValueSource(strings = { "query ℵ查询 { investigator(id: \"1\") { name } }", // Hebrew aleph + Chinese "query" -
                                                                               // invalid per GraphQL spec
            "query Поиск { investigator(id: \"1\") { name } }", // Russian "search" - invalid per GraphQL spec
            "query 調査 { investigator(id: \"1\") { name } }" // Japanese "investigation" - invalid per GraphQL spec
    })
    void when_parseQueryWithUnicodeInOperationName_then_failsPerGraphQLSpec(String query) {
        val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));

        assertThatVal(result).hasValue();
        assertThat(result.get().get("valid").asBoolean()).isFalse();
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

        val result = GraphQLFunctionLibrary.parseQuery(Val.of(queryBuilder.toString()));

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
            val result = GraphQLFunctionLibrary.parseQuery(Val.of(query));
            assertThatVal(result).hasValue();
        }
    }

    /* Error Handling Tests */

    @ParameterizedTest
    @MethodSource("provideInvalidInputTestCases")
    void when_parseWithInvalidInput_then_returnsError(Val input, String description) {
        val result = GraphQLFunctionLibrary.parseQuery(input);

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
        val result = GraphQLFunctionLibrary.parse(Val.of(query), schema);

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

    /* Integration Tests */

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
        val result = GraphQLFunctionLibrary.parse(Val.of(query), Val.of(BASIC_SCHEMA));

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
        val parsed  = GraphQLFunctionLibrary.parseQuery(Val.of(query));
        val weights = Val.ofJson("""
                {
                  "tomes": 50,
                  "rituals": 30
                }
                """);

        val complexity = GraphQLFunctionLibrary.complexity(parsed, weights);
        val maxAllowed = 100;

        assertThatVal(complexity).hasValue();

        if (complexity.get().asInt() > maxAllowed) {
            assertThat(complexity.get().asInt()).isGreaterThan(maxAllowed);
        }
    }
}
