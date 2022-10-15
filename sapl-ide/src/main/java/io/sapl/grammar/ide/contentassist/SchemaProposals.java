package io.sapl.grammar.ide.contentassist;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.wnameless.json.flattener.JsonFlattener;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class SchemaProposals {

    private final Collection<String> unwantedJsonKeywords = Set.of(
            "$schema",
            "required(\\[\\d+\\])*",
            ".*enum\\[\\d+\\]",
            "additionalProperties(\\[\\d+\\])*");

    private final Collection<String> unwantedPathKeywords = Set.of(
            "properties\\.?",
            "\\.?type\\.?",
            "java\\.?");

    private final String enumKeyword = "enum\\[\\d+\\]";

    private final VariablesAndCombinatorSource variablesAndCombinatorSource;

    public List<String> getVariableNamesAsTemplates(){
        return getAllVariables().next().block()
                .keySet().stream().collect(Collectors.toList());
    }

    public List<String> getCodeTemplates(Expression expression) {
        return getAllVariables().next().flatMap(vars ->
                expression
                        .evaluate()
                        .contextWrite(ctx -> AuthorizationContext.setVariables(ctx, vars))
                        .flatMap(s -> getCodeTemplates(s))
                        .filter(StringUtils::isNotBlank)
                        .collectList()
        ).block();
    }

    private Flux<String> getCodeTemplates(Val v) {
        List<String> schema = new ArrayList<>();
        try {
            schema = flattenSchema(v.getText());
        } finally {
            return Flux.fromIterable(schema);
        }
    }

    private Flux<Map<String, JsonNode>> getAllVariables() {
        return variablesAndCombinatorSource
                .getVariables()
                .map(s -> getMapOfVariables(s));
    }

    private Map<String, JsonNode> getMapOfVariables(Optional<Map<String, JsonNode>> s) {
        return s.isPresent() ? s.get() : Collections.EMPTY_MAP;
    }


    private List<String> flattenSchema(String schema) {
        var flattenJson = JsonFlattener.flattenAsMap(schema);
        List<String> paths = new ArrayList<>(flattenJson.keySet());
        var unwantedEnumMatch = ".*".concat(enumKeyword);
        //flattenJson.forEach((key, value) -> System.out.println(key + ": " + value));

        for (var entry: flattenJson.entrySet()){
            if(entry.getKey().matches(unwantedEnumMatch)){
                var correctedPath = entry.getKey()
                        .replaceAll(enumKeyword, (String) entry.getValue());
                paths.add(correctedPath);
            }
        }
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
        String alteredPath = path;
        for (String keyword : unwantedPathKeywords) {
            alteredPath = alteredPath.replaceAll(keyword, "");
        }
        return alteredPath;
    }

}