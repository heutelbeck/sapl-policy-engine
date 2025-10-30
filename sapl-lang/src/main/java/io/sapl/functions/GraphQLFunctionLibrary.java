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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import graphql.language.*;
import graphql.parser.InvalidSyntaxException;
import graphql.parser.Parser;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.errors.SchemaProblem;
import graphql.validation.ValidationError;
import graphql.validation.Validator;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.*;
import java.util.function.Function;

/**
 * GraphQL query parsing and analysis for authorization policies.
 * <p/>
 * Provides query inspection capabilities including field analysis, complexity
 * calculation, and security metrics for authorization decisions based on
 * GraphQL query characteristics.
 */
@UtilityClass
@FunctionLibrary(name = GraphQLFunctionLibrary.NAME, description = GraphQLFunctionLibrary.DESCRIPTION, libraryDocumentation = GraphQLFunctionLibrary.DOCUMENTATION)
public class GraphQLFunctionLibrary {

    public static final String NAME = "graphql";

    public static final String DESCRIPTION = "GraphQL query parsing and analysis for authorization policies.";

    public static final String DOCUMENTATION = """
            # GraphQL Function Library for SAPL

            Parses GraphQL queries and extracts security metrics for authorization policy decisions.

            ## Basic Usage

            ```sapl
            var gql = graphql.parse(resource.query, resource."schema");

            // Access properties directly
            gql.valid                   // boolean - query validity
            gql.fields                  // array - all field names
            gql.depth                   // integer - maximum nesting depth
            gql.operation               // string - operation type (query/mutation/subscription)
            gql.complexity              // integer - complexity score
            gql.aliasCount              // integer - aliased field count
            gql.maxPaginationLimit      // integer - highest pagination limit
            ```

            ## Authorization Subscription

            Typical subscription structure for GraphQL authorization:

            ```json
            {
              "subject": {
                "username": "alice",
                "role": "user"
              },
              "action": "execute",
              "resource": {
                "query": "query { user(id: \\"123\\") { name email ssn } }",
                "schema": "type Query { user(id: ID!): User } type User { name: String! email: String! ssn: String! }"
              }
            }
            ```

            Policy examples below assume this structure with `resource.query` and `resource."schema"`.

            ## Properties

            ### Query Validation

            - `valid` (boolean) - Query is syntactically correct and valid against schema.
            - `errors` (array) - Validation error messages if invalid.
            - `operation` (string) - Operation type: "query", "mutation", "subscription", or "unknown".
            - `operationName` (string) - Operation name or empty string if anonymous.

            ### Field Analysis

            - `fields` (array) - All field names in the query including nested fields.
            - `fieldCount` (integer) - Total number of fields requested.
            - `depth` (integer) - Maximum nesting depth (capped at 100).
            - `isIntrospection` (boolean) - Query uses introspection fields (prefix `__`).

            ### Type and Fragment Information

            - `types` (array) - GraphQL type names accessed via inline fragments and fragment spreads.
            - `fragments` (object) - Fragment definitions with `typeName` and `fields` properties.
            - `fragmentCount` (integer) - Number of fragment definitions.
            - `hasCircularFragments` (boolean) - Fragments contain circular references.

            ### Directives

            - `directives` (array) - Directive usages with `name` and `arguments` properties.
            - `directiveCount` (integer) - Total directive usage count.
            - `directivesPerField` (number) - Average directives per field.

            ### Complexity and Security Metrics

            - `complexity` (integer) - Basic score: `fieldCount + (depth × 2)`.
            - `aliasCount` (integer) - Number of aliased fields.
            - `rootFieldCount` (integer) - Fields at root level.
            - `batchingScore` (integer) - Calculated as `(aliasCount × 5) + rootFieldCount`.
            - `maxPaginationLimit` (integer) - Highest pagination argument value across first, last, limit, offset, skip, take.

            ### Arguments and Variables

            - `arguments` (object) - Field arguments mapped by field name.
            - `variables` (object) - Variable definitions with default values.

            ## Functions

            ### parse

            ```
            graphql.parse(TEXT query, TEXT schema) -> OBJECT
            ```

            Parses and validates a GraphQL query against a schema. Returns object with all security metrics.

            **Parameters:**
            - `query` - GraphQL query string
            - `schema` - GraphQL schema definition (SDL)

            **Returns:** Object with all properties listed above.

            **Example:**
            ```sapl
            policy "validate-graphql-query"
            permit action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.valid && gql.depth <= 5 && !("ssn" in gql.fields);
            ```

            ### parseQuery

            ```
            graphql.parseQuery(TEXT query) -> OBJECT
            ```

            Parses a GraphQL query without schema validation. Returns same metrics as `parse()` but `valid` only checks syntax.

            **Parameters:**
            - `query` - GraphQL query string

            **Example:**
            ```sapl
            policy "check-query-structure"
            permit action == "execute"
            where
              var gql = graphql.parseQuery(resource.query);
              gql.depth <= 5 && gql.aliasCount <= 10;
            ```

            ### complexity

            ```
            graphql.complexity(OBJECT parsed, OBJECT fieldWeights) -> NUMBER
            ```

            Calculates weighted complexity using custom field weights. Unweighted fields default to 1.

            **Parameters:**
            - `parsed` - Parsed query object from `parse()` or `parseQuery()`
            - `fieldWeights` - Object mapping field names to numeric weights

            **Example:**
            ```sapl
            policy "enforce-complexity-limit"
            permit action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              var weights = {"posts": 5, "comments": 3, "user": 1};
              graphql.complexity(gql, weights) <= 200;
            ```

            ### parseSchema

            ```
            graphql.parseSchema(TEXT schema) -> OBJECT
            ```

            Parses and validates a GraphQL schema definition.

            **Parameters:**
            - `schema` - GraphQL schema definition (SDL)

            **Returns:** Object with `valid` (boolean), `ast` (object), and `errors` (array) properties.

            **Example:**
            ```sapl
            policy "require-valid-schema"
            permit action == "configure"
            where
              var schemaResult = graphql.parseSchema(resource."schema");
              schemaResult.valid;
            ```

            ## Use Cases

            ### Field-Level Access Control

            Deny access to sensitive PII fields:

            ```sapl
            policy "restrict-pii-fields"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              var piiFields = ["ssn", "creditCard", "taxId", "passport"];
              array.containsAny(gql.fields, piiFields);
            ```

            ### Depth Limiting

            Prevent deeply nested queries:

            ```sapl
            policy "limit-query-depth"
            permit action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.valid && gql.depth <= 5;
            ```

            ### Operation Type Control

            Restrict mutations to admins:

            ```sapl
            policy "mutations-require-admin"
            permit action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.operation != "mutation" || subject.role == "admin";
            ```

            ### Introspection Blocking

            Block schema introspection in production:

            ```sapl
            policy "block-introspection-in-production"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              environment.stage == "production" && gql.isIntrospection;
            ```

            ### Complexity Limiting

            Enforce complexity limits:

            ```sapl
            policy "complexity-limits"
            permit action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.valid && gql.complexity <= 100;
            ```

            ### Batching Attack Prevention

            Detect and block alias-based batching:

            ```sapl
            policy "prevent-batching-attacks"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.aliasCount > 10 || gql.batchingScore > 50;
            ```

            ### Pagination Limit Enforcement

            Prevent excessive pagination:

            ```sapl
            policy "limit-pagination"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.maxPaginationLimit > 100;
            ```

            ### Fragment Security

            All fragment fields are included in `gql.fields`, so check that array:

            ```sapl
            policy "check-sensitive-fields"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              var sensitiveFields = ["ssn", "password"];
              array.containsAny(gql.fields, sensitiveFields);
            ```

            To check specific fragments (note: `fragments` is an object, not array):

            ```sapl
            policy "check-fragment-by-name"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              var sensitiveFields = ["ssn", "password"];
              array.containsAny(gql.fragments.SensitiveFragment.fields, sensitiveFields);
            ```

            ### Type-Based Access Control

            Restrict access to admin-only types:

            ```sapl
            policy "admin-only-types"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              var adminTypes = ["AdminUser", "SystemConfig"];
              subject.role != "admin" && array.containsAny(gql.types, adminTypes);
            ```

            ### Directive Whitelisting

            Only allow specific directives:

            ```sapl
            policy "whitelist-directives"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              var allowed = ["include", "skip", "deprecated"];
              gql.directives |- var directive : !(directive.name in allowed);
            ```

            ### Comprehensive Security

            Combine multiple security checks:

            ```sapl
            policy "comprehensive-security"
            permit action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.valid &&
              gql.depth <= 5 &&
              gql.fieldCount <= 50 &&
              gql.aliasCount <= 10 &&
              gql.maxPaginationLimit <= 100 &&
              !gql.hasCircularFragments &&
              !gql.isIntrospection &&
              !("ssn" in gql.fields);
            ```

            ### Tier-Based Complexity Budgets

            Apply complexity limits by user tier:

            ```sapl
            policy "tiered-limits"
            permit action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              var weights = {"posts": 5, "comments": 3, "users": 2};
              var cost = graphql.complexity(gql, weights);

              (subject.tier == "enterprise" && cost <= 1000) ||
              (subject.tier == "professional" && cost <= 200) ||
              (subject.tier == "free" && cost <= 50);
            ```

            ## Notes

            **Performance:** Single-pass analysis. Parse once and reuse result object. Schema caching enabled (max 100 schemas).

            **Error Handling:** Invalid queries set `valid` to false with errors in `errors` array. Check `valid` before using other metrics.

            **Schema Validation:** `parse()` requires schema definition. Use `parseQuery()` for syntax-only validation.
            """;

