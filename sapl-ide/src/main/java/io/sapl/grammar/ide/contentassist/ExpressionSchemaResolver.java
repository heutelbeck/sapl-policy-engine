/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.grammar.ide.contentassist;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;
import io.sapl.grammar.sapl.*;
import io.sapl.interpreter.context.AuthorizationContext;
import io.sapl.pdp.config.PDPConfiguration;
import lombok.experimental.UtilityClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import java.util.*;

@UtilityClass
public class ExpressionSchemaResolver {

    public List<JsonNode> inferPotentialSchemasOfExpression(Expression expression, ContentAssistContext context,
            PDPConfiguration pdpConfiguration, PolicyInformationPointDocumentationProvider docsProvider) {
        if (expression == null) {
            return new ArrayList<>();
        }
        List<Step>     steps;
        List<JsonNode> baseSchemas;
        switch (expression) {
        case BasicGroup basicGroup                                       -> {
            // a BasicGroup may contain an expression with implicit schemas
            baseSchemas = inferPotentialSchemasOfExpression(basicGroup.getExpression(), context, pdpConfiguration,
                    docsProvider);
            steps       = basicGroup.getSteps();
        }
        case BasicFunction basicFunction                                 -> {
            // function implementations may have schemas associated
            baseSchemas = inferPotentialSchemasFromFunction(basicFunction.getIdentifier().getNameFragments(), context,
                    pdpConfiguration);
            steps       = basicFunction.getSteps();
        }
        case BasicEnvironmentAttribute basicEnvironmentAttribute         -> {
            // PIP implementations may have schemas associated
            baseSchemas = inferPotentialSchemasFromAttributeFinder(
                    basicEnvironmentAttribute.getIdentifier().getNameFragments(), context, docsProvider);
            steps       = basicEnvironmentAttribute.getSteps();
        }
        case BasicEnvironmentHeadAttribute basicEnvironmentHeadAttribute -> {
            // PIP implementations may have schemas associated
            baseSchemas = inferPotentialSchemasFromAttributeFinder(
                    basicEnvironmentHeadAttribute.getIdentifier().getNameFragments(), context, docsProvider);
            steps       = basicEnvironmentHeadAttribute.getSteps();
        }
        case BasicIdentifier basicIdentifier                             -> {
            // an identifier may be an authorization subscription element with schema, or
            // the result of a value definition with an expression with explicit or implicit
            // schemas
            baseSchemas = inferPotentialSchemasFromIdentifier(basicIdentifier.getIdentifier(), context,
                    pdpConfiguration, docsProvider);
            steps       = basicIdentifier.getSteps();
        }
        default                                                          -> {
            // BasicValue -> no schema possible
            // BasicRelative traversing relative @ nodes -> unclear how this could be
            // resolved
            // All other expressions are operations that will remove schema association.
            return new ArrayList<>();
        }
        }
        return inferPotentialSchemasStepsAfterExpression(baseSchemas, steps, context, pdpConfiguration, docsProvider);
    }

    public List<JsonNode> inferValueDefinitionSchemas(ValueDefinition valueDefinition, ContentAssistContext context,
            PDPConfiguration pdpConfiguration, PolicyInformationPointDocumentationProvider docsProvider) {
        final var schemas = inferPotentialSchemasOfExpression(valueDefinition.getEval(), context, pdpConfiguration,
                docsProvider);
        for (final var schemaExpression : valueDefinition.getSchemaVarExpression()) {
            evaluateExpressionToSchema(schemaExpression, pdpConfiguration).ifPresent(schemas::add);
        }
        return schemas;
    }

    private List<JsonNode> inferPotentialSchemasStepsAfterExpression(List<JsonNode> baseSchemas, List<Step> steps,
            ContentAssistContext context, PDPConfiguration pdpConfiguration,
            PolicyInformationPointDocumentationProvider docsProvider) {
        if (steps.isEmpty()) {
            return baseSchemas;
        }
        final var head       = steps.getFirst();
        var       newSchemas = List.<JsonNode>of();

        // each step after one which has schemas associated with it will lose schema
        // association only attribute steps may imply new schemas based on PIP function
        // annotations in this case the attribute schemas are the new implied schemas
        // until invalidated by following steps or overridden by following attribute
        // steps.
        // Attempting to identify matching sub-schemas for any given step is a valid
        // improvement to be made here in the future.

        if (head instanceof final AttributeFinderStep attributeFinderStep) {
            newSchemas = inferPotentialSchemasFromAttributeFinder(
                    attributeFinderStep.getIdentifier().getNameFragments(), context, docsProvider);
        } else if (head instanceof final HeadAttributeFinderStep headAttributeFinderStep) {
            newSchemas = inferPotentialSchemasFromAttributeFinder(
                    headAttributeFinderStep.getIdentifier().getNameFragments(), context, docsProvider);
        }

        return inferPotentialSchemasStepsAfterExpression(newSchemas, tail(steps), context, pdpConfiguration,
                docsProvider);
    }

    private <T> List<T> tail(List<T> list) {
        if (list.size() <= 1) {
            return new ArrayList<>();
        }
        return list.subList(1, list.size());
    }

    private List<JsonNode> inferPotentialSchemasFromAttributeFinder(Iterable<String> idSteps,
            ContentAssistContext context, PolicyInformationPointDocumentationProvider docsProvider) {
        final var nameInUse    = joinStepsToName(idSteps);
        final var resolvedName = resolveImport(nameInUse, context);
        return lookupSchemasByName(resolvedName, docsProvider.getAttributeSchemas());
    }

