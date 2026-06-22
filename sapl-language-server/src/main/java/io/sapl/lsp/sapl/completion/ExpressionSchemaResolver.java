/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.lsp.sapl.completion;

import static io.sapl.compiler.util.StringsUtil.unquoteString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import tools.jackson.databind.JsonNode;

import io.sapl.grammar.antlr.SAPLParser.AttributeFinderDotStepContext;
import io.sapl.grammar.antlr.SAPLParser.BasicContext;
import io.sapl.grammar.antlr.SAPLParser.BasicExprContext;
import io.sapl.grammar.antlr.SAPLParser.BasicExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.BracketStepContext;
import io.sapl.grammar.antlr.SAPLParser.EnvAttributeBasicContext;
import io.sapl.grammar.antlr.SAPLParser.EnvHeadAttributeBasicContext;
import io.sapl.grammar.antlr.SAPLParser.EscapedKeyDotStepContext;
import io.sapl.grammar.antlr.SAPLParser.ExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.FunctionBasicContext;
import io.sapl.grammar.antlr.SAPLParser.FunctionIdentifierContext;
import io.sapl.grammar.antlr.SAPLParser.GroupBasicContext;
import io.sapl.grammar.antlr.SAPLParser.HeadAttributeFinderDotStepContext;
import io.sapl.grammar.antlr.SAPLParser.IdentifierBasicContext;
import io.sapl.grammar.antlr.SAPLParser.ImportStatementContext;
import io.sapl.grammar.antlr.SAPLParser.KeyDotStepContext;
import io.sapl.grammar.antlr.SAPLParser.PolicyBodyContext;
import io.sapl.grammar.antlr.SAPLParser.SaplContext;
import io.sapl.grammar.antlr.SAPLParser.SaplIdContext;
import io.sapl.grammar.antlr.SAPLParser.SchemaStatementContext;
import io.sapl.grammar.antlr.SAPLParser.StepContext;
import io.sapl.grammar.antlr.SAPLParser.UnaryExpressionContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionContext;
import io.sapl.grammar.antlr.SAPLParser.ValueDefinitionStatementContext;
import io.sapl.lsp.configuration.LSPConfiguration;
import io.sapl.lsp.sapl.evaluation.ExpressionEvaluator;
import io.sapl.lsp.sapl.util.TreeNavigationUtil;
import lombok.experimental.UtilityClass;

/**
 * Resolves potential schemas associated with expressions in SAPL documents.
 * Schemas can come from:
 * - Document-level schema statements for subscription elements
 * - Value definitions with explicit schema declarations
 * - Function/attribute return types from documentation
 */
@UtilityClass
class ExpressionSchemaResolver {

    /**
     * Infers potential schemas for an expression by analyzing its structure.
     * The expression may be a basic identifier referencing a variable with a
     * schema,
     * a function call with a return type schema, or an attribute access.
     *
     * @param expression the expression to analyze
     * @param sapl the root SAPL parse tree
     * @param cursorOffset the current cursor offset for scope filtering
     * @param config the LSP configuration containing schemas
     * @return list of potential schemas for the expression
     */
    public List<JsonNode> inferPotentialSchemasOfExpression(ExpressionContext expression, SaplContext sapl,
            int cursorOffset, LSPConfiguration config) {
        return inferPotentialSchemasOfExpression(expression, sapl, cursorOffset, config, newVisitedSet());
    }

