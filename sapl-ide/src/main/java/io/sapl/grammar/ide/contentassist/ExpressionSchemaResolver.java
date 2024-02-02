package io.sapl.grammar.ide.contentassist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.BasicEnvironmentAttribute;
import io.sapl.grammar.sapl.BasicEnvironmentHeadAttribute;
import io.sapl.grammar.sapl.BasicFunction;
import io.sapl.grammar.sapl.BasicGroup;
import io.sapl.grammar.sapl.BasicIdentifier;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.HeadAttributeFinderStep;
import io.sapl.grammar.sapl.Import;
import io.sapl.grammar.sapl.LibraryImport;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.Schema;
import io.sapl.grammar.sapl.Step;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.grammar.sapl.WildcardImport;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.pdp.config.PDPConfiguration;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ExpressionSchemaResolver {

    public List<JsonNode> inferPotentialSchemasOfExpression(Expression expression, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {

        List<Step>     steps;
        List<JsonNode> baseSchemas;
        if (expression instanceof BasicGroup basicGroup) {
            // a BasicGroup may contain an expression with implicit schemas
            baseSchemas = inferPotentialSchemasOfExpression(basicGroup.getExpression(), context, pdpConfiguration);
            steps       = basicGroup.getSteps();
        } else if (expression instanceof BasicFunction basicFunction) {
            // function implementations may have schemas associated
            baseSchemas = inferPotentialSchemasFromFunction(basicFunction.getFsteps(), context, pdpConfiguration);
            steps       = basicFunction.getSteps();
        } else if (expression instanceof BasicEnvironmentAttribute basicEnvironmentAttribute) {
            // PIP implementations may have schemas associated
            baseSchemas = inferPotentialSchemasFromAttributeFinder(basicEnvironmentAttribute.getIdSteps(), context,
                    pdpConfiguration);
            steps       = basicEnvironmentAttribute.getSteps();
        } else if (expression instanceof BasicEnvironmentHeadAttribute basicEnvironmentHeadAttribute) {
            // PIP implementations may have schemas associated
            baseSchemas = inferPotentialSchemasFromAttributeFinder(basicEnvironmentHeadAttribute.getIdSteps(), context,
                    pdpConfiguration);
            steps       = basicEnvironmentHeadAttribute.getSteps();
        } else if (expression instanceof BasicIdentifier basicIdentifier) {
            // an identifier may be an authorization subscription element with schema, or
            // the result of a value definition with an expression with explicit or implicit
            // schemas
            baseSchemas = inferPotentialSchemasFromIdentifier(basicIdentifier.getIdentifier(), context,
                    pdpConfiguration);
            steps       = basicIdentifier.getSteps();
        } else {
            // BasicValue -> no schema possible
            // BasicRelative traversing relative @ nodes -> unclear how this could be
            // resolved
            // All other expressions are operations that will remove schema association.
            return List.of();
        }
        return inferPotentialSchemasStepsAfterExpression(baseSchemas, steps, context, pdpConfiguration);

    }

    private List<JsonNode> inferPotentialSchemasStepsAfterExpression(List<JsonNode> baseSchemas, List<Step> steps,
            ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        if (steps.isEmpty())
            return baseSchemas;
        var head       = steps.get(0);
        var newSchemas = List.<JsonNode>of();

        // each step after one which has schemas associated with it will lose schema
        // association only attribute steps may imply new schemas based on PIP function
        // annotations in this case the attribute schemas are the new implied schemas
        // until invalidated by following steps or overridden by following attribute
        // steps.

        if (head instanceof AttributeFinderStep attributeFinderStep) {
            newSchemas = inferPotentialSchemasFromAttributeFinder(attributeFinderStep.getIdSteps(), context,
                    pdpConfiguration);
        } else if (head instanceof HeadAttributeFinderStep headAttributeFinderStep) {
            newSchemas = inferPotentialSchemasFromAttributeFinder(headAttributeFinderStep.getIdSteps(), context,
                    pdpConfiguration);
        }

        return inferPotentialSchemasStepsAfterExpression(newSchemas, tail(steps), context, pdpConfiguration);
    }

    private <T> List<T> tail(List<T> list) {
        if (list.size() <= 1)
            return List.of();
        return list.subList(1, list.size());
    }

    private List<JsonNode> inferPotentialSchemasFromAttributeFinder(List<String> idSteps, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        var attributeContext = pdpConfiguration.attributeContext();
        var nameInUse        = joinStepsToName(idSteps);
        var resolvedName     = resolveImport(nameInUse, context, attributeContext.getAllFullyQualifiedFunctions());
        return lookupSchemasByName(resolvedName, attributeContext.getAttributeSchemas());
    }

    private List<JsonNode> inferPotentialSchemasFromFunction(List<String> idSteps, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        var functionContext = pdpConfiguration.functionContext();
        var nameInUse       = joinStepsToName(idSteps);
        var resolvedName    = resolveImport(nameInUse, context, functionContext.getAllFullyQualifiedFunctions());
        return lookupSchemasByName(resolvedName, functionContext.getFunctionSchemas());
    }

    private List<JsonNode> lookupSchemasByName(String resolvedFunctionName,
            Map<String, JsonNode> schemasByCodeTemplate) {
        var discoveredSchemas = new ArrayList<JsonNode>();
        for (var schemaEntry : schemasByCodeTemplate.entrySet()) {
            if (schemaEntry.getKey().contains(resolvedFunctionName)) {
                discoveredSchemas.add(schemaEntry.getValue());
            }
        }
        return discoveredSchemas;
    }

    private List<JsonNode> inferPotentialSchemasFromIdentifier(String identifier, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        if (SAPLContentProposalProvider.AUTHORIRIZATION_SUBSCRIPTION_VARIABLE_NAME_PROPOSALS.contains(identifier)) {
            return inferSubscriptionElementSchema(identifier, context, pdpConfiguration);
        }
        var schemas = new ArrayList<JsonNode>();
        schemas.addAll(lookupSchemasOfMatchingValueDefinitionsInPolicySetHeader(identifier, context, pdpConfiguration));
        schemas.addAll(lookupSchemasOfMatchingValueDefinitionsInPolicyBody(identifier, context, pdpConfiguration));
        return schemas;
    }

    private List<JsonNode> lookupSchemasOfMatchingValueDefinitionsInPolicySetHeader(String identifier,
            ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        var schemas = new ArrayList<JsonNode>();
        if (context.getRootModel() instanceof SAPL sapl && sapl.getPolicyElement() instanceof PolicySet policySet) {
            for (var valueDefinition : ((List<ValueDefinition>) policySet.getValueDefinitions())) {
                if (nameMatchesAndIsInScope(identifier, valueDefinition, context)) {
                    schemas.addAll(inferValueDefinitionSchemas(valueDefinition, context, pdpConfiguration));
                }
            }
        }
        return schemas;
    }

    private List<JsonNode> lookupSchemasOfMatchingValueDefinitionsInPolicyBody(String identifier,
            ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        var schemas    = new ArrayList<JsonNode>();
        var policyBody = TreeNavigationUtil.goToFirstParent(context.getCurrentModel(), PolicyBody.class);

        if (policyBody == null)
            return schemas;

        for (var statement : policyBody.getStatements()) {
            if (statement instanceof ValueDefinition valueDefinition) {
                if (nameMatchesAndIsInScope(identifier, valueDefinition, context)) {
                    schemas.addAll(inferValueDefinitionSchemas(valueDefinition, context, pdpConfiguration));
                }
            }
        }
        return schemas;
    }

    private boolean nameMatchesAndIsInScope(String identifier, ValueDefinition definition,
            ContentAssistContext context) {
        return Objects.equals(definition.getName(), identifier) && context.getOffset() > offsetOf(definition);
    }

    private List<JsonNode> inferValueDefinitionSchemas(ValueDefinition valueDefinition, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        var schemas = inferPotentialSchemasOfExpression(valueDefinition.getEval(), context, pdpConfiguration);
        for (var schemaExpression : valueDefinition.getSchemaVarExpression()) {
            evaluateExpressionToSchema(schemaExpression, pdpConfiguration).ifPresent(s -> schemas.add(s));
        }
        return schemas;
    }

    private List<JsonNode> inferSubscriptionElementSchema(String identifier, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        var schemas = new ArrayList<JsonNode>();
        if (context.getRootModel() instanceof SAPL sapl) {
            for (var schema : ((List<Schema>) sapl.getSchemas())) {
                if (Objects.equals(identifier, schema.getSubscriptionElement())) {
                    evaluateExpressionToSchema(schema.getSchemaExpression(), pdpConfiguration)
                            .ifPresent(s -> schemas.add(s));
                }
            }
        }
        return schemas;
    }

    private Optional<JsonNode> evaluateExpressionToSchema(Expression expression, PDPConfiguration pdpConfiguration) {
        var expressionValue = expression.evaluate()
                .contextWrite(ctx -> AuthorizationContext.setVariables(ctx, pdpConfiguration.variables())).blockFirst();
        if (expressionValue.isDefined()) {
            return Optional.of(expressionValue.get());
        }
        return Optional.empty();
    }

    private static int offsetOf(EObject statement) {
        return NodeModelUtils.getNode(statement).getOffset();
    }

    private String joinStepsToPrefix(List<String> steps) {
        return joinStepsToName(steps) + '.';
    }

    private String joinStepsToName(List<String> steps) {
        return String.join(".", steps);
    }

    private String resolveImport(String nameInUse, ContentAssistContext context, Collection<String> allFunctions) {
        if (context.getRootModel() instanceof SAPL sapl) {
            var imports = Objects.requireNonNullElse(sapl.getImports(), List.<Import>of());
            for (var anImport : imports) {
                if (anImport instanceof WildcardImport wildcardImport) {
                    var resolutionCandidate = joinStepsToPrefix(wildcardImport.getLibSteps()) + nameInUse;
                    if (allFunctions.contains(resolutionCandidate))
                        return resolutionCandidate;
                } else if (anImport instanceof LibraryImport libraryImport) {
                    var alias = libraryImport.getLibAlias();
                    if (nameInUse.startsWith(alias)) {
                        var prefix              = joinStepsToPrefix(libraryImport.getLibSteps());
                        var suffix              = nameInUse.substring(alias.length() + 1);
                        var resolutionCandidate = prefix + suffix;
                        if (allFunctions.contains(resolutionCandidate))
                            return resolutionCandidate;
                    }
                } else {
                    // Basic Import
                    var importedName = joinStepsToName(anImport.getLibSteps());
                    if (importedName.endsWith(nameInUse) && allFunctions.contains(importedName)) {
                        return importedName;
                    }
                }
            }
        }
        return nameInUse;
    }

}
