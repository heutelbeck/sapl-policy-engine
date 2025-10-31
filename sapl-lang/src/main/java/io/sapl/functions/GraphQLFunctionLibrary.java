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

import java.util.*;

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
import graphql.validation.Validator;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.JsonObject;
import io.sapl.api.validation.Text;
import lombok.experimental.UtilityClass;
import lombok.val;

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
            var gql = graphql.validateQuery(resource.query, resource."schema");

            // Access properties directly
            gql.valid                   // boolean - query validity
            gql.fields                  // array - all field names
            gql.depth                   // integer - maximum nesting depth
            gql.operation               // string - operation type (query/mutation/subscription)
            gql.complexity              // integer - complexity score

            // Security metrics
            gql.security.aliasCount              // integer - aliased field count
            gql.security.batchingScore           // integer - batching attack indicator
            gql.security.maxPaginationLimit      // integer - highest pagination limit
            gql.security.hasCircularFragments    // boolean - circular fragment detection
            gql.security.isIntrospection         // boolean - introspection query
            gql.security.directiveCount          // integer - directive count
            gql.security.directivesPerField      // number - average directives per field

            // AST details
            gql.ast.operationName       // string - operation name
            gql.ast.types               // array - types used
            gql.ast.variables           // object - variable definitions
            gql.ast.arguments           // object - field arguments
            gql.ast.fragments           // object - fragment definitions
            gql.ast.directives          // array - directive details
            ```

            ## Functions

            ### validateQuery

            ```
            graphql.validateQuery(TEXT query, TEXT schema) -> OBJECT
            ```

            Parses and validates a GraphQL query against a schema. Returns object with all security metrics.

            **Example:**
            ```sapl
            policy "validate-graphql-query"
            permit action == "execute"
            where
              var gql = graphql.validateQuery(resource.query, resource."schema");
              gql.valid && gql.depth <= 5 && !("ssn" in gql.fields);
            ```

            ### analyzeQuery

            ```
            graphql.analyzeQuery(TEXT query) -> OBJECT
            ```

            Parses a GraphQL query without schema validation. Returns same metrics as `validateQuery()` but `valid` only checks syntax.

            **Example:**
            ```sapl
            policy "analyze-query-structure"
            permit action == "execute"
            where
              var gql = graphql.analyzeQuery(resource.query);
              gql.depth <= 5 && gql.security.aliasCount <= 10;
            ```

            ### complexity

            ```
            graphql.complexity(OBJECT parsed, OBJECT fieldWeights) -> NUMBER
            ```

            Calculates weighted complexity using custom field weights. Unweighted fields default to 1.

            **Example:**
            ```sapl
            var gql = graphql.validateQuery(resource.query, resource."schema");
            var weights = {"posts": 5, "comments": 3, "user": 1};
            graphql.complexity(gql, weights) <= 200;
            ```

            ### parseSchema

            ```
            graphql.parseSchema(TEXT schema) -> OBJECT
            ```

            Parses and validates a GraphQL schema definition.

            ## Common Use Cases

            ### Field-Level Access Control

            ```sapl
            policy "restrict-pii-fields"
            deny action == "execute"
            where
              var gql = graphql.validateQuery(resource.query, resource."schema");
              var piiFields = ["ssn", "creditCard", "taxId"];
              array.containsAny(gql.fields, piiFields);
            ```

            ### Depth and Complexity Limiting

            ```sapl
            policy "enforce-limits"
            permit action == "execute"
            where
              var gql = graphql.validateQuery(resource.query, resource."schema");
              gql.valid && gql.depth <= 5 && gql.complexity <= 100;
            ```

            ### Operation Type Control

            ```sapl
            policy "mutations-require-admin"
            permit action == "execute"
            where
              var gql = graphql.validateQuery(resource.query, resource."schema");
              gql.operation != "mutation" || subject.role == "admin";
            ```

            ### Batching Attack Prevention

            ```sapl
            policy "prevent-batching"
            deny action == "execute"
            where
              var gql = graphql.validateQuery(resource.query, resource."schema");
              gql.security.aliasCount > 10 || gql.security.batchingScore > 50;
            ```

            ### Comprehensive Security

            ```sapl
            policy "comprehensive-security"
            permit action == "execute"
            where
              var gql = graphql.validateQuery(resource.query, resource."schema");
              gql.valid &&
              gql.depth <= 5 &&
              gql.fieldCount <= 50 &&
              gql.security.aliasCount <= 10 &&
              gql.security.maxPaginationLimit <= 100 &&
              !gql.security.hasCircularFragments &&
              !gql.security.isIntrospection &&
              !("ssn" in gql.fields);
            ```

            ## Error Handling

            Invalid queries set `valid` to false with errors in `errors` array. Check `valid` before using other metrics.
            """;

    // Configuration constants
    private static final int BATCHING_SCORE_MULTIPLIER = 5;
    private static final int DEFAULT_FIELD_WEIGHT      = 1;
    private static final int DEFAULT_MAX_DEPTH         = 100;

    /**
     * Complexity factor applied to query depth. Each level of nesting multiplies
     * the base complexity by this factor. Default is 2, meaning a query with
     * depth 3 adds 6 to the complexity score (3 * 2).
     */
    public static final int DEPTH_COMPLEXITY_FACTOR = 2;

    private static final int MAX_SCHEMA_CACHE_SIZE = 100;

    private static final String INTROSPECTION_PREFIX = "__";

    private static final Set<String> PAGINATION_ARGS = Set.of("first", "last", "limit", "offset", "skip", "take");
    private static final Set<String> PAGINATION_ARGS_LOWER = Set.of("first", "last", "limit", "offset", "skip", "take");

    // Schema cache configuration constants
    private static final float  CACHE_LOAD_FACTOR  = 0.75f;
    private static final boolean CACHE_ACCESS_ORDER = true;

    // Schema cache - LRU with size limit
    private static final Map<String, GraphQLSchema> SCHEMA_CACHE = Collections
            .synchronizedMap(new LinkedHashMap<>(MAX_SCHEMA_CACHE_SIZE, CACHE_LOAD_FACTOR, CACHE_ACCESS_ORDER) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, GraphQLSchema> eldest) {
                    return size() > MAX_SCHEMA_CACHE_SIZE;
                }
            });

    // Return type schema for IDE support
    private static final String RETURNS_PARSED_QUERY = """
            {
              "type": "object",
              "properties": {
                "valid": {"type": "boolean"},
                "operation": {"type": "string", "enum": ["query", "mutation", "subscription"]},
                "fields": {"type": "array", "items": {"type": "string"}},
                "fieldCount": {"type": "integer", "minimum": 0},
                "depth": {"type": "integer", "minimum": 0, "maximum": 100},
                "complexity": {"type": "integer", "minimum": 0},
                "security": {
                  "type": "object",
                  "properties": {
                    "aliasCount": {"type": "integer", "minimum": 0},
                    "rootFieldCount": {"type": "integer", "minimum": 0},
                    "batchingScore": {"type": "integer", "minimum": 0},
                    "maxPaginationLimit": {"type": "integer", "minimum": 0},
                    "hasCircularFragments": {"type": "boolean"},
                    "isIntrospection": {"type": "boolean"},
                    "directiveCount": {"type": "integer", "minimum": 0},
                    "directivesPerField": {"type": "number", "minimum": 0}
                  }
                },
                "ast": {
                  "type": "object",
                  "properties": {
                    "operationName": {"type": "string"},
                    "types": {"type": "array", "items": {"type": "string"}},
                    "variables": {"type": "object"},
                    "arguments": {"type": "object"},
                    "fragments": {"type": "object"},
                    "fragmentCount": {"type": "integer", "minimum": 0},
                    "directives": {"type": "array"}
                  }
                },
                "errors": {"type": "array", "items": {"type": "string"}}
              }
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
     *         results
     */
    @io.sapl.api.functions.Function(docs = """
            ```
            graphql.validateQuery(TEXT query, TEXT schema) -> OBJECT
            ```

            Parses and validates a GraphQL query against a schema.

            Returns comprehensive security analysis including validation, field extraction,
            complexity metrics, and potential security concerns.

            **Example:**
            ```sapl
            var gql = graphql.validateQuery(resource.query, resource."schema");
            gql.valid && gql.depth <= 5 && !("ssn" in gql.fields);
            ```
            """, schema = RETURNS_PARSED_QUERY)
    public static Val validateQuery(@Text Val query, @Text Val schema) {
        try {
            val document      = new Parser().parseDocument(query.getText());
            val graphQLSchema = parseSchemaWithCache(schema.getText());

            val result = Val.JSON.objectNode();

            val validationErrors = new Validator().validateDocument(graphQLSchema, document, Locale.ENGLISH);
            val isValid          = validationErrors.isEmpty();

            result.put("valid", isValid);

            if (!isValid) {
                val errors = Val.JSON.arrayNode();
                validationErrors.forEach(error -> errors.add(error.getMessage()));
                result.set("errors", errors);
                addEmptyDefaults(result);
                return Val.of(result);
            }

            val operation = extractOperation(document);
            val metrics   = analyzeQueryInSinglePass(document, operation);
            metrics.populateResult(result);

            return Val.of(result);

        } catch (SchemaProblem exception) {
            return handleParseException(exception, "Schema parsing failed");
        } catch (InvalidSyntaxException | IllegalArgumentException exception) {
            return handleParseException(exception, "Failed to parse GraphQL query");
        }
    }

    /**
     * Parses a GraphQL query without schema validation.
     * <p/>
     * Returns the same comprehensive metrics as validateQuery() except for
     * validation results. Use this when you need query analysis but don't have
     * the schema available or want to analyze structure without enforcing schema
     * conformance.
     *
     * @param query the GraphQL query string to parse and analyze
     * @return Val containing parsed query object with all metrics
     */
    @io.sapl.api.functions.Function(docs = """
            ```
            graphql.analyzeQuery(TEXT query) -> OBJECT
            ```

            Parses a GraphQL query without schema validation. Only validates syntax.

            **Example:**
            ```sapl
            var gql = graphql.analyzeQuery(resource.query);
            gql.depth <= 5 && gql.security.aliasCount <= 10;
            ```
            """, schema = RETURNS_PARSED_QUERY)
    public static Val analyzeQuery(@Text Val query) {
        try {
            val document  = new Parser().parseDocument(query.getText());
            val result    = Val.JSON.objectNode();
            val operation = extractOperation(document);

            result.put("valid", true);

            val metrics = analyzeQueryInSinglePass(document, operation);
            metrics.populateResult(result);

            return Val.of(result);

        } catch (InvalidSyntaxException | IllegalArgumentException exception) {
            return handleParseException(exception, "Failed to parse GraphQL query");
        }
    }

    /**
     * Calculates weighted complexity for a parsed query.
     * <p/>
     * Applies custom weights to fields based on their expected resource cost.
     * Each field can be assigned a custom weight. Fields not specified in the
     * weights object receive a default weight of 1.
     *
     * @param parsed the parsed query object from validateQuery() or analyzeQuery()
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
            var gql = graphql.validateQuery(resource.query, resource."schema");
            var weights = {"posts": 5, "comments": 3};
            graphql.complexity(gql, weights) <= 200;
            ```
            """, schema = """
            {"type": "integer", "minimum": 0}
            """)
    public static Val complexity(@JsonObject Val parsed, @JsonObject Val fieldWeights) {
        val fieldsNode = parsed.getJsonNode().get("fields");
        val depthNode  = parsed.getJsonNode().get("depth");

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

        val depth           = depthNode != null && depthNode.isNumber() ? depthNode.asInt() : 1;
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

            **Example:**
            ```sapl
            var schemaResult = graphql.parseSchema(resource."schema");
            schemaResult.valid;
            ```
            """, schema = """
            {
              "type": "object",
              "properties": {
                "valid": {"type": "boolean"},
                "ast": {"type": "object"},
                "errors": {"type": "array", "items": {"type": "string"}}
              }
            }
            """)
    public static Val parseSchema(@Text Val schema) {
        try {
            val schemaParser           = new SchemaParser();
            val typeDefinitionRegistry = schemaParser.parse(schema.getText());

            val result = Val.JSON.objectNode();
            result.put("valid", true);
            result.set("ast", buildSchemaAst(typeDefinitionRegistry));

            return Val.of(result);

        } catch (SchemaProblem | IllegalArgumentException exception) {
            val result = Val.JSON.objectNode();
            result.put("valid", false);
            result.set("ast", Val.JSON.objectNode());

            val errors = Val.JSON.arrayNode();
            errors.add(exception.getMessage());
            result.set("errors", errors);

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
        val astNode    = Val.JSON.objectNode();
        val typesArray = Val.JSON.arrayNode();

        // Add regular types
        typeDefinitionRegistry.types().values().forEach(typeDef -> {
            val typeNode = Val.JSON.objectNode();
            typeNode.put("kind", typeDef.getClass().getSimpleName());
            typeNode.put("name", typeDef.getName());
            typesArray.add(typeNode);
        });

        // Add scalar types
        typeDefinitionRegistry.scalars().values().forEach(scalarDef -> {
            val typeNode = Val.JSON.objectNode();
            typeNode.put("kind", scalarDef.getClass().getSimpleName());
            typeNode.put("name", scalarDef.getName());
            typesArray.add(typeNode);
        });

        astNode.set("types", typesArray);

        val directivesArray = Val.JSON.arrayNode();
        typeDefinitionRegistry.getDirectiveDefinitions().values().forEach(directiveDef -> {
            val directiveNode = Val.JSON.objectNode();
            directiveNode.put("name", directiveDef.getName());
            directivesArray.add(directiveNode);
        });
        astNode.set("directives", directivesArray);

        return astNode;
    }

    /**
     * Extracts the operation definition from a parsed document.
     *
     * @param document the parsed GraphQL document
     * @return the operation definition
     * @throws IllegalArgumentException if no operation definition is found
     */
    private static OperationDefinition extractOperation(Document document) {
        return (OperationDefinition) document.getDefinitions().stream()
                .filter(OperationDefinition.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No operation definition found."));
    }

    /**
     * Analyzes a GraphQL query in a single pass, collecting all metrics at once.
     *
     * @param document the parsed document
     * @param operation the operation definition
     * @return QueryMetrics containing all collected metrics
     */
    private static QueryMetrics analyzeQueryInSinglePass(Document document, OperationDefinition operation) {
        val metrics = new QueryMetrics();

        metrics.operation = switch (operation.getOperation()) {
            case QUERY -> "query";
            case MUTATION -> "mutation";
            case SUBSCRIPTION -> "subscription";
        };
        metrics.operationName = Objects.requireNonNullElse(operation.getName(), "");
        metrics.variables     = extractVariablesFromOperation(operation);

        analyzeSelectionSet(operation.getSelectionSet(), 0, metrics, true);
        processFragments(document, metrics);

        return metrics;
    }

    /**
     * Recursively analyzes a selection set, collecting all metrics.
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
                case FragmentSpread spread   -> processDirectivesContainer(spread, metrics);
                default                      -> {
                }
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
        metrics.addField(field.getName(), isRoot, field.getAlias() != null);
        processFieldArguments(field, metrics);
        processDirectivesContainer(field, metrics);
        analyzeSelectionSet(field.getSelectionSet(), currentDepth, metrics, false);
    }

    /**
     * Processes arguments from a field, extracting pagination limits and storing
     * argument values.
     *
     * @param field the field to process
     * @param metrics the metrics accumulator
     */
    private static void processFieldArguments(Field field, QueryMetrics metrics) {
        if (field.getArguments() == null || field.getArguments().isEmpty()) {
            return;
        }

        val fieldArgs = Val.JSON.objectNode();

        for (Argument argument : field.getArguments()) {
            val argName  = argument.getName();
            val argValue = argument.getValue();

            fieldArgs.set(argName, convertValueToJson(argValue));

            if (PAGINATION_ARGS_LOWER.contains(argName.toLowerCase()) && argValue instanceof IntValue intValue) {
                metrics.updateMaxPaginationLimit(intValue.getValue().intValue());
            }
        }

        metrics.arguments.set(field.getName(), fieldArgs);
    }

    /**
     * Processes directives from any directive container (field, inline fragment,
     * fragment spread).
     *
     * @param container the container with directives
     * @param metrics the metrics accumulator
     */
    private static void processDirectivesContainer(DirectivesContainer<?> container, QueryMetrics metrics) {
        if (container.getDirectives() == null || container.getDirectives().isEmpty()) {
            return;
        }

        val directives = container.getDirectives();
        metrics.directiveCount += directives.size();

        for (Directive directive : directives) {
            val directiveNode = Val.JSON.objectNode();
            directiveNode.put("name", directive.getName());

            val argsNode = Val.JSON.objectNode();
            if (directive.getArguments() != null) {
                directive.getArguments()
                        .forEach(argument -> argsNode.set(argument.getName(), convertValueToJson(argument.getValue())));
            }
            directiveNode.set("arguments", argsNode);

            metrics.directivesList.add(directiveNode);
        }
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
        if (fragment.getTypeCondition() != null) {
            metrics.types.add(fragment.getTypeCondition().getName());
        }

        processDirectivesContainer(fragment, metrics);
        analyzeSelectionSet(fragment.getSelectionSet(), currentDepth, metrics, false);
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

                val fragmentInfo = Val.JSON.objectNode();
                fragmentInfo.put("typeName", fragment.getTypeCondition().getName());

                val fragmentFields = new ArrayList<String>();
                extractFieldsFromSelectionSet(fragment.getSelectionSet(), fragmentFields);
                fragmentInfo.set("fields", arrayFrom(fragmentFields));

                metrics.fragments.set(fragment.getName(), fragmentInfo);
                metrics.types.add(fragment.getTypeCondition().getName());
            }
        }

        metrics.fragmentCount        = fragmentDefinitions.size();
        metrics.hasCircularFragments = detectCircularFragments(fragmentDefinitions);
    }

    /**
     * Extracts field names from a selection set.
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
                default                      -> {
                }
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
            if (hasCircularReference(entry.getKey(), entry.getValue(), fragments, new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks for circular references in a fragment using depth-first search.
     * <p/>
     * Uses backtracking DFS with a visited set to detect cycles. The algorithm:
     * 1. Marks current fragment as visited
     * 2. Recursively checks all fragment spreads
     * 3. Backtracks by removing from visited set
     * <p/>
     * If a fragment is encountered while already in the visited set, a cycle exists.
     *
     * @param fragmentName the fragment name being checked
     * @param fragment the fragment definition
     * @param allFragments map of all fragments
     * @param visited set of already visited fragment names in current path
     * @return true if circular reference found
     */
    private static boolean hasCircularReference(String fragmentName, FragmentDefinition fragment,
                                                Map<String, FragmentDefinition> allFragments, Set<String> visited) {
        // Cycle detected: fragment references itself through a chain
        if (visited.contains(fragmentName)) {
            return true;
        }

        // Mark as visited for this path
        visited.add(fragmentName);

        // Find all fragments this fragment references
        val referencedFragments = findFragmentSpreads(fragment.getSelectionSet());
        for (String refName : referencedFragments) {
            // Recursively check each referenced fragment for cycles
            if (allFragments.containsKey(refName)
                    && hasCircularReference(refName, allFragments.get(refName), allFragments, visited)) {
                return true;
            }
        }

        // Backtrack: remove from visited to allow other paths to use this fragment
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
                default                      -> {
                }
            }
        }

        return spreads;
    }

    /**
     * Converts a GraphQL AST Value to a proper JSON node.
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
     * Extracts variable definitions from an operation. Only includes variables
     * that have default values specified.
     *
     * @param operation the operation definition
     * @return object containing variable definitions with their default values
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
     * Adds empty default values for properties when query is invalid.
     *
     * @param result the result object to populate with defaults
     */
    private static void addEmptyDefaults(ObjectNode result) {
        result.put("operation", "query");
        result.put("depth", 0);
        result.put("fieldCount", 0);
        result.put("complexity", 0);

        result.set("fields", Val.JSON.arrayNode());

        val security = Val.JSON.objectNode();
        security.put("aliasCount", 0);
        security.put("rootFieldCount", 0);
        security.put("batchingScore", 0);
        security.put("maxPaginationLimit", 0);
        security.put("hasCircularFragments", false);
        security.put("isIntrospection", false);
        security.put("directiveCount", 0);
        security.put("directivesPerField", 0.0);
        result.set("security", security);

        val ast = Val.JSON.objectNode();
        ast.put("operationName", "");
        ast.set("types", Val.JSON.arrayNode());
        ast.set("directives", Val.JSON.arrayNode());
        ast.set("variables", Val.JSON.objectNode());
        ast.set("fragments", Val.JSON.objectNode());
        ast.put("fragmentCount", 0);
        ast.set("arguments", Val.JSON.objectNode());
        result.set("ast", ast);
    }

    /**
     * Creates an error result object with default values.
     *
     * @param errorMessage the error message to include
     * @return Val containing error result
     */
    private static Val createErrorResult(String errorMessage) {
        val result = Val.JSON.objectNode();
        result.put("valid", false);
        val errors = Val.JSON.arrayNode();
        errors.add(errorMessage);
        result.set("errors", errors);
        addEmptyDefaults(result);
        return Val.of(result);
    }

    /**
     * Handles parsing and validation exceptions with consistent error messages.
     *
     * @param exception the exception that occurred
     * @param context the context describing what operation failed
     * @return Val containing error result
     */
    private static Val handleParseException(Exception exception, String context) {
        return createErrorResult(context + ": " + exception.getMessage());
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
     * Converts a list of strings to a JSON array.
     *
     * @param items list of strings
     * @return JSON array node
     */
    private static ArrayNode arrayFrom(List<String> items) {
        val array = Val.JSON.arrayNode();
        items.forEach(array::add);
        return array;
    }

    /**
     * Mutable metrics accumulator for collecting query analysis data during a
     * single-pass traversal. Provides methods to update metrics and populate the
     * final result object.
     */
    private static class QueryMetrics {
        String           operation          = "query";
        String           operationName      = "";
        List<String>     fields             = new ArrayList<>();
        int              fieldCount         = 0;
        int              depth              = 0;
        ObjectNode       variables          = Val.JSON.objectNode();
        Set<String>      types              = new HashSet<>();
        List<ObjectNode> directivesList     = new ArrayList<>();
        ObjectNode       fragments          = Val.JSON.objectNode();
        int              aliasCount         = 0;
        int              rootFieldCount     = 0;
        int              maxPaginationLimit = 0;
        ObjectNode       arguments          = Val.JSON.objectNode();
        int              fragmentCount      = 0;
        boolean          hasCircularFragments = false;
        int              directiveCount     = 0;

        /**
         * Adds a field to the metrics collection.
         *
         * @param fieldName the name of the field
         * @param isRoot whether this is a root-level field
         * @param hasAlias whether this field has an alias
         */
        void addField(String fieldName, boolean isRoot, boolean hasAlias) {
            fields.add(fieldName);
            fieldCount++;

            if (isRoot) {
                rootFieldCount++;
            }

            if (hasAlias) {
                aliasCount++;
            }
        }

        /**
         * Updates the maximum pagination limit encountered.
         *
         * @param limit the pagination limit value
         */
        void updateMaxPaginationLimit(int limit) {
            maxPaginationLimit = Math.max(maxPaginationLimit, limit);
        }

        /**
         * Populates the result object with all collected metrics in a hybrid
         * flat/grouped structure. Common fields remain flat for policy ergonomics,
         * while detailed metrics are grouped by category.
         *
         * @param result the result object to populate
         */
        void populateResult(ObjectNode result) {
            populateCommonFields(result);
            populateSecurityMetrics(result);
            populateAstDetails(result);
        }

        /**
         * Populates common fields that are frequently accessed in policies.
         *
         * @param result the result object to populate
         */
        private void populateCommonFields(ObjectNode result) {
            result.put("operation", operation);
            result.set("fields", arrayFrom(fields));
            result.put("fieldCount", fieldCount);
            result.put("depth", depth);
            result.put("complexity", fieldCount + (depth * DEPTH_COMPLEXITY_FACTOR));
        }

        /**
         * Populates security-related metrics in a grouped structure.
         *
         * @param result the result object to populate
         */
        private void populateSecurityMetrics(ObjectNode result) {
            val security = Val.JSON.objectNode();

            security.put("aliasCount", aliasCount);
            security.put("rootFieldCount", rootFieldCount);
            security.put("batchingScore", aliasCount * BATCHING_SCORE_MULTIPLIER + rootFieldCount);
            security.put("maxPaginationLimit", maxPaginationLimit);
            security.put("hasCircularFragments", hasCircularFragments);
            security.put("isIntrospection", fields.stream().anyMatch(field -> field.startsWith(INTROSPECTION_PREFIX)));
            security.put("directiveCount", directiveCount);
            security.put("directivesPerField", fieldCount > 0 ? (double) directiveCount / fieldCount : 0.0);

            result.set("security", security);
        }

        /**
         * Populates AST details in a grouped structure.
         *
         * @param result the result object to populate
         */
        private void populateAstDetails(ObjectNode result) {
            val ast = Val.JSON.objectNode();

            ast.put("operationName", operationName);
            ast.set("types", arrayFrom(new ArrayList<>(types)));
            ast.set("variables", variables);
            ast.set("arguments", arguments);
            ast.set("fragments", fragments);
            ast.put("fragmentCount", fragmentCount);

            val directivesArray = Val.JSON.arrayNode();
            directivesList.forEach(directivesArray::add);
            ast.set("directives", directivesArray);

            result.set("ast", ast);
        }
    }
}