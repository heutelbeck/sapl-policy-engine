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

import static io.sapl.grammar.ide.contentassist.ExpressionSchemaResolver.offsetOf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;
import org.eclipse.xtext.nodemodel.INode;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.inject.Inject;

import io.sapl.grammar.ide.contentassist.ContextAnalyzer.ContextAnalysisResult;
import io.sapl.grammar.ide.contentassist.ContextAnalyzer.ProposalType;
import io.sapl.grammar.ide.contentassist.ProposalCreator.Proposal;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.BasicEnvironmentAttribute;
import io.sapl.grammar.sapl.BasicEnvironmentHeadAttribute;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.HeadAttributeFinderStep;
import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.grammar.services.SAPLGrammarAccess;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;

/**
 * This class enhances the auto-completion proposals that the language server
 * offers.
 */
public class SAPLContentProposalProvider extends IdeContentProposalProvider {

    private static final Collection<String> BLACKLIST_OF_KEYWORD_PROPOSALS = Set.of("null", "undefined", "true",
            "false");
    private static final Collection<String> WHITELIST_OF_KEYWORD_PROPOSALS = Set.of("as");

    private static final int MINIMUM_KEYWORD_LENGTH = 3;

    private PDPConfigurationProvider pdpConfigurationProvider;

    private SAPLGrammarAccess saplAccess;

    @Inject
    public void setService(SAPLGrammarAccess saplAccess) {
        this.saplAccess = saplAccess;
    }

    private void lazyLoadDependencies() {
        if (null == pdpConfigurationProvider) {
            pdpConfigurationProvider = getPDPConfigurationProvider();
        }
    }

    protected PDPConfigurationProvider getPDPConfigurationProvider() {
        return SpringContext.getBean(PDPConfigurationProvider.class);
    }

    /**
     * Here SAPL filters out very short and blacklisted keywords.
     */
    @Override
    protected boolean filterKeyword(final Keyword keyword, final ContentAssistContext context) {
        final var keywordValue = keyword.getValue();

        if (WHITELIST_OF_KEYWORD_PROPOSALS.contains(keywordValue)) {
            return true;
        }

        if ((keywordValue.length() < MINIMUM_KEYWORD_LENGTH) || BLACKLIST_OF_KEYWORD_PROPOSALS.contains(keywordValue)) {
            return false;
        }

        return super.filterKeyword(keyword, context);
    }

