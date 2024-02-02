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
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import com.google.common.collect.Iterables;

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
import lombok.experimental.UtilityClass;

@UtilityClass
public class ProposalExtractionUtil {

    public Collection<String> getProposals(EObject model, ProposalType proposalType, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        int currentOffset = context.getOffset();
        var policyBody    = getPolicyBody(model);

        if (policyBody == null && proposalType == ProposalType.SCHEMA)
            return getPreambleSchemaProposals(pdpConfiguration);

        if (policyBody == null)
            return new HashSet<>();

        return getBodyProposals(proposalType, currentOffset, policyBody, model, context, pdpConfiguration);
    }

    private Collection<String> getPreambleSchemaProposals(PDPConfiguration pdpConfiguration) {
        return pdpConfiguration.variables().keySet();
    }

    public List<String> getFunctionProposals(ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        var functionContext = pdpConfiguration.functionContext();
        var variables       = pdpConfiguration.variables();
        var proposals       = new LinkedList<String>();
        var allSchemas      = functionContext.getFunctionSchemas();
        for (var entry : allSchemas.entrySet()) {
            var fun              = entry.getKey();
            var allTemplates     = functionContext.getCodeTemplates();
            var fullFunctionName = getFullFunctionName(fun, allTemplates);
            var paths            = SchemaProposalGenerator.getCodeTemplates(fullFunctionName, entry.getValue(),
                    variables);
            proposals.addAll(paths);
        }
        return proposals;
    }

    public List<String> getAttributeProposals(PDPConfiguration pdpConfiguration) {
        var attributeContext = pdpConfiguration.attributeContext();
        var variables        = pdpConfiguration.variables();
        var proposals        = new LinkedList<String>();
        var allTemplates     = new LinkedList<String>();
        var allSchemas       = attributeContext.getAttributeSchemas();

        allTemplates.addAll(attributeContext.getAttributeCodeTemplates());
        allTemplates.addAll(attributeContext.getEnvironmentAttributeCodeTemplates());

        for (var entry : allSchemas.entrySet()) {
            var fun              = entry.getKey();
            var fullFunctionName = getFullFunctionName(fun, allTemplates);
            var paths            = SchemaProposalGenerator.getCodeTemplates(fullFunctionName, entry.getValue(),
                    variables);
            proposals.addAll(paths);
        }
        return proposals;
    }