    // Argument name constants
    private static final String ARG_FIRST  = "first";
    private static final String ARG_LAST   = "last";
    private static final String ARG_LIMIT  = "limit";
    private static final String ARG_OFFSET = "offset";
    private static final String ARG_SKIP   = "skip";
    private static final String ARG_TAKE   = "take";

    // AST structure field name constants
    private static final String AST_ARGUMENTS_LOWER = "arguments";
    private static final String AST_DIRECTIVES      = "directives";
    private static final String AST_KIND            = "kind";
    private static final String AST_NAME_LOWER      = "name";

    // Fragment detail field constants
    private static final String FRAGMENT_TYPE_NAME = "typeName";
    private static final String FRAGMENT_FIELDS    = "fields";

    // Schema AST field constants
    private static final String SCHEMA_TYPES = "types";

    // Configuration constants
    private static final int BATCHING_SCORE_MULTIPLIER = 5;
    private static final int DEFAULT_FIELD_WEIGHT      = 1;
    private static final int DEFAULT_MAX_DEPTH         = 100;
    private static final int DEPTH_COMPLEXITY_FACTOR   = 2;
    private static final int MAX_SCHEMA_CACHE_SIZE     = 100;

    // Error message constants
    private static final String ERROR_NO_OPERATION = "No operation definition found.";
    private static final String ERROR_PARSE_FAILED = "Failed to parse GraphQL query: ";

    // Operation type constants
    private static final String OPERATION_MUTATION     = "mutation";
    private static final String OPERATION_QUERY        = "query";
    private static final String OPERATION_SUBSCRIPTION = "subscription";

    // Property name constants
    private static final String PROP_ALIAS_COUNT            = "aliasCount";
    private static final String PROP_ARGUMENTS              = "arguments";
    private static final String PROP_AST                    = "ast";
    private static final String PROP_BATCHING_SCORE         = "batchingScore";
    private static final String PROP_COMPLEXITY             = "complexity";
    private static final String PROP_DEPTH                  = "depth";
    private static final String PROP_DIRECTIVE_COUNT        = "directiveCount";
    private static final String PROP_DIRECTIVES             = "directives";
    private static final String PROP_DIRECTIVES_PER_FIELD   = "directivesPerField";
    private static final String PROP_ERRORS                 = "errors";
    private static final String PROP_FIELD_COUNT            = "fieldCount";
    private static final String PROP_FIELDS                 = "fields";
    private static final String PROP_FRAGMENT_COUNT         = "fragmentCount";
    private static final String PROP_FRAGMENTS              = "fragments";
    private static final String PROP_HAS_CIRCULAR_FRAGMENTS = "hasCircularFragments";
    private static final String PROP_IS_INTROSPECTION       = "isIntrospection";
    private static final String PROP_MAX_PAGINATION_LIMIT   = "maxPaginationLimit";
    private static final String PROP_OPERATION              = "operation";
    private static final String PROP_OPERATION_NAME         = "operationName";
    private static final String PROP_ROOT_FIELD_COUNT       = "rootFieldCount";
    private static final String PROP_TYPES                  = "types";
    private static final String PROP_VALID                  = "valid";
    private static final String PROP_VARIABLES              = "variables";

