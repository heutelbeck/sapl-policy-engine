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
package io.sapl.grammar.ide.contentassist;

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
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.context.AuthorizationContext;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SchemaProposalGenerator {

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

    public static Collection<String> getCodeTemplates(String prefix, Expression expression,
            Map<String, Val> variables) {
        if (expression == null)
            return List.of();
        return expression.evaluate().contextWrite(ctx -> AuthorizationContext.setVariables(ctx, variables))
                .map(schema -> SchemaProposalGenerator.getCodeTemplates(prefix, schema, variables)).blockFirst();
    }

    public static List<String> getCodeTemplates(String prefix, Val schema, Map<String, Val> variables) {
        if (!schema.isDefined())
            return List.of();

        return getCodeTemplates(prefix, schema.get(), variables);
    }

    public static List<String> getCodeTemplates(String prefix, JsonNode schema, Map<String, Val> variables) {
        var proposals = new ArrayList<String>();
        if (schema == null)
            return proposals;

        var definitions = new HashMap<String, JsonNode>();
        // lookup of URI vie remote web request is not supported.
        // We assume all schemas are either embedded in the schema or are stored in the
        // variables, where they are identified by their respective $id field
        // schemas with no $id will be ignored in variables
        loadSchema(schema, definitions);
        loadSchemasFromVariables(variables, definitions);
        addProposals(schema, prefix, schema, definitions, proposals, 0);
        return proposals;
    }

    private static void loadSchemasFromVariables(Map<String, Val> variables, Map<String, JsonNode> definitions) {
        var schemaArray = variables.getOrDefault("SCHEMAS", Val.ofEmptyArray());
        if (!schemaArray.isArray()) {
            return;
        }
        for (var variable : schemaArray.getArrayNode()) {
            loadSchema(variable, definitions);
        }
    }

    private static void loadSchema(JsonNode schema, Map<String, JsonNode> definitions) {
        definitions.put(idIfPresentOrDefaultId(schema), schema);
    }

    private JsonNode lookupReferencedSchema(JsonNode baseSchema, JsonNode referenceNode,
            Map<String, JsonNode> definitions) {
        if (!referenceNode.isTextual())
            return null;
        var reference = referenceNode.asText();
        try {
            var ref = URI.create(reference);
            if (ref.isAbsolute()) {
                var schema = definitions.get(withoutFragment(ref).toString());
                if (schema == null)
                    return null;
                var fragment = ref.getFragment();
                if (fragment == null)
                    return schema;
                return lookupFragmentReference(schema, fragment);
            }
            return lookupFragmentReference(baseSchema, reference);
        } catch (URISyntaxException | IllegalArgumentException e) {
            // cannot resolve schema => no proposals will be made.
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
        var path             = fragment.split("/");
        var identifiedSchema = schema;
        for (var step : path) {
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

    private String idIfPresentOrDefaultId(JsonNode node) {
        if (!node.has(ID))
            return DEFAULT_ID;

        var id = node.get(ID);
        if (!id.isTextual())
            return DEFAULT_ID;

        var idValue = id.asText();
        if (idValue.isBlank())
            return DEFAULT_ID;
        return idValue;
    }

    private static void addProposals(JsonNode baseSchema, String prefix, JsonNode schema,
            Map<String, JsonNode> definitions, Collection<String> proposals, int recursionDepth) {
        if (recursionDepth == MAX_DEPTH || schema == null || !schema.isObject())
            return;

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
            // sometimes the "type" is omitted in schemata. only if "type" explicitly is set
            // to "array" we can conclude to add [] as a proposal, as it is not necessary to
            // declare the type of the contained items in an array
            var typeNode = schema.get(TYPE);
            if (typeNode.isTextual() && ARRAY.equals(typeNode.asText()))
                proposals.add(arrayPrefix);
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
            addProposals(baseSchema, newPath, field.getValue(), definitions, proposals, recursionDepth + 1);
        }
    }

    private static void addArrayProposals(JsonNode baseSchema, String prefix, JsonNode schema,
            Map<String, JsonNode> definitions, Collection<String> proposals, int recursionDepth) {
        if (hasArrayFieldNamed(schema, PREFIX_ITEMS)) {
            addMultipleProposals(baseSchema, prefix, schema.get(PREFIX_ITEMS).elements(), definitions, proposals,
                    recursionDepth);
        }
        if (!schema.has(ITEMS))
            return;
        var items = schema.get(ITEMS);
        if (!items.isObject())
            return;
        addProposals(baseSchema, prefix, items, definitions, proposals, recursionDepth + 1);
    }

    private static String escaped(String s) {
        // @formatter:off
        var escaped = s.replace("\\", "\\\\")
                       .replace("\t", "\\t")
                       .replace("\b", "\\b")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\f", "\\f")
                       .replace("\"", "\\\"")
                       .replace("'",  "\\'");
        // @formatter:on
        if (escaped.contains(" ")) {
            return "'" + escaped + "'";
        }
        return s;
    }

}
