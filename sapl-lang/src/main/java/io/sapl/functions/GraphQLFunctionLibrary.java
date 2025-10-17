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
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.*;

/**
 * GraphQL query parsing and analysis for authorization policies.
 * <p/>
 * Provides query inspection capabilities including field analysis, complexity
 * calculation, and security metrics for authorization decisions based on
 * GraphQL
 * query characteristics.
 */
@UtilityClass
@FunctionLibrary(name = GraphQLFunctionLibrary.NAME, description = GraphQLFunctionLibrary.DESCRIPTION, libraryDocumentation = GraphQLFunctionLibrary.DOCUMENTATION)
public class GraphQLFunctionLibrary {

    public static final String NAME = "graphql";

    public static final String DESCRIPTION = "GraphQL query parsing and analysis for authorization policies.";

    public static final String DOCUMENTATION = """
            # GraphQL Function Library for SAPL

            This library provides GraphQL query parsing and analysis capabilities for use in
            authorization policies. It enables inspection of GraphQL queries to make informed
            authorization decisions based on query structure, complexity, and content.

            ## Overview

            The library parses GraphQL queries and extracts security-relevant metrics for use in
            policy decisions. All analysis is performed in a single pass during parsing, and
            results are returned as an object with properties for direct access.

            ### Basic Usage

            ```
            var gql = graphql.parse(resource.query, resource."schema");

            // Access properties directly
            gql.valid                   // boolean - query validity
            gql.fields                  // array - all field names
            gql.depth                   // integer - maximum nesting depth
            gql.operation               // string - operation type
            gql.types                   // array - GraphQL types accessed
            gql.directives              // array - directive details
            gql.fragments               // object - fragment definitions
            gql.ast                     // object - complete raw AST
            ```

            ### Authorization Subscription Structure

            A typical authorization subscription for GraphQL authorization may look like this:

            ```
            {
              "subject": {
                "username": "alice",
                "role": "user"
              },
              "action": "execute",
              "resource": {
                "query": "query { user(id: \\"123\\") { ssn } }",
                "schema": "type Query { user(id: ID!): User ... }"
              }
            }
            ```

            The GraphQL query is the resource being accessed, and the action is typically "execute".
            The following documentation assumes a subscription in this format. But of course, this is
            only an example and can be combined with other approaches based on the application domain.

            ### Design Approach

            The library follows an "attributes first" design where all metrics are available as
            properties rather than requiring separate function calls. All analysis happens in a
            single parsing pass for efficiency, and the result object provides natural property
            access that works well with IDE autocomplete. The structure remains consistent
            regardless of query validity, making it straightforward to combine multiple checks
            in a single policy expression.

            ## Available Properties

            The parsed query object exposes all metrics as properties for convenient access in
            policy expressions. Each property is pre-calculated during parsing and provides
            specific information about the query structure, validation status, or potential
            security concerns.

            ### Basic Query Information

            - `valid` (boolean) - Indicates whether the query is syntactically correct and valid
              against the provided schema. Calculated by running the GraphQL validator. Use this
              to ensure queries are well-formed before making authorization decisions based on
              other metrics.

            - `operation` (string) - The GraphQL operation type. Returns "query", "mutation",
              "subscription", or "unknown". Extracted from the operation definition. Essential for
              applying different authorization rules to read versus write operations.

            - `operationName` (string) - The name specified in the operation definition, or an
              empty string if the operation is anonymous. Extracted directly from the parsed AST.
              Useful for logging and audit trails.

            - `errors` (array of strings) - Validation error messages describing why a query is
              invalid. Only present when `valid` is false. Contains human-readable descriptions of
              schema violations to help diagnose rejected queries.

            ### Field Analysis

            - `fields` (array of strings) - A flat list of all field names requested anywhere in
              the query, including nested fields. Extracted by recursively traversing the selection
              set. Used for field-level access control to deny access to sensitive fields like
              personally identifiable information.

            - `fieldCount` (integer) - The total number of fields requested in the query. Counted
              during field extraction. A high field count may indicate an overly broad query that
              could impact performance.

            - `depth` (integer) - The maximum nesting depth of field selections in the query.
              Calculated by recursively measuring selection set depth, capped at 100. Deep queries
              can lead to performance issues and are often used in denial-of-service attacks.

            - `isIntrospection` (boolean) - Indicates whether the query requests schema metadata
              through introspection fields (those starting with `__`). Detected by checking for
              the introspection prefix in field names. Introspection queries can reveal API
              structure to attackers.

            ### Type Information

            - `types` (array of strings) - GraphQL type names that are explicitly accessed in the
              query through inline fragments and fragment spreads. Extracted from type conditions.
              Use this for type-based access control to restrict access to sensitive types.

            ### Directive Information

            - `directives` (array of objects) - All directives used in the query with their
              arguments. Each object contains `name` (string) and `arguments` (object). Extracted
              by traversing all fields and selections. Use this to restrict specific custom
              directives or validate directive arguments.

            ### Fragment Information

            - `fragments` (object) - Fragment definitions mapping fragment names to their content.
              Each key is a fragment name, and the value is an object containing `typeName` (the
              type condition) and `fields` (array of field names in the fragment). Use this to
              analyze fragment content for security concerns.

            ### Complexity Metrics

            - `complexity` (integer) - A basic complexity score calculated as `fieldCount + (depth × 2)`.
              Provides a simple heuristic for query cost. Higher scores indicate queries that may
              consume more resources. This metric treats all fields equally.

            - `graphql.complexity(parsed, weights)` (function returning integer) - Calculates
              weighted complexity by assigning custom costs to specific fields. Pass a weights
              object mapping field names to integer costs. Fields not in the weights object default
              to cost 1. Use this when different fields have significantly different resource costs
              (e.g., database joins, external API calls).

            ### Security Analysis

            - `aliasCount` (integer) - The number of fields using aliases at the root level.
              Counted by checking for alias definitions in root selections. High alias counts can
              indicate query batching attacks where multiple requests are bundled into one to
              bypass rate limits.

            - `rootFieldCount` (integer) - The number of fields at the root level of the query.
              Counted directly from the operation's selection set. Combined with alias count to
              detect batching patterns.

            - `batchingScore` (integer) - A heuristic score for detecting batching attacks,
              calculated as `(aliasCount × 5) + rootFieldCount`. Higher scores suggest potential
              abuse. The multiplier weights aliases more heavily as they are the primary batching
              mechanism.

            - `maxPaginationLimit` (integer) - The largest value found in pagination arguments
              (`first`, `last`, `limit`, `offset`, `skip`, `take`). Extracted by scanning all
              field arguments. Large pagination limits can cause servers to return excessive
              amounts of data.

            - `fragmentCount` (integer) - The number of fragment definitions in the query. Counted
              by examining document definitions. While fragments are useful for query organization,
              excessive fragments may indicate unnecessarily complex queries.

            - `hasCircularFragments` (boolean) - Indicates whether any fragments reference each
              other in a cycle. Detected using depth-first search with cycle detection. Circular
              fragments can create infinite loops during query execution.

            - `directiveCount` (integer) - The total number of directives used across all fields.
              Counted by traversing the query and summing directive usage. Excessive directives can
              increase processing overhead.

            - `directivesPerField` (number) - The average number of directives per field,
              calculated as `directiveCount / fieldCount`. Returns 0 if there are no fields.
              A high ratio suggests potential directive abuse.

            ### Detailed Information

            - `arguments` (object) - Field arguments organized by field name. Each key is a field
              name mapping to an object of argument name-value pairs. Extracted during field
              traversal. Use this to validate specific argument values or ranges.

            - `variables` (object) - Query variable definitions with their default values. Only
              includes variables that have default values specified. Extracted from variable
              definitions in the operation. Useful for validating query parameterization.

            - `selectionSet` (array) - The complete AST structure of the query represented as
              nested JSON objects. Each object contains `Name`, `Alias`, `Args`, and `SelectionSet`
              properties. Provides low-level access to the query structure for advanced analysis.

            - `ast` (object) - The complete raw Abstract Syntax Tree representation of the parsed
              GraphQL document. This provides full access to all AST nodes and their properties
              for advanced custom analysis not covered by the pre-calculated metrics.

            ## Common Authorization Patterns

            ### Pattern 1: Field-Level Access Control

            Restricting access to specific fields based on user attributes.

            ```
            policy "protect-sensitive-fields"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              "ssn" in gql.fields && subject.role != "admin";
            ```

            ### Pattern 2: Query Complexity Limits

            Preventing resource-intensive queries from overloading the system.

            ```
            policy "limit-query-complexity"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.depth > 5 || gql.fieldCount > 100;
            ```

            ### Pattern 3: Weighted Complexity

            Assigning different costs to fields based on their resource requirements.

            ```
            policy "weighted-complexity"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              var weights = {"posts": 5, "comments": 3, "users": 2};
              graphql.complexity(gql, weights) > 200;
            ```

            ### Pattern 4: Operation Type Restrictions

            Applying different rules based on whether the operation reads or writes data.

            ```
            policy "restrict-mutations"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.operation == "mutation" && !(subject.role in ["editor", "admin"]);
            ```

            ### Pattern 5: Type-Based Access Control

            Restricting access to specific GraphQL types.

            ```
            policy "restrict-internal-types"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              "InternalUser" in gql.types && subject.clearanceLevel < 3;
            ```

            ### Pattern 6: Custom Directive Restrictions

            Blocking queries that use specific custom directives.

            ```
            policy "restrict-admin-directive"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.directives.[*].name contains "@admin" && subject.role != "admin";
            ```

            ### Pattern 7: Fragment Content Validation

            Checking fields within specific fragments.

            ```
            policy "validate-fragment-content"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              "ssn" in gql.fragments.UserDetails.fields && subject.role != "admin";
            ```

            ## Security Use Cases

            The following examples demonstrate how the library can be used to address common
            GraphQL security concerns. Each example shows a potential attack pattern and how
            to construct policies to mitigate it.

            ### Use Case 1: Sensitive Field Protection

            **Scenario**: Preventing unauthorized access to fields containing personally identifiable
            information or other sensitive data.

            **Example Query**:
            ```graphql
            query {
              user(id: "123") {
                name
                email
                ssn
                creditCard
              }
            }
            ```

            **Policy**:
            ```
            policy "sensitive-fields"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              ("ssn" in gql.fields || "creditCard" in gql.fields) && subject.clearanceLevel < 3;
            ```

            ### Use Case 2: Resource Exhaustion Prevention

            **Scenario**: Queries with excessive depth or field counts can cause database overload
            and slow response times.

            **Example Query**:
            ```graphql
            query {
              users {
                posts {
                  comments {
                    author {
                      posts {
                        comments {
                          author { ... }
                        }
                      }
                    }
                  }
                }
              }
            }
            ```

            **Policy**:
            ```
            policy "prevent-deep-queries"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.depth > 5 || gql.fieldCount > 100;
            ```

            ### Use Case 3: Schema Introspection Control

            **Scenario**: Introspection queries reveal API structure which can aid attackers in
            discovering vulnerabilities.

            **Example Query**:
            ```graphql
            query {
              __schema {
                types {
                  name
                  fields {
                    name
                  }
                }
              }
            }
            ```

            **Policy**:
            ```
            policy "restrict-introspection"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.isIntrospection && environment.stage == "production";
            ```

            ### Use Case 4: Batching Attack Detection

            **Scenario**: Using aliases to send many queries in a single request can bypass rate
            limits and enable data enumeration.

            **Example Query**:
            ```graphql
            query {
              user1: user(id: "1") { ssn }
              user2: user(id: "2") { ssn }
              user3: user(id: "3") { ssn }
            }
            ```

            **Policy**:
            ```
            policy "detect-batching"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.aliasCount > 10 || gql.batchingScore > 50;
            ```

            ### Use Case 5: Pagination Limit Enforcement

            **Scenario**: Large pagination arguments can cause the server to return excessive data.

            **Example Query**:
            ```graphql
            query {
              users(first: 999999) {
                posts(first: 999999) {
                  id
                }
              }
            }
            ```

            **Policy**:
            ```
            policy "limit-pagination"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.maxPaginationLimit > 100;
            ```

            ### Use Case 6: Circular Fragment Detection

            **Scenario**: Fragments that reference each other can create infinite loops.

            **Example Query**:
            ```graphql
            fragment UserInfo on User {
              posts { ...PostInfo }
            }

            fragment PostInfo on Post {
              author { ...UserInfo }
            }

            query { user(id: "1") { ...UserInfo } }
            ```

            **Policy**:
            ```
            policy "reject-circular-fragments"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.hasCircularFragments;
            ```

            ### Use Case 7: Directive Abuse Prevention

            **Scenario**: Excessive directive usage can increase processing overhead.

            **Example Query**:
            ```graphql
            query {
              user(id: "1")
                @include(if: true)
                @include(if: true)
              {
                name
              }
            }
            ```

            **Policy**:
            ```
            policy "limit-directives"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.directiveCount > 50 || gql.directivesPerField > 5;
            ```

            ### Use Case 8: Argument Validation

            **Scenario**: Field arguments may contain invalid or malicious values.

            **Example Query**:
            ```graphql
            query {
              users(limit: -1, filter: "admin=true OR 1=1") {
                id
              }
            }
            ```

            **Policy**:
            ```
            policy "validate-arguments"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.arguments.users.limit != null && gql.arguments.users.limit < 0;
            ```

            ### Use Case 9: Operation Type-Based Authorization

            **Scenario**: Mutations modify data and typically require higher privileges than queries.

            **Example Query**:
            ```graphql
            mutation {
              deleteUser(id: "123") {
                id
              }
            }
            ```

            **Policy**:
            ```
            policy "restrict-mutations"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.operation == "mutation" && !(subject.role in ["editor", "admin"]);
            ```

            ### Use Case 10: Subscription Control

            **Scenario**: Real-time subscriptions consume server resources and may have different
            authorization requirements.

            **Example Query**:
            ```graphql
            subscription {
              messageAdded {
                id
                content
              }
            }
            ```

            **Policy**:
            ```
            policy "limit-subscriptions"
            deny action == "execute"
            where
              var gql = graphql.parse(resource.query, resource."schema");
              gql.operation == "subscription" && subject.tier != "premium";
            ```

            ### Use Case 11: Multi-Constraint Policies

            **Scenario**: Combining multiple security checks in a comprehensive policy.

            **Example Query**:
            ```graphql
            query {
              user1: user(id: "1") {
                posts(first: 9999) {
                  comments {
                    author {
                      posts {
                        ssn
                      }
                    }
                  }
                }
              }
              user2: user(id: "2") { ... }
            }
            ```

            **Policy**:
            ```
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
              gql.directiveCount <= 50 &&
              !gql.isIntrospection &&
              !("ssn" in gql.fields);
            ```

            ### Use Case 12: Tier-Based Complexity Budgets

            **Scenario**: Different user tiers may have different resource allocation limits.

            **Policy**:
            ```
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

            ## Additional Notes

            ### Performance Considerations

            The library performs all analysis during the initial parse operation. For optimal
            performance, parse the query once and reuse the result object throughout the policy
            evaluation rather than calling `parse()` multiple times.

            ### Error Handling

            When a query cannot be parsed or validated, the library returns an error Val or sets
            `valid` to false with error details in the `errors` array. Policies should check the
            `valid` property before relying on other metrics.

            ### Schema Requirements

            The parse function requires a GraphQL schema definition. This should be the same schema
            that the GraphQL server uses to execute queries. Schema validation ensures that field
            names, types, and structure are checked against the API contract.
            """;