    // Special field prefixes
    private static final String INTROSPECTION_PREFIX = "__";

    // Pagination argument names
    private static final Set<String> PAGINATION_ARGS = Set.of(ARG_FIRST, ARG_LAST, ARG_LIMIT, ARG_OFFSET, ARG_SKIP,
            ARG_TAKE);

    // Default values for empty result objects
    private static final Map<String, Object> DEFAULT_VALUES = Map.ofEntries(Map.entry(PROP_OPERATION, OPERATION_QUERY),
            Map.entry(PROP_OPERATION_NAME, ""), Map.entry(PROP_DEPTH, 0), Map.entry(PROP_FIELD_COUNT, 0),
            Map.entry(PROP_IS_INTROSPECTION, false), Map.entry(PROP_COMPLEXITY, 0), Map.entry(PROP_ALIAS_COUNT, 0),
            Map.entry(PROP_ROOT_FIELD_COUNT, 0), Map.entry(PROP_BATCHING_SCORE, 0),
            Map.entry(PROP_MAX_PAGINATION_LIMIT, 0), Map.entry(PROP_FRAGMENT_COUNT, 0),
            Map.entry(PROP_HAS_CIRCULAR_FRAGMENTS, false), Map.entry(PROP_DIRECTIVE_COUNT, 0),
            Map.entry(PROP_DIRECTIVES_PER_FIELD, 0.0));

    private static final Set<String> ARRAY_PROPERTIES = Set.of(PROP_FIELDS, PROP_TYPES, PROP_DIRECTIVES);

    private static final Set<String> OBJECT_PROPERTIES = Set.of(PROP_VARIABLES, PROP_FRAGMENTS, PROP_ARGUMENTS);

