package io.sapl.grammar.ide.contentassist.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
public class SchemaParser {

    private final Map<String, JsonNode> variables;

    private static final int DEPTH = 20;

    private static final Collection<String> RESERVED_KEYWORDS = Set.of(
            "$schema", "$id",
            "additionalProperties", "allOf", "anyOf", "dependentRequired", "description", "enum", "format", "items",
            "oneOf", "properties", "required", "title", "type"
            );

    public List<String> generatePaths(String schema) {

        ObjectMapper mapper = new ObjectMapper();
        JsonNode schemaNode;
        try {
            schemaNode = mapper.readTree(schema);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not a valid schema:\n" + schema);
        }

        List<String> jsonPaths = getJsonPaths(schemaNode, "", schemaNode, 0);
        jsonPaths.removeIf(s -> s.startsWith("$defs"));
        jsonPaths.removeIf(RESERVED_KEYWORDS::contains);
        return jsonPaths;
    }

    private static JsonNode getReferencedNodeFromDifferentDocument(String ref, Map<String, JsonNode> variables) {
        JsonNode refNode = null;
        String schemaName;
        String internalRef = null;
        if (ref.contains("#/")){
            var refSplit = ref.split("#/", 2);
            schemaName = refSplit[0].replaceAll("\\.json$", "");
            internalRef = refSplit[1];
        } else {
            schemaName = ref.replaceAll("\\.json$", "");
        }
        if (variables != null && variables.containsKey(schemaName)){
            refNode = variables.get(schemaName);
            if (internalRef != null){
                refNode = refNode.get(internalRef);
            }
        }
        return refNode;
    }

    private static JsonNode getReferencedNodeFromSameDocument(JsonNode schema, String ref){
        ref = ref.replace("#/", "");
        return getNestedSubnode(schema, ref);
    }

    public static JsonNode getNestedSubnode(JsonNode rootNode, String path) {
        String[] pathElements = path.split("/");
        JsonNode currentNode = rootNode;

        for (String element : pathElements) {
            currentNode = currentNode.get(element);

            if (currentNode == null) {
                return null; // Path not found
            }
        }

        return currentNode;
    }

    private List<String> getJsonPaths(JsonNode jsonNode, String parentPath, final JsonNode originalSchema, int depth){

        Collection<String> paths = new HashSet<>();

        depth++;
        if (depth > DEPTH)
            return new ArrayList<>();

        if (jsonNode != null && jsonNode.isObject()) {
            paths.addAll(constructPathsFromObject(jsonNode, parentPath, originalSchema, depth));
        } else if (jsonNode != null && jsonNode.isArray()) {
            paths.addAll(constructPathsFromArray(jsonNode, parentPath, originalSchema, depth));
        } else {
            paths.add(parentPath);
        }

        return new ArrayList<>(paths);
    }

    private Collection<String> constructPathsFromArray(JsonNode jsonNode, String parentPath, JsonNode originalSchema, int depth) {
        Collection<String> paths = new HashSet<>();

        for (int i = 0; i < jsonNode.size(); i++) {
            JsonNode childNode = jsonNode.get(i);
            String currentPath = parentPath.isEmpty() ? "" : parentPath;
            paths.addAll(getJsonPaths(childNode, currentPath, originalSchema, depth));
        }
        return paths;
    }

    private Collection<String> constructPathsFromObject(JsonNode jsonNode, String parentPath, JsonNode originalSchema, int depth) {
        Collection<String> paths = new HashSet<>();

        JsonNode childNode;
        String currentPath;
        List<String> enumPaths = new ArrayList<>();
        Iterator<String> fieldNames = jsonNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            childNode = jsonNode.get(fieldName);

            if ("$ref".equals(fieldName)) {
                childNode = handleReferences(originalSchema, childNode);
                currentPath = parentPath;
            } else if ("enum".equals(fieldName)){
                currentPath = handleEnum(jsonNode, parentPath, enumPaths);
            } else if("patternProperties".equals(fieldName)) {
                childNode = JsonNodeFactory.instance.nullNode();
                currentPath = parentPath;
            } else if(RESERVED_KEYWORDS.contains(fieldName)) {
                currentPath = parentPath;
            } else {
                currentPath = parentPath.isEmpty() ? fieldName : parentPath + "." + fieldName;
            }
            paths.addAll(getJsonPaths(childNode, currentPath, originalSchema, depth));
            paths.addAll(enumPaths);
        }

        return paths;
    }

    private JsonNode handleReferences(JsonNode originalSchema, JsonNode childNode) {
        JsonNode refNode;
        if (childNode.textValue().startsWith("#"))
            refNode = getReferencedNodeFromSameDocument(originalSchema, childNode.textValue());
        else {
            refNode = getReferencedNodeFromDifferentDocument(childNode.textValue(), variables);
        }
        return refNode;
    }

    private static String handleEnum(JsonNode jsonNode, String parentPath, List<String> enumPaths) {
        String currentPath;
        JsonNode enumValuesNode = jsonNode.get("enum");
        ObjectMapper mapper = new ObjectMapper();
        String[] enumValuesArray;
        try {
            enumValuesArray = mapper.treeToValue(enumValuesNode, String[].class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Not a valid enum in schema!");
        }
        List<String> enumValues = new ArrayList<>(Arrays.asList(enumValuesArray));
        for (String enumPath : enumValues){
            enumPaths.add(parentPath + "." + enumPath);
        }
        currentPath = parentPath;
        return currentPath;
    }

}
