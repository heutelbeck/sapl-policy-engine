package io.sapl.grammar.ide.contentassist.schema;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;

import java.util.*;

@RequiredArgsConstructor
public class SchemaTemplates {

    private final Map<String, JsonNode> variables;

    private static final Collection<String> unwantedPathKeywords = Set.of("java\\.?");

    public List<String> schemaTemplatesFromJson(String source){
        return flattenSchemaFromJson(source);
    }

   private List<String> flattenSchemaFromJson(String schema) {
       if ("".equals(schema))
           return new ArrayList<>();
       var paths = new SchemaParser(variables).generatePaths(schema);
       return paths.stream()
               .map(SchemaTemplates::removeUnwantedKeywordsFromPath)
               .toList();
   }

    private static String removeUnwantedKeywordsFromPath(String path) {
        String alteredPath = path;
        for (String keyword : unwantedPathKeywords) {
            alteredPath = alteredPath.replaceAll(keyword, "");
        }
        return alteredPath;
    }
}