    // Schema cache - LRU with size limit
    private static final Map<String, GraphQLSchema> SCHEMA_CACHE = Collections
            .synchronizedMap(new LinkedHashMap<>(MAX_SCHEMA_CACHE_SIZE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, GraphQLSchema> eldest) {
                    return size() > MAX_SCHEMA_CACHE_SIZE;
                }
            });

    // Return type schemas with full structure for IDE support
    private static final String RETURNS_NUMBER = """
            {
              "type": "integer",
              "minimum": 0,
              "description": "Weighted complexity score"
            }
            """;

    private static final String RETURNS_PARSED_QUERY = """
            {
              "type": "object",
              "properties": {
                "valid": {
                  "type": "boolean",
                  "description": "True if the query is valid against the schema"
                },
                "operation": {
                  "type": "string",
                  "enum": ["query", "mutation", "subscription", "unknown"],
                  "description": "The GraphQL operation type"
                },
                "operationName": {
                  "type": "string",
                  "description": "The operation name if specified"
                },
                "fields": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "All field names requested in the query"
                },
                "fieldCount": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Total number of fields requested"
                },
                "depth": {
                  "type": "integer",
                  "minimum": 0,
                  "maximum": 100,
                  "description": "Maximum nesting depth of field selections"
                },
                "complexity": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Basic complexity score (fieldCount + depth × 2)"
                },
                "isIntrospection": {
                  "type": "boolean",
                  "description": "True if the query uses introspection fields"
                },
                "variables": {
                  "type": "object",
                  "description": "Variable definitions with default values"
                },
                "types": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "GraphQL types accessed via fragments"
                },
                "directives": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "name": {"type": "string"},
                      "arguments": {"type": "object"}
                    }
                  },
                  "description": "All directives used in the query"
                },
                "fragments": {
                  "type": "object",
                  "description": "Fragment definitions with their fields and types"
                },
                "aliasCount": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Number of aliased fields"
                },
                "rootFieldCount": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Number of fields at root level"
                },
                "batchingScore": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Score indicating potential batching patterns"
                },
                "arguments": {
                  "type": "object",
                  "description": "All field arguments used in the query"
                },
                "maxPaginationLimit": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Maximum pagination limit requested"
                },
                "fragmentCount": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Number of fragment definitions"
                },
                "hasCircularFragments": {
                  "type": "boolean",
                  "description": "True if fragments contain circular references"
                },
                "directiveCount": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Total number of directive usages"
                },
                "directivesPerField": {
                  "type": "number",
                  "minimum": 0,
                  "description": "Average directives per field"
                },
                "errors": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "Validation errors if invalid"
                }
              },
              "required": ["valid", "operation", "fields", "depth", "complexity"]
            }
            """;

    /**
     * Parses and validates a GraphQL query against a schema.
     * <p/>
     * Returns a comprehensive object containing all security metrics and
     * validation results. Parsing and validation happen in a single pass for
     * optimal performance.
     *
     * @param query the GraphQL query string to parse and analyze
     * @param schema the GraphQL schema definition (SDL) to validate against
     * @return Val containing parsed query object with all metrics and validation
     * results
     */
    @io.sapl.api.functions.Function(docs = """
            ```
            graphql.parse(TEXT query, TEXT schema) -> OBJECT
            ```

            Parses and validates a GraphQL query against a schema.

            Returns comprehensive security analysis including validation, field extraction,
            complexity metrics, and potential security concerns.

            **Access all metrics via properties:**

            Basic Information:
            - `gql.valid`, `gql.operation`, `gql.operationName`, `gql.errors`

            Field Analysis:
            - `gql.fields`, `gql.fieldCount`, `gql.depth`, `gql.isIntrospection`,
              `gql.complexity`, `gql.isIntrospection`

            Type and Directive Information:
            - `gql.types`, `gql.directives`, `gql.fragments`

            Advanced Security:
            - `gql.aliasCount`, `gql.rootFieldCount`, `gql.batchingScore`,
              `gql.maxPaginationLimit`, `gql.arguments`, `gql.fragmentCount`,
              `gql.hasCircularFragments`, `gql.directiveCount`, `gql.directivesPerField`

            **Example:**
            ```sapl
            var gql = graphql.parse(resource.query, resource."schema");
            gql.valid && gql.depth <= 5 && !("ssn" in gql.fields)
            ```
            """, schema = RETURNS_PARSED_QUERY)
    public static Val parse(@Text Val query, @Text Val schema) {
        try {
            val document      = parseQueryDocument(query.getText());
            val graphQLSchema = parseSchemaWithCache(schema.getText());

            val result = Val.JSON.objectNode();

            val validationErrors = validateQuery(document, graphQLSchema);
            val isValid          = validationErrors.isEmpty();

            result.put(PROP_VALID, isValid);

            if (!isValid) {
                result.set(PROP_ERRORS,
                        buildArray(validationErrors, (ValidationError error) -> Val.JSON.textNode(error.getMessage())));
                addEmptyDefaults(result);
                return Val.of(result);
            }

            populateQueryAnalysis(document, result);

            return Val.of(result);

        } catch (SchemaProblem exception) {
            return createErrorResult("Schema parsing failed: " + exception.getMessage());
        } catch (InvalidSyntaxException | IllegalArgumentException exception) {
            return createErrorResult(ERROR_PARSE_FAILED + exception.getMessage());
        }
    }

    /**
     * Parses a GraphQL query without schema validation.
     * <p/>
     * Returns the same comprehensive metrics as parse() except for validation
     * results. Use this when you need query analysis but don't have the schema
     * available.
     *
     * @param query the GraphQL query string to parse and analyze
     * @return Val containing parsed query object with all metrics
     */
    @io.sapl.api.functions.Function(docs = """
            ```
            graphql.parseQuery(TEXT query) -> OBJECT
            ```

            Parses a GraphQL query without schema validation.

            Returns the same comprehensive metrics as `parse()` except for validation.
            Use when you need query analysis but don't have the schema available.

            **Access all metrics via properties:**

            Basic Information:
            - `gql.valid`, `gql.operation`, `gql.operationName`

            Field Analysis:
            - `gql.fields`, `gql.fieldCount`, `gql.depth`,
              `gql.complexity`, `gql.isIntrospection`

            Type and Directive Information:
            - `gql.types`, `gql.directives`, `gql.fragments`

            Advanced Security:
            - `gql.aliasCount`, `gql.rootFieldCount`, `gql.batchingScore`,
              `gql.maxPaginationLimit`, `gql.arguments`, `gql.fragmentCount`,
              `gql.hasCircularFragments`, `gql.directiveCount`, `gql.directivesPerField`

            **Example:**
            ```sapl
            var gql = graphql.parseQuery(resource.query);
            gql.depth <= 5 && gql.aliasCount <= 10
            ```
            """, schema = RETURNS_PARSED_QUERY)
    public static Val parseQuery(@Text Val query) {
        try {
            val document = parseQueryDocument(query.getText());
            val result   = Val.JSON.objectNode();

            result.put(PROP_VALID, true);
            populateQueryAnalysis(document, result);

            return Val.of(result);

        } catch (InvalidSyntaxException | IllegalArgumentException exception) {
            return createErrorResult(ERROR_PARSE_FAILED + exception.getMessage());
        }
    }

    /**
     * Calculates weighted complexity for a parsed query.
     * <p/>
     * Applies custom weights to fields based on their expected resource cost.
     * Each field can be assigned a custom weight. Fields not specified in the
     * weights object receive a default weight of 1.
     * <p/>
     * Important: When fragments are used multiple times via fragment spreads,
     * their complexity is counted for each spread, reflecting the actual
     * execution cost of evaluating the fragment content multiple times. For
     * example, if a fragment with complexity 10 is spread 5 times, it
     * contributes 50 to the total complexity.
     *
     * @param parsed the parsed query object from parse() or parseQuery()
     * @param fieldWeights object mapping field names to numeric weights
     * @return Val containing the weighted complexity score
     */
    @io.sapl.api.functions.Function(docs = """
            ```
            graphql.complexity(OBJECT parsed, OBJECT fieldWeights) -> NUMBER
            ```

            Calculates weighted complexity with custom field weights.

            **Example:**
            ```sapl
            var gql = graphql.parse(resource.query, resource."schema");
            var weights = {"posts": 5, "comments": 3, "user": 1};
            graphql.complexity(gql, weights) <= 200
            ```
            """, schema = RETURNS_NUMBER)
    public static Val complexity(@JsonObject Val parsed, @JsonObject Val fieldWeights) {
        val fieldsNode = parsed.getJsonNode().get(PROP_FIELDS);
        val depthNode  = parsed.getJsonNode().get(PROP_DEPTH);

        if (fieldsNode == null || !fieldsNode.isArray()) {
            return Val.of(0);
        }

        var fieldComplexity = 0;
        val weightsMap      = fieldWeights.getObjectNode();

        for (JsonNode field : fieldsNode) {
            if (field.isTextual()) {
                val fieldName = field.asText();
                val weight    = weightsMap.has(fieldName) ? weightsMap.get(fieldName).asInt(DEFAULT_FIELD_WEIGHT)
                        : DEFAULT_FIELD_WEIGHT;
                fieldComplexity += weight;
            }
        }

        val depth = depthNode != null && depthNode.isNumber() ? depthNode.asInt() : 1;

        val depthComplexity = depth * DEPTH_COMPLEXITY_FACTOR;
        val totalComplexity = fieldComplexity + depthComplexity;

        return Val.of(totalComplexity);
    }

    /**
     * Parses and validates a GraphQL schema definition.
     * <p/>
     * Returns an object indicating validity and containing the schema AST
     * representation.
     *
     * @param schema the GraphQL schema definition string
     * @return Val containing schema validation result with AST
     */
    @io.sapl.api.functions.Function(docs = """
            ```
            graphql.parseSchema(TEXT schema) -> OBJECT
            ```

            Parses and validates a GraphQL schema definition.

            Returns an object with:
            - `valid`: boolean indicating schema validity
            - `ast`: the schema type definition registry as JSON
            - `errors`: array of error messages if invalid

            **Example:**
            ```sapl
            var schemaResult = graphql.parseSchema(resource."schema");
            schemaResult.valid
            ```
            """, schema = """
            {
              "type": "object",
              "properties": {
                "valid": {
                  "type": "boolean",
                  "description": "True if the schema is valid"
                },
                "ast": {
                  "type": "object",
                  "description": "Schema type definition registry representation"
                },
                "errors": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "Error messages if schema is invalid"
                }
              },
              "required": ["valid", "ast"]
            }
            """)
    public static Val parseSchema(@Text Val schema) {
        try {
            val schemaParser           = new SchemaParser();
            val typeDefinitionRegistry = schemaParser.parse(schema.getText());

            val result = Val.JSON.objectNode();
            result.put(PROP_VALID, true);
            result.set(PROP_AST, buildSchemaAst(typeDefinitionRegistry));

            return Val.of(result);

        } catch (SchemaProblem | IllegalArgumentException exception) {
            val result = Val.JSON.objectNode();
            result.put(PROP_VALID, false);
            result.set(PROP_AST, Val.JSON.objectNode());

            val errors = Val.JSON.arrayNode();
            errors.add(exception.getMessage());
            result.set(PROP_ERRORS, errors);

            return Val.of(result);
        }
    }

    /**
     * Builds a schema AST representation from a type definition registry.
     *
     * @param typeDefinitionRegistry the schema type definition registry
     * @return JSON object representing the schema structure
     */
    private static ObjectNode buildSchemaAst(graphql.schema.idl.TypeDefinitionRegistry typeDefinitionRegistry) {
        val astNode = Val.JSON.objectNode();

        val typesArray = Val.JSON.arrayNode();

        // Add regular types (object, interface, union, enum, input)
        typeDefinitionRegistry.types().values().forEach(typeDef -> {
            val typeNode = Val.JSON.objectNode();
            typeNode.put(AST_KIND, typeDef.getClass().getSimpleName());
            typeNode.put(AST_NAME_LOWER, typeDef.getName());
            typesArray.add(typeNode);
        });

        // Add scalar types (stored separately in GraphQL Java)
        typeDefinitionRegistry.scalars().values().forEach(scalarDef -> {
            val typeNode = Val.JSON.objectNode();
            typeNode.put(AST_KIND, scalarDef.getClass().getSimpleName());
            typeNode.put(AST_NAME_LOWER, scalarDef.getName());
            typesArray.add(typeNode);
        });

        astNode.set(SCHEMA_TYPES, typesArray);

        val directivesArray = Val.JSON.arrayNode();
        typeDefinitionRegistry.getDirectiveDefinitions().values().forEach(directiveDef -> {
            val directiveNode = Val.JSON.objectNode();
            directiveNode.put(AST_NAME_LOWER, directiveDef.getName());
            directivesArray.add(directiveNode);
        });
        astNode.set(AST_DIRECTIVES, directivesArray);

        return astNode;
    }

    /**
     * Populates a result object with query analysis data using a single-pass
     * traversal.
     *
     * @param document the parsed query document
     * @param result the result object to populate
     */
    private static void populateQueryAnalysis(Document document, ObjectNode result) {
        val operationDefinition = extractOperationDefinition(document);

        // Single-pass analysis collecting all metrics at once
        val metrics = analyzeQueryInSinglePass(document, operationDefinition);

        // Populate basic metrics
        result.set(PROP_OPERATION, Val.JSON.textNode(metrics.operation));
        result.set(PROP_OPERATION_NAME, Val.JSON.textNode(metrics.operationName));
        result.set(PROP_FIELDS, buildStringArray(metrics.fields));
        result.set(PROP_FIELD_COUNT, Val.JSON.numberNode(metrics.fieldCount));
        result.set(PROP_DEPTH, Val.JSON.numberNode(metrics.depth));
        result.set(PROP_IS_INTROSPECTION, Val.JSON.booleanNode(metrics.isIntrospection));
        result.set(PROP_VARIABLES, metrics.variables);
        result.set(PROP_COMPLEXITY, Val.JSON.numberNode(calculateBasicComplexity(metrics.fieldCount, metrics.depth)));

        // Type and directive information
        result.set(PROP_TYPES, buildStringArray(new ArrayList<>(metrics.types)));
        result.set(PROP_DIRECTIVES, buildArray(metrics.directivesList, directive -> directive));

        // Fragment information
        result.set(PROP_FRAGMENTS, metrics.fragments);
        result.set(PROP_FRAGMENT_COUNT, Val.JSON.numberNode(metrics.fragmentCount));
        result.set(PROP_HAS_CIRCULAR_FRAGMENTS, Val.JSON.booleanNode(metrics.hasCircularFragments));

        // Advanced security metrics
        result.set(PROP_ALIAS_COUNT, Val.JSON.numberNode(metrics.aliasCount));
        result.set(PROP_ROOT_FIELD_COUNT, Val.JSON.numberNode(metrics.rootFieldCount));
        result.set(PROP_BATCHING_SCORE, Val.JSON.numberNode(metrics.batchingScore));
        result.set(PROP_MAX_PAGINATION_LIMIT, Val.JSON.numberNode(metrics.maxPaginationLimit));
        result.set(PROP_ARGUMENTS, metrics.arguments);
        result.set(PROP_DIRECTIVE_COUNT, Val.JSON.numberNode(metrics.directiveCount));
        result.set(PROP_DIRECTIVES_PER_FIELD, Val.JSON.numberNode(metrics.directivesPerField));
    }

    /**
     * Analyzes a GraphQL query in a single pass, collecting all metrics at once.
     * This consolidates multiple recursive traversals into one efficient pass.
     *
     * @param document the parsed document
     * @param operation the operation definition
     * @return QueryMetrics containing all collected metrics
     */
    private static QueryMetrics analyzeQueryInSinglePass(Document document, OperationDefinition operation) {
        val metrics = new QueryMetrics();

        // Basic operation information
        metrics.operation     = determineOperationType(operation);
        metrics.operationName = extractOperationName(operation);
        metrics.variables     = extractVariablesFromOperation(operation);

        // Single pass through operation selection set
        analyzeSelectionSet(operation.getSelectionSet(), 0, metrics, true);

        // Calculate derived metrics
        metrics.batchingScore      = metrics.aliasCount * BATCHING_SCORE_MULTIPLIER + metrics.rootFieldCount;
        metrics.directivesPerField = metrics.fieldCount > 0 ? (double) metrics.directiveCount / metrics.fieldCount
                : 0.0;
        metrics.isIntrospection    = metrics.fields.stream().anyMatch(field -> field.startsWith(INTROSPECTION_PREFIX));

        // Process fragments separately (different document iteration)
        processFragments(document, metrics);

        return metrics;
    }

    /**
     * Recursively analyzes a selection set, collecting all metrics in a single
     * pass.
     *
     * @param selectionSet the selection set to analyze
     * @param currentDepth the current nesting depth
     * @param metrics the metrics accumulator
     * @param isRoot true if this is the root selection set
     */
    private static void analyzeSelectionSet(SelectionSet selectionSet, int currentDepth, QueryMetrics metrics,
            boolean isRoot) {
        if (selectionSet == null) {
            return;
        }

        val nextDepth = currentDepth + 1;
        metrics.depth = Math.clamp(nextDepth, metrics.depth, DEFAULT_MAX_DEPTH);

        for (Selection<?> selection : selectionSet.getSelections()) {
            switch (selection) {
            case Field field             -> analyzeField(field, nextDepth, metrics, isRoot);
            case InlineFragment fragment -> analyzeInlineFragment(fragment, nextDepth, metrics);
            case FragmentSpread spread   -> analyzeFragmentSpread(spread, metrics);
            default                      -> { /* Ignore unknown selection types */ }
            }
        }
    }

    /**
     * Analyzes a field, collecting field name, alias, arguments, directives, and
     * recursing into nested selections.
     *
     * @param field the field to analyze
     * @param currentDepth the current nesting depth
     * @param metrics the metrics accumulator
     * @param isRoot true if this field is at root level
     */
    private static void analyzeField(Field field, int currentDepth, QueryMetrics metrics, boolean isRoot) {
        // Collect field name
        metrics.fields.add(field.getName());
        metrics.fieldCount++;

        // Root field counting
        if (isRoot) {
            metrics.rootFieldCount++;
        }

        // Alias counting
        if (field.getAlias() != null) {
            metrics.aliasCount++;
        }

        // Argument analysis
        if (field.getArguments() != null && !field.getArguments().isEmpty()) {
            val fieldArgs = Val.JSON.objectNode();

            for (Argument argument : field.getArguments()) {
                val argName  = argument.getName();
                val argValue = argument.getValue();

                fieldArgs.set(argName, convertValueToJson(argValue));

                // Track max pagination limit
                if (PAGINATION_ARGS.contains(argName.toLowerCase()) && argValue instanceof IntValue intValue) {
                    val value = intValue.getValue().intValue();
                    metrics.maxPaginationLimit = Math.max(metrics.maxPaginationLimit, value);
                }
            }

            metrics.arguments.set(field.getName(), fieldArgs);
        }

        // Directive counting
        if (field.getDirectives() != null) {
            val directives = field.getDirectives();
            metrics.directiveCount += directives.size();
            processDirectivesForExtraction(directives, metrics.directivesList);
        }

        // Recurse into nested selections
        analyzeSelectionSet(field.getSelectionSet(), currentDepth, metrics, false);
    }

    /**
     * Analyzes an inline fragment, collecting type conditions and recursing into
     * nested selections.
     *
     * @param fragment the inline fragment to analyze
     * @param currentDepth the current nesting depth
     * @param metrics the metrics accumulator
     */
    private static void analyzeInlineFragment(InlineFragment fragment, int currentDepth, QueryMetrics metrics) {
        // Collect type condition
        if (fragment.getTypeCondition() != null) {
            metrics.types.add(fragment.getTypeCondition().getName());
        }

        // Directive counting
        if (fragment.getDirectives() != null) {
            val directives = fragment.getDirectives();
            metrics.directiveCount += directives.size();
            processDirectivesForExtraction(directives, metrics.directivesList);
        }

        // Recurse into nested selections
        analyzeSelectionSet(fragment.getSelectionSet(), currentDepth, metrics, false);
    }

    /**
     * Analyzes a fragment spread, processing directives.
     *
     * @param spread the fragment spread to analyze
     * @param metrics the metrics accumulator
     */
    private static void analyzeFragmentSpread(DirectivesContainer<?> spread, QueryMetrics metrics) {
        // Directive counting
        if (spread.getDirectives() != null) {
            val directives = spread.getDirectives();
            metrics.directiveCount += directives.size();
            processDirectivesForExtraction(directives, metrics.directivesList);
        }
    }

    /**
     * Processes directives for extraction into the directives list.
     *
     * @param directives the list of directives
     * @param directivesList the list to accumulate directive objects
     */
    private static void processDirectivesForExtraction(List<Directive> directives, List<ObjectNode> directivesList) {
        for (Directive directive : directives) {
            val directiveNode = Val.JSON.objectNode();
            directiveNode.put(AST_NAME_LOWER, directive.getName());

            val argsNode = Val.JSON.objectNode();
            if (directive.getArguments() != null) {
                for (Argument argument : directive.getArguments()) {
                    argsNode.set(argument.getName(), convertValueToJson(argument.getValue()));
                }
            }
            directiveNode.set(AST_ARGUMENTS_LOWER, argsNode);

            directivesList.add(directiveNode);
        }
    }

    /**
     * Processes fragments to extract fragment details and detect circular
     * references.
     *
     * @param document the parsed document
     * @param metrics the metrics accumulator
     */
    private static void processFragments(Document document, QueryMetrics metrics) {
        val fragmentDefinitions = new HashMap<String, FragmentDefinition>();

        for (Definition<?> definition : document.getDefinitions()) {
            if (definition instanceof FragmentDefinition fragment) {
                fragmentDefinitions.put(fragment.getName(), fragment);

                // Extract fragment details
                val fragmentInfo = Val.JSON.objectNode();
                fragmentInfo.put(FRAGMENT_TYPE_NAME, fragment.getTypeCondition().getName());

                val fragmentFields = new ArrayList<String>();
                extractFieldsFromSelectionSet(fragment.getSelectionSet(), fragmentFields);
                fragmentInfo.set(FRAGMENT_FIELDS, buildStringArray(fragmentFields));

                metrics.fragments.set(fragment.getName(), fragmentInfo);

                // Collect fragment type
                metrics.types.add(fragment.getTypeCondition().getName());
            }
        }

        metrics.fragmentCount        = fragmentDefinitions.size();
        metrics.hasCircularFragments = detectCircularFragments(fragmentDefinitions);
    }

    /**
     * Extracts field names from a selection set (used only for fragment field
     * extraction).
     *
     * @param selectionSet the selection set to process
     * @param accumulator list to accumulate field names
     */
    private static void extractFieldsFromSelectionSet(SelectionSet selectionSet, List<String> accumulator) {
        if (selectionSet == null) {
            return;
        }

        for (Selection<?> selection : selectionSet.getSelections()) {
            switch (selection) {
            case Field field             -> {
                accumulator.add(field.getName());
                extractFieldsFromSelectionSet(field.getSelectionSet(), accumulator);
            }
            case InlineFragment fragment -> extractFieldsFromSelectionSet(fragment.getSelectionSet(), accumulator);
            default                      -> { /* Ignore fragment spreads and unknown selection types */ }
            }
        }
    }

    /**
     * Detects circular references in fragment definitions.
     *
     * @param fragments map of fragment names to definitions
     * @return true if circular references detected
     */
    private static boolean detectCircularFragments(Map<String, FragmentDefinition> fragments) {
        for (Map.Entry<String, FragmentDefinition> entry : fragments.entrySet()) {
            val visited = new HashSet<String>();
            if (hasCircularReference(entry.getKey(), entry.getValue(), fragments, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks for circular references in a fragment using depth-first search.
     *
     * @param fragmentName the fragment name being checked
     * @param fragment the fragment definition
     * @param allFragments map of all fragments
     * @param visited set of already visited fragment names
     * @return true if circular reference found
     */
    private static boolean hasCircularReference(String fragmentName, FragmentDefinition fragment,
            Map<String, FragmentDefinition> allFragments, Set<String> visited) {
        if (visited.contains(fragmentName)) {
            return true;
        }

        visited.add(fragmentName);

        val referencedFragments = findFragmentSpreads(fragment.getSelectionSet());
        for (String refName : referencedFragments) {
            if (allFragments.containsKey(refName)
                    && hasCircularReference(refName, allFragments.get(refName), allFragments, visited)) {
                return true;
            }
        }

        visited.remove(fragmentName);
        return false;
    }

    /**
     * Finds fragment spreads in a selection set.
     *
     * @param selectionSet the selection set to search
     * @return set of fragment names referenced
     */
    private static Set<String> findFragmentSpreads(SelectionSet selectionSet) {
        val spreads = new HashSet<String>();
        if (selectionSet == null) {
            return spreads;
        }

        for (Selection<?> selection : selectionSet.getSelections()) {
            switch (selection) {
            case FragmentSpread spread   -> spreads.add(spread.getName());
            case Field field             -> spreads.addAll(findFragmentSpreads(field.getSelectionSet()));
            case InlineFragment fragment -> spreads.addAll(findFragmentSpreads(fragment.getSelectionSet()));
            default                      -> { /* Ignore unknown selection types */ }
            }
        }

        return spreads;
    }

    /**
     * Converts a GraphQL AST Value to a proper JSON node.
     * Handles all GraphQL value types including scalars, arrays, objects, and
     * variable references. This preserves proper JSON types instead of converting
     * everything to strings.
     *
     * @param value the GraphQL value to convert
     * @return JSON representation of the value with correct types
     */
    private static JsonNode convertValueToJson(Value<?> value) {
        return switch (value) {
        case IntValue intValue             -> Val.JSON.numberNode(intValue.getValue().intValue());
        case FloatValue floatValue         -> Val.JSON.numberNode(floatValue.getValue().doubleValue());
        case StringValue stringValue       -> Val.JSON.textNode(stringValue.getValue());
        case BooleanValue booleanValue     -> Val.JSON.booleanNode(booleanValue.isValue());
        case EnumValue enumValue           -> Val.JSON.textNode(enumValue.getName());
        case NullValue ignored             -> Val.JSON.nullNode();
        case ArrayValue arrayValue         -> {
            val array = Val.JSON.arrayNode();
            arrayValue.getValues().forEach(v -> array.add(convertValueToJson(v)));
            yield array;
        }
        case ObjectValue objectValue       -> {
            val object = Val.JSON.objectNode();
            objectValue.getObjectFields()
                    .forEach(field -> object.set(field.getName(), convertValueToJson(field.getValue())));
            yield object;
        }
        case VariableReference variableRef -> {
            val varObject = Val.JSON.objectNode();
            varObject.put("$variable", variableRef.getName());
            yield varObject;
        }
        default                            -> Val.JSON.textNode(value.toString());
        };
    }

    /**
     * Extracts variable definitions from an operation.
     * Only includes variables that have default values specified in the query.
     * Variables without default values are excluded from the result.
     *
     * @param operation the operation definition
     * @return object containing variable definitions with their default values as
     * proper JSON types (numbers, strings, booleans, arrays, objects)
     */
    private static ObjectNode extractVariablesFromOperation(OperationDefinition operation) {
        val variables = Val.JSON.objectNode();

        if (operation.getVariableDefinitions() == null) {
            return variables;
        }

        for (VariableDefinition variableDefinition : operation.getVariableDefinitions()) {
            val variableName = variableDefinition.getName();
            val defaultValue = variableDefinition.getDefaultValue();

            if (defaultValue != null) {
                variables.set(variableName, convertValueToJson(defaultValue));
            }
        }

        return variables;
    }

    /**
     * Calculates basic complexity score from field count and depth.
     *
     * @param fieldCount total number of fields
     * @param depth maximum nesting depth
     * @return complexity score
     */
    private static int calculateBasicComplexity(int fieldCount, int depth) {
        return fieldCount + (depth * DEPTH_COMPLEXITY_FACTOR);
    }

    /**
     * Builds a JSON array from a list of items using a mapper function.
     *
     * @param items list of items to convert
     * @param mapper function to convert each item to a JsonNode
     * @param <T> type of items in the list
     * @return JSON array node
     */
    private static <T> ArrayNode buildArray(List<T> items, Function<T, JsonNode> mapper) {
        val array = Val.JSON.arrayNode();
        items.stream().map(mapper).forEach(array::add);
        return array;
    }

    /**
     * Builds a JSON array from strings.
     *
     * @param items list of strings
     * @return JSON array node
     */
    private static ArrayNode buildStringArray(List<String> items) {
        val array = Val.JSON.arrayNode();
        items.forEach(array::add);
        return array;
    }

    /**
     * Adds empty default values for properties when query is invalid.
     *
     * @param result the result object to populate with defaults
     */
    private static void addEmptyDefaults(ObjectNode result) {
        // Add scalar defaults
        DEFAULT_VALUES.forEach((key, value) -> {
            switch (value) {
            case Integer i -> result.put(key, i);
            case Double d  -> result.put(key, d);
            case Boolean b -> result.put(key, b);
            case String s  -> result.put(key, s);
            default        -> throw new IllegalStateException("Unexpected default value type: " + value.getClass());
            }
        });

        // Add array defaults
        ARRAY_PROPERTIES.forEach(key -> result.set(key, Val.JSON.arrayNode()));

        // Add object defaults
        OBJECT_PROPERTIES.forEach(key -> result.set(key, Val.JSON.objectNode()));
    }

    /**
     * Creates an error result object with default values.
     *
     * @param errorMessage the error message to include
     * @return Val containing error result
     */
    private static Val createErrorResult(String errorMessage) {
        val result = Val.JSON.objectNode();
        result.put(PROP_VALID, false);
        val errors = Val.JSON.arrayNode();
        errors.add(errorMessage);
        result.set(PROP_ERRORS, errors);
        addEmptyDefaults(result);
        return Val.of(result);
    }

    /**
     * Parses a GraphQL query document from a string.
     *
     * @param query the query string to parse
     * @return parsed Document
     */
    private static Document parseQueryDocument(String query) {
        return new Parser().parseDocument(query);
    }

    /**
     * Parses a GraphQL schema from a string and creates an executable schema with
     * caching.
     *
     * @param schemaString the schema definition string
     * @return executable GraphQLSchema
     */
    private static GraphQLSchema parseSchemaWithCache(String schemaString) {
        return SCHEMA_CACHE.computeIfAbsent(schemaString, key -> {
            val schemaParser           = new SchemaParser();
            val typeDefinitionRegistry = schemaParser.parse(key);
            val runtimeWiring          = RuntimeWiring.newRuntimeWiring().build();
            val schemaGenerator        = new SchemaGenerator();
            return schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
        });
    }

    /**
     * Validates the query document against the schema.
     *
     * @param document the parsed query document
     * @param schema the GraphQL schema
     * @return list of validation errors, empty if valid
     */
    private static List<ValidationError> validateQuery(Document document, GraphQLSchema schema) {
        val validator = new Validator();
        return validator.validateDocument(schema, document, Locale.ENGLISH);
    }

    /**
     * Extracts the first operation definition from a document.
     *
     * @param document the parsed document
     * @return the operation definition
     * @throws IllegalArgumentException if no operation found
     */
    private static OperationDefinition extractOperationDefinition(Document document) {
        return (OperationDefinition) document.getDefinitions().stream().filter(OperationDefinition.class::isInstance)
                .findFirst().orElseThrow(() -> new IllegalArgumentException(ERROR_NO_OPERATION));
    }

    /**
     * Determines the operation type from an operation definition.
     *
     * @param operation the operation definition
     * @return operation type string
     */
    private static String determineOperationType(OperationDefinition operation) {
        return switch (operation.getOperation()) {
        case QUERY        -> OPERATION_QUERY;
        case MUTATION     -> OPERATION_MUTATION;
        case SUBSCRIPTION -> OPERATION_SUBSCRIPTION;
        };
    }

    /**
     * Extracts the operation name from an operation definition.
     *
     * @param operation the operation definition
     * @return operation name or empty string if not specified
     */
    private static String extractOperationName(NamedNode<?> operation) {
        return Objects.requireNonNullElse(operation.getName(), "");
    }

    /**
     * Mutable metrics accumulator for collecting query analysis data during a
     * single-pass traversal. All metrics are accumulated during the traversal and
     * then converted to an immutable JSON result object. This mutable accumulator
     * pattern is more efficient than creating new immutable instances at each step.
     */
    private static class QueryMetrics {
        String           operation            = OPERATION_QUERY;
        String           operationName        = "";
        List<String>     fields               = new ArrayList<>();
        int              fieldCount           = 0;
        int              depth                = 0;
        boolean          isIntrospection      = false;
        ObjectNode       variables            = Val.JSON.objectNode();
        Set<String>      types                = new HashSet<>();
        List<ObjectNode> directivesList       = new ArrayList<>();
        ObjectNode       fragments            = Val.JSON.objectNode();
        int              aliasCount           = 0;
        int              rootFieldCount       = 0;
        int              batchingScore        = 0;
        int              maxPaginationLimit   = 0;
        ObjectNode       arguments            = Val.JSON.objectNode();
        int              fragmentCount        = 0;
        boolean          hasCircularFragments = false;
        int              directiveCount       = 0;
        double           directivesPerField   = 0.0;
    }
}
