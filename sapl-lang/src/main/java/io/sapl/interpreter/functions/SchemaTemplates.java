package io.sapl.interpreter.functions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wnameless.json.flattener.JsonFlattener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SchemaTemplates {

    private static final Collection<String> unwantedJsonKeywords = Set.of(
            "\\$schema",
            "\\$id",
            "type",
            "required(\\[\\d+\\])*",
            ".*enum\\[\\d+\\]",
            "additionalProperties(\\[\\d+\\])*");

    private static final Collection<String> unwantedPathKeywords = Set.of(
            "properties\\.?",
            "\\.type\\.",
            "\\.type$",
            "java\\.?");

    private static final String ENUM_KEYWORD = "enum\\[\\d+]";

    public List<String> schemaTemplatesFromJson(String source){
        return flattenSchemaFromJson(source);
    }

    public List<String> schemaTemplatesFromFile(String source){
        return this.flattenSchemaFromFile(source);
    }

    private List<String> flattenSchemaFromFile(String schemaFilePath){
        String schema = getSchemaFromFile(schemaFilePath);
        if(schema.equals(""))
            return new ArrayList<>();
        //return flattenSchemaFromJson(schema);
        return generatePaths(schema);
    }

    private static List<String> generatePaths(String schema){
        List<String> paths = new ArrayList<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode schemaNode = mapper.readTree(schema);
            processSchema(schemaNode, "#", paths);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    private static void processSchema(JsonNode schemaNode, String currentPath, List<String> paths) {
        if (schemaNode.isObject()){
            var fields = schemaNode.fields();
            fields.forEachRemaining(field -> {
                var propertyPath = currentPath + field.getKey();
                paths.add(propertyPath);
                processSchema(field.getValue(), propertyPath, paths);
            });
        } else if (schemaNode.isArray()){
            // TODO
        }
    }

    private static List<String> flattenSchemaFromJson(String schema) {
        var unwantedEnumMatch = ".*".concat(ENUM_KEYWORD);
        var flattenJson = JsonFlattener.flattenAsMap(schema);
        List<String> paths = new ArrayList<>(flattenJson.keySet());

        var correctedPaths = correctJsonPathForEnums(unwantedEnumMatch, flattenJson);
        paths.addAll(correctedPaths);

        return paths.stream()
                .filter(path -> !stringMatchesUnwantedJsonKeyword(path))
                .map(SchemaTemplates::removeUnwantedKeywordsFromPath)
                .toList();
    }

    private String getSchemaFromFile(final String fileName) {
        String fileAsString;

        InputStream ioStream = this.getClass()
                .getClassLoader()
                .getResourceAsStream(fileName);

        if (ioStream == null) {
            throw new IllegalArgumentException(fileName + " was not found.");
        }

        try {
            fileAsString = new String(ioStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalArgumentException(fileName + " is not a valid text file.");
        }

        return fileAsString;
    }

    private static List<String> correctJsonPathForEnums(String unwantedEnumMatch, Map<String, Object> flattenJson) {
        List<String> paths = new ArrayList<>();
        for (var entry: flattenJson.entrySet()){
            if(entry.getKey().matches(unwantedEnumMatch)){
                var correctedPath = entry.getKey()
                        .replaceAll(ENUM_KEYWORD, (String) entry.getValue());
                paths.add(correctedPath);
            }
        }
        return paths;
    }

    private static boolean stringMatchesUnwantedJsonKeyword(String matchingString) {
        for (String regex : unwantedJsonKeywords) {
            if (matchingString.matches(regex))
                return true;
        }
        return false;
    }

    private static String removeUnwantedKeywordsFromPath(String path) {
        String alteredPath = path;
        for (String keyword : unwantedPathKeywords) {
            alteredPath = alteredPath.replaceAll(keyword, "");
        }
        return alteredPath;
    }
}