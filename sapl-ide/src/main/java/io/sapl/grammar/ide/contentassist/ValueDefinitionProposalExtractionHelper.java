/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com.google.common.collect.Iterables;

import io.sapl.grammar.ide.contentassist.schema.SchemaProposals;
import io.sapl.grammar.sapl.BasicEnvironmentAttribute;
import io.sapl.grammar.sapl.BasicFunction;
import io.sapl.grammar.sapl.BasicIdentifier;
import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.KeyStep;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.grammar.sapl.SaplFactory;
import io.sapl.grammar.sapl.Statement;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.grammar.sapl.impl.util.FunctionUtil;
import io.sapl.grammar.sapl.impl.util.ImportsUtil;
import io.sapl.pdp.config.PDPConfiguration;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ValueDefinitionProposalExtractionHelper {

    private final PDPConfiguration     pdpConfiguration;
    private final ContentAssistContext context;

    public Collection<String> getProposals(EObject model, ProposalType proposalType) {
        int currentOffset = context.getOffset();
        var policyBody    = getPolicyBody(model);

        if (policyBody == null && proposalType == ProposalType.SCHEMA)
            return getPreambleSchemaProposals();

        if (policyBody == null)
            return new HashSet<>();

        return getBodyProposals(proposalType, currentOffset, policyBody, model);
    }

    public ContentAssistContext getContextWithFullPrefix(int offset, boolean forAttribute) {

        var model = context.getCurrentModel();
        if (getPolicyBody(model) == null)
            return context;

        List<String> tokens = new LinkedList<>();
        addOldPrefixToTokens(context.getPrefix(), tokens);

        var rootNode = context.getRootNode();
        if (shouldReturnOriginalContext(offset))
            return context;

        int indexOfCurrentNode = findIndexOfCurrentNode(rootNode, offset);
        var currentNode        = NodeModelUtils.findLeafNodeAtOffset(rootNode, indexOfCurrentNode);
        var previousNode       = NodeModelUtils.findLeafNodeAtOffset(rootNode, indexOfCurrentNode - 1);

        if (lastCharacterBeforeCursorIsBlank(previousNode))
            return context;

        if (isNodeBeforeCursorFirstNode(rootNode, indexOfCurrentNode, currentNode)) {
            String newPrefix = getNewPrefix(currentNode.getText());
            return context.copy().setPrefix(newPrefix).toContext();
        }

        String newPrefix;

        if (forAttribute) {
            newPrefix = computeNewPrefix(rootNode, currentNode, tokens);
        } else {
            addTokensUntilDelimiter(rootNode, tokens, currentNode);
            newPrefix = getNewPrefix(tokens);
        }
        newPrefix = newPrefix.trim();

        return context.copy().setPrefix(newPrefix).toContext();
    }

    public List<String> getFunctionProposals() {

        var functionContext = pdpConfiguration.functionContext();
        var proposals       = new LinkedList<String>();
        var schemaProposals = new SchemaProposals(pdpConfiguration.variables());
        var allSchemas      = functionContext.getFunctionSchemas();
        for (var entry : allSchemas.entrySet()) {
            var paths = schemaProposals.schemaTemplatesForFunctions(entry.getValue());
            for (var path : paths) {
                var    fun              = entry.getKey();
                var    allTemplates     = functionContext.getCodeTemplates();
                String fullFunctionName = getFullFunctionName(fun, allTemplates);
                if (!fullFunctionName.isBlank()) {
                    var proposal = String.join(".", fullFunctionName, path);
                    proposals.add(proposal);
                }
            }
        }
        return proposals;
    }

    public List<String> getAttributeProposals() {
        var          attributeContext = pdpConfiguration.attributeContext();
        List<String> proposals        = new LinkedList<>();
        List<String> allTemplates     = new LinkedList<>();
        var          schemaProposals  = new SchemaProposals(pdpConfiguration.variables());
        var          allSchemas       = attributeContext.getAttributeSchemas();

        allTemplates.addAll(attributeContext.getAttributeCodeTemplates());
        allTemplates.addAll(attributeContext.getEnvironmentAttributeCodeTemplates());

        for (var entry : allSchemas.entrySet()) {
            var paths = schemaProposals.schemaTemplatesForAttributes(entry.getValue());
            for (var path : paths) {
                var fun              = entry.getKey();
                var fullFunctionName = getFullFunctionName(fun, allTemplates);
                var proposal         = String.join(".", fullFunctionName, path);
                proposals.add(proposal);
            }
        }
        return proposals;
    }

    private static Collection<String> constructProposals(String elementName, Iterable<String> templates) {
        Collection<String> proposals = new HashSet<>();
        if (Iterables.isEmpty(templates))
            return proposals;
        for (var template : templates) {
            String proposal;
            if (!template.startsWith("."))
                proposal = elementName.concat(".").concat(template);
            else
                proposal = elementName.concat(template);
            proposals.add(proposal);
        }
        return proposals;
    }

    private static int getValueDefinitionOffset(EObject statement) {
        INode valueDefinitionNode = NodeModelUtils.getNode(statement);
        return valueDefinitionNode.getOffset();
    }

    private static PolicyBody getPolicyBody(EObject model) {
        // try to move up to the policy body
        if (model.eContainer() instanceof Condition) {
            return TreeNavigationHelper.goToFirstParent(model, PolicyBody.class);
        } else {
            return TreeNavigationHelper.goToLastParent(model, PolicyBody.class);
        }
    }

    private String computeNewPrefix(INode rootNode, INode currentNode, List<String> tokens) {
        String currentNodeText = currentNode.getText();
        if (!context.getPrefix().equals(currentNodeText)) {
            if ("|<".equals(currentNodeText)) {
                tokens.add("<");
            } else {
                var req2 = (">").equals(currentNodeText);
                if (!currentNodeText.isBlank() && !req2) {
                    tokens.add(currentNodeText);
                } else {
                    var prevNodeOffset = currentNode.getEndOffset() - currentNode.getTotalLength();
                    currentNode = NodeModelUtils.findLeafNodeAtOffset(rootNode, prevNodeOffset - 1);
                }
            }
        }
        if (tokens.isEmpty() || !"<".equals(tokens.get(0))) {
            addTokensUntilDelimiter(rootNode, tokens, currentNode);
        }
        return getNewPrefix(tokens);
    }

    private void addOldPrefixToTokens(String oldPrefix, List<String> tokens) {
        if (!oldPrefix.isBlank()) {
            tokens.add(context.getPrefix());
        }
    }

    private String getNewPrefix(String text) {
        var newPrefix = text;
        if (newPrefix.startsWith("|<")) {
            newPrefix = newPrefix.replaceFirst("\\|<", "<");
        }
        return newPrefix;
    }

    private String getNewPrefix(List<String> tokens) {
        var sb = new StringBuilder(tokens.size());
        for (int j = tokens.size() - 1; j >= 0; j--) {
            sb.append(tokens.get(j));
        }
        return getNewPrefix(sb.toString());
    }

    private boolean shouldReturnOriginalContext(int offset) {
        INode prevNode;
        int   offsetOfPrevNode;
        var   rootNode       = context.getRootNode();
        var   currentNode    = NodeModelUtils.findLeafNodeAtOffset(rootNode, offset);
        var   lastNode       = NodeModelUtils.findLeafNodeAtOffset(rootNode, offset - 1);
        var   lengthLastNode = lastNode.getLength();
        if (!".".equals(lastNode.getText()) && !"<".equals(lastNode.getText())) {
            offsetOfPrevNode = offset - 1;
        } else if (currentNode != null && !".".equals(lastNode.getText())) {
            offsetOfPrevNode = offset - lengthLastNode - 1;
        } else
            offsetOfPrevNode = offset - 1;
        prevNode = NodeModelUtils.findLeafNodeAtOffset(context.getRootNode(), offsetOfPrevNode);
        return prevNode.getText().isBlank() || ";".equals(prevNode.getText());
    }

    private boolean lastCharacterBeforeCursorIsBlank(INode currentNode) {
        return currentNode.getText().isBlank();
    }

    private int findIndexOfCurrentNode(INode rootNode, int offset) {
        int   i           = offset;
        INode currentNode = NodeModelUtils.findLeafNodeAtOffset(rootNode, offset);

        while (currentNode == null) {
            i--;
            currentNode = NodeModelUtils.findLeafNodeAtOffset(rootNode, i);
        }

        return i;
    }

    private void addTokensUntilDelimiter(INode rootNode, List<String> tokens, INode leafNode) {
        String tokenText;
        String lastChar;
        var    currentNode = leafNode;
        currentNode = NodeModelUtils.findLeafNodeAtOffset(rootNode, currentNode.getEndOffset() - 1);
        tokenText   = NodeModelUtils.getTokenText(currentNode);
        var req1 = currentNode.getEndOffset() == context.getOffset();
        var req2 = context.getPrefix().equals(tokenText);
        if (!tokenText.isBlank() && !(req1 && req2))
            tokens.add(tokenText);
        do {
            currentNode = NodeModelUtils.findLeafNodeAtOffset(rootNode,
                    currentNode.getEndOffset() - currentNode.getLength() - 1);
            tokenText   = NodeModelUtils.getTokenText(currentNode);
            if (!tokenText.isBlank() && currentNode.getEndOffset() != this.context.getOffset())
                tokens.add(tokenText);
            else
                break;
            lastChar = NodeModelUtils.findLeafNodeAtOffset(rootNode, currentNode.getTotalOffset() - 1).getText();
        } while (!lastChar.isBlank() && !"<".equals(tokenText) && !"|<".equals(tokenText));
    }

    private boolean isNodeBeforeCursorFirstNode(ICompositeNode rootNode, int indexOfCurrentNode,
            ILeafNode currentNode) {
        return NodeModelUtils.findLeafNodeAtOffset(rootNode, indexOfCurrentNode - currentNode.getTotalLength())
                .getText().isBlank();
    }

    private String getFullFunctionName(String fun, Iterable<String> allTemplates) {
        String fullFunctionName = "";
        for (var template : allTemplates) {
            if (template.startsWith(fun)) {
                fullFunctionName = template;
                break;
            }
        }
        return fullFunctionName;
    }

    private Collection<String> getPreambleSchemaProposals() {
        return new SchemaProposals(pdpConfiguration.variables()).getVariableNamesAsTemplates();
    }

    private Collection<String> getBodyProposals(ProposalType proposalType, int currentOffset, PolicyBody policyBody,
            EObject model) {
        Collection<String> proposals = new HashSet<>();
        for (var statement : policyBody.getStatements()) {
            var currentProposals = getProposalsFromStatement(proposalType, currentOffset, statement, model);
            proposals.addAll(currentProposals);
        }
        var authzProposals = getAuthzProposals();
        proposals.addAll(authzProposals);
        return proposals;
    }

    private Collection<String> getAuthzProposals() {
        Collection<String> proposals       = new HashSet<>();
        var                schemaProposals = new SchemaProposals(pdpConfiguration.variables());
        var                saplSchemas     = getSapl().getSchemas();

        for (var schema : saplSchemas) {
            var subscriptionElement = schema.getSubscriptionElement();
            var codeTemplates       = schemaProposals.getCodeTemplates(schema.getSchemaExpression());
            var templates           = constructProposals(subscriptionElement, codeTemplates);
            proposals.addAll(templates);
        }
        return proposals;
    }

    private SAPL getSapl() {
        return Objects.requireNonNullElse(TreeNavigationHelper.goToFirstParent(context.getCurrentModel(), SAPL.class),
                SaplFactory.eINSTANCE.createSAPL());
    }

    private Collection<String> getProposalsFromStatement(ProposalType proposalType, int currentOffset,
            Statement statement, EObject model) {
        Collection<String> proposals = new HashSet<>();
        if (statement instanceof ValueDefinition valueDefStatement) {

            List<String> currentProposals;
            if (proposalType == ProposalType.SCHEMA)
                currentProposals = getSchemaFromValueDefinitionStatement(currentOffset, valueDefStatement, model);
            else
                currentProposals = getValueFromStatement(currentOffset, valueDefStatement);

            proposals.addAll(currentProposals);
        } else {
            List<String> currentProposals;
            if (proposalType == ProposalType.SCHEMA) {
                currentProposals = getSchemaFromConditionStatement(currentOffset, model);
                proposals.addAll(currentProposals);
            }
        }
        return proposals;
    }

    private List<String> getValueFromStatement(int currentOffset, ValueDefinition statement) {
        List<String> valueList             = new ArrayList<>();
        var          valueDefinitionOffset = getValueDefinitionOffset(statement);

        if (currentOffset > valueDefinitionOffset) {
            String valueDefinitionName = statement.getName();
            valueList.add(valueDefinitionName);
        }
        return valueList;
    }

    private List<String> getSchemaFromValueDefinitionStatement(int currentOffset, ValueDefinition statement,
            EObject model) {
        var valueDefinitionOffset = getValueDefinitionOffset(statement);
        if (currentOffset <= valueDefinitionOffset)
            return new LinkedList<>();

        List<String> proposalTemplates;
        List<String> allTemplates        = new LinkedList<>();
        var          schemaVarExpression = statement.getSchemaVarExpression();
        var          policyBody          = getPolicyBody(model);

        for (var aStatement : policyBody.getStatements()) {
            var statementOffset = getValueDefinitionOffset(aStatement);
            if (currentOffset > statementOffset && aStatement instanceof ValueDefinition valueDefinition) {
                var aStatementName     = valueDefinition.getName();
                var allSchemaTemplates = getAllSchemaTemplates(valueDefinition);
                var proposals          = constructProposals(aStatementName, allSchemaTemplates);
                allTemplates.addAll(proposals);
            }
        }
        proposalTemplates = getProposalTemplates(statement, schemaVarExpression);
        proposalTemplates.addAll(allTemplates);

        return proposalTemplates;
    }

    private List<String> getSchemaFromConditionStatement(int currentOffset, EObject model) {

        List<String> allTemplates = new LinkedList<>();
        var          policyBody   = getPolicyBody(model);

        for (var aStatement : policyBody.getStatements()) {
            var statementOffset = getValueDefinitionOffset(aStatement);
            if (currentOffset > statementOffset && aStatement instanceof ValueDefinition valueDefinition) {
                var aStatementName     = valueDefinition.getName();
                var allSchemaTemplates = getAllSchemaTemplates(valueDefinition);
                var proposals          = constructProposals(aStatementName, allSchemaTemplates);
                allTemplates.addAll(proposals);
            } else if (currentOffset > statementOffset && ((Condition) aStatement)
                    .getExpression() instanceof BasicEnvironmentAttribute basicEnvironmentAttribute) {
                var stepsString        = combineKeystepsFromBasicEnvironmentAttribute(basicEnvironmentAttribute);
                var allSchemaTemplates = getAttributeSchemaTemplates(stepsString);
                var sb                 = new StringBuilder();
                sb.append('<');
                for (int i = 0; i < stepsString.size(); i++) {
                    String step = stepsString.get(i);
                    sb.append(step);
                    if (i < stepsString.size() - 1)
                        sb.append('.');
                    else
                        sb.append('>');
                }
                var elementName = sb.toString();
                var proposals   = constructProposals(elementName, allSchemaTemplates);
                allTemplates.addAll(proposals);
            }
        }
        return allTemplates;
    }

    private List<String> getAllSchemaTemplates(ValueDefinition valueDefinition) {
        List<String> allTemplates             = new LinkedList<>();
        List<String> functionSchemaTemplates  = List.of();
        List<String> attributeSchemaTemplates = List.of();
        if (valueDefinition.getEval() instanceof BasicIdentifier basicIdentifier) {
            // A function or attribute is assigned to a variable name. Proposals for the
            // variable name.
            var stepsString = combineKeystepsFromBasicIdentifier(basicIdentifier);
            var identifier  = basicIdentifier.getIdentifier();
            functionSchemaTemplates  = getFunctionSchemaTemplates(stepsString, identifier);
            attributeSchemaTemplates = getAttributeSchemaTemplates(stepsString);
        } else if (valueDefinition.getEval() instanceof BasicFunction basicFunction) {
            // Proposals for a function name
            String       identifier;
            List<String> stepsString;
            identifier              = basicFunction.getFsteps().get(0);
            stepsString             = combineFstepsFromBasicFunction(basicFunction);
            functionSchemaTemplates = getFunctionSchemaTemplates(stepsString, identifier);

        } else if (valueDefinition.getEval() instanceof BasicEnvironmentAttribute basicEnvironmentAttribute) {
            var stepsString = combineKeystepsFromBasicEnvironmentAttribute(basicEnvironmentAttribute);
            attributeSchemaTemplates = getAttributeSchemaTemplates(stepsString);

        }
        allTemplates.addAll(functionSchemaTemplates);
        allTemplates.addAll(attributeSchemaTemplates);
        return allTemplates;
    }

    private List<String> getFunctionSchemaTemplates(List<String> stepsString, String identifier) {
        List<String> functionSchemaTemplates;
        var          imports        = ImportsUtil.fetchImports(getSapl(), pdpConfiguration.attributeContext(),
                pdpConfiguration.functionContext());
        var          name           = getFunctionName(stepsString, identifier, imports);
        var          absName        = FunctionUtil.resolveAbsoluteFunctionName(name, imports);
        var          allSchemas     = pdpConfiguration.functionContext().getFunctionSchemas();
        var          functionSchema = allSchemas.get(absName);
        functionSchemaTemplates = new SchemaProposals(pdpConfiguration.variables())
                .schemaTemplatesFromJson(functionSchema);
        return functionSchemaTemplates;
    }

    private List<String> getAttributeSchemaTemplates(List<String> stepsString) {
        List<String> attributeSchemaTemplates;
        var          imports        = ImportsUtil.fetchImports(getSapl(), pdpConfiguration.attributeContext(),
                pdpConfiguration.functionContext());
        var          name           = getFunctionName(stepsString, "", imports);
        var          absName        = FunctionUtil.resolveAbsoluteFunctionName(name, imports);
        var          functionSchema = pdpConfiguration.attributeContext().getAttributeSchemas().get(absName);
        attributeSchemaTemplates = new SchemaProposals(pdpConfiguration.variables())
                .schemaTemplatesFromJson(functionSchema);
        return attributeSchemaTemplates;
    }

    private String getFunctionName(List<String> stepsString, String identifier, Map<String, String> imports) {
        var name = "";
        if (!stepsString.isEmpty()) {
            name = String.join(".", stepsString);
            if (!identifier.isEmpty())
                name = identifier.concat(".").concat(name);
        } else {
            name = FunctionUtil.resolveAbsoluteFunctionName(identifier, imports);
        }
        return name;
    }

    private List<String> combineKeystepsFromBasicIdentifier(BasicIdentifier basicIdentifier) {
        var stepsString = new LinkedList<String>();
        var steps       = basicIdentifier.getSteps();
        for (var step : steps) {
            var keyStep = (KeyStep) step;
            stepsString.add(keyStep.getId());
        }
        return stepsString;
    }

    private List<String> combineKeystepsFromBasicEnvironmentAttribute(
            BasicEnvironmentAttribute basicEnvironmentAttribute) {
        var steps = basicEnvironmentAttribute.getIdSteps();
        return new LinkedList<>(steps);
    }

    private List<String> combineFstepsFromBasicFunction(BasicFunction basicFunction) {
        var stepsString = new LinkedList<String>();
        var fSteps      = new LinkedList<>(basicFunction.getFsteps());
        fSteps.remove(0);
        stepsString.add(String.join(".", fSteps));
        return stepsString;
    }

    private List<String> getProposalTemplates(ValueDefinition valueDefinition,
            Iterable<Expression> schemaVarExpression) {
        List<String> proposalTemplates = new ArrayList<>();
        for (Expression varExpression : schemaVarExpression) {
            var schemaProposals     = new SchemaProposals(pdpConfiguration.variables());
            var schemaTemplates     = schemaProposals.getCodeTemplates(varExpression);
            var valueDefinitionName = valueDefinition.getName();

            var templates = constructProposals(valueDefinitionName, schemaTemplates);
            proposalTemplates.addAll(templates);
        }
        return proposalTemplates;
    }

    public enum ProposalType {
        VALUE, SCHEMA
    }

}
