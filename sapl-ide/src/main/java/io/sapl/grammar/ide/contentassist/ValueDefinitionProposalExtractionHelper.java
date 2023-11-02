package io.sapl.grammar.ide.contentassist;

import com.google.common.collect.Iterables;
import io.sapl.grammar.ide.contentassist.schema.SchemaProposals;
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
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import lombok.RequiredArgsConstructor;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RequiredArgsConstructor
public class ValueDefinitionProposalExtractionHelper {

    private final VariablesAndCombinatorSource variablesAndCombinatorSource;
    private final FunctionContext functionContext;
    private final AttributeContext attributeContext;
    private final ContentAssistContext context;

    public Collection<String> getProposals(EObject model, ProposalType proposalType) {
        int currentOffset = context.getOffset();
        var policyBody = getPolicyBody(model);

        if (policyBody == null && proposalType == ProposalType.SCHEMA)
            return getPreambleSchemaProposals();

        if (policyBody == null)
            return new HashSet<>();

        return getBodyProposals(proposalType, currentOffset, policyBody, model);
    }

    public List<String> getFunctionProposals(){
        var proposals = new LinkedList<String>();
        var schemaProposals = new SchemaProposals(variablesAndCombinatorSource);
        var allSchemas = functionContext.getFunctionSchemas();
        for (var entry : allSchemas.entrySet()){
            var paths = schemaProposals.schemaTemplatesForFunctions(entry.getValue());
            for (var path : paths){
                if (!"".equals(path)) {
                    var fun = entry.getKey();
                    var allTemplates = functionContext.getCodeTemplates();
                    String fullFunctionName = getFullFunctionName(fun, allTemplates);
                    if (!fullFunctionName.isBlank()){
                        var proposal = String.join(".", fullFunctionName, path);
                        proposals.add(proposal);
                    }
                }
            }
        }
        return proposals;
    }

    private String getFullFunctionName(String fun, List<String> allTemplates) {
        String fullFunctionName = "";
        for (var template : allTemplates){
            if (template.startsWith(fun)){
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

    private Collection<String> getPreambleSchemaProposals() {
        return new SchemaProposals(variablesAndCombinatorSource).getVariableNamesAsTemplates();
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

    private Collection<String> getAuthzProposals() {
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

    private Collection<String> getProposalsFromStatement(ProposalType proposalType, int currentOffset, Statement statement, EObject model) {
        Collection<String> proposals = new HashSet<>();
        if (statement instanceof ValueDefinition valueDefStatement) {

            List<String> currentProposals;
            if (proposalType == ProposalType.SCHEMA)
                currentProposals = getSchemaFromStatement(valueDefStatement, model);
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

    private List<String> getSchemaFromStatement(ValueDefinition statement, EObject model) {
        List<String> proposalTemplates;
        List<String> functionSchemaTemplates = new ArrayList<>();

        var schemaVarExpression = statement.getSchemaVarExpression();

        // try to move up to the policy body
        if (model instanceof Condition) {
            model = TreeNavigationHelper.goToFirstParent(model, PolicyBody.class);
        }

        // look up all defined variables in the policy
        if (model instanceof PolicyBody policyBody) {
            // iterate through defined statements which are either conditions or
            // variables
            for (var aStatement : policyBody.getStatements()) {
                // add any encountered valuable to the list of proposals
                if (aStatement instanceof ValueDefinition valueDefinition) {
                    if (valueDefinition.getEval() instanceof BasicIdentifier basicExpression) {
                        // A function is assigned to a variable name. Proposals for the variable name.
                        var stepsString = combineKeystepsFromBasicIdentifier(basicExpression);
                        var identifier = basicExpression.getIdentifier();
                        functionSchemaTemplates = getFunctionSchemaTemplates(stepsString, identifier);
                    } else if(valueDefinition.getEval() instanceof BasicFunction basicFunction){
                        // Proposals for a function name
                        var identifier = basicFunction.getFsteps().get(0);
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
        var functionTemplates = constructProposals(valueDefinitionName, functionSchemaTemplates);
        proposalTemplates.addAll(functionTemplates);
        proposalTemplates.addAll(functionSchemaTemplates);

        return proposalTemplates;
    }

    private List<String> getFunctionSchemaTemplates(List<String> stepsString, String identifier) {
        List<String> functionSchemaTemplates;
        var imports = ImportsUtil.fetchImports(getSapl(), attributeContext, functionContext);
        var name = getFunctionName(stepsString, identifier, imports);
        var absName = FunctionUtil.resolveAbsoluteFunctionName(name, imports);
        var functionSchema = functionContext.getFunctionSchemas().get(absName);
        functionSchemaTemplates = new SchemaProposals(variablesAndCombinatorSource).schemaTemplatesFromJson(functionSchema);
        return functionSchemaTemplates;
    }

    private String getFunctionName(List<String> stepsString, String identifier, Map<String, String> imports) {
        var name = "";
        if (!stepsString.isEmpty()) {
            name = String.join(".", stepsString);
            name = identifier.concat(".").concat(name);
        } else {
            name = FunctionUtil.resolveAbsoluteFunctionName(identifier, imports);
        }
        return name;
    }

    private List<String> combineKeystepsFromBasicIdentifier(BasicIdentifier basicIdentifier) {
        var stepsString = new LinkedList<String>();
        var steps = basicIdentifier.getSteps();
        for (var step : steps) {
            KeyStep keyStep = (KeyStep) step;
            stepsString.add(keyStep.getId());
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


    private List<String> getProposalTemplates(ValueDefinition valueDefinition, Iterable<Expression> schemaVarExpression) {
        List<String> proposalTemplates = new ArrayList<>();
        for (Expression varExpression : schemaVarExpression) {
            var schemaProposals = new SchemaProposals(variablesAndCombinatorSource);
            var schemaTemplates = schemaProposals.getCodeTemplates(varExpression);
            var valueDefinitionName = valueDefinition.getName();

            var templates = constructProposals(valueDefinitionName, schemaTemplates);
            proposalTemplates.addAll(templates);
        }
        return proposalTemplates;
    }

    public enum ProposalType {VALUE, SCHEMA}

}
