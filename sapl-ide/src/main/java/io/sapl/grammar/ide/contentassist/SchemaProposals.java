package io.sapl.grammar.ide.contentassist;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.wnameless.json.flattener.JsonFlattener;
import io.sapl.pdp.config.filesystem.FileSystemVariablesAndCombinatorSource;

import java.util.*;
import java.util.stream.Collectors;
public class SchemaProposals {

    private final Collection<String> unwantedJsonKeywords = Set.of(
            "$schema",
            "required(\\[\\d\\])*",
            "additionalProperties(\\[\\d\\])*");

    private final Collection<String> unwantedPathKeywords = Set.of("properties.", ".type");

    FileSystemVariablesAndCombinatorSource varSource;

    String JsonSchema = "{"
            + "     \"$schema\": \"http://json-schema.org/draft-07/schema#\","
            + "     \"properties\": "
            + "       {"
            + "         \"subject\": {\"type\": \"object\","
            + "                       \"properties\": "
            + "                         {"
            + "                            \"name\": {\"type\": \"object\","
            + "                                       \"properties\": "
            + "                                         {"
            + "                                            \"firstname\": {\"type\": \"string\"}"
            + "                                         }"
            + "                                      },"
            + "                            \"age\": {\"type\": \"number\"}"
            + "                         }"
            + "                       },"
            + "         \"action\": {\"type\": \"string\"},"
            + "         \"resource\": {\"type\": \"string\"},"
            + "         \"somethingElse\": {\"type\": \"string\"},"
            + "         \"environment\": {\"type\": \"string\"}"
            + "       },"
            + "     \"required\": [\"subject\", \"action\", \"resource\"],"
            + "     \"additionalProperties\": false "
            + "   }";

//    public List<String> getCodeTemplates() {
//        return flattenSchema(JsonSchema);
//    }

    public List<String> getCodeTemplates() {

        List<JsonNode> schemasFromVariables = new ArrayList<>();
        List<String> templates = new ArrayList<>();

        var schemaVariables =
                getSchemaVariablesFromSource();

        for (var optFileNamesAndSchemas: schemaVariables)
            optFileNamesAndSchemas.ifPresent(filenamesAndSchemas -> schemasFromVariables.addAll(filenamesAndSchemas.values()));

        for (var listElem: schemasFromVariables)
            templates.addAll(flattenSchema(listElem.toString()));

        return templates;
    }

    private List<Optional<Map<String, JsonNode>>> getSchemaVariablesFromSource() {
        return new FileSystemVariablesAndCombinatorSource("~")
        .getVariables()
                .collectList()
                .block();
    }


    private List<String> flattenSchema(String schema) {
        var flattenJson = JsonFlattener.flattenAsMap(schema);
        List<String> paths = new ArrayList<>(flattenJson.keySet());
        paths.remove("$schema");
        return paths.stream()
                .filter(path -> !stringMatchesUnwantedJsonKeyword(path))
                .map(path -> removeUnwantedKeywordsFromPath(path))
                .collect(Collectors.toList());
    }

    private boolean stringMatchesUnwantedJsonKeyword(String matchingString) {
        for (String regex : unwantedJsonKeywords) {
            if (matchingString.matches(regex))
                return true;
        }
        return false;
    }

    private String removeUnwantedKeywordsFromPath(String path) {
        for (String keyword : unwantedPathKeywords) {
            path = path.replace(keyword, "");
        }
        return path;
    }

}