    private List<JsonNode> inferPotentialSchemasOfExpression(ExpressionContext expression, SaplContext sapl,
            int cursorOffset, LSPConfiguration config, Set<ValueDefinitionContext> visited) {
        if (expression == null) {
            return new ArrayList<>();
        }

        // Navigate down through lazyOr -> lazyAnd -> ... -> unaryExpression ->
        // basicExpression -> basic
        var basicExpression = findBasicExpression(expression);
        if (basicExpression == null) {
            return new ArrayList<>();
        }

        var basic = basicExpression.basic();
        if (basic == null) {
            return new ArrayList<>();
        }

        List<StepContext> steps;
        List<JsonNode>    baseSchemas;

        switch (basic) {
        case GroupBasicContext groupBasic                       -> {
            // Group may contain an expression with implicit schemas
            var innerExpression = groupBasic.basicGroup().expression();
            baseSchemas = inferPotentialSchemasOfExpression(innerExpression, sapl, cursorOffset, config, visited);
            steps       = extractStepsFromBasic(groupBasic);
        }
        case FunctionBasicContext functionBasic                 -> {
            // Function implementations may have schemas associated
            var functionCtx = functionBasic.basicFunction();
            baseSchemas = inferPotentialSchemasFromFunction(functionCtx.functionIdentifier(), sapl, config);
            steps       = functionCtx.step();
        }
        case EnvAttributeBasicContext envAttributeBasic         -> {
            // PIP implementations may have schemas associated
            var attrCtx = envAttributeBasic.basicEnvironmentAttribute();
            baseSchemas = inferPotentialSchemasFromAttributeFinder(attrCtx.functionIdentifier(), sapl, config);
            steps       = attrCtx.step();
        }
        case EnvHeadAttributeBasicContext envHeadAttributeBasic -> {
            // PIP implementations may have schemas associated
            var attrCtx = envHeadAttributeBasic.basicEnvironmentHeadAttribute();
            baseSchemas = inferPotentialSchemasFromAttributeFinder(attrCtx.functionIdentifier(), sapl, config);
            steps       = attrCtx.step();
        }
        case IdentifierBasicContext identifierBasic             -> {
            // Identifier may be a subscription element or value definition
            var identifierCtx = identifierBasic.basicIdentifier();
            var identifier    = getIdentifierText(identifierCtx.saplId());
            baseSchemas = inferPotentialSchemasFromIdentifier(identifier, sapl, cursorOffset, config, visited);
            steps       = identifierCtx.step();
        }
        default                                                 -> {
            // BasicValue, BasicRelative, BasicRelativeLocation - no schema
            return new ArrayList<>();
        }
        }

        return inferPotentialSchemasStepsAfterExpression(baseSchemas, steps, sapl, config);
    }

    /**
     * Infers schemas for a value definition, including explicit schema
     * declarations.
     *
     * @param valueDefinition the value definition
     * @param sapl the root SAPL parse tree
     * @param cursorOffset cursor offset for scope filtering
     * @param config the LSP configuration
     * @return list of potential schemas
     */
    public List<JsonNode> inferValueDefinitionSchemas(ValueDefinitionContext valueDefinition, SaplContext sapl,
            int cursorOffset, LSPConfiguration config) {
        return inferValueDefinitionSchemas(valueDefinition, sapl, cursorOffset, config, newVisitedSet());
    }

    private List<JsonNode> inferValueDefinitionSchemas(ValueDefinitionContext valueDefinition, SaplContext sapl,
            int cursorOffset, LSPConfiguration config, Set<ValueDefinitionContext> visited) {
        // Guard against mutually recursive value definitions (e.g. var a = b; var b =
        // a;) that would otherwise recurse without termination.
        if (!visited.add(valueDefinition)) {
            return new ArrayList<>();
        }

        // First infer schemas from the assigned expression
        var schemas = inferPotentialSchemasOfExpression(valueDefinition.eval, sapl, cursorOffset, config, visited);

        // Then add explicit schema declarations
        for (var schemaExpression : valueDefinition.schemaVarExpression) {
            ExpressionEvaluator.evaluateExpressionToJsonNode(schemaExpression, config).ifPresent(schemas::add);
        }

        return schemas;
    }

    private Set<ValueDefinitionContext> newVisitedSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private List<StepContext> extractStepsFromBasic(BasicContext basic) {
        // Different basic types have steps at different locations
        // For groups, the steps come from basicGroup
        if (basic instanceof GroupBasicContext groupBasic) {
            return groupBasic.basicGroup().step();
        }
        return List.of();
    }