    private List<JsonNode> inferPotentialSchemasFromFunction(Iterable<String> idSteps, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        final var functionContext = pdpConfiguration.functionContext();
        final var nameInUse       = joinStepsToName(idSteps);
        final var resolvedName    = resolveImport(nameInUse, context);
        return lookupSchemasByName(resolvedName, functionContext.getFunctionSchemas());
    }

    private String joinStepsToName(Iterable<String> steps) {
        return String.join(".", steps);
    }

    private List<JsonNode> lookupSchemasByName(String resolvedFunctionName,
            Map<String, JsonNode> schemasByCodeTemplate) {
        final var discoveredSchemas = new ArrayList<JsonNode>();
        for (final var schemaEntry : schemasByCodeTemplate.entrySet()) {
            if (schemaEntry.getKey().contains(resolvedFunctionName)) {
                discoveredSchemas.add(schemaEntry.getValue());
            }
        }
        return discoveredSchemas;
    }

    private List<JsonNode> inferPotentialSchemasFromIdentifier(String identifier, ContentAssistContext context,
            PDPConfiguration pdpConfiguration, PolicyInformationPointDocumentationProvider docsProvider) {
        if (VariablesProposalsGenerator.AUTHORIZATION_SUBSCRIPTION_VARIABLES.contains(identifier)) {
            return inferSubscriptionElementSchema(identifier, context, pdpConfiguration);
        }
        final var schemas = new ArrayList<>(lookupSchemasOfMatchingValueDefinitionsInPolicySetHeader(identifier,
                context, pdpConfiguration, docsProvider));
        schemas.addAll(lookupSchemasOfMatchingValueDefinitionsInPolicyBody(identifier, context, pdpConfiguration,
                docsProvider));
        return schemas;
    }

    private List<JsonNode> lookupSchemasOfMatchingValueDefinitionsInPolicySetHeader(String identifier,
            ContentAssistContext context, PDPConfiguration pdpConfiguration,
            PolicyInformationPointDocumentationProvider docsProvider) {
        final var schemas = new ArrayList<JsonNode>();
        if (context.getRootModel() instanceof final SAPL sapl
                && sapl.getPolicyElement() instanceof final PolicySet policySet) {
            for (final var valueDefinition : ((List<ValueDefinition>) policySet.getValueDefinitions())) {
                if (nameMatchesAndIsInScope(identifier, valueDefinition, context)) {
                    schemas.addAll(
                            inferValueDefinitionSchemas(valueDefinition, context, pdpConfiguration, docsProvider));
                }
            }
        }
        return schemas;
    }

    private List<JsonNode> lookupSchemasOfMatchingValueDefinitionsInPolicyBody(String identifier,
            ContentAssistContext context, PDPConfiguration pdpConfiguration,
            PolicyInformationPointDocumentationProvider docsProvider) {
        final var schemas    = new ArrayList<JsonNode>();
        final var policyBody = TreeNavigationUtil.goToFirstParent(context.getCurrentModel(), PolicyBody.class);

        if (null == policyBody) {
            return schemas;
        }

        for (final var statement : policyBody.getStatements()) {
            if (statement instanceof final ValueDefinition valueDefinition
                    && nameMatchesAndIsInScope(identifier, valueDefinition, context)) {
                schemas.addAll(inferValueDefinitionSchemas(valueDefinition, context, pdpConfiguration, docsProvider));
            }
        }
        return schemas;
    }

    private boolean nameMatchesAndIsInScope(String identifier, ValueDefinition definition,
            ContentAssistContext context) {
        return Objects.equals(definition.getName(), identifier) && context.getOffset() > offsetOf(definition);
    }

    private List<JsonNode> inferSubscriptionElementSchema(String identifier, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        final var schemas = new ArrayList<JsonNode>();
        if (context.getRootModel() instanceof final SAPL sapl) {
            for (final var schema : ((List<Schema>) sapl.getSchemas())) {
                if (Objects.equals(identifier, schema.getSubscriptionElement())) {
                    evaluateExpressionToSchema(schema.getSchemaExpression(), pdpConfiguration).ifPresent(schemas::add);
                }
            }
        }
        return schemas;
    }

    private Optional<JsonNode> evaluateExpressionToSchema(Expression expression, PDPConfiguration pdpConfiguration) {
        final var expressionValue = expression.evaluate().contextWrite(ctx -> {
            /*
             * explicitly do not add the attribute context, as schema definitions must not
             * contain attribute finders. Functions are allowed in schema expressions.
             */
            var newCtx = AuthorizationContext.setVariables(ctx, pdpConfiguration.variables());
            newCtx = AuthorizationContext.setFunctionContext(newCtx, pdpConfiguration.functionContext());
            return newCtx;
        }).blockFirst();
        if (null != expressionValue && expressionValue.isDefined()) {
            return Optional.of(expressionValue.get());
        }
        return Optional.empty();
    }

    public static int offsetOf(EObject statement) {
        return NodeModelUtils.getNode(statement).getOffset();
    }

    private String resolveImport(String nameReference, ContentAssistContext context) {
        if (nameReference.contains(".")) {
            return nameReference;
        }

        final var rootModel = context.getRootModel();
        if (rootModel instanceof final SAPL sapl) {
            final var imports = sapl.getImports();
            if (null == imports) {
                return nameReference;
            }

            for (var currentImport : imports) {
                final var importedFunction = fullyQualifiedFunctionName(currentImport);
                if (nameReference.equals(currentImport.getFunctionAlias())
                        || importedFunction.endsWith(nameReference)) {
                    return importedFunction;
                }
            }
        }
        return nameReference;
    }

    private String fullyQualifiedFunctionName(Import anImport) {
        final var library = String.join(".", anImport.getLibSteps());
        return library + "." + anImport.getFunctionName();
    }

}
