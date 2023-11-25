/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.google.common.collect.Iterables;
import io.sapl.grammar.ide.contentassist.schema.SchemaProposals;
import io.sapl.grammar.sapl.*;
import io.sapl.grammar.sapl.impl.util.FunctionUtil;
import io.sapl.grammar.sapl.impl.util.ImportsUtil;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RequiredArgsConstructor
public class ValueDefinitionProposalExtractionHelper {

    private final VariablesAndCombinatorSource variablesAndCombinatorSource;
    private final FunctionContext              functionContext;
    private final AttributeContext             attributeContext;
    private final ContentAssistContext         context;

    public Collection<String> getProposals(EObject model, ProposalType proposalType) {
        int currentOffset = context.getOffset();
        var policyBody    = getPolicyBody(model);

        if (policyBody == null && proposalType == ProposalType.SCHEMA)
            return getPreambleSchemaProposals();

        if (policyBody == null)
            return new HashSet<>();

        return getBodyProposals(proposalType, currentOffset, policyBody, model);
    }

    public ContentAssistContext getContextWithFixedPrefix(int offset) {

        var modifiedContext = context.copy().toContext();
        var model = context.getCurrentModel();
        if (!(model instanceof PolicyBody)) {
            model = TreeNavigationHelper.goToFirstParent(model, PolicyBody.class);
        }

        if (model instanceof PolicyBody policyBody) {
            var statements = policyBody.getStatements();
            EObject currentStatement = statements.get(statements.size()-1);

            for(var aStatement : statements){
                var statementOffset = getValueDefinitionOffset(aStatement);
                if(statementOffset <= offset){
                    currentStatement = aStatement;
                }
            }

            if (currentStatement instanceof Condition condition){
                var expression = condition.getExpression();
                if (expression instanceof BasicIdentifier basicIdentifier){
                    var identifier  = basicIdentifier.getIdentifier();
                    var keySteps  = combineKeystepsFromBasicIdentifier(basicIdentifier);
                    var fullPrefix = getFunctionName(keySteps, identifier);
                    modifiedContext = context.copy().setPrefix(fullPrefix).toContext();
                }
            } else if (currentStatement instanceof ValueDefinition valueDefinition && valueDefinition.getEval() instanceof BasicIdentifier basicIdentifier){
                    var identifier  = basicIdentifier.getIdentifier();
                    var keySteps  = combineKeystepsFromBasicIdentifier(basicIdentifier);
                    var fullPrefix = getFunctionName(keySteps, identifier);
                    modifiedContext = context.copy().setPrefix(fullPrefix).toContext();
            }
        }
        return modifiedContext;
    }

    public List<String> getAttributeProposals() {
/*        List<String> proposalsWithPrefix = new LinkedList<>();
        String identifier = "";
        var model = context.getCurrentModel();*/
        //List<String> proposals = new LinkedList<>(attributeContext.getAttributeCodeTemplates());
        List<String> proposals = new LinkedList<>();
        var schemaProposals = new SchemaProposals(variablesAndCombinatorSource);
        var allSchemas      = attributeContext.getAttributeSchemas();

        for (var entry : allSchemas.entrySet()) {
            var paths = schemaProposals.schemaTemplatesForAttributes(entry.getValue());
            for (var path : paths) {
                if (!"".equals(path)) {
                    var    fun              = entry.getKey();
                    var    allTemplates     = attributeContext.getAttributeCodeTemplates();
                    String fullFunctionName = getFullFunctionName(fun, allTemplates);
                    var    proposal         = String.join(".", fullFunctionName, path);
                    proposals.add(proposal);
                }
            }
        }

/*        if (!(model instanceof PolicyBody)) {
            model = TreeNavigationHelper.goToFirstParent(model, PolicyBody.class);
        }

        if (model instanceof PolicyBody policyBody) {
            var statements = policyBody.getStatements();

            for (var aStatement : statements) {
                if (aStatement instanceof Condition condition) {
                    var expression = condition.getExpression();
                    if (expression instanceof BasicIdentifier basicIdentifier) {
                        identifier = basicIdentifier.getIdentifier();
                    }
                } else if (aStatement instanceof ValueDefinition valueDefinition && valueDefinition.getEval() instanceof BasicIdentifier basicIdentifier) {
                    identifier = basicIdentifier.getIdentifier();
                }
            }
        }

        for (var proposal : proposals){
            proposalsWithPrefix.add(identifier.concat(".").concat(proposal));
        }*/
        return proposals;
    }

