package io.sapl.grammar.ide.contentassist;

import com.google.common.collect.Iterables;
import io.sapl.grammar.ide.contentassist.schema.SchemaProposals;
import io.sapl.grammar.sapl.*;
import io.sapl.grammar.sapl.impl.BasicValueImpl;
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

import java.util.*;

@RequiredArgsConstructor
public class ValueDefinitionProposalExtractionHelper {

    private final VariablesAndCombinatorSource variablesAndCombinatorSource;
    private final FunctionContext functionContext;
    private final AttributeContext attributeContext;
    private final ContentAssistContext context;
    private final Collection<String> authzSubProposals = Set.of("subject", "action", "resource", "environment");
    public enum ProposalType {VALUE, SCHEMA}

    public Collection<String> getProposals(EObject model, ProposalType proposalType){
        int currentOffset = context.getOffset();
        var policyBody = getPolicyBody(model);

        if (policyBody == null && proposalType == ProposalType.SCHEMA)
            return getPreambleSchemaProposals();

        if (policyBody == null && authzSubProposals.contains(context.getPrefix()))
            return getAuthzProposals();

        if (policyBody == null)
            return new HashSet<>();

        return getBodyProposals(proposalType, currentOffset, policyBody, model);
    }

    private Collection<String> getPreambleSchemaProposals(){
        return new SchemaProposals(variablesAndCombinatorSource, functionContext).getVariableNamesAsTemplates();
    }

    private Collection<String> getBodyProposals(ProposalType proposalType, int currentOffset, PolicyBody policyBody, EObject model) {
        Collection<String> proposals = new HashSet<>();
        for (var statement : policyBody.getStatements()) {
            var currentProposals = getProposalsFromStatement(proposalType, currentOffset, statement, model);
            proposals.addAll(currentProposals);
        }
        var authzProposals = getAuthzProposals();
        proposals.addAll(authzProposals);
        return proposals;
    }

    private Collection<String> getAuthzProposals(){
        Collection<String> proposals = new HashSet<>();
        var schemaProposals = new SchemaProposals(variablesAndCombinatorSource, functionContext);
        var saplSchemas = getSapl().getSchemas();
        
        for (var schema : saplSchemas) {
            var subscriptionElement = schema.getSubscriptionElement();
            var codeTemplates = schemaProposals.getCodeTemplates(schema.getSchemaExpression());
            var templates = constructProposals(subscriptionElement, codeTemplates);
            proposals.addAll(templates);
        }
        return proposals;
    }

    private SAPL getSapl() {
        return Objects.requireNonNullElse(
                TreeNavigationHelper.goToFirstParent(context.getCurrentModel(), SAPL.class),
                SaplFactory.eINSTANCE.createSAPL());
    }

    private Collection<String> getProposalsFromStatement(ProposalType proposalType, int currentOffset, Statement statement, EObject model) {
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
        List<String> valueList = new ArrayList<>();
        int valueDefinitionOffset = getValueDefinitionOffset(statement);

        if (currentOffset > valueDefinitionOffset) {
            String valueDefinitionName = statement.getName();
            valueList.add(valueDefinitionName);
        }
        return valueList;
    }

