package io.sapl.grammar.ide.contentassist;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.wnameless.json.flattener.JsonFlattener;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class SchemaProposals {

    private final VariablesAndCombinatorSource variablesAndCombinatorSource;

    private final Collection<String> unwantedJsonKeywords = Set.of(
            "\\$schema",
            "required(\\[\\d+\\])*",
            ".*enum\\[\\d+\\]",
            "additionalProperties(\\[\\d+\\])*");

    private final Collection<String> unwantedPathKeywords = Set.of(
            "properties\\.?",
            "\\.type\\.",
            "\\.type$",
            "java\\.?");

    private static final String ENUM_KEYWORD = "enum\\[\\d+\\]";


    public List<String> getVariableNamesAsTemplates(){
        List<String> templates = new ArrayList<>();
        var variables = getAllVariables().next().block();
        if(variables != null)
            templates = variables.keySet().stream().collect(Collectors.toList());
        return templates;
    }

    public List<String> getCodeTemplates(Expression expression) {
        return getAllVariables().next().flatMap(vars ->
                expression
                        .evaluate()
                        .contextWrite(ctx -> AuthorizationContext.setVariables(ctx, vars))
                        .flatMap(this::getCodeTemplates)
                        .filter(StringUtils::isNotBlank)
                        .collectList()
        ).block();
    }

    private Flux<String> getCodeTemplates(Val v) {
        List<String> schema = new ArrayList<>();
        try {
            schema = flattenSchema(v.getText());
        } catch (Exception e) {
            log.info("Could not flatten schema: {}", v.getText());
        }
        return Flux.fromIterable(schema);
    }

    private Flux<Map<String, JsonNode>> getAllVariables() {
        return variablesAndCombinatorSource
                .getVariables()
                .map(this::getMapOfVariables);
    }

    private Map<String, JsonNode> getMapOfVariables(Optional<Map<String, JsonNode>> s) {
        return s.isPresent() ? s.get() : Collections.emptyMap();
    }


    private List<String> flattenSchema(String schema) {
        var unwantedEnumMatch = ".*".concat(ENUM_KEYWORD);
        var flattenJson = JsonFlattener.flattenAsMap(schema);
        List<String> paths = new ArrayList<>(flattenJson.keySet());

        var correctedPaths = correctJsonPathForEnums(unwantedEnumMatch, flattenJson);
        paths.addAll(correctedPaths);

        return paths.stream()
                .filter(path -> !stringMatchesUnwantedJsonKeyword(path))
                .map(this::removeUnwantedKeywordsFromPath)
                .collect(Collectors.toList());
    }

    private List<String> correctJsonPathForEnums(String unwantedEnumMatch, Map<String, Object> flattenJson) {
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