    private BasicExpressionContext findBasicExpression(ExpressionContext expression) {
        // Navigate through expression hierarchy: expression -> lazyOr -> lazyAnd -> ...
        // -> basicExpression. At every binary level a single operand means no operator
        // was applied; more than one operand means the value is a binary expression and
        // must not inherit its left operand's schema.
        if (expression == null || expression.lazyOr() == null) {
            return null;
        }

        var lazyOr = expression.lazyOr();
        if (lazyOr.lazyAnd().size() != 1) {
            return null;
        }

        var lazyAnd = lazyOr.lazyAnd(0);
        if (lazyAnd.eagerOr().size() != 1) {
            return null;
        }

        var eagerOr = lazyAnd.eagerOr(0);
        if (eagerOr.exclusiveOr().size() != 1) {
            return null;
        }

        var exclusiveOr = eagerOr.exclusiveOr(0);
        if (exclusiveOr.eagerAnd().size() != 1) {
            return null;
        }

        var eagerAnd = exclusiveOr.eagerAnd(0);
        if (eagerAnd.equality().size() != 1) {
            return null;
        }

        var equality = eagerAnd.equality(0);
        if (equality.hasExpression().size() != 1) {
            return null;
        }

        var hasExpression = equality.hasExpression(0);
        if (hasExpression.comparison().size() != 1) {
            return null;
        }

        var comparison = hasExpression.comparison(0);
        if (comparison.addition().size() != 1) {
            return null;
        }

        var addition = comparison.addition(0);
        if (addition.multiplication().size() != 1) {
            return null;
        }

        var multiplication = addition.multiplication(0);
        if (multiplication.unaryExpression().size() != 1) {
            return null;
        }

        var unaryExpression = multiplication.unaryExpression(0);
        return extractBasicExpression(unaryExpression);
    }

    private BasicExpressionContext extractBasicExpression(UnaryExpressionContext unaryExpression) {
        // UnaryExpressionContext is a base class - only BasicExprContext has
        // basicExpression()
        if (unaryExpression instanceof BasicExprContext basicExpr) {
            return basicExpr.basicExpression();
        }
        return null;
    }

    private List<JsonNode> inferPotentialSchemasStepsAfterExpression(List<JsonNode> baseSchemas,
            List<StepContext> steps, SaplContext sapl, LSPConfiguration config) {
        if (steps.isEmpty()) {
            return baseSchemas;
        }

        var head       = steps.getFirst();
        var newSchemas = List.<JsonNode>of();

        if (head instanceof KeyDotStepContext keyDotStep) {
            // Navigate into property of object schema: subject.propertyName
            var propertyName = keyDotStep.keyStep().saplId().getText();
            newSchemas = navigateToProperty(baseSchemas, propertyName);
        } else if (head instanceof EscapedKeyDotStepContext escapedKeyStep) {
            // Navigate into property with escaped key: subject.'property name'
            var escapedText  = escapedKeyStep.escapedKeyStep().STRING().getText();
            var propertyName = unquoteString(escapedText);
            newSchemas = navigateToProperty(baseSchemas, propertyName);
        } else if (head instanceof BracketStepContext) {
            // Navigate into array items: subject[0] or subject[]
            newSchemas = navigateToArrayItems(baseSchemas);
        } else if (head instanceof AttributeFinderDotStepContext attrStep) {
            // Attribute access implies new schemas from PIP function annotations
            newSchemas = inferPotentialSchemasFromAttributeFinder(attrStep.attributeFinderStep().functionIdentifier(),
                    sapl, config);
        } else if (head instanceof HeadAttributeFinderDotStepContext headAttrStep) {
            newSchemas = inferPotentialSchemasFromAttributeFinder(
                    headAttrStep.headAttributeFinderStep().functionIdentifier(), sapl, config);
        }
        // Other step types (wildcard, recursive, etc.) lose schema association

        return inferPotentialSchemasStepsAfterExpression(newSchemas, tail(steps), sapl, config);
    }

    private List<JsonNode> navigateToProperty(List<JsonNode> schemas, String propertyName) {
        var result = new ArrayList<JsonNode>();
        for (var schema : schemas) {
            var propertySchema = getPropertySchema(schema, propertyName);
            if (propertySchema != null) {
                result.add(propertySchema);
            }
        }
        return result;
    }

    private JsonNode getPropertySchema(JsonNode schema, String propertyName) {
        if (schema == null || !schema.isObject()) {
            return null;
        }
        var properties = schema.get("properties");
        if (properties != null && properties.has(propertyName)) {
            return properties.get(propertyName);
        }
        return null;
    }

    private List<JsonNode> navigateToArrayItems(List<JsonNode> schemas) {
        var result = new ArrayList<JsonNode>();
        for (var schema : schemas) {
            var itemsSchema = getArrayItemsSchema(schema);
            if (itemsSchema != null) {
                result.add(itemsSchema);
            }
        }
        return result;
    }