    private static Collection<String> constructProposals(String elementName, Iterable<String> templates) {
        Collection<String> proposals = new HashSet<>();
        if (Iterables.isEmpty(templates))
            return proposals;
        for (var template : templates) {
            proposals.add(elementName.concat(template));
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
            return TreeNavigationUtil.goToFirstParent(model, PolicyBody.class);
        } else {
            return TreeNavigationUtil.goToLastParent(model, PolicyBody.class);
        }
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

    private Collection<String> getBodyProposals(ProposalType proposalType, int currentOffset, PolicyBody policyBody,
            EObject model, ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        Collection<String> proposals = new HashSet<>();
        for (var statement : policyBody.getStatements()) {
            var currentProposals = getProposalsFromStatement(proposalType, currentOffset, statement, model, context,
                    pdpConfiguration);
            proposals.addAll(currentProposals);
        }
        for (var s : proposals) {
            System.out.println("+++> '" + s + "'");
        }
        var authzProposals = getAuthzProposals(context, pdpConfiguration);
        proposals.addAll(authzProposals);
        return proposals;
    }

    private Collection<String> getAuthzProposals(ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        Collection<String> proposals   = new HashSet<>();
        var                saplSchemas = getSapl(context).getSchemas();

        for (var schema : saplSchemas) {
            var subscriptionElement = schema.getSubscriptionElement();
            var codeTemplates       = SchemaProposalGenerator.getCodeTemplates("", schema.getSchemaExpression(),
                    pdpConfiguration.variables());
            var templates           = constructProposals(subscriptionElement, codeTemplates);
            proposals.addAll(templates);
        }
        return proposals;
    }

    private SAPL getSapl(ContentAssistContext context) {
        return Objects.requireNonNullElse(TreeNavigationUtil.goToFirstParent(context.getCurrentModel(), SAPL.class),
                SaplFactory.eINSTANCE.createSAPL());
    }

    private Collection<String> getProposalsFromStatement(ProposalType proposalType, int currentOffset,
            Statement statement, EObject model, ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        Collection<String> proposals = new HashSet<>();
        if (statement instanceof ValueDefinition valueDefStatement) {

            List<String> currentProposals;
            if (proposalType == ProposalType.SCHEMA)
                currentProposals = getSchemaFromValueDefinitionStatement(currentOffset, valueDefStatement, model,
                        context, pdpConfiguration);
            else
                currentProposals = getValueFromStatement(currentOffset, valueDefStatement);

            proposals.addAll(currentProposals);
        } else {
            List<String> currentProposals;
            if (proposalType == ProposalType.SCHEMA) {
                currentProposals = getSchemaFromConditionStatement(currentOffset, model, context, pdpConfiguration);
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
            EObject model, ContentAssistContext context, PDPConfiguration pdpConfiguration) {
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
                var allSchemaTemplates = getAllSchemaTemplates(valueDefinition, context, pdpConfiguration);
                var proposals          = constructProposals(aStatementName, allSchemaTemplates);
                allTemplates.addAll(proposals);
            }
        }
        proposalTemplates = getProposalTemplates(statement, schemaVarExpression, pdpConfiguration);
        proposalTemplates.addAll(allTemplates);

        return proposalTemplates;
    }

    private List<String> getSchemaFromConditionStatement(int currentOffset, EObject model, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {

        List<String> allTemplates = new LinkedList<>();
        var          policyBody   = getPolicyBody(model);

        for (var aStatement : policyBody.getStatements()) {
            var statementOffset = getValueDefinitionOffset(aStatement);
            if (currentOffset > statementOffset && aStatement instanceof ValueDefinition valueDefinition) {
                var aStatementName     = valueDefinition.getName();
                var allSchemaTemplates = getAllSchemaTemplates(valueDefinition, context, pdpConfiguration);
                var proposals          = constructProposals(aStatementName, allSchemaTemplates);
                allTemplates.addAll(proposals);
            } else if (currentOffset > statementOffset && ((Condition) aStatement)
                    .getExpression() instanceof BasicEnvironmentAttribute basicEnvironmentAttribute) {
                var stepsString        = combineKeystepsFromBasicEnvironmentAttribute(basicEnvironmentAttribute);
                var allSchemaTemplates = getAttributeSchemaTemplates(stepsString, context, pdpConfiguration);
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

    private List<String> getAllSchemaTemplates(ValueDefinition valueDefinition, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        List<String> allTemplates             = new LinkedList<>();
        List<String> functionSchemaTemplates  = List.of();
        List<String> attributeSchemaTemplates = List.of();
        if (valueDefinition.getEval() instanceof BasicIdentifier basicIdentifier) {
            // A function or attribute is assigned to a variable name. Proposals for the
            // variable name.
            var stepsString = combineKeystepsFromBasicIdentifier(basicIdentifier);
            var identifier  = basicIdentifier.getIdentifier();
            functionSchemaTemplates  = getFunctionSchemaTemplates(stepsString, identifier, context, pdpConfiguration);
            attributeSchemaTemplates = getAttributeSchemaTemplates(stepsString, context, pdpConfiguration);
        } else if (valueDefinition.getEval() instanceof BasicFunction basicFunction) {
            // Proposals for a function name
            String       identifier;
            List<String> stepsString;
            identifier              = basicFunction.getFsteps().get(0);
            stepsString             = combineFstepsFromBasicFunction(basicFunction);
            functionSchemaTemplates = getFunctionSchemaTemplates(stepsString, identifier, context, pdpConfiguration);

        } else if (valueDefinition.getEval() instanceof BasicEnvironmentAttribute basicEnvironmentAttribute) {
            var stepsString = combineKeystepsFromBasicEnvironmentAttribute(basicEnvironmentAttribute);
            attributeSchemaTemplates = getAttributeSchemaTemplates(stepsString, context, pdpConfiguration);

        }
        allTemplates.addAll(functionSchemaTemplates);
        allTemplates.addAll(attributeSchemaTemplates);
        return allTemplates;
    }

    private List<String> getFunctionSchemaTemplates(List<String> stepsString, String identifier,
            ContentAssistContext context, PDPConfiguration pdpConfiguration) {
        List<String> functionSchemaTemplates;
        var          imports        = ImportsUtil.fetchImports(getSapl(context), pdpConfiguration.attributeContext(),
                pdpConfiguration.functionContext());
        var          name           = getFunctionName(stepsString, identifier, imports);
        var          absName        = FunctionUtil.resolveAbsoluteFunctionName(name, imports);
        var          allSchemas     = pdpConfiguration.functionContext().getFunctionSchemas();
        var          functionSchema = allSchemas.get(absName);
        functionSchemaTemplates = SchemaProposalGenerator.getCodeTemplates("", functionSchema,
                pdpConfiguration.variables());
        return functionSchemaTemplates;
    }

    private List<String> getAttributeSchemaTemplates(List<String> stepsString, ContentAssistContext context,
            PDPConfiguration pdpConfiguration) {
        List<String> attributeSchemaTemplates;
        var          imports        = ImportsUtil.fetchImports(getSapl(context), pdpConfiguration.attributeContext(),
                pdpConfiguration.functionContext());
        var          name           = getFunctionName(stepsString, "", imports);
        var          absName        = FunctionUtil.resolveAbsoluteFunctionName(name, imports);
        var          functionSchema = pdpConfiguration.attributeContext().getAttributeSchemas().get(absName);
        attributeSchemaTemplates = SchemaProposalGenerator.getCodeTemplates("", functionSchema,
                pdpConfiguration.variables());

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

    private List<String> getProposalTemplates(ValueDefinition valueDefinition, Iterable<Expression> schemaVarExpression,
            PDPConfiguration pdpConfiguration) {
        List<String> proposalTemplates = new ArrayList<>();
        for (Expression varExpression : schemaVarExpression) {
            var schemaTemplates     = SchemaProposalGenerator.getCodeTemplates("", varExpression,
                    pdpConfiguration.variables());
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