    private List<String> getSchemaFromStatement(int currentOffset, ValueDefinition statement, EObject model) {
        List<String> proposalTemplates = new ArrayList<>();
        List<String> functionSchemaTemplates = new ArrayList<>();
        int valueDefinitionOffset = getValueDefinitionOffset(statement);

        var schemaVarExpression = statement.getSchemaVarExpression();

        // try to move up to the policy body
        if (model instanceof Condition) {
            model = TreeNavigationHelper.goToFirstParent(model, PolicyBody.class);
        }

        // look up all defined variables in the policy
        if (model instanceof PolicyBody policyBody) {
            Collection<String> definedValuesPolicyBody = new HashSet<>();

            // iterate through defined statements which are either conditions or
            // variables
            for (var aStatement : policyBody.getStatements()) {
                // add any encountered valuable to the list of proposals
                if (aStatement instanceof ValueDefinition valueDefinition) {

                    // check if variable definition is happening after cursor
                    INode valueDefinitionNode   = NodeModelUtils.getNode(valueDefinition);
                    int aValueDefinitionOffset = valueDefinitionNode.getOffset();

                    if (currentOffset > aValueDefinitionOffset) {
                        if (((ValueDefinition) aStatement).getEval() instanceof BasicIdentifier) {
                            BasicIdentifier basicExpression = (BasicIdentifier) ((ValueDefinition) aStatement).getEval();
                            var identifier = basicExpression.getIdentifier();
                            var steps = basicExpression.getSteps();
                            var stepsString = new LinkedList<String>();
                            for (var step : steps) {
                                KeyStep keyStep = (KeyStep) step;
                                stepsString.add(keyStep.getId());
                            }
                            var imports = ImportsUtil.fetchImports(getSapl(), attributeContext, functionContext);
                            var name = "";
                            if (!stepsString.isEmpty()) {
                                name = String.join(".", stepsString);
                                name = identifier.concat(".").concat(name);
                            } else {
                                name = FunctionUtil.resolveAbsoluteFunctionName(identifier, imports);
                            }
                            var absName = FunctionUtil.resolveAbsoluteFunctionName(name, imports);
                            boolean isFunction = functionContext.isProvidedFunction(FunctionUtil.resolveAbsoluteFunctionName(name, imports));
                            if (isFunction) {
                                var allSchemas = functionContext.getFunctionSchemas();


                                var functionSchema = functionContext.getFunctionSchemas().get(absName);
                                functionSchemaTemplates = new SchemaProposals(variablesAndCombinatorSource, functionContext).schemaTemplatesFromJson(functionSchema);
                            }
                            definedValuesPolicyBody.add(valueDefinition.getName());
                        } else {
                            break;
                        }
                    }
                }
            }

            // add variables to list of proposals
            // addSimpleProposals(definedValuesPolicyBody, context, acceptor);
        }


        if (currentOffset > valueDefinitionOffset && schemaVarExpression != null) {
            proposalTemplates = getProposalTemplates(statement, schemaVarExpression);
        }

        var schemaProposals = new SchemaProposals(variablesAndCombinatorSource, functionContext);
        var eval = statement.getEval();
        var test = statement.toString();
        //var functionSchemaTemplates = schemaProposals.getFunctionSchemaTemplates(eval);
        var valueDefinitionName = statement.getName();
        //valueDefinition.getEval() - evaluieren, dann prüfen, ob so eine Fkt. im Fkt.context existiert, und wenn ja, ihr Schema rausholen und parsen
        var functionTemplates = constructProposals(valueDefinitionName, functionSchemaTemplates);
        proposalTemplates.addAll(functionTemplates);
        proposalTemplates.addAll(functionSchemaTemplates);


        return proposalTemplates;
    }

    private List<String> getProposalTemplates(ValueDefinition valueDefinition, Iterable<Expression> schemaVarExpression) {
        List<String> proposalTemplates = new ArrayList<>();
        for (Expression varExpression: schemaVarExpression) {
            var schemaProposals = new SchemaProposals(variablesAndCombinatorSource, functionContext);
            var schemaTemplates = schemaProposals.getCodeTemplates(varExpression);
            var valueDefinitionName = valueDefinition.getName();

            var templates = constructProposals(valueDefinitionName, schemaTemplates);
            proposalTemplates.addAll(templates);
        }
        return proposalTemplates;
    }

    private static Collection<String> constructProposals(String elementName, Iterable<String> templates) {
        Collection<String> proposals = new HashSet<>();
        if (Iterables.isEmpty(templates))
            return proposals;
        for(var template: templates){
            String proposal;
            if(!template.startsWith("."))
                proposal = elementName.concat(".").concat(template);
            else
                proposal = elementName.concat(template);
            proposals.add(proposal);
        }
        return proposals;
    }

    private static int getValueDefinitionOffset(ValueDefinition valueDefinition) {
        INode valueDefinitionNode = NodeModelUtils.getNode(valueDefinition);
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

}