    private JsonNode getArrayItemsSchema(JsonNode schema) {
        if (schema == null || !schema.isObject()) {
            return null;
        }
        var items = schema.get("items");
        if (items != null && items.isObject()) {
            return items;
        }
        return null;
    }

    private <T> List<T> tail(List<T> list) {
        if (list.size() <= 1) {
            return new ArrayList<>();
        }
        return list.subList(1, list.size());
    }

    private List<JsonNode> inferPotentialSchemasFromAttributeFinder(FunctionIdentifierContext identifier,
            SaplContext sapl, LSPConfiguration config) {
        var nameInUse    = joinIdFragments(identifier);
        var resolvedName = resolveImport(nameInUse, sapl);
        return lookupSchemasByName(resolvedName, config.getAttributeSchemas());
    }

    private List<JsonNode> inferPotentialSchemasFromFunction(FunctionIdentifierContext identifier, SaplContext sapl,
            LSPConfiguration config) {
        var nameInUse    = joinIdFragments(identifier);
        var resolvedName = resolveImport(nameInUse, sapl);
        return lookupSchemasByName(resolvedName, config.getFunctionSchemas());
    }

    private String joinIdFragments(FunctionIdentifierContext identifier) {
        return identifier.idFragment.stream().map(ExpressionSchemaResolver::getIdentifierText)
                .collect(Collectors.joining("."));
    }

    private String getIdentifierText(SaplIdContext saplId) {
        if (saplId == null) {
            return "";
        }
        return saplId.getText();
    }

    private List<JsonNode> lookupSchemasByName(String resolvedName, Map<String, JsonNode> schemasByCodeTemplate) {
        var discoveredSchemas = new ArrayList<JsonNode>();
        for (var schemaEntry : schemasByCodeTemplate.entrySet()) {
            // Match on the function identity, not on substring containment, so a short
            // name cannot pick up the schema of an unrelated longer-named function.
            if (qualifiedNameOf(schemaEntry.getKey()).equals(resolvedName)) {
                discoveredSchemas.add(schemaEntry.getValue());
            }
        }
        return discoveredSchemas;
    }

    private String qualifiedNameOf(String codeTemplate) {
        // Function templates read library.name(args); attribute templates read
        // <library.name(args)> or <library.name>. Strip decorations to the bare name.
        var name = codeTemplate;
        if (name.startsWith("<")) {
            name = name.substring(1);
        }
        var firstDecoration = indexOfFirstDecoration(name);
        if (firstDecoration >= 0) {
            name = name.substring(0, firstDecoration);
        }
        return name;
    }

    private int indexOfFirstDecoration(String name) {
        var parenIndex = name.indexOf('(');
        var angleIndex = name.indexOf('>');
        if (parenIndex < 0) {
            return angleIndex;
        }
        if (angleIndex < 0) {
            return parenIndex;
        }
        return Math.min(parenIndex, angleIndex);
    }

    private List<JsonNode> inferPotentialSchemasFromIdentifier(String identifier, SaplContext sapl, int cursorOffset,
            LSPConfiguration config, Set<ValueDefinitionContext> visited) {
        if (VariablesProposalsGenerator.AUTHORIZATION_SUBSCRIPTION_VARIABLES.contains(identifier)) {
            return inferSubscriptionElementSchema(identifier, sapl, config);
        }

        var schemas = new ArrayList<>(lookupSchemasOfMatchingValueDefinitionsInPolicySetHeader(identifier, sapl,
                cursorOffset, config, visited));
        schemas.addAll(
                lookupSchemasOfMatchingValueDefinitionsInPolicyBody(identifier, sapl, cursorOffset, config, visited));
        return schemas;
    }

    private List<JsonNode> lookupSchemasOfMatchingValueDefinitionsInPolicySetHeader(String identifier, SaplContext sapl,
            int cursorOffset, LSPConfiguration config, Set<ValueDefinitionContext> visited) {
        var schemas = new ArrayList<JsonNode>();

        var policyElement = sapl.policyElement();
        if (policyElement instanceof io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext policySetElement) {
            var policySet = policySetElement.policySet();
            for (var valueDefinition : policySet.valueDefinition()) {
                if (nameMatchesAndIsInScope(identifier, valueDefinition, cursorOffset)) {
                    schemas.addAll(inferValueDefinitionSchemas(valueDefinition, sapl, cursorOffset, config, visited));
                }
            }
        }

        return schemas;
    }

