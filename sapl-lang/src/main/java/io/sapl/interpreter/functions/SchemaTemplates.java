package io.sapl.interpreter.functions;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wnameless.json.flattener.JsonFlattener;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;


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

    public static List<String> schemaTemplates(String source){
        if (isValidJson(source))
            return flattenSchemaFromJson(source);
        return flattenSchemaFromFile(source);
    }

    private static List<String> flattenSchemaFromFile(String filePath){
        String schema = getSchemaFromFile(filePath);
        if(schema.equals(""))
            return new ArrayList<>();
        return flattenSchemaFromJson(schema);
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


    private static boolean isValidJson(String json) {
        ObjectMapper mapper = new ObjectMapper()
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try {
            mapper.readTree(json);
        } catch (JacksonException e) {
            return false;
        }
        return true;
    }

    private static String getSchemaFromFile(String filePath){
        String schema;
        Path schemaPath = resolveHomeFolderIfPresent(filePath);

        try {
            schema = Files.readString(schemaPath);
        } catch (IOException e) {
            schema = "";
        }

        return schema;
    }

    private static Path resolveHomeFolderIfPresent(String filePath) {
        filePath = filePath.replace("/", File.separator);

        if (filePath.startsWith("~" + File.separator))
            return Path.of(getUserDirProperty() + filePath.substring(1));

        return Path.of(filePath);
    }

    private static String getUserDirProperty() {
        return System.getProperty("user.dir");
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