    /**
     * For a back end with multiple configurations, the HTTP request to the content
     * proposal provider can contain a query parameter 'configurationId' which
     * identifies a specific configuration.
     *
     * The content proposal provider uses this id to determine the associated
     * environment variables.
     *
     * These variables can now be proposed as code and also, they may contain values
     * for JSON schema definitions.
     *
     * @return the configurationId, or an empty String if not set.
     */
    private static String extractConfigurationIdFromRequest() {
        try {
            Class.forName("org.springframework.web.context.request.ServletRequestAttributes");
        } catch (final ClassNotFoundException e) {
            return "";
        }
        final var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof final ServletRequestAttributes servletRequestAttributes) {
            final var httpServletRequest = servletRequestAttributes.getRequest();
            final var query              = httpServletRequest.getQueryString();
            final var queryParameters    = Splitter.on('&').trimResults().withKeyValueSeparator('=').split(query);
            return queryParameters.getOrDefault("configurationId", "");
        }
        return "";
    }

    // @formatter:off
    private boolean isAttributeIdentifierAssignment(Assignment a, ContextAnalysisResult analysis) {
        return    analysis.type() == ProposalType.ATTRIBUTE
                && "+=".equals(a.getOperator())
                && isInsideOfPolicyBody(analysis.startNode())
                && !isInsideOfSchemaExpression(analysis.startNode())
                && (    saplAccess.getAttributeFinderStepAccess().getIdentifierAssignment_1().equals(a)
                        || saplAccess.getHeadAttributeFinderStepAccess().getIdentifierAssignment_1().equals(a)
                        || isIn(analysis.startNode(), AttributeFinderStep.class)
                        || isIn(analysis.startNode(), HeadAttributeFinderStep.class));
    }
    // @formatter:on

    // @formatter:off
    private boolean isEnvironmentAttributeIdentifierAssignment(Assignment a, ContextAnalysisResult analysis) {
        final var isAttributeAnalysis = analysis.type() == ProposalType.ENVIRONMENT_ATTRIBUTE;
        final var isInsideOfPolicyBody = isInsideOfPolicyBody(analysis.startNode());
        final var isNotInSchemaDefinition = !isInsideOfSchemaExpression(analysis.startNode());
        final var isPlusEqualOperator = "+=".equals(a.getOperator());
        final var isAssignmentToIdentifier =
                saplAccess.getBasicEnvironmentAttributeAccess().getIdentifierAssignment_2().equals(a)
                || saplAccess.getBasicEnvironmentHeadAttributeAccess().getIdentifierAssignment_2().equals(a);
        final var isSomewhereUnderEnvironmentAttributeNode =
                isIn(analysis.startNode(), BasicEnvironmentAttribute.class)
                || isIn(analysis.startNode(), BasicEnvironmentHeadAttribute.class);
        return    isAttributeAnalysis && isInsideOfPolicyBody && isNotInSchemaDefinition && isPlusEqualOperator
                && (isAssignmentToIdentifier || isSomewhereUnderEnvironmentAttributeNode);
    }
    // @formatter:on

    // @formatter:off
    private List<Assignment> functionIdentifierAssignments() {
        return List.of(saplAccess.getBasicFunctionAccess()     .getIdentifierAssignment_0(),
                saplAccess.getFunctionIdentifierAccess().getNameFragmentsAssignment_1(),
                saplAccess.getFunctionIdentifierAccess().getNameFragmentsAssignment_2_1());
    }
    // @formatter:on

    private boolean isFunctionIdentifierAssignment(Assignment a, ContextAnalysisResult analysis) {
        return analysis.type() == ProposalType.VARIABLE_OR_FUNCTION_NAME && functionIdentifierAssignments().contains(a);
    }

    private boolean isIn(INode startNode, Class<?> clazz) {
        if (null == startNode) {
            return false;
        }
        final var semanticElement = startNode.getSemanticElement();
        if (semanticElement == null) {
            return false;
        } else if (clazz.isAssignableFrom(semanticElement.getClass())) {
            return true;
        } else {
            return isIn(startNode.getParent(), clazz);
        }
    }

    private boolean isVariableAssignment(final Assignment assignment, final ContextAnalysisResult analysis) {
        return analysis.type() == ProposalType.VARIABLE_OR_FUNCTION_NAME
                && saplAccess.getBasicIdentifierAccess().getStepsAssignment_2().equals(assignment);
    }

    private boolean isEnvironmentAttributeSchemaExtension(ContextAnalysisResult analysis) {
        return analysis.type() == ProposalType.ENVIRONMENT_ATTRIBUTE;
    }

    private boolean isAttributeSchemaExtension(ContextAnalysisResult analysis) {
        return analysis.type() == ProposalType.ATTRIBUTE;
    }

    @Override
    protected void _createProposals(final Assignment assignment, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        lazyLoadDependencies();
        final var configurationId  = extractConfigurationIdFromRequest();
        final var pdpConfiguration = pdpConfigurationProvider.pdpConfiguration(configurationId).blockFirst();
        final var feature          = assignment.getFeature();
        final var parserRule       = GrammarUtil.containingParserRule(assignment).getName().toLowerCase();
        final var analysis         = ContextAnalyzer.analyze(context);
        if (null == pdpConfiguration) {
            return;
        }
        if (isAttributeIdentifierAssignment(assignment, analysis)) {
            this.addProposals(LibraryProposalsGenerator.allAttributeFinders(analysis, context, pdpConfiguration),
                    context, acceptor);
        } else if (isEnvironmentAttributeIdentifierAssignment(assignment, analysis)) {
            this.addProposals(
                    LibraryProposalsGenerator.allEnvironmentAttributeFinders(analysis, context, pdpConfiguration),
                    context, acceptor);
        } else if (isAttributeSchemaExtension(analysis)) {
            this.addProposals(
                    LibraryProposalsGenerator.allAttributeSchemaExtensions(analysis, context, pdpConfiguration),
                    context, acceptor);
        } else if (isEnvironmentAttributeSchemaExtension(analysis)) {
            this.addProposals(LibraryProposalsGenerator.allEnvironmentAttributeSchemaExtensions(analysis, context,
                    pdpConfiguration), context, acceptor);
        } else if (isFunctionIdentifierAssignment(assignment, analysis)) {
            this.addProposals(LibraryProposalsGenerator.allFunctions(analysis, context, pdpConfiguration), context,
                    acceptor);
        } else if (isVariableAssignment(assignment, analysis)) {
            this.addProposals(
                    VariablesProposalsGenerator.variableProposalsForContext(analysis, context, pdpConfiguration),
                    context, acceptor);
            this.createPolicyBodyInScopeVariableProposals(analysis, pdpConfiguration, context, acceptor);
        } else if (analysis.type() == ProposalType.FUNCTION) {
            this.addProposals(
                    LibraryProposalsGenerator.allFunctionSchemaExtensions(analysis, context, pdpConfiguration), context,
                    acceptor);
        } else if ("saplName".equals(feature)) {
            addProposal(ProposalCreator.createSimpleEntry("\"\"", context), context, acceptor);
        } else if ("import".equals(parserRule) && ("libSteps".equals(feature) || "functionName".equals(feature))) {
            createImportProposals(analysis, context, acceptor, pdpConfiguration);
        } else if ("sapl".equals(parserRule) && "schemas".equals(feature)) {
            VariablesProposalsGenerator.AUTHORIZATION_SUBSCRIPTION_VARIABLES
                    .forEach(s -> addProposal(ProposalCreator.createSimpleEntry(s, context), context, acceptor));
        } else if ("schema".equals(parserRule) && "schemaExpression".equals(feature)) {
            addProposals(LibraryProposalsGenerator.allFunctions(analysis, context, pdpConfiguration), context,
                    acceptor);
            addProposals(VariablesProposalsGenerator.variableProposalsForContext(analysis, context, pdpConfiguration),
                    context, acceptor);
        }
    }

    @Override
    protected void _createProposals(final RuleCall ruleCall, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        lazyLoadDependencies();
        final var configurationId  = extractConfigurationIdFromRequest();
        final var pdpConfiguration = pdpConfigurationProvider.pdpConfiguration(configurationId).blockFirst();
        final var analysis         = ContextAnalyzer.analyze(context);
        if (analysis.type() == ProposalType.VARIABLE_OR_FUNCTION_NAME
                && isInsideOf(context.getCurrentNode(), Policy.class)
                || isInsideOf(context.getCurrentNode(), PolicySet.class)) {
            addProposals(LibraryProposalsGenerator.allFunctions(analysis, context, pdpConfiguration), context,
                    acceptor);
            addProposals(VariablesProposalsGenerator.variableProposalsForContext(analysis, context, pdpConfiguration),
                    context, acceptor);
        }
    }

    /*
     * Extracts all ValueDefinitions from the current policy body in order and stops
     * when it finds the statement where the cursor currently resides in. All
     * variable names on the way are returned.
     */
    private void createPolicyBodyInScopeVariableProposals(ContextAnalysisResult analysis,
            PDPConfiguration pdpConfiguration, ContentAssistContext context, IIdeContentProposalAcceptor acceptor) {
        final var currentModel  = context.getCurrentModel();
        final var currentOffset = context.getOffset();
        final var policyBody    = TreeNavigationUtil.goToFirstParent(currentModel, PolicyBody.class);

        if (policyBody == null) {
            return;
        }
        for (final var statement : policyBody.getStatements()) {
            if (offsetOf(statement) >= currentOffset) {
                break;
            }
            if (statement instanceof final ValueDefinition valueDefinition) {
                createValueDefinitionProposalsWithSchemaExtensions(analysis, valueDefinition, pdpConfiguration, context,
                        acceptor);
            }
        }
    }

    /*
     * This method adds the variable name of the value and if the value definition
     * has an explicit schema declaration, the matching schema extensions are added.
     */
    private void createValueDefinitionProposalsWithSchemaExtensions(ContextAnalysisResult analysis,
            ValueDefinition valueDefinition, PDPConfiguration pdpConfiguration, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        final var variableName = valueDefinition.getName();
        if (Strings.isNullOrEmpty(variableName)) {
            return;
        }
        final var schemas = ExpressionSchemaResolver.inferValueDefinitionSchemas(valueDefinition, context,
                pdpConfiguration);
        for (final var schema : schemas) {
            final var proposals = SchemaProposalsGenerator.getCodeTemplates(variableName, schema,
                    pdpConfiguration.variables());
            proposals.forEach(
                    pText -> ProposalCreator.createNormalizedEntry(pText, analysis.prefix(), analysis.ctxPrefix())
                            .ifPresent(p -> addProposal(p, context, acceptor)));

        }
    }

    /*
     * Adds the fully qualified names and library names for attributes and functions
     * as completion options in import statements.
     */
    private void createImportProposals(ContextAnalysisResult analysis, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        final var attributeContext = pdpConfiguration.attributeContext();
        final var proposals        = new ArrayList<>(attributeContext.getAllFullyQualifiedFunctions());
        proposals.addAll(attributeContext.getAvailableLibraries());
        final var functionContext = pdpConfiguration.functionContext();
        proposals.addAll(functionContext.getAllFullyQualifiedFunctions());
        proposals.addAll(functionContext.getAvailableLibraries());
        if (analysis.prefix().endsWith(".")) {
            proposals.add(analysis.prefix() + "*");
        }
        proposals.forEach(pText -> ProposalCreator.createNormalizedEntry(pText, analysis.prefix(), analysis.ctxPrefix())
                .ifPresent(p -> addProposal(p, context, acceptor)));
    }

    private boolean isInsideOfPolicyBody(final INode n) {
        return isInsideOf(n, PolicyBody.class);
    }

    private boolean isInsideOf(final INode n, Class<?> clazz) {
        if (null == n) {
            return false;
        }
        var node = n;
        do {
            if (clazz.isAssignableFrom(node.getSemanticElement().getClass())) {
                return true;
            }
            node = node.getParent();
        } while (node != null);
        return false;
    }

    private boolean isInsideOfSchemaExpression(INode n) {
        if (null == n) {
            return false;
        }
        ValueDefinition valueDefinition = null;
        var             node            = n;
        do {
            if (node.getSemanticElement() instanceof final ValueDefinition definition) {
                valueDefinition = definition;
                break;
            }
            node = node.getParent();
        } while (node != null);
        if (null == valueDefinition) {
            return false;
        }
        return isInSchemaDefinitionsOfValueDefinition(n.getSemanticElement(), valueDefinition.getSchemaVarExpression());
    }

    private boolean isInSchemaDefinitionsOfValueDefinition(EObject needle, EList<Expression> schemata) {
        for (final var schema : schemata) {
            if (isChildOrEqual(needle, schema)) {
                return true;
            }
        }
        return false;
    }

    private boolean isChildOrEqual(EObject needle, EObject haystack) {
        if (null == haystack) {
            return false;
        }
        if (haystack == needle) {
            return true;
        }
        final var contents = haystack.eAllContents();
        while (contents.hasNext()) {
            if (contents.next() == haystack) {
                return true;
            }
        }
        return false;
    }

    private void addProposals(final Collection<Proposal> proposals, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        proposals.forEach(p -> addProposal(p, context, acceptor));
    }

    private void addProposal(final Proposal proposal, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        final var entry = getProposalCreator().createProposal(proposal.proposal(), context, p -> {
            p.setKind(proposal.kind());
            p.setLabel(proposal.label());
            p.setDocumentation(proposal.documentation());
        });
        acceptor.accept(entry, 0);
    }
}
