package io.sapl.grammar.ide.contentassist.schema;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RequiredArgsConstructor
public class SchemaTemplates {

    private final Map<String, JsonNode> variables;

    private static final Collection<String> unwantedPathKeywords = Set.of("java\\.?");

    public List<String> schemaTemplatesFromJson(String source){
        return flattenSchemaFromJson(source);
    }

    public List<String> schemaTemplatesFromFile(String source){
        return this.flattenSchemaFromFile(source);
    }

    private List<String> flattenSchemaFromFile(String schemaFilePath){
        String schema = getSchemaFromFile(schemaFilePath);
        return flattenSchemaFromJson(schema);
    }

   private List<String> flattenSchemaFromJson(String schema) {
       if ("".equals(schema))
           return new ArrayList<>();
       var paths = new SchemaParser(variables).generatePaths(schema);
       return paths.stream()
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

    private static String removeUnwantedKeywordsFromPath(String path) {
        String alteredPath = path;
        for (String keyword : unwantedPathKeywords) {
            alteredPath = alteredPath.replaceAll(keyword, "");
        }
        return alteredPath;
    }
}