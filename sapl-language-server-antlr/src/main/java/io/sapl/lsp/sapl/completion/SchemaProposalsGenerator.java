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
package io.sapl.lsp.sapl.completion;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.lsp.configuration.LSPConfiguration;
import io.sapl.lsp.sapl.evaluation.ExpressionEvaluator;
import lombok.experimental.UtilityClass;

/**
 * Generates code completion proposals from JSON Schema definitions.
 * Supports JSON Schema features including $ref, allOf/anyOf/oneOf,
 * properties, items, prefixItems, and external schema references.
 */
@UtilityClass
public class SchemaProposalsGenerator {

    private static final String DEFAULT_ID   = "https://sapl.io/schemas";
    private static final String ANCHOR       = "$anchor";
    private static final String ARRAY        = "array";
    private static final String ID           = "$id";
    private static final String ITEMS        = "items";
    private static final String PREFIX_ITEMS = "prefixItems";
    private static final String PROPERTIES   = "properties";
    private static final String REF          = "$ref";
    private static final String TYPE         = "type";

    private static final int MAX_DEPTH = 10;

    private static final Collection<String> KEYWORDS_INDICATING_TYPE_ARRAY = Set.of("allOf", "anyOf", "oneOf", TYPE);

    /**
     * Generates code templates from an ANTLR expression parse tree.
     * Evaluates the expression to obtain a schema and generates property paths.
     *
     * @param prefix the prefix to prepend to generated paths
     * @param expression the ANTLR expression context representing a schema
     * @param config the LSP configuration containing brokers and variables
     * @return list of property path proposals
     */
    public static List<String> getCodeTemplates(String prefix, ExpressionContext expression, LSPConfiguration config) {
        if (expression == null) {
            return List.of();
        }
        var maybeSchema = ExpressionEvaluator.evaluateExpressionToJsonNode(expression, config);
        return maybeSchema.map(jsonNode -> getCodeTemplates(prefix, jsonNode, config.variables())).orElseGet(List::of);
    }

    /**
     * Generates code templates from a Value schema.
     *
     * @param prefix the prefix to prepend to generated paths
     * @param schema the schema as a Value
     * @param variables environment variables containing SCHEMAS array
     * @return list of property path proposals
     */
    public static List<String> getCodeTemplates(String prefix, Value schema, Map<String, Value> variables) {
        try {
            return getCodeTemplates(prefix, ValueJsonMarshaller.toJsonNode(schema), variables);
        } catch (IllegalArgumentException e) {
            return List.of();
        }
    }

    /**
     * Generates code templates from a JSON Schema.
     *
     * @param prefix the prefix to prepend to generated paths
     * @param schema the JSON Schema
     * @param variables environment variables containing SCHEMAS array for external
     * references
     * @return list of property path proposals (e.g., ".name", ".address.street",
     * "[]")
     */
    public static List<String> getCodeTemplates(String prefix, JsonNode schema, Map<String, Value> variables) {
        var proposals = new ArrayList<String>();
        if (schema == null) {
            return proposals;
        }
        var definitions = new HashMap<String, JsonNode>();
        loadSchema(schema, definitions);
        loadSchemasFromVariables(variables, definitions);
        addProposals(schema, prefix, schema, definitions, proposals, 0);
        return proposals;
    }

    private static void loadSchemasFromVariables(Map<String, Value> variables, Map<String, JsonNode> definitions) {
        var schemaArray = variables.getOrDefault("SCHEMAS", Value.EMPTY_ARRAY);
        if (!(schemaArray instanceof ArrayValue actualArray)) {
            return;
        }
        for (var variable : actualArray) {
            try {
                loadSchema(ValueJsonMarshaller.toJsonNode(variable), definitions);
            } catch (IllegalArgumentException e) {
                // silently ignore errors
            }
        }
    }

    private static void loadSchema(JsonNode schema, Map<String, JsonNode> definitions) {
        definitions.put(idIfPresentOrDefaultId(schema), schema);
    }

