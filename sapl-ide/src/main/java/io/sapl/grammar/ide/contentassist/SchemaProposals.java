package io.sapl.grammar.ide.contentassist;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.SchemaTemplates;
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

    public List<String> getVariableNamesAsTemplates(){
        List<String> templates = new ArrayList<>();
        var variables = getAllVariables().next().block();
        if(variables != null)
            templates = new ArrayList<>(variables.keySet());
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
            SchemaTemplates schemaTemplate = new SchemaTemplates();
            schema = schemaTemplate.schemaTemplatesFromJson(v.getText());
        } catch (Exception e) {
            log.info("Could not flatten schema: {}", v.getText());
        }
        return Flux.fromIterable(schema);
    }

    private Flux<Map<String, JsonNode>> getAllVariables() {
        return variablesAndCombinatorSource
                .getVariables()
                .map(v -> v.orElse(Collections.emptyMap()));
    }
}