    // Argument name constants
    private static final String ARG_FIRST  = "first";
    private static final String ARG_LAST   = "last";
    private static final String ARG_LIMIT  = "limit";
    private static final String ARG_OFFSET = "offset";
    private static final String ARG_SKIP   = "skip";
    private static final String ARG_TAKE   = "take";

    // AST structure field name constants
    private static final String AST_ALIAS                = "Alias";
    private static final String AST_ALIAS_LOWER          = "alias";
    private static final String AST_ARGUMENTS            = "Args";
    private static final String AST_ARGUMENTS_LOWER      = "arguments";
    private static final String AST_DEFAULT_VALUE        = "defaultValue";
    private static final String AST_DEFINITIONS          = "definitions";
    private static final String AST_DIRECTIVES           = "directives";
    private static final String AST_KIND                 = "kind";
    private static final String AST_NAME                 = "Name";
    private static final String AST_NAME_LOWER           = "name";
    private static final String AST_OPERATION            = "operation";
    private static final String AST_SELECTION_SET        = "SelectionSet";
    private static final String AST_SELECTION_SET_LOWER  = "selectionSet";
    private static final String AST_SELECTIONS           = "selections";
    private static final String AST_TYPE                 = "type";
    private static final String AST_TYPE_CONDITION       = "typeCondition";
    private static final String AST_VALUE                = "Value";
    private static final String AST_VARIABLE             = "variable";
    private static final String AST_VARIABLE_DEFINITIONS = "variableDefinitions";

