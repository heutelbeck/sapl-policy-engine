/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.ide.contentassist.schema;

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

import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class SchemaParser {

    private static final String ANCHOR     = "$anchor";
    private static final String ARRAY      = "array";
    private static final String ID         = "$id";
    private static final String OBJECT     = "object";
    private static final String PROPERTIES = "properties";
    private static final String REF        = "$ref";
    private static final String TYPE       = "type";

    private static final int MAX_DEPTH = 10;

    private static final Collection<String> KEYWORDS_INDICATING_TYPE_ARRAY = Set.of("allOf", "anyOf", "oneOf", TYPE);

    public static List<String> generateProposals(Val schema, Map<String, JsonNode> variables) {
        if (!schema.isDefined())
            return List.of();

        return generateProposals("", schema.get(), variables);
    }

    public static List<String> generateProposals(String prefix, JsonNode schema, Map<String, JsonNode> variables) {
        var proposals   = new ArrayList<String>();
        var definitions = new HashMap<String, JsonNode>();
        var id          = idIfPresent(schema);
        // lookup of URI vie remote web request is not supported.
        // We assume all schemas are either embedded in the schema or are stored in the
        // variables, where they are identified by their respective $id field
        // schemas with no $id will be ignored in variables
        loadSchema(schema, definitions);
        loadSchemasFromVariables(variables, definitions);
        addProposals(id, schema, prefix, schema, definitions, proposals, 0);
        return proposals;
    }

    private static void loadSchemasFromVariables(Map<String, JsonNode> variables, Map<String, JsonNode> definitions) {
        for (var variable : variables.values()) {
            loadSchema(variable, definitions);
        }
    }

    private static void loadSchema(JsonNode schema, Map<String, JsonNode> definitions) {
        var id = idIfPresent(schema);
        if (!id.isBlank()) {
            definitions.put(id, schema);
        }
    }

    private JsonNode lookupReferencedSchema(String baseUri, JsonNode baseSchema, JsonNode referenceNode,
            Map<String, JsonNode> definitions) {
        if (!referenceNode.isTextual())
            return null;
        var reference = referenceNode.asText();
        log.error("reference:{}", reference);
        try {
            var base = URI.create(baseUri);
            var ref  = URI.create(reference);
            if (ref.isAbsolute()) {
                var schema = definitions.get(withoutFragment(ref).toString());
                if (schema == null)
                    return null;
                var fragment = ref.getFragment();
                if (fragment == null)
                    return schema;
                return lookupFragmentReference(schema, fragment);
            }
            log.error("non absoute reference");
            return lookupFragmentReference(baseSchema, reference);
        } catch (URISyntaxException | IllegalArgumentException e) {
            // cannot resolve schema - no proposals
            return null;
        }
    }

    private JsonNode lookupFragmentReference(JsonNode schema, String fragment) {
        if (fragment.startsWith("#"))
            fragment = fragment.substring(1);

        if (fragment.startsWith("/")) {
            return lookupJsonPointerReference(schema, fragment);
        }

        if (fragment.isBlank())
            return schema; // https://json-schema.org/understanding-json-schema/structuring#recursion

        return lookupAnchorReference(schema, fragment);
    }

    private JsonNode lookupAnchorReference(JsonNode schema, String anchor) {
        log.error("lookup anchor reference: '{}'", anchor);

        if (schema instanceof ObjectNode objectNode)
            return lookupAnchorReferenceInObject(objectNode, anchor);

        if (schema instanceof ArrayNode arrayNode)
            return lookupAnchorReferenceInArray(arrayNode, anchor);

        return null;
    }

    private JsonNode lookupAnchorReferenceInArray(ArrayNode arrayNode, String anchor) {
        var elementsIterator = arrayNode.elements();
        while (elementsIterator.hasNext()) {
            var schema = lookupAnchorReference(elementsIterator.next(), anchor);
            if (schema != null)
                return schema;
        }
        return null;
    }

    private JsonNode lookupAnchorReferenceInObject(ObjectNode objectNode, String anchor) {
        var anchorField = objectNode.get(ANCHOR);
        if (anchorField != null && anchorField.asText().equals(anchor))
            return objectNode;
        var fieldsIterator = objectNode.fields();
        while (fieldsIterator.hasNext()) {
            var schema = lookupAnchorReference(fieldsIterator.next().getValue(), anchor);
            if (schema != null)
                return schema;
        }
        return null;
    }

    private JsonNode lookupJsonPointerReference(JsonNode schema, String fragment) {
        log.error("lookup pointer reference: '{}'", fragment);
        var path = fragment.split("/");
        log.error("path:{}", List.of(path));
        var identifiedSchema = schema;
        for (var step : path) {
            log.error("inspected schema: {}", identifiedSchema);
            log.error("step:{}", step);
            if (!step.isBlank()) {
                if (!identifiedSchema.has(step))
                    return null;
                identifiedSchema = identifiedSchema.get(step);
            }
        }
        return identifiedSchema;
    }

    private URI withoutFragment(URI u) throws URISyntaxException {
        return new URI(u.getScheme(), u.getSchemeSpecificPart(), null);
    }

    private String idIfPresent(JsonNode node) {
        if (node == null || !node.has(ID))
            return "";

        var id = node.get(ID);
        if (!id.isTextual())
            return "";

        return id.asText();
    }

    private static void addProposals(String id, JsonNode baseSchema, String prefix, JsonNode schema,
            Map<String, JsonNode> definitions, Collection<String> proposals, int recursionDepth) {
        if (recursionDepth == MAX_DEPTH || schema == null || !schema.isObject())
            return;

        if (schema.has(REF)) {
            addProposals(id, baseSchema, prefix, lookupReferencedSchema(id, baseSchema, schema.get(REF), definitions),
                    definitions, proposals, recursionDepth);
            return;
        }

        for (var multiTypeKeyword : KEYWORDS_INDICATING_TYPE_ARRAY) {
            if (hasArrayFieldNamed(schema, multiTypeKeyword)) {
                addMultipleProposals(id, baseSchema, prefix, schema.get(multiTypeKeyword).elements(), definitions,
                        proposals, recursionDepth);
                return;
            }
        }

        if (!schema.has(TYPE))
            return;

        var typeNode = schema.get(TYPE);
        if (!typeNode.isTextual())
            return;

        var type = typeNode.asText();
        if (OBJECT.equals(type)) {
            addObjectProposals(id, baseSchema, prefix, schema, definitions, proposals, recursionDepth);
        }

        if (ARRAY.equals(type)) {
            addArrayProposals(id, baseSchema, prefix, schema, definitions, proposals, recursionDepth);
        }

    }

    private static void addMultipleProposals(String id, JsonNode baseSchema, String prefix,
            Iterator<JsonNode> elementsIterator, Map<String, JsonNode> definitions, Collection<String> proposals,
            int recursionDepth) {
        while (elementsIterator.hasNext()) {
            addProposals(id, baseSchema, prefix, elementsIterator.next(), definitions, proposals, recursionDepth);
        }
    }

    private static boolean hasArrayFieldNamed(JsonNode node, String fieldName) {
        return node.isObject() && node.has(fieldName) && node.get(fieldName).isArray();

    }

    private static void addObjectProposals(String id, JsonNode baseSchema, String prefix, JsonNode schema,
            Map<String, JsonNode> definitions, Collection<String> proposals, int recursionDepth) {
        if (!schema.has(PROPERTIES))
            return;
        var properties = schema.get(PROPERTIES);
        if (!properties.isObject())
            return;

        var fieldsIterator = properties.fields();
        while (fieldsIterator.hasNext()) {
            var field   = fieldsIterator.next();
            var newPath = prefix + '.' + escaped(field.getKey());
            proposals.add(newPath);
            addProposals(id, baseSchema, newPath, field.getValue(), definitions, proposals, recursionDepth + 1);
        }

    }

    private static void addArrayProposals(String id, JsonNode baseSchema, String prefix, JsonNode schema,
            Map<String, JsonNode> definitions, Collection<String> proposals, int recursionDepth) {
        var newPrefix = prefix + "[]";
        proposals.add(newPrefix);

    }

    private static String escaped(String s) {
        var escaped = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\b", "\\b").replace("\n", "\\n")
                .replace("\r", "\\r").replace("\f", "\\f").replace("\"", "\\\"").replace("'", "\'");
        if (escaped.contains(" ")) {
            return "'" + escaped + "'";
        }
        return s;
    }

}