    private List<JsonNode> lookupSchemasOfMatchingValueDefinitionsInPolicyBody(String identifier, SaplContext sapl,
            int cursorOffset, LSPConfiguration config, Set<ValueDefinitionContext> visited) {
        var schemas      = new ArrayList<JsonNode>();
        var policyBodies = collectPolicyBodies(sapl);

        for (var policyBody : policyBodies) {
            schemas.addAll(
                    findMatchingValueDefinitionsInBody(identifier, policyBody, sapl, cursorOffset, config, visited));
        }
        return schemas;
    }

    private List<PolicyBodyContext> collectPolicyBodies(SaplContext sapl) {
        var policyBodies  = new ArrayList<PolicyBodyContext>();
        var policyElement = sapl.policyElement();
        if (policyElement == null) {
            return policyBodies;
        }
        if (policyElement instanceof io.sapl.grammar.antlr.SAPLParser.PolicyOnlyElementContext policyOnlyElement) {
            var policy = policyOnlyElement.policy();
            if (policy.policyBody() != null) {
                policyBodies.add(policy.policyBody());
            }
        } else if (policyElement instanceof io.sapl.grammar.antlr.SAPLParser.PolicySetElementContext policySetElement) {
            for (var policy : policySetElement.policySet().policy()) {
                if (policy.policyBody() != null) {
                    policyBodies.add(policy.policyBody());
                }
            }
        }
        return policyBodies;
    }

    private List<JsonNode> findMatchingValueDefinitionsInBody(String identifier, PolicyBodyContext policyBody,
            SaplContext sapl, int cursorOffset, LSPConfiguration config, Set<ValueDefinitionContext> visited) {
        var schemas = new ArrayList<JsonNode>();
        for (var statement : policyBody.statement()) {
            if (statement instanceof ValueDefinitionStatementContext valueDefStatement) {
                var valueDefinition = valueDefStatement.valueDefinition();
                if (nameMatchesAndIsInScope(identifier, valueDefinition, cursorOffset)) {
                    schemas.addAll(inferValueDefinitionSchemas(valueDefinition, sapl, cursorOffset, config, visited));
                }
            }
        }
        return schemas;
    }

    private boolean nameMatchesAndIsInScope(String identifier, ValueDefinitionContext definition, int cursorOffset) {
        var name = definition.name != null ? definition.name.getText() : null;
        return Objects.equals(name, identifier) && cursorOffset > TreeNavigationUtil.offsetOf(definition);
    }

    private List<JsonNode> inferSubscriptionElementSchema(String identifier, SaplContext sapl,
            LSPConfiguration config) {
        var schemas = new ArrayList<JsonNode>();
        for (var schemaStatement : sapl.schemaStatement()) {
            var subscriptionElement = getSubscriptionElementName(schemaStatement);
            if (Objects.equals(identifier, subscriptionElement)) {
                ExpressionEvaluator.evaluateExpressionToJsonNode(schemaStatement.schemaExpression, config)
                        .ifPresent(schemas::add);
            }
        }
        return schemas;
    }

    private String getSubscriptionElementName(SchemaStatementContext schemaStatement) {
        if (schemaStatement.subscriptionElement == null) {
            return null;
        }
        return schemaStatement.subscriptionElement.getText();
    }

    String resolveImport(String nameReference, SaplContext sapl) {
        if (nameReference.contains(".")) {
            return nameReference;
        }

        var imports = sapl.importStatement();
        if (imports == null || imports.isEmpty()) {
            return nameReference;
        }

        for (var currentImport : imports) {
            var importedFunction = fullyQualifiedFunctionName(currentImport);
            var alias            = currentImport.functionAlias != null ? currentImport.functionAlias.getText() : null;

            if (nameReference.equals(alias) || importedFunction.equals(nameReference)
                    || importedFunction.endsWith("." + nameReference)) {
                return importedFunction;
            }
        }

        return nameReference;
    }

    private String fullyQualifiedFunctionName(ImportStatementContext anImport) {
        // Import structure: library.steps.functionName
        var libSteps     = anImport.libSteps.stream().map(SaplIdContext::getText).collect(Collectors.joining("."));
        var functionName = anImport.functionName != null ? anImport.functionName.getText() : "";
        if (libSteps.isEmpty()) {
            return functionName;
        }
        return libSteps + "." + functionName;
    }

}
