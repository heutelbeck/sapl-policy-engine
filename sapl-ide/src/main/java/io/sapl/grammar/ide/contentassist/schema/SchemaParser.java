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
            return new LinkedList<>();
        }

        List<String> jsonPaths = getJsonPaths(schemaNode, "", schemaNode, 0);
        jsonPaths.removeIf(s -> s.startsWith("$defs"));
        jsonPaths.removeIf(RESERVED_KEYWORDS::contains);
        return jsonPaths;
    }


    private List<String> getJsonPaths(JsonNode jsonNode, String parentPath, final JsonNode originalSchema, int depth){

        Collection<String> paths = new HashSet<>();

        depth++;
        if (depth > DEPTH)
            return new ArrayList<>();

        if (jsonNode != null && jsonNode.isObject()) {
            if (propertyIsArray(jsonNode)){
                var items = jsonNode.get("items");
                if (items != null){
                    if(items.isArray())
                        paths.addAll(constructPathsFromArray(items, parentPath, originalSchema, depth));
                    else
                        paths.addAll(constructPathFromNonArrayProperty(items, parentPath, originalSchema, depth));
                } else
                    paths.add(parentPath);
            } else {
                paths.addAll(constructPathFromNonArrayProperty(jsonNode, parentPath, originalSchema, depth));

            }
        } else if (jsonNode != null && jsonNode.isArray()) {
            paths.addAll(constructPathsFromArray(jsonNode, parentPath, originalSchema, depth));
        } else {
            paths.add(parentPath);
        }

        return new ArrayList<>(paths);
    }

    private static boolean propertyIsArray(JsonNode jsonNode){
        JsonNode typeNode;
        typeNode = jsonNode.get("type");

        if (typeNode != null){
            var type = typeNode.textValue();
            return "array".equals(type);
        } else
            return false;
    }

    private static JsonNode getReferencedNodeFromDifferentDocument(String ref, Map<String, JsonNode> variables) {
        JsonNode refNode = null;
        String schemaName;
        String internalRef = null;
        if (ref.contains("#/")){
            var refSplit = ref.split("#/", 2);
            schemaName = refSplit[0].replaceAll("\\.json$", "").replace("/","");
            internalRef = "#/".concat(refSplit[1]);
        } else {
            schemaName = ref.replaceAll("\\.json$", "");
        }
        refNode = variables.get(schemaName);
        if (internalRef != null && refNode != null){
            refNode = getReferencedNodeFromSameDocument(refNode, internalRef);
        }
        return refNode;
    }

    private static JsonNode getReferencedNodeFromSameDocument(JsonNode originalSchema, String ref){
        if ("#".equals(ref))
            return originalSchema;
        ref = ref.replace("#/", "");
        return getNestedSubnode(originalSchema, ref);
    }

    public static JsonNode getNestedSubnode(JsonNode rootNode, String path) {
        String[] pathElements = path.split("/");
        JsonNode currentNode = rootNode;

        for (String element : pathElements) {
            currentNode = currentNode.get(element);
        }

        return currentNode;
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

    private Collection<String> constructPathFromNonArrayProperty(JsonNode jsonNode, String parentPath, JsonNode originalSchema, int depth) {
        Collection<String> paths = new HashSet<>();

        JsonNode childNode;
        String currentPath;
        List<String> enumPaths = new LinkedList<>();

        Iterator<String> fieldNames = jsonNode.fieldNames();
        if(!fieldNames.hasNext())
            paths.add(parentPath);
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            childNode = jsonNode.get(fieldName);

            if ("$ref".equals(fieldName)) {
                childNode = handleReferences(originalSchema, childNode);
                currentPath = parentPath;
            } else if ("enum".equals(fieldName)){
                enumPaths = handleEnum(jsonNode, parentPath);
                currentPath = parentPath;
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

    private static List<String> handleEnum(JsonNode jsonNode, String parentPath) {
        JsonNode enumValuesNode = jsonNode.get("enum");
        ObjectMapper mapper = new ObjectMapper();
        String[] enumValuesArray;
        var paths = new LinkedList<String>();
        try {
            enumValuesArray = mapper.treeToValue(enumValuesNode, String[].class);
        } catch (JsonProcessingException e) {
            return new LinkedList<>();
        }
        List<String> enumValues = new ArrayList<>(Arrays.asList(enumValuesArray));
        for (String enumPath : enumValues){
            paths.add(parentPath + "." + enumPath);
        }
        return paths;
    }

}
