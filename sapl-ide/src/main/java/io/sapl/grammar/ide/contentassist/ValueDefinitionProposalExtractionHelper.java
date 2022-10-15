package io.sapl.grammar.ide.contentassist;

import io.sapl.grammar.sapl.*;
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
    private final ContentAssistContext context;

    private final Collection<String> authzSubProposals = Set.of("subject", "action", "resource", "environment");

    public enum ProposalType {VALUE, SCHEMA};

    public Collection<String> getProposals(ContentAssistContext context, EObject model, ProposalType proposalType){
        int currentOffset = context.getOffset();
        var policyBody = (PolicyBody) getPolicyBody(model);

        if (policyBody == null & proposalType == ProposalType.SCHEMA)
            return getPreambleSchemaProposals();

        if (policyBody == null & authzSubProposals.contains(context.getPrefix()))
            return getAuthzProposals();

        if (policyBody == null)
            return new HashSet<>();

        return getBodyProposals(proposalType, currentOffset, policyBody);
    }

    private Collection<String> getPreambleSchemaProposals(){
        return new SchemaProposals(variablesAndCombinatorSource).getVariableNamesAsTemplates();
    }

    private Collection<String> getBodyProposals(ProposalType proposalType, int currentOffset, PolicyBody policyBody) {
        Collection<String> proposals = new HashSet<>();
        for (var statement : policyBody.getStatements()) {
            var currentProposals = getProposalsFromStatement(proposalType, currentOffset, statement);
            proposals.addAll(currentProposals);
        }
        proposals.addAll(getAuthzProposals());
        return proposals;
    }

    private Collection<String> getAuthzProposals(){
        Collection<String> proposals = new HashSet<>();
        var schemaProposals = new SchemaProposals(variablesAndCombinatorSource);
        var saplSchemas = getSapl().getSchemas();
        
        for (var schema : saplSchemas) {
            var subscriptionElement = schema.getSubscriptionElement();
            var schemaExpression = schema.getSchemaExpression();
            var codeTemplates = schemaProposals.getCodeTemplates(schemaExpression);
            var templates = constructTemplates(subscriptionElement, codeTemplates);
            proposals.addAll(templates);
        }
        return proposals;
    }

    private SAPL getSapl() {
        var sapl = Objects.requireNonNullElse(
                TreeNavigationHelper.goToFirstParent(context.getCurrentModel(), SAPL.class),
                SaplFactory.eINSTANCE.createSAPL());
        return sapl;
    }

    private Collection<String> getProposalsFromStatement(ProposalType proposalType, int currentOffset, Statement statement) {
        Collection<String> proposals = new HashSet<>();
        if (statement instanceof ValueDefinition) {

            List<String> currentProposals;
            if (proposalType == ProposalType.SCHEMA)
                currentProposals = getSchemaFromStatement(currentOffset, (ValueDefinition) statement);
            else
                currentProposals = getValueFromStatement(currentOffset, (ValueDefinition) statement);

            proposals.addAll(currentProposals);
        }
        return proposals;
    }

/*    public Collection<String> getDefinedValues(ContentAssistContext context, EObject model) {

        Collection<String> definedValues = new HashSet<>();
        var policyBody = (PolicyBody) getPolicyBody(model);

        if (policyBody != null){
            int currentOffset = context.getOffset();

            for (var statement : policyBody.getStatements()) {
                if (statement instanceof ValueDefinition) {
                    var vals = getValueFromStatement(currentOffset, (ValueDefinition) statement);
                    definedValues.addAll(vals);
                }
            }
        }
        return definedValues;
    }

    public Collection<String> getDefinedSchemas(ContentAssistContext context, EObject model) {

        Collection<String> definedValues = new HashSet<>();
        var policyBody = (PolicyBody) getPolicyBody(model);

        if (policyBody != null){
            int currentOffset = context.getOffset();
            for (var statement : policyBody.getStatements()) {
                if (statement instanceof ValueDefinition) {
                    var schemaFromStatement = getSchemaFromStatement(currentOffset, (ValueDefinition) statement);
                    definedValues.addAll(schemaFromStatement);
                }
            }
        }
        return definedValues;
    }*/

    private List<String> getValueFromStatement(int currentOffset, ValueDefinition statement) {
        var valueDefinition = statement;
        List<String> valueList = new ArrayList<>();
        int valueDefinitionOffset = getValueDefinitionOffset(valueDefinition);

        if (currentOffset >= valueDefinitionOffset) {
            String valueDefinitionName = valueDefinition.getName();
            valueList.add(valueDefinitionName);
        }
        return valueList;
    }

    private List<String> getSchemaFromStatement(int currentOffset, ValueDefinition statement) {
        var valueDefinition = statement;
        List<String> proposalTemplates = new ArrayList<>();
        int valueDefinitionOffset = getValueDefinitionOffset(valueDefinition);

        var schemaVarExpression = valueDefinition.getSchemaVarExpression();

        if (currentOffset >= valueDefinitionOffset & schemaVarExpression != null) {
            proposalTemplates = getProposalTemplates(valueDefinition, schemaVarExpression);
        }
        return proposalTemplates;
    }

    private List<String> getProposalTemplates(ValueDefinition valueDefinition, Expression schemaVarExpression) {
        var schemaTemplates = new SchemaProposals(variablesAndCombinatorSource)
                .getCodeTemplates(schemaVarExpression);
        List<String> proposalTemplates = new ArrayList<>();
        var valueDefinitionName = valueDefinition.getName();
        var templates = constructTemplates(valueDefinitionName, schemaTemplates);
        proposalTemplates.addAll(templates);
        return proposalTemplates;
    }

    private static Collection<String> constructTemplates(String elementName, List<String> templates) {
        Collection<String> proposals = new HashSet<>();
        for(var template: templates){
            var proposal = elementName.concat(".").concat(template);
            proposals.add(proposal);
        }
        return proposals;
    }

    private static int getValueDefinitionOffset(ValueDefinition valueDefinition) {
        INode valueDefinitionNode = NodeModelUtils.getNode(valueDefinition);
        return valueDefinitionNode.getOffset();
    }

    private static EObject getPolicyBody(EObject model) {
        // try to move up to the policy body
        if (model.eContainer() instanceof Condition) {
            model = TreeNavigationHelper.goToFirstParent(model, PolicyBody.class);
        } else {
            model = TreeNavigationHelper.goToLastParent(model, PolicyBody.class);
        }
        return model;
    }

}