    private JsonNode lookupReferencedSchema(JsonNode baseSchema, JsonNode referenceNode,
            Map<String, JsonNode> definitions) {
        if (!referenceNode.isTextual()) {
            return null;
        }
        var reference = referenceNode.asText();
        try {
            var ref = URI.create(reference);
            if (ref.isAbsolute()) {
                var schema = definitions.get(withoutFragment(ref).toString());
                if (schema == null) {
                    return null;
                }
                var fragment = ref.getFragment();
                if (fragment == null) {
                    return schema;
                }
                return lookupFragmentReference(schema, fragment);
            }
            return lookupFragmentReference(baseSchema, reference);
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    private JsonNode lookupFragmentReference(JsonNode schema, String fragment) {
        var localFragment = fragment;
        if (localFragment.startsWith("#")) {
            localFragment = localFragment.substring(1);
        }

        if (localFragment.startsWith("/")) {
            return lookupJsonPointerReference(schema, localFragment);
        }

        if (localFragment.isBlank()) {
            return schema;
        }

        return lookupAnchorReference(schema, localFragment);
    }

    private JsonNode lookupAnchorReference(JsonNode schema, String anchor) {
        if (schema instanceof ObjectNode objectNode) {
            return lookupAnchorReferenceInObject(objectNode, anchor);
        }

        if (schema instanceof ArrayNode arrayNode) {
            return lookupAnchorReferenceInArray(arrayNode, anchor);
        }

        return null;
    }

    private JsonNode lookupAnchorReferenceInArray(ArrayNode arrayNode, String anchor) {
        var elementsIterator = arrayNode.elements();
        while (elementsIterator.hasNext()) {
            var schema = lookupAnchorReference(elementsIterator.next(), anchor);
            if (schema != null) {
                return schema;
            }
        }
        return null;
    }

    private JsonNode lookupAnchorReferenceInObject(ObjectNode objectNode, String anchor) {
        var anchorField = objectNode.get(ANCHOR);
        if (anchorField != null && anchorField.asText().equals(anchor)) {
            return objectNode;
        }
        for (var entry : objectNode.properties()) {
            var schema = lookupAnchorReference(entry.getValue(), anchor);
            if (schema != null) {
                return schema;
            }
        }
        return null;
    }

    private JsonNode lookupJsonPointerReference(JsonNode schema, String fragment) {
        var path             = fragment.split("/");
        var identifiedSchema = schema;
        for (var step : path) {
            if (!step.isBlank()) {
                if (!identifiedSchema.has(step)) {
                    return null;
                }
                identifiedSchema = identifiedSchema.get(step);
            }
        }
        return identifiedSchema;
    }

    private URI withoutFragment(URI uri) throws URISyntaxException {
        return new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null);
    }

    private String idIfPresentOrDefaultId(JsonNode node) {
        if (!node.has(ID)) {
            return DEFAULT_ID;
        }

        var id = node.get(ID);
        if (!id.isTextual()) {
            return DEFAULT_ID;
        }

        var idValue = id.asText();
        if (idValue.isBlank()) {
            return DEFAULT_ID;
        }
        return idValue;
    }

    private static void addProposals(JsonNode baseSchema, String prefix, JsonNode schema,
            Map<String, JsonNode> definitions, Collection<String> proposals, int recursionDepth) {
        if (recursionDepth == MAX_DEPTH || schema == null || !schema.isObject()) {
            return;
        }

        if (schema.has(REF)) {
            addProposals(baseSchema, prefix, lookupReferencedSchema(baseSchema, schema.get(REF), definitions),
                    definitions, proposals, recursionDepth);
            return;
        }

        for (var multiTypeKeyword : KEYWORDS_INDICATING_TYPE_ARRAY) {
            if (hasArrayFieldNamed(schema, multiTypeKeyword)) {
                addMultipleProposals(baseSchema, prefix, schema.get(multiTypeKeyword).elements(), definitions,
                        proposals, recursionDepth);
                return;
            }
        }

        addObjectProposals(baseSchema, prefix, schema, definitions, proposals, recursionDepth);

        var arrayPrefix = prefix + "[]";
        if (schema.has(TYPE)) {
            var typeNode = schema.get(TYPE);
            if (typeNode.isTextual() && ARRAY.equals(typeNode.asText())) {
                proposals.add(arrayPrefix);
            }
        }

        addArrayProposals(baseSchema, arrayPrefix, schema, definitions, proposals, recursionDepth);
    }

    private static void addMultipleProposals(JsonNode baseSchema, String prefix, Iterator<JsonNode> elementsIterator,
            Map<String, JsonNode> definitions, Collection<String> proposals, int recursionDepth) {
        while (elementsIterator.hasNext()) {
            addProposals(baseSchema, prefix, elementsIterator.next(), definitions, proposals, recursionDepth);
        }
    }

    private static boolean hasArrayFieldNamed(JsonNode node, String fieldName) {
        return node.has(fieldName) && node.get(fieldName).isArray();
    }

    private static void addObjectProposals(JsonNode baseSchema, String prefix, JsonNode schema,
            Map<String, JsonNode> definitions, Collection<String> proposals, int recursionDepth) {
        if (!schema.has(PROPERTIES)) {
            return;
        }
        var properties = schema.get(PROPERTIES);
        if (!properties.isObject()) {
            return;
        }

        for (var field : properties.properties()) {
            var newPath = prefix + '.' + escaped(field.getKey());
            proposals.add(newPath);
            addProposals(baseSchema, newPath, field.getValue(), definitions, proposals, recursionDepth + 1);
        }
    }

    private static void addArrayProposals(JsonNode baseSchema, String prefix, JsonNode schema,
            Map<String, JsonNode> definitions, Collection<String> proposals, int recursionDepth) {
        if (hasArrayFieldNamed(schema, PREFIX_ITEMS)) {
            addMultipleProposals(baseSchema, prefix, schema.get(PREFIX_ITEMS).elements(), definitions, proposals,
                    recursionDepth);
        }
        if (!schema.has(ITEMS)) {
            return;
        }
        var items = schema.get(ITEMS);
        if (!items.isObject()) {
            return;
        }
        addProposals(baseSchema, prefix, items, definitions, proposals, recursionDepth + 1);
    }

    private static String escaped(String text) {
        var escaped = text.replace("\\", "\\\\").replace("\t", "\\t").replace("\b", "\\b").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\f", "\\f").replace("\"", "\\\"").replace("'", "\\'");
        if (escaped.contains(" ")) {
            return "'" + escaped + "'";
        }
        return text;
    }

}
