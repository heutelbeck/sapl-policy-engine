package io.sapl.grammar.ide.contentassist;

import io.sapl.grammar.ide.contentassist.schema.SchemaProposals;
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
    public enum ProposalType {VALUE, SCHEMA}

    public Collection<String> getProposals(EObject model, ProposalType proposalType){
        int currentOffset = context.getOffset();
        var policyBody = (PolicyBody) getPolicyBody(model);

        if (policyBody == null && proposalType == ProposalType.SCHEMA)
            return getPreambleSchemaProposals();

        if (policyBody == null && authzSubProposals.contains(context.getPrefix()))
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
        var authzProposals = getAuthzProposals();
        proposals.addAll(authzProposals);
        return proposals;
    }

    private Collection<String> getAuthzProposals(){
        Collection<String> proposals = new HashSet<>();
        var schemaProposals = new SchemaProposals(variablesAndCombinatorSource);
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

    private Collection<String> getProposalsFromStatement(ProposalType proposalType, int currentOffset, Statement statement) {
        Collection<String> proposals = new HashSet<>();
        if (statement instanceof ValueDefinition valueDefStatement) {

            List<String> currentProposals;
            if (proposalType == ProposalType.SCHEMA)
                currentProposals = getSchemaFromStatement(currentOffset, valueDefStatement);
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

    private List<String> getSchemaFromStatement(int currentOffset, ValueDefinition statement) {
        List<String> proposalTemplates = new ArrayList<>();
        int valueDefinitionOffset = getValueDefinitionOffset(statement);

        var schemaVarExpression = statement.getSchemaVarExpression();

        if (currentOffset > valueDefinitionOffset && schemaVarExpression != null) {
            proposalTemplates = getProposalTemplates(statement, schemaVarExpression);
        }
        return proposalTemplates;
    }

    private List<String> getProposalTemplates(ValueDefinition valueDefinition, Iterable<Expression> schemaVarExpression) {
        List<String> proposalTemplates = new ArrayList<>();
        for (Expression varExpression: schemaVarExpression) {
            var schemaTemplates = new SchemaProposals(variablesAndCombinatorSource)
                    .getCodeTemplates(varExpression);
            var valueDefinitionName = valueDefinition.getName();
            var templates = constructProposals(valueDefinitionName, schemaTemplates);
            proposalTemplates.addAll(templates);
        }
        return proposalTemplates;
    }

    private static Collection<String> constructProposals(String elementName, Iterable<String> templates) {
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
