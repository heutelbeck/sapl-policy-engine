package io.sapl.grammar.ide.contentassist.schema;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import reactor.core.publisher.Flux;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
public class SchemaProposals {

    private final VariablesAndCombinatorSource variablesAndCombinatorSource;

    private static final Collection<String> unwantedPathKeywords = Set.of("java\\.?");


    public List<String> getVariableNamesAsTemplates(){
        var variables = getAllVariablesAsMap();
        if (variables != null)
            return new ArrayList<>(variables.keySet());
        else
            return new ArrayList<>();
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
        try {
            return Flux.fromIterable(schemaTemplatesFromJson(v.getText()));
        } catch (Exception e) {
            log.trace("Could not flatten schema: {}", v.getText(), e);
            return Flux.empty();
        }
    }

    private Map<String, JsonNode> getAllVariablesAsMap(){
        return getAllVariables().next().block();
    }

    private Flux<Map<String, JsonNode>> getAllVariables() {
        return variablesAndCombinatorSource
                .getVariables()
                .map(v -> v.orElse(Map.of()));
    }

    private List<String> schemaTemplatesFromJson(String source){
        return flattenSchemaFromJson(source);
    }

    private List<String> flattenSchemaFromJson(String schema) {
        var paths = new SchemaParser(getAllVariablesAsMap()).generatePaths(schema);
        return paths.stream()
                .map(this::removeUnwantedKeywordsFromPath)
                .toList();
    }

    private String removeUnwantedKeywordsFromPath(String path) {
        String alteredPath = path;
        for (String keyword : unwantedPathKeywords) {
            alteredPath = alteredPath.replaceAll(keyword, "");
        }
        return alteredPath;
    }


}