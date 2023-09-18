package io.sapl.grammar.ide.contentassist.schema;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.Expression;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.FunctionContext;
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
    private final FunctionContext functionContext;

    private static final String MULTIPLE_SCHEMA_ANNOTATIONS_NOT_ALLOWED = "Please only provide either a schema or a schemaPath annotation.";

    public List<String> getVariableNamesAsTemplates(){
        List<String> templates = new ArrayList<>();
        var variables = getAllVariablesAsMap();
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
            SchemaTemplates schemaTemplate = new SchemaTemplates(getAllVariablesAsMap());
            schema = schemaTemplate.schemaTemplatesFromJson(v.getText());
        } catch (Exception e) {
            log.info("Could not flatten schema: {}", v.getText());
        }
        return Flux.fromIterable(schema);
    }

    public List<String> getSchemaTemplates(){
        StringBuilder sb;
        List<String> paths;
        var schemaTemplates = new ArrayList<String>();
        var funCodeTemplate = functionContext.getCodeTemplates();
        var functions = functionContext.getAllFullyQualifiedFunctionsWithMetadata();
        for (var functionMetadata : functions.values()){
            var functionSchema = functionMetadata.getFunctionSchema();
            var pathToSchema = functionMetadata.getFunctionPathToSchema();

            if (functionSchema.length() > 0 && pathToSchema.length() > 0)
                throw new IllegalArgumentException(MULTIPLE_SCHEMA_ANNOTATIONS_NOT_ALLOWED);

            if (functionSchema.length() > 0 || pathToSchema.length() > 0){
                SchemaTemplates schemaTemplate = new SchemaTemplates(this.getAllVariablesAsMap());

                if (functionSchema.length() > 0)
                    paths = schemaTemplate.schemaTemplatesFromJson(functionSchema);
                else
                    paths = schemaTemplate.schemaTemplatesFromFile(pathToSchema);

                for (var path : paths){
                    sb = new StringBuilder();
                    sb.append(funCodeTemplate).append('.').append(path);
                    schemaTemplates.add(sb.toString());
                }
            }
        }
        return schemaTemplates;
    }


    private Map<String, JsonNode> getAllVariablesAsMap(){
        return getAllVariables().next().block();
    }

    private Flux<Map<String, JsonNode>> getAllVariables() {
        return variablesAndCombinatorSource
                .getVariables()
                .map(v -> v.orElse(Collections.emptyMap()));
    }
}