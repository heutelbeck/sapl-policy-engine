package io.sapl.grammar.ide.contentassist.schema;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.interpreter.Val;
import io.sapl.grammar.sapl.*;
import io.sapl.grammar.sapl.impl.BasicExpressionImplCustom;
import io.sapl.grammar.sapl.impl.BasicIdentifierImplCustom;
import io.sapl.grammar.sapl.impl.KeyStepImpl;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.emf.ecore.util.FeatureMapUtil;
import reactor.core.publisher.Flux;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
public class SchemaProposals {

    private final VariablesAndCombinatorSource variablesAndCombinatorSource;
    private final FunctionContext functionContext;


    private static final Collection<String> unwantedPathKeywords = Set.of("java\\.?");


    public List<String> getVariableNamesAsTemplates(){
        var variables = getAllVariablesAsMap();
        if (variables != null)
            return new ArrayList<>(variables.keySet());
        else
            return new ArrayList<>();
    }

    // VORGEHEN
    // 1. Weise ich der Variablen eine Funktion zu? Also steht rechts vom "="-Zeichen ein Funktionsname?
    // 2. Falls ja, hole das Schema für diese Funktion
    // 3. Generiere Proposals für diese Zeile
    // 4. Speichere dieses Schema als SchemaVarExpression der Variablen ab
    // ============
    // PROBLEME
    // 1. Bei der ValueDefinition steht rechts vom "="-Zeichen eine Expression. Um an den wörtlichen Text ranzukommen,
    //    muss ich zu BasicIdentifier casten, den Identifier rauslesen, und die KeySteps ranhängen, falls welche da sind.
    // 2. Da es Importe (Libraries, Wildcards) geben kann, ist es schwierig, den Identifier + Steps manuell darauf zu
    //    prüfen, ob es sich um eine Funktion handelt, bzw. ihren Fully Qualified Name rauszufinden.
    // 3. Da das Schema zu einer Funktion als String gespeichert ist, lässt es sich nicht ohne Weiteres in eine
    //    schemaVarExpression umwandeln.
    // ===========
    // LÖSUNGSANSÄTZE
    // 1. Idealerweise hätte ich die Möglichkeit, direkt für die Expression, die zur Variablen gehört, zu prüfen,
    //    ob sie einer Funktion entspricht, und den FullyQualifiedName davon zu kriegen. Gibt es dazu Code im
    //    Policy-Parser?
    // 2. Das Funktionsschema sollte ich dann in eine Expression umwandeln können. Dazu müsste ich schauen, ob es
    //    konkrete Subklassen von Expression gibt, die ich aus einem String aufbauen kann. Welche konkrete Klasse
    //    hat die SchemaVarExpression bei Variablen?

    public List<String> getFunctionSchemaTemplates(Expression expression) {
        var templates = new LinkedList<String>();

        boolean isInstance = expression instanceof BasicIdentifier;
        BasicIdentifier basicIdent;
        String identifier;
        List<Step> steps;
        if (isInstance){
            basicIdent = (BasicIdentifier) expression;
            identifier = basicIdent.getIdentifier();
            steps = basicIdent.getSteps();

            String fullName = "";
            for (Step step : steps) {
                var isKeyStep = step instanceof KeyStep;
                if (isKeyStep){
                    var keyStep = (KeyStep) step;
                    fullName = fullName.concat(".").concat(keyStep.getId());
                }
            }
            fullName = identifier.concat(fullName);
            var isFunction = isFunction(fullName);

            if (isFunction){
                var schema = functionContext.getFunctionSchemas().get(fullName);
                templates.addAll(schemaTemplatesFromJson(schema));
            }
        }

        return templates;
    }

    private Flux<String> getFunctionSchemaTemplates(Val v) {
        try {
            var valText = v.getText();
            if (isFunction(valText)){
                var functionSchema = functionContext.getFunctionSchemas().get(valText);
                var templates = schemaTemplatesFromJson(functionSchema);
                return Flux.fromIterable(templates);
            }
            return Flux.empty();
        } catch (Exception e) {
            log.trace("Could not flatten schema: {}", v.getText(), e);
            return Flux.empty();
        }
    }

    private boolean isFunction(String functionName){
        return functionContext.getAllFullyQualifiedFunctions().contains(functionName);
        //return functionContext.isProvidedFunction(functionName);
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

    public List<String> schemaTemplatesFromJson(String source){
        return flattenSchemaFromJson(source);
    }

    private List<String> flattenSchemaFromJson(String schema) {
        var paths = new SchemaParser(getAllVariablesAsMap()).generatePaths(schema);
        return paths.stream()
                .map(this::removeUnwantedKeywordsFromPath)
                .toList();
    }

    private String removeUnwantedKeywordsFromPath(String path) {
        var alteredPath = path;
        for (String keyword : unwantedPathKeywords) {
            alteredPath = alteredPath.replaceAll(keyword, "");
        }
        return alteredPath;
    }



}