    // AST node kind constants
    private static final String NODE_DIRECTIVE            = "Directive";
    private static final String NODE_FIELD                = "Field";
    private static final String NODE_FRAGMENT_DEFINITION  = "FragmentDefinition";
    private static final String NODE_FRAGMENT_SPREAD      = "FragmentSpread";
    private static final String NODE_INLINE_FRAGMENT      = "InlineFragment";
    private static final String NODE_OPERATION_DEFINITION = "OperationDefinition";
    private static final String NODE_SELECTION_SET        = "SelectionSet";
    private static final String NODE_VARIABLE_DEFINITION  = "VariableDefinition";

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

    // Error message constants
    private static final String ERROR_NO_OPERATION = "No operation definition found";
    private static final String ERROR_PARSE_FAILED = "Failed to parse GraphQL query: ";

    // Operation type constants
    private static final String OPERATION_MUTATION     = "mutation";
    private static final String OPERATION_QUERY        = "query";
    private static final String OPERATION_SUBSCRIPTION = "subscription";
    private static final String OPERATION_UNKNOWN      = "unknown";

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
    private static final String PROP_SELECTION_SET          = "selectionSet";
    private static final String PROP_TYPES                  = "types";
    private static final String PROP_VALID                  = "valid";
    private static final String PROP_VARIABLES              = "variables";

