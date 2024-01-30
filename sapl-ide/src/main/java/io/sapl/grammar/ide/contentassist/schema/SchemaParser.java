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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SchemaParser {

    private static final int                MAX_DEPTH         = 20;
    private static final ObjectMapper       MAPPER            = new ObjectMapper();
    private static final Collection<String> RESERVED_KEYWORDS = Set.of("$schema", "$id", "additionalProperties",
            "allOf", "anyOf", "dependentRequired", "description", "enum", "format", "items", "oneOf", "properties",
            "required", "title", "type");

    public static List<String> generatePaths(JsonNode schema, Map<String, JsonNode> variables) {

        var jsonPaths = getJsonPaths(schema, "", schema, 0, variables);
        jsonPaths.removeIf(s -> s.startsWith("$defs"));
        jsonPaths.removeIf(String::isBlank);
        jsonPaths.removeIf(RESERVED_KEYWORDS::contains);
        jsonPaths.replaceAll(SchemaParser::encloseWithQuotationMarksIfContainsSpace);
        return jsonPaths;
    }

    private static boolean propertyIsArray(JsonNode jsonNode) {
        var typeNode = jsonNode.get("type");

        if (typeNode != null) {
            var type = typeNode.textValue();
            return "array".equals(type);
        } else
            return false;
    }

    private static JsonNode getReferencedNodeFromDifferentDocument(String ref, Map<String, JsonNode> variables) {
        JsonNode refNode;
        String   schemaName;
        String   internalRef = null;
        if (ref.contains("#/")) {
            var refSplit = ref.split("#/", 2);
            schemaName  = refSplit[0].replaceAll("\\.json$", "").replace("/", "");
            internalRef = "#/".concat(refSplit[1]);
        } else {
            schemaName = ref.replaceAll("\\.json$", "");
        }
        refNode = variables.get(schemaName);
        if (internalRef != null && refNode != null) {
            refNode = getReferencedNodeFromSameDocument(refNode, internalRef);
        }
        return refNode;
    }

    private static JsonNode getReferencedNodeFromSameDocument(JsonNode originalSchema, String ref) {
        if ("#".equals(ref))
            return originalSchema;
        ref = ref.replace("#/", "");
        return getNestedSubnode(originalSchema, ref);
    }

    private static JsonNode getNestedSubnode(JsonNode rootNode, String path) {
        var pathElements = path.split("/");
        var currentNode  = rootNode;

        for (String element : pathElements) {
            currentNode = currentNode.get(element);
        }

        return currentNode;
    }

    private static List<String> handleEnum(JsonNode jsonNode, String parentPath) {
        var      enumValuesNode = jsonNode.get("enum");
        String[] enumValuesArray;
        var      paths          = new LinkedList<String>();
        try {
            enumValuesArray = MAPPER.treeToValue(enumValuesNode, String[].class);
        } catch (JsonProcessingException e) {
            return new LinkedList<>();
        }
        List<String> enumValues = new ArrayList<>(Arrays.asList(enumValuesArray));
        for (String enumPath : enumValues) {
            paths.add(parentPath + "." + enumPath);
        }
        return paths;
    }

    private static List<String> getJsonPaths(JsonNode jsonNode, String parentPath, final JsonNode originalSchema,
            int depth, Map<String, JsonNode> variables) {

        Collection<String> paths = new HashSet<>();

        depth++;
        if (depth > MAX_DEPTH)
            return new ArrayList<>();

        if (jsonNode != null && jsonNode.isObject()) {
            if (propertyIsArray(jsonNode)) {
                paths = handleArray(jsonNode, parentPath, originalSchema, depth, variables);
            } else {
                paths.addAll(constructPathFromNonArrayProperty(jsonNode, parentPath, originalSchema, depth, variables));
            }
        } else if (jsonNode != null && jsonNode.isArray()) {
            paths.addAll(constructPathsFromArray(jsonNode, parentPath, originalSchema, depth, variables));
        } else {
            paths.add(parentPath);
        }

        return new ArrayList<>(paths);
    }

    private static Collection<String> handleArray(JsonNode jsonNode, String parentPath, JsonNode originalSchema,
            int depth, Map<String, JsonNode> variables) {
        Collection<String> paths = new HashSet<>();
        var                items = jsonNode.get("items");
        if (items != null) {
            if (items.isArray())
                paths.addAll(constructPathsFromArray(items, parentPath, originalSchema, depth, variables));
            else
                paths.addAll(constructPathFromNonArrayProperty(items, parentPath, originalSchema, depth, variables));
        } else
            paths.add(parentPath);
        return paths;
    }

    private static Collection<String> constructPathsFromArray(JsonNode jsonNode, String parentPath,
            JsonNode originalSchema, int depth, Map<String, JsonNode> variables) {
        Collection<String> paths = new HashSet<>();

        for (int i = 0; i < jsonNode.size(); i++) {
            var childNode   = jsonNode.get(i);
            var currentPath = parentPath.isEmpty() ? "" : parentPath;
            paths.addAll(getJsonPaths(childNode, currentPath, originalSchema, depth, variables));
        }
        return paths;
    }

    private static Collection<String> constructPathFromNonArrayProperty(JsonNode jsonNode, String parentPath,
            JsonNode originalSchema, int depth, Map<String, JsonNode> variables) {
        Collection<String> paths = new HashSet<>();

        JsonNode     childNode;
        String       currentPath;
        List<String> enumPaths = new LinkedList<>();

        Iterator<String> fieldNames = jsonNode.fieldNames();
        if (!fieldNames.hasNext())
            paths.add(parentPath);
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            childNode = jsonNode.get(fieldName);

            if ("$ref".equals(fieldName)) {
                childNode   = handleReferences(originalSchema, childNode, variables);
                currentPath = parentPath;
            } else if ("enum".equals(fieldName)) {
                enumPaths   = handleEnum(jsonNode, parentPath);
                currentPath = parentPath;
            } else if ("patternProperties".equals(fieldName)) {
                childNode   = JsonNodeFactory.instance.nullNode();
                currentPath = parentPath;
            } else if (RESERVED_KEYWORDS.contains(fieldName)) {
                currentPath = parentPath;
            } else {
                currentPath = parentPath.isEmpty() ? fieldName : parentPath + "." + fieldName;
            }
            paths.addAll(getJsonPaths(childNode, currentPath, originalSchema, depth, variables));
            paths.addAll(enumPaths);
        }

        return paths;
    }

    private static JsonNode handleReferences(JsonNode originalSchema, JsonNode childNode,
            Map<String, JsonNode> variables) {
        JsonNode refNode;
        if (childNode.textValue().startsWith("#"))
            refNode = getReferencedNodeFromSameDocument(originalSchema, childNode.textValue());
        else {
            refNode = getReferencedNodeFromDifferentDocument(childNode.textValue(), variables);
        }
        return refNode;
    }

    private static String encloseWithQuotationMarksIfContainsSpace(String s) {
        if (s.contains(" ")) {
            return "'" + s + "'";
        }
        return s;
    }

}