    public List<String> getFunctionProposals() {

        var proposals       = new LinkedList<String>();
        var schemaProposals = new SchemaProposals(variablesAndCombinatorSource);
        var allSchemas      = functionContext.getFunctionSchemas();
        var allSchemaPaths = functionContext.getFunctionSchemaPaths();
        for (var path : allSchemaPaths.entrySet()) {
            String schema = fetchSchemaFromPath(path.getValue());
            if (!schema.isEmpty()){
                allSchemas.put(path.getKey(), schema);
            }
        }
        for (var entry : allSchemas.entrySet()) {
            var paths = schemaProposals.schemaTemplatesForFunctions(entry.getValue());
            for (var path : paths) {
                if (!"".equals(path)) {
                    var    fun              = entry.getKey();
                    var    allTemplates     = functionContext.getCodeTemplates();
                    String fullFunctionName = getFullFunctionName(fun, allTemplates);
                    if (!fullFunctionName.isBlank()) {
                        var proposal = String.join(".", fullFunctionName, path);
                        proposals.add(proposal);
                    }
                }
            }
        }
        return proposals;
    }

    private static String fetchSchemaFromPath(String path) {
        String schema;
        Path file = Paths.get(path);
        try {
            schema = Files.readString(file);
        } catch (IOException e) {
            return "";
        }
        return schema;
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

    private static Collection<String> constructProposals(String elementName, Iterable<String> templates) {
        Collection<String> proposals = new HashSet<>();
        if (Iterables.isEmpty(templates))
            return proposals;
        for (var template : templates) {
            if (template.isBlank())
                continue;
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

    private Collection<String> getPreambleSchemaProposals() {
        return new SchemaProposals(variablesAndCombinatorSource).getVariableNamesAsTemplates();
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
        var                schemaProposals = new SchemaProposals(variablesAndCombinatorSource);
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
                currentProposals = getSchemaFromStatement(currentOffset, valueDefStatement, model);
            else
                currentProposals = getValueFromStatement(currentOffset, valueDefStatement);

            proposals.addAll(currentProposals);
        }
        return proposals;
    }

    private List<String> getValueFromStatement(int currentOffset, ValueDefinition statement) {
        List<String> valueList             = new ArrayList<>();
        int          valueDefinitionOffset = getValueDefinitionOffset(statement);

        if (currentOffset > valueDefinitionOffset) {
            String valueDefinitionName = statement.getName();
            valueList.add(valueDefinitionName);
        }
        return valueList;
    }

    private List<String> getSchemaFromStatement(int currentOffset, ValueDefinition statement, EObject model) {
        List<String> proposalTemplates;
        List<String> functionSchemaTemplates  = List.of();
        List<String> attributeSchemaTemplates = List.of();
        int          valueDefinitionOffset    = getValueDefinitionOffset(statement);

        var schemaVarExpression = statement.getSchemaVarExpression();

        // try to move up to the policy body
        if (model instanceof Condition || model instanceof AttributeFinderStep) {
            model = TreeNavigationHelper.goToFirstParent(model, PolicyBody.class);
        }

        // look up all defined variables in the policy
        if (model instanceof PolicyBody policyBody) {
            // iterate through defined statements which are either conditions or
            // variables
            for (var aStatement : policyBody.getStatements()) {
                // add any encountered valuable to the list of proposals
                if (currentOffset > valueDefinitionOffset && aStatement instanceof ValueDefinition valueDefinition) {
                    if (valueDefinition.getEval() instanceof BasicIdentifier basicIdentifier) {
                        // A function or attribute is assigned to a variable name. Proposals for the
                        // variable name.
                        var stepsString = combineKeystepsFromBasicIdentifier(basicIdentifier);
                        var identifier  = basicIdentifier.getIdentifier();
                        functionSchemaTemplates  = getFunctionSchemaTemplates(stepsString, identifier);
                        attributeSchemaTemplates = getAttributeSchemaTemplates(stepsString);
                    } else if (valueDefinition.getEval() instanceof BasicFunction basicFunction) {
                        // Proposals for a function name
                        var identifier  = basicFunction.getFsteps().get(0);
                        var stepsString = combineFstepsFromBasicFunction(basicFunction);
                        functionSchemaTemplates = getFunctionSchemaTemplates(stepsString, identifier);
                    } else {
                        break;
                    }
                }
            }
        }
        proposalTemplates = getProposalTemplates(statement, schemaVarExpression);

        var valueDefinitionName = statement.getName();
        var allTemplates        = new LinkedList<>(functionSchemaTemplates);
        allTemplates.addAll(attributeSchemaTemplates);
        var functionTemplates = constructProposals(valueDefinitionName, allTemplates);
        proposalTemplates.addAll(functionTemplates);
        proposalTemplates.addAll(functionSchemaTemplates);
        proposalTemplates.addAll(attributeSchemaTemplates);

        return proposalTemplates;
    }

    private List<String> getFunctionSchemaTemplates(List<String> stepsString, String identifier) {
        List<String> functionSchemaTemplates;
        var          imports        = ImportsUtil.fetchImports(getSapl(), attributeContext, functionContext);
        var          name           = getFunctionName(stepsString, identifier, imports);
        var          absName        = FunctionUtil.resolveAbsoluteFunctionName(name, imports);
        var          allSchemas      = functionContext.getFunctionSchemas();
        var          allSchemaPaths = functionContext.getFunctionSchemaPaths();
        for (var path : allSchemaPaths.entrySet()) {
            String schema = fetchSchemaFromPath(path.getValue());
            if (!schema.isEmpty()){
                allSchemas.put(path.getKey(), schema);
            }
        }
        var          functionSchema = allSchemas.get(absName);
        functionSchemaTemplates = new SchemaProposals(variablesAndCombinatorSource)
                .schemaTemplatesFromJson(functionSchema);
        return functionSchemaTemplates;
    }

    private List<String> getAttributeSchemaTemplates(List<String> stepsString) {
        List<String> attributeSchemaTemplates;
        var          imports        = ImportsUtil.fetchImports(getSapl(), attributeContext, functionContext);
        var          name           = getFunctionName(stepsString, "", imports);
        var          absName        = FunctionUtil.resolveAbsoluteFunctionName(name, imports);
        var          functionSchema = attributeContext.getAttributeSchemas().get(absName);
        attributeSchemaTemplates = new SchemaProposals(variablesAndCombinatorSource)
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

    private String getFunctionName(List<String> stepsString, String identifier) {
        var name = "";
        if (!stepsString.isEmpty()) {
            name = String.join(".", stepsString);
            if (!identifier.isEmpty()){
                name = identifier.concat(".").concat(name);
            }
            return name;
        }
        return identifier;
    }

    private List<String> combineKeystepsFromBasicIdentifier(BasicIdentifier basicIdentifier) {
        var stepsString = new LinkedList<String>();
        var steps       = basicIdentifier.getSteps();
        for (var step : steps) {
            if (step instanceof KeyStep keyStep) {
                stepsString.add(keyStep.getId());
            } else {
                // step is HeadAttributeFinderStep
                List<String> idSteps;
                if(step instanceof AttributeFinderStep attributeFinderStep) {
                    idSteps = attributeFinderStep.getIdSteps();
                } else {
                    idSteps = ((HeadAttributeFinderStep) step).getIdSteps();
                }
                var firstStep = idSteps.get(0);
                if(firstStep != null) {
                    var firstFinderElement = firstStep;
                    if (!firstStep.startsWith("<"))
                        firstFinderElement = "<".concat(firstStep);
                    idSteps.set(0, firstFinderElement);
                    stepsString.addAll(idSteps);
                }
            }
        }
        return stepsString;
    }

    private List<String> combineFstepsFromBasicFunction(BasicFunction basicFunction) {
        var stepsString = new LinkedList<String>();
        basicFunction.getFsteps().remove(0);
        var fsteps = basicFunction.getFsteps();
        stepsString.add(String.join(".", fsteps));
        return stepsString;
    }

    private List<String> getProposalTemplates(ValueDefinition valueDefinition,
            Iterable<Expression> schemaVarExpression) {
        List<String> proposalTemplates = new ArrayList<>();
        for (Expression varExpression : schemaVarExpression) {
            var schemaProposals     = new SchemaProposals(variablesAndCombinatorSource);
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