    // Special field prefixes
    private static final String INTROSPECTION_PREFIX = "__";

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
                  "description": "The name of the operation if specified, empty string otherwise"
                },
                "fields": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "Flat array of all field names requested in the query"
                },
                "depth": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Maximum nesting depth of the query"
                },
                "fieldCount": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Total number of fields requested"
                },
                "isIntrospection": {
                  "type": "boolean",
                  "description": "True if this is a schema introspection query"
                },
                "complexity": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Basic complexity score (fieldCount + depth * 2)"
                },
                "aliasCount": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Number of aliased fields (batching attack detection)"
                },
                "rootFieldCount": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Number of root-level fields"
                },
                "batchingScore": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Batching attack heuristic score"
                },
                "maxPaginationLimit": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Largest pagination argument value (first, last, limit, etc.)"
                },
                "fragmentCount": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Number of fragments in the query"
                },
                "hasCircularFragments": {
                  "type": "boolean",
                  "description": "True if circular fragment references detected"
                },
                "directiveCount": {
                  "type": "integer",
                  "minimum": 0,
                  "description": "Total number of directives used"
                },
                "directivesPerField": {
                  "type": "number",
                  "minimum": 0,
                  "description": "Average number of directives per field"
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
                  "description": "All directives used in the query with their arguments"
                },
                "types": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "GraphQL type names accessed in the query"
                },
                "fragments": {
                  "type": "object",
                  "description": "Fragment definitions with their type and fields",
                  "additionalProperties": {
                    "type": "object",
                    "properties": {
                      "typeName": {"type": "string"},
                      "fields": {"type": "array", "items": {"type": "string"}}
                    }
                  }
                },
                "arguments": {
                  "type": "object",
                  "description": "All field arguments (field name -> argument name -> value)"
                },
                "variables": {
                  "type": "object",
                  "description": "Query variable definitions with their default values"
                },
                "selectionSet": {
                  "type": "array",
                  "description": "Detailed AST structure of the query",
                  "items": {
                    "type": "object",
                    "properties": {
                      "Name": {"type": "string"},
                      "Alias": {"type": "string"},
                      "Args": {"type": "array"},
                      "SelectionSet": {"type": "array"}
                    }
                  }
                },
                "ast": {
                  "type": "object",
                  "description": "Complete raw Abstract Syntax Tree of the document"
                },
                "errors": {
                  "type": "array",
                  "items": {"type": "string"},
                  "description": "Validation error messages (only present if valid is false)"
                }
              },
              "required": ["valid", "operation", "operationName", "fields", "depth", "fieldCount",
                           "isIntrospection", "complexity", "aliasCount", "rootFieldCount",
                           "batchingScore", "maxPaginationLimit", "fragmentCount", "hasCircularFragments",
                           "directiveCount", "directivesPerField", "directives", "types", "fragments",
                           "arguments", "variables", "selectionSet", "ast"]
            }
            """;

    // Pagination argument names
    private static final Set<String> PAGINATION_ARGS = Set.of(ARG_FIRST, ARG_LAST, ARG_LIMIT, ARG_OFFSET, ARG_SKIP,
            ARG_TAKE);

    /**
     * Parses and analyzes a GraphQL query with schema validation.
     * <p/>
     * Returns metadata covering query characteristics relevant for security
     * analysis. Metrics are calculated during parsing.
     *
     * @param query the GraphQL query string to parse
     * @param schema the GraphQL schema definition
     * @return Val containing query object with security metrics, or Val.error on
     * failure
     */
    @Function(docs = """
            ```
            graphql.parse(TEXT query, TEXT schema) -> OBJECT
            ```

            Parses a GraphQL query and validates it against a schema, returning
            comprehensive security analysis.

            All security metrics are pre-calculated and available as properties:

            Basic Properties:
            - `gql.valid`, `gql.operation`, `gql.fields`, `gql.depth`, `gql.fieldCount`,
              `gql.complexity`, `gql.isIntrospection`

            Type and Directive Information:
            - `gql.types`, `gql.directives`, `gql.fragments`

            Advanced Security:
            - `gql.aliasCount`, `gql.rootFieldCount`, `gql.batchingScore`,
              `gql.maxPaginationLimit`, `gql.arguments`, `gql.fragmentCount`,
              `gql.hasCircularFragments`, `gql.directiveCount`, `gql.directivesPerField`

            Raw AST Access:
            - `gql.ast`

            **Example:**
            ```sapl
            var gql = graphql.parse(resource.query, resource."schema");
            gql.valid && gql.depth <= 5 && !("ssn" in gql.fields)
            ```
            """, schema = RETURNS_PARSED_QUERY)
    public static Val parse(@Text Val query, @Text Val schema) {
        try {
            val document      = parseQueryDocument(query.getText());
            val graphQLSchema = parseSchema(schema.getText());

            val result = Val.JSON.objectNode();

            val validationErrors = validateQuery(document, graphQLSchema);
            val isValid          = validationErrors.isEmpty();

            result.put(PROP_VALID, isValid);

            if (!isValid) {
                result.set(PROP_ERRORS, buildErrorArray(validationErrors));
                addEmptyDefaults(result);
                return Val.of(result);
            }

            populateQueryAnalysis(document, result);

            return Val.of(result);

        } catch (InvalidSyntaxException | SchemaProblem | IllegalArgumentException exception) {
            return createErrorResult(ERROR_PARSE_FAILED + exception.getMessage());
        }
    }

    /**
     * Parses and analyzes a GraphQL query without schema validation.
     * <p/>
     * Returns metadata covering query characteristics without validating against
     * a schema. The valid property indicates syntactic correctness only. Metrics
     * are calculated during parsing.
     *
     * @param query the GraphQL query string to parse
     * @return Val containing query object with security metrics, or Val.error on
     * failure
     */
    @Function(docs = """
            ```
            graphql.parseQuery(TEXT query) -> OBJECT
            ```

            Parses a GraphQL query without schema validation, returning comprehensive
            security analysis based on syntax only.

            The valid property indicates syntactic correctness only, not schema compliance.
            All other security metrics are pre-calculated and available as properties.

            Basic Properties:
            - `gql.valid`, `gql.operation`, `gql.fields`, `gql.depth`, `gql.fieldCount`,
              `gql.complexity`, `gql.isIntrospection`

            Type and Directive Information:
            - `gql.types`, `gql.directives`, `gql.fragments`

            Advanced Security:
            - `gql.aliasCount`, `gql.rootFieldCount`, `gql.batchingScore`,
              `gql.maxPaginationLimit`, `gql.arguments`, `gql.fragmentCount`,
              `gql.hasCircularFragments`, `gql.directiveCount`, `gql.directivesPerField`

            Raw AST Access:
            - `gql.ast`

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
     *
     * @param parsed the parsed query object from parse() or parseQuery()
     * @param fieldWeights object mapping field names to numeric weights
     * @return Val containing the weighted complexity score
     */
    @Function(docs = """
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
    @Function(docs = """
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
     * Populates a result object with query analysis data.
     *
     * @param document the parsed query document
     * @param result the result object to populate
     */
    private static void populateQueryAnalysis(Document document, ObjectNode result) {
        val operationDefinition = extractOperationDefinition(document);

        // Basic metrics
        result.set(PROP_OPERATION, Val.JSON.textNode(determineOperationType(operationDefinition)));
        result.set(PROP_OPERATION_NAME, Val.JSON.textNode(extractOperationName(operationDefinition)));

        val allFields = extractAllFields(operationDefinition);
        result.set(PROP_FIELDS, buildFieldArray(allFields));
        result.set(PROP_FIELD_COUNT, Val.JSON.numberNode(allFields.size()));

        val depth = calculateDepth(operationDefinition.getSelectionSet(), 0);
        result.set(PROP_DEPTH, Val.JSON.numberNode(depth));

        result.set(PROP_IS_INTROSPECTION, Val.JSON.booleanNode(detectIntrospection(allFields)));
        result.set(PROP_VARIABLES, extractVariablesFromOperation(operationDefinition));
        result.set(PROP_COMPLEXITY, Val.JSON.numberNode(calculateBasicComplexity(allFields.size(), depth)));

        // Type information
        result.set(PROP_TYPES, extractTypes(document));

        // Directive information
        result.set(PROP_DIRECTIVES, extractDirectives(operationDefinition));

        // Fragment information
        val fragmentDetails = extractFragmentDetails(document);
        result.set(PROP_FRAGMENTS, fragmentDetails);

        // Advanced security metrics
        val aliasAnalysis = analyzeAliases(operationDefinition);
        result.set(PROP_ALIAS_COUNT, Val.JSON.numberNode(aliasAnalysis.get(PROP_ALIAS_COUNT).asInt()));
        result.set(PROP_ROOT_FIELD_COUNT, Val.JSON.numberNode(aliasAnalysis.get(PROP_ROOT_FIELD_COUNT).asInt()));
        result.set(PROP_BATCHING_SCORE, Val.JSON.numberNode(aliasAnalysis.get(PROP_BATCHING_SCORE).asInt()));

        val argumentAnalysis = analyzeArguments(operationDefinition);
        result.set(PROP_MAX_PAGINATION_LIMIT,
                Val.JSON.numberNode(argumentAnalysis.get(PROP_MAX_PAGINATION_LIMIT).asInt()));
        result.set(PROP_ARGUMENTS, argumentAnalysis.get(PROP_ARGUMENTS));

        val fragmentAnalysis = analyzeFragments(document);
        result.set(PROP_FRAGMENT_COUNT, Val.JSON.numberNode(fragmentAnalysis.get(PROP_FRAGMENT_COUNT).asInt()));
        result.set(PROP_HAS_CIRCULAR_FRAGMENTS,
                Val.JSON.booleanNode(fragmentAnalysis.get(PROP_HAS_CIRCULAR_FRAGMENTS).asBoolean()));

        val directiveAnalysis = analyzeDirectives(operationDefinition);
        result.set(PROP_DIRECTIVE_COUNT, Val.JSON.numberNode(directiveAnalysis.get(PROP_DIRECTIVE_COUNT).asInt()));
        result.set(PROP_DIRECTIVES_PER_FIELD,
                Val.JSON.numberNode(directiveAnalysis.get(PROP_DIRECTIVES_PER_FIELD).asDouble()));

        result.set(PROP_SELECTION_SET, buildStructuredAst(operationDefinition.getSelectionSet()));

        // Raw AST
        result.set(PROP_AST, buildCompleteAst(document));
    }

    /**
     * Extracts GraphQL type names accessed in the query.
     *
     * @param document the parsed document
     * @return JSON array of type names
     */
    private static ArrayNode extractTypes(Document document) {
        val types = new HashSet<String>();

        for (Definition<?> definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition operation) {
                extractTypesFromSelectionSet(operation.getSelectionSet(), types);
            } else if (definition instanceof FragmentDefinition fragment) {
                types.add(fragment.getTypeCondition().getName());
                extractTypesFromSelectionSet(fragment.getSelectionSet(), types);
            }
        }

        val array = Val.JSON.arrayNode();
        types.forEach(array::add);
        return array;
    }

    /**
     * Extracts type names from a selection set recursively.
     *
     * @param selectionSet the selection set to process
     * @param accumulator set to accumulate type names
     */
    private static void extractTypesFromSelectionSet(SelectionSet selectionSet, Set<String> accumulator) {
        if (selectionSet == null) {
            return;
        }

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof InlineFragment fragment) {
                if (fragment.getTypeCondition() != null) {
                    accumulator.add(fragment.getTypeCondition().getName());
                }
                extractTypesFromSelectionSet(fragment.getSelectionSet(), accumulator);
            } else if (selection instanceof Field field) {
                extractTypesFromSelectionSet(field.getSelectionSet(), accumulator);
            }
        }
    }

    /**
     * Extracts directives used in the query.
     *
     * @param operation the operation definition
     * @return JSON array of directive objects
     */
    private static ArrayNode extractDirectives(OperationDefinition operation) {
        val directiveList = new ArrayList<ObjectNode>();
        extractDirectivesRecursive(operation.getSelectionSet(), directiveList);

        val array = Val.JSON.arrayNode();
        directiveList.forEach(array::add);
        return array;
    }

    /**
     * Extracts directives from a selection set recursively.
     *
     * @param selectionSet the selection set to process
     * @param accumulator list to accumulate directive objects
     */
    private static void extractDirectivesRecursive(SelectionSet selectionSet, List<ObjectNode> accumulator) {
        if (selectionSet == null) {
            return;
        }

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                processDirectives(field.getDirectives(), accumulator);
                extractDirectivesRecursive(field.getSelectionSet(), accumulator);
            } else if (selection instanceof InlineFragment fragment) {
                processDirectives(fragment.getDirectives(), accumulator);
                extractDirectivesRecursive(fragment.getSelectionSet(), accumulator);
            } else if (selection instanceof FragmentSpread spread) {
                processDirectives(spread.getDirectives(), accumulator);
            }
        }
    }

    /**
     * Processes a list of directives and adds them to the accumulator.
     *
     * @param directives the list of directives
     * @param accumulator list to accumulate directive objects
     */
    private static void processDirectives(List<Directive> directives, List<ObjectNode> accumulator) {
        if (directives == null) {
            return;
        }

        for (Directive directive : directives) {
            val directiveNode = Val.JSON.objectNode();
            directiveNode.put(AST_NAME_LOWER, directive.getName());

            val argsNode = Val.JSON.objectNode();
            if (directive.getArguments() != null) {
                for (Argument argument : directive.getArguments()) {
                    argsNode.put(argument.getName(), argument.getValue().toString());
                }
            }
            directiveNode.set(AST_ARGUMENTS_LOWER, argsNode);

            accumulator.add(directiveNode);
        }
    }

    /**
     * Extracts fragment definitions with their type and fields.
     *
     * @param document the parsed document
     * @return JSON object mapping fragment names to their details
     */
    private static ObjectNode extractFragmentDetails(Document document) {
        val fragmentsNode = Val.JSON.objectNode();

        for (Definition<?> definition : document.getDefinitions()) {
            if (definition instanceof FragmentDefinition fragment) {
                val fragmentInfo = Val.JSON.objectNode();
                fragmentInfo.put(FRAGMENT_TYPE_NAME, fragment.getTypeCondition().getName());

                val fields = new ArrayList<String>();
                extractFieldsRecursive(fragment.getSelectionSet(), fields);
                fragmentInfo.set(FRAGMENT_FIELDS, buildFieldArray(fields));

                fragmentsNode.set(fragment.getName(), fragmentInfo);
            }
        }

        return fragmentsNode;
    }

    /**
     * Builds an AST representation of the document.
     *
     * @param document the parsed document
     * @return JSON object representing the AST
     */
    private static ObjectNode buildCompleteAst(Document document) {
        val astNode     = Val.JSON.objectNode();
        val definitions = Val.JSON.arrayNode();

        for (Definition<?> definition : document.getDefinitions()) {
            if (definition instanceof OperationDefinition operation) {
                definitions.add(buildOperationDefinitionNode(operation));
            } else if (definition instanceof FragmentDefinition fragment) {
                definitions.add(buildFragmentDefinitionNode(fragment));
            }
        }

        astNode.set(AST_DEFINITIONS, definitions);
        return astNode;
    }

    /**
     * Builds an AST node for an operation definition.
     *
     * @param operation the operation definition
     * @return JSON object representing the operation
     */
    private static ObjectNode buildOperationDefinitionNode(OperationDefinition operation) {
        val node = Val.JSON.objectNode();
        node.put(AST_KIND, NODE_OPERATION_DEFINITION);
        node.put(AST_OPERATION, operation.getOperation().name().toLowerCase());
        node.put(AST_NAME_LOWER, operation.getName() != null ? operation.getName() : "");
        node.set(AST_VARIABLE_DEFINITIONS, buildVariableDefinitionsArray(operation.getVariableDefinitions()));
        node.set(AST_DIRECTIVES, buildDirectivesArray(operation.getDirectives()));
        node.set(AST_SELECTION_SET_LOWER, buildSelectionSetNode(operation.getSelectionSet()));
        return node;
    }

    /**
     * Builds an AST node for a fragment definition.
     *
     * @param fragment the fragment definition
     * @return JSON object representing the fragment
     */
    private static ObjectNode buildFragmentDefinitionNode(FragmentDefinition fragment) {
        val node = Val.JSON.objectNode();
        node.put(AST_KIND, NODE_FRAGMENT_DEFINITION);
        node.put(AST_NAME_LOWER, fragment.getName());
        node.put(AST_TYPE_CONDITION, fragment.getTypeCondition().getName());
        node.set(AST_DIRECTIVES, buildDirectivesArray(fragment.getDirectives()));
        node.set(AST_SELECTION_SET_LOWER, buildSelectionSetNode(fragment.getSelectionSet()));
        return node;
    }

    /**
     * Builds an array of variable definitions.
     *
     * @param variableDefinitions the list of variable definitions
     * @return JSON array of variable definition nodes
     */
    private static ArrayNode buildVariableDefinitionsArray(List<VariableDefinition> variableDefinitions) {
        val array = Val.JSON.arrayNode();
        if (variableDefinitions == null) {
            return array;
        }

        for (VariableDefinition varDef : variableDefinitions) {
            val node = Val.JSON.objectNode();
            node.put(AST_KIND, NODE_VARIABLE_DEFINITION);
            node.put(AST_VARIABLE, varDef.getName());
            node.put(AST_TYPE, varDef.getType().toString());
            if (varDef.getDefaultValue() != null) {
                node.put(AST_DEFAULT_VALUE, varDef.getDefaultValue().toString());
            }
            array.add(node);
        }

        return array;
    }

    /**
     * Builds an array of directive nodes.
     *
     * @param directives the list of directives
     * @return JSON array of directive nodes
     */
    private static ArrayNode buildDirectivesArray(List<Directive> directives) {
        val array = Val.JSON.arrayNode();
        if (directives == null) {
            return array;
        }

        for (Directive directive : directives) {
            val node = Val.JSON.objectNode();
            node.put(AST_KIND, NODE_DIRECTIVE);
            node.put(AST_NAME_LOWER, directive.getName());
            node.set(AST_ARGUMENTS_LOWER, buildArgumentsArray(directive.getArguments()));
            array.add(node);
        }

        return array;
    }

    /**
     * Builds a selection set node.
     *
     * @param selectionSet the selection set
     * @return JSON object representing the selection set
     */
    private static ObjectNode buildSelectionSetNode(SelectionSet selectionSet) {
        val node = Val.JSON.objectNode();
        node.put(AST_KIND, NODE_SELECTION_SET);
        node.set(AST_SELECTIONS, buildSelectionsArray(selectionSet));
        return node;
    }

    /**
     * Builds an array of selection nodes.
     *
     * @param selectionSet the selection set
     * @return JSON array of selection nodes
     */
    private static ArrayNode buildSelectionsArray(SelectionSet selectionSet) {
        val array = Val.JSON.arrayNode();
        if (selectionSet == null) {
            return array;
        }

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                array.add(buildFieldAstNode(field));
            } else if (selection instanceof InlineFragment fragment) {
                array.add(buildInlineFragmentNode(fragment));
            } else if (selection instanceof FragmentSpread spread) {
                array.add(buildFragmentSpreadNode(spread));
            }
        }

        return array;
    }

    /**
     * Builds an AST node for a field.
     *
     * @param field the field
     * @return JSON object representing the field
     */
    private static ObjectNode buildFieldAstNode(Field field) {
        val node = Val.JSON.objectNode();
        node.put(AST_KIND, NODE_FIELD);
        node.put(AST_NAME_LOWER, field.getName());
        if (field.getAlias() != null) {
            node.put(AST_ALIAS_LOWER, field.getAlias());
        }
        node.set(AST_ARGUMENTS_LOWER, buildArgumentsArray(field.getArguments()));
        node.set(AST_DIRECTIVES, buildDirectivesArray(field.getDirectives()));
        if (field.getSelectionSet() != null) {
            node.set(AST_SELECTION_SET_LOWER, buildSelectionSetNode(field.getSelectionSet()));
        }
        return node;
    }

    /**
     * Builds an AST node for an inline fragment.
     *
     * @param fragment the inline fragment
     * @return JSON object representing the inline fragment
     */
    private static ObjectNode buildInlineFragmentNode(InlineFragment fragment) {
        val node = Val.JSON.objectNode();
        node.put(AST_KIND, NODE_INLINE_FRAGMENT);
        if (fragment.getTypeCondition() != null) {
            node.put(AST_TYPE_CONDITION, fragment.getTypeCondition().getName());
        }
        node.set(AST_DIRECTIVES, buildDirectivesArray(fragment.getDirectives()));
        node.set(AST_SELECTION_SET_LOWER, buildSelectionSetNode(fragment.getSelectionSet()));
        return node;
    }

    /**
     * Builds an AST node for a fragment spread.
     *
     * @param spread the fragment spread
     * @return JSON object representing the fragment spread
     */
    private static ObjectNode buildFragmentSpreadNode(FragmentSpread spread) {
        val node = Val.JSON.objectNode();
        node.put(AST_KIND, NODE_FRAGMENT_SPREAD);
        node.put(AST_NAME_LOWER, spread.getName());
        node.set(AST_DIRECTIVES, buildDirectivesArray(spread.getDirectives()));
        return node;
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
        val parser = new Parser();
        return parser.parseDocument(query);
    }

    /**
     * Parses a GraphQL schema from a string and creates an executable schema.
     *
     * @param schemaString the schema definition string
     * @return executable GraphQLSchema
     */
    private static GraphQLSchema parseSchema(String schemaString) {
        val schemaParser           = new SchemaParser();
        val typeDefinitionRegistry = schemaParser.parse(schemaString);
        val runtimeWiring          = RuntimeWiring.newRuntimeWiring().build();
        val schemaGenerator        = new SchemaGenerator();
        return schemaGenerator.makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
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
    private static String extractOperationName(OperationDefinition operation) {
        return operation.getName() != null ? operation.getName() : "";
    }

    /**
     * Extracts field names from an operation definition.
     *
     * @param operation the operation definition
     * @return list of field names
     */
    private static List<String> extractAllFields(OperationDefinition operation) {
        val fields = new ArrayList<String>();
        extractFieldsRecursive(operation.getSelectionSet(), fields);
        return fields;
    }

    /**
     * Extracts field names from a selection set recursively.
     *
     * @param selectionSet the selection set to process
     * @param accumulator list to accumulate field names
     */
    private static void extractFieldsRecursive(SelectionSet selectionSet, List<String> accumulator) {
        if (selectionSet == null) {
            return;
        }

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                accumulator.add(field.getName());
                extractFieldsRecursive(field.getSelectionSet(), accumulator);
            } else if (selection instanceof InlineFragment fragment) {
                extractFieldsRecursive(fragment.getSelectionSet(), accumulator);
            }
        }
    }

    /**
     * Calculates the maximum nesting depth of a selection set.
     *
     * @param selectionSet the selection set to analyze
     * @param currentDepth the current depth level
     * @return maximum depth
     */
    private static int calculateDepth(SelectionSet selectionSet, int currentDepth) {
        if (selectionSet == null || selectionSet.getSelections().isEmpty()) {
            return currentDepth;
        }

        var maxDepth = currentDepth + 1;

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                val fieldDepth = calculateDepth(field.getSelectionSet(), currentDepth + 1);
                maxDepth = Math.max(maxDepth, fieldDepth);
            } else if (selection instanceof InlineFragment fragment) {
                val fragmentDepth = calculateDepth(fragment.getSelectionSet(), currentDepth + 1);
                maxDepth = Math.max(maxDepth, fragmentDepth);
            }
        }

        return Math.min(maxDepth, DEFAULT_MAX_DEPTH);
    }

    /**
     * Detects whether any field in the list is an introspection field.
     *
     * @param fields list of field names
     * @return true if any field starts with introspection prefix
     */
    private static boolean detectIntrospection(List<String> fields) {
        return fields.stream().anyMatch(field -> field.startsWith(INTROSPECTION_PREFIX));
    }

    /**
     * Analyzes aliases in an operation to detect batching patterns.
     *
     * @param operation the operation definition
     * @return object containing alias analysis metrics
     */
    private static ObjectNode analyzeAliases(OperationDefinition operation) {
        val analysis       = Val.JSON.objectNode();
        var aliasCount     = 0;
        var rootFieldCount = 0;

        if (operation.getSelectionSet() != null) {
            for (Selection<?> selection : operation.getSelectionSet().getSelections()) {
                if (selection instanceof Field field) {
                    rootFieldCount++;
                    if (field.getAlias() != null) {
                        aliasCount++;
                    }
                }
            }
        }

        val batchingScore = aliasCount * BATCHING_SCORE_MULTIPLIER + rootFieldCount;

        analysis.put(PROP_ALIAS_COUNT, aliasCount);
        analysis.put(PROP_ROOT_FIELD_COUNT, rootFieldCount);
        analysis.put(PROP_BATCHING_SCORE, batchingScore);

        return analysis;
    }

    /**
     * Analyzes arguments in an operation to extract pagination limits and
     * argument details.
     *
     * @param operation the operation definition
     * @return object containing argument analysis metrics
     */
    private static ObjectNode analyzeArguments(OperationDefinition operation) {
        val analysis            = Val.JSON.objectNode();
        val argumentsMap        = Val.JSON.objectNode();
        val maxPaginationHolder = new int[] { 0 };

        analyzeArgumentsRecursive(operation.getSelectionSet(), argumentsMap, maxPaginationHolder);

        analysis.put(PROP_MAX_PAGINATION_LIMIT, maxPaginationHolder[0]);
        analysis.set(PROP_ARGUMENTS, argumentsMap);

        return analysis;
    }

    /**
     * Analyzes arguments in a selection set recursively.
     *
     * @param selectionSet the selection set to analyze
     * @param argumentsMap map to accumulate arguments
     * @param maxPaginationHolder array holding maximum pagination value
     */
    private static void analyzeArgumentsRecursive(SelectionSet selectionSet, ObjectNode argumentsMap,
            int[] maxPaginationHolder) {
        if (selectionSet == null) {
            return;
        }

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                processFieldArguments(field, argumentsMap, maxPaginationHolder);
                analyzeArgumentsRecursive(field.getSelectionSet(), argumentsMap, maxPaginationHolder);
            } else if (selection instanceof InlineFragment fragment) {
                analyzeArgumentsRecursive(fragment.getSelectionSet(), argumentsMap, maxPaginationHolder);
            }
        }
    }

    /**
     * Processes arguments for a single field.
     *
     * @param field the field to process
     * @param argumentsMap map to store field arguments
     * @param maxPaginationHolder array holding maximum pagination value
     */
    private static void processFieldArguments(Field field, ObjectNode argumentsMap, int[] maxPaginationHolder) {
        if (field.getArguments() == null || field.getArguments().isEmpty()) {
            return;
        }

        val fieldArgs = Val.JSON.objectNode();

        for (Argument argument : field.getArguments()) {
            val argName  = argument.getName();
            val argValue = argument.getValue();

            fieldArgs.put(argName, argValue.toString());
            updateMaxPaginationIfApplicable(argName, argValue, maxPaginationHolder);
        }

        argumentsMap.set(field.getName(), fieldArgs);
    }

    /**
     * Updates the maximum pagination limit if the argument is a pagination
     * argument.
     *
     * @param argumentName the argument name
     * @param argumentValue the argument value
     * @param maxPaginationHolder array holding maximum pagination value
     */
    private static void updateMaxPaginationIfApplicable(String argumentName, Value<?> argumentValue,
            int[] maxPaginationHolder) {
        if (PAGINATION_ARGS.contains(argumentName.toLowerCase()) && argumentValue instanceof IntValue intValue) {
            val value = intValue.getValue().intValue();
            maxPaginationHolder[0] = Math.max(maxPaginationHolder[0], value);
        }
    }

    /**
     * Analyzes fragments in a document to detect circular references.
     *
     * @param document the parsed document
     * @return object containing fragment analysis metrics
     */
    private static ObjectNode analyzeFragments(Document document) {
        val analysis    = Val.JSON.objectNode();
        val fragments   = extractFragmentDefinitions(document);
        val hasCircular = detectCircularFragments(fragments);

        analysis.put(PROP_FRAGMENT_COUNT, fragments.size());
        analysis.put(PROP_HAS_CIRCULAR_FRAGMENTS, hasCircular);

        return analysis;
    }

    /**
     * Extracts fragment definitions from a document.
     *
     * @param document the parsed document
     * @return map of fragment names to definitions
     */
    private static Map<String, FragmentDefinition> extractFragmentDefinitions(Document document) {
        val fragments = new HashMap<String, FragmentDefinition>();

        for (Definition<?> definition : document.getDefinitions()) {
            if (definition instanceof FragmentDefinition fragment) {
                fragments.put(fragment.getName(), fragment);
            }
        }

        return fragments;
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
            if (selection instanceof FragmentSpread spread) {
                spreads.add(spread.getName());
            } else if (selection instanceof Field field) {
                spreads.addAll(findFragmentSpreads(field.getSelectionSet()));
            } else if (selection instanceof InlineFragment fragment) {
                spreads.addAll(findFragmentSpreads(fragment.getSelectionSet()));
            }
        }

        return spreads;
    }

    /**
     * Analyzes directives in an operation to count usage.
     *
     * @param operation the operation definition
     * @return object containing directive analysis metrics
     */
    private static ObjectNode analyzeDirectives(OperationDefinition operation) {
        val analysis           = Val.JSON.objectNode();
        val counts             = new DirectiveCounts();
        countDirectivesRecursive(operation.getSelectionSet(), counts);
        val directivesPerField = counts.fieldCount > 0 ? (double) counts.directiveCount / counts.fieldCount : 0.0;

        analysis.put(PROP_DIRECTIVE_COUNT, counts.directiveCount);
        analysis.put(PROP_DIRECTIVES_PER_FIELD, directivesPerField);

        return analysis;
    }

    /**
     * Counts directives and fields in a selection set recursively.
     *
     * @param selectionSet the selection set to analyze
     * @param counts accumulator for directive and field counts
     */
    private static void countDirectivesRecursive(SelectionSet selectionSet, DirectiveCounts counts) {
        if (selectionSet == null) {
            return;
        }

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                counts.fieldCount++;
                counts.directiveCount += countDirectives(field.getDirectives());
                countDirectivesRecursive(field.getSelectionSet(), counts);
            } else if (selection instanceof InlineFragment fragment) {
                counts.directiveCount += countDirectives(fragment.getDirectives());
                countDirectivesRecursive(fragment.getSelectionSet(), counts);
            }
        }
    }

    /**
     * Mutable holder for directive and field counts.
     */
    private static class DirectiveCounts {
        int directiveCount = 0;
        int fieldCount = 0;
    }
    /**
     * Counts the number of directives in a list.
     *
     * @param directives the list of directives
     * @return number of directives, or 0 if list is null
     */
    private static int countDirectives(List<Directive> directives) {
        return directives != null ? directives.size() : 0;
    }

    /**
     * Extracts variable definitions from an operation.
     *
     * @param operation the operation definition
     * @return object containing variable definitions with default values
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
                variables.put(variableName, defaultValue.toString());
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
     * Builds a JSON array from a list of field names.
     *
     * @param fields list of field names
     * @return JSON array node
     */
    private static ArrayNode buildFieldArray(List<String> fields) {
        val array = Val.JSON.arrayNode();
        fields.forEach(array::add);
        return array;
    }

    /**
     * Builds a JSON array from a list of validation errors.
     *
     * @param errors list of validation errors
     * @return JSON array node containing error messages
     */
    private static ArrayNode buildErrorArray(List<ValidationError> errors) {
        val array = Val.JSON.arrayNode();
        errors.stream().map(ValidationError::getMessage).forEach(array::add);
        return array;
    }

    /**
     * Builds a structured AST representation from a selection set.
     *
     * @param selectionSet the selection set to convert
     * @return JSON array representing the AST
     */
    private static ArrayNode buildStructuredAst(SelectionSet selectionSet) {
        val ast = Val.JSON.arrayNode();

        if (selectionSet == null) {
            return ast;
        }

        for (Selection<?> selection : selectionSet.getSelections()) {
            if (selection instanceof Field field) {
                ast.add(buildFieldNode(field));
            }
        }

        return ast;
    }

    /**
     * Builds a JSON node representing a single field in the AST.
     *
     * @param field the field to convert
     * @return JSON object representing the field
     */
    private static ObjectNode buildFieldNode(Field field) {
        val fieldNode = Val.JSON.objectNode();
        fieldNode.put(AST_NAME, field.getName());
        fieldNode.put(AST_ALIAS, field.getAlias() != null ? field.getAlias() : "");
        fieldNode.set(AST_ARGUMENTS, buildArgumentsArray(field.getArguments()));

        if (field.getSelectionSet() != null) {
            fieldNode.set(AST_SELECTION_SET, buildStructuredAst(field.getSelectionSet()));
        }

        return fieldNode;
    }

    /**
     * Builds a JSON array of argument nodes from a list of arguments.
     *
     * @param arguments the list of arguments to convert
     * @return JSON array of argument nodes
     */
    private static ArrayNode buildArgumentsArray(List<Argument> arguments) {
        val argumentsArray = Val.JSON.arrayNode();

        if (arguments == null) {
            return argumentsArray;
        }

        for (Argument argument : arguments) {
            argumentsArray.add(buildArgumentNode(argument));
        }

        return argumentsArray;
    }

    /**
     * Builds a JSON node representing a single argument.
     *
     * @param argument the argument to convert
     * @return JSON object representing the argument
     */
    private static ObjectNode buildArgumentNode(Argument argument) {
        val argumentNode = Val.JSON.objectNode();
        argumentNode.put(AST_NAME, argument.getName());

        val valueNode = Val.JSON.objectNode();
        valueNode.put(AST_VALUE, argument.getValue().toString());
        argumentNode.set(AST_VALUE, valueNode);

        return argumentNode;
    }

    /**
     * Adds empty default values for properties when query is invalid.
     *
     * @param result the result object to populate with defaults
     */
    private static void addEmptyDefaults(ObjectNode result) {
        result.put(PROP_OPERATION, OPERATION_UNKNOWN);
        result.put(PROP_OPERATION_NAME, "");
        result.set(PROP_FIELDS, Val.JSON.arrayNode());
        result.put(PROP_DEPTH, 0);
        result.put(PROP_FIELD_COUNT, 0);
        result.put(PROP_IS_INTROSPECTION, false);
        result.set(PROP_VARIABLES, Val.JSON.objectNode());
        result.set(PROP_TYPES, Val.JSON.arrayNode());
        result.set(PROP_DIRECTIVES, Val.JSON.arrayNode());
        result.set(PROP_FRAGMENTS, Val.JSON.objectNode());
        result.put(PROP_COMPLEXITY, 0);
        result.put(PROP_ALIAS_COUNT, 0);
        result.put(PROP_ROOT_FIELD_COUNT, 0);
        result.put(PROP_BATCHING_SCORE, 0);
        result.put(PROP_MAX_PAGINATION_LIMIT, 0);
        result.put(PROP_FRAGMENT_COUNT, 0);
        result.put(PROP_HAS_CIRCULAR_FRAGMENTS, false);
        result.put(PROP_DIRECTIVE_COUNT, 0);
        result.put(PROP_DIRECTIVES_PER_FIELD, 0.0);
        result.set(PROP_ARGUMENTS, Val.JSON.objectNode());
        result.set(PROP_SELECTION_SET, Val.JSON.arrayNode());
        result.set(PROP_AST, Val.JSON.objectNode());
    }
}
