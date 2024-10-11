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
import java.util.Set;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.google.common.base.Splitter;
import com.google.inject.Inject;

import io.sapl.grammar.ide.contentassist.NewLibraryProposalsGenerator.DocumentedProposal;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.ValueDefinition;
import io.sapl.grammar.services.SAPLGrammarAccess;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * This class enhances the auto-completion proposals that the language server
 * offers.
 */
@Slf4j
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
        } catch (ClassNotFoundException e) {
            return "";
        }
        final var requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            final var httpServletRequest = servletRequestAttributes.getRequest();
            final var query              = httpServletRequest.getQueryString();
            final var queryParameters    = Splitter.on('&').trimResults().withKeyValueSeparator('=').split(query);
            return queryParameters.getOrDefault("configurationId", "");
        }
        return "";
    }

    @Override
    protected void _createProposals(final Assignment assignment, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        lazyLoadDependencies();
        if (saplAccess.getBasicFunctionAccess().getIdentifierAssignment_0().equals(assignment)) {
            log.error("IdentifierAssignment {}",
                    saplAccess.getBasicFunctionAccess().getIdentifierAssignment_0().getFeature());
        } else if (saplAccess.getFunctionIdentifierAccess().getNameFragmentsAssignment_1().equals(assignment)) {
            log.info("getNameFragmentsAssignment_1");
            /* This is the start of a function name or a PIP name */
        } else if (saplAccess.getFunctionIdentifierAccess().getNameFragmentsAssignment_2_1().equals(assignment)) {
            log.info("getNameFragmentsAssignment_2_1");
            /* This is at least the second element of a function name or a PIP name */
            var n = context.getCurrentNode();
            this.dumpSiblings(context);
        }
        log.trace("Assignment: '{}' '{}' '{}' '{}'", assignment.getFeature(), assignment.getOperator(),
                assignment.getTerminal().eClass().getName(), context.getPrefix());

    }

    @Override
    protected void _createProposals(final RuleCall ruleCall, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        lazyLoadDependencies();

        if (saplAccess.getBasicFunctionAccess().getIdentifierFunctionIdentifierParserRuleCall_0_0().equals(ruleCall)) {
            log.error("FunctionIdentifierParserRuleCall {}",
                    saplAccess.getBasicFunctionAccess().getIdentifierAssignment_0().getFeature());
        }

        if (true)
            return;
        log.error("->{}", this.saplAccess);

        final var configurationId  = extractConfigurationIdFromRequest();
        final var pdpConfiguration = pdpConfigurationProvider.pdpConfiguration(configurationId).blockFirst();

//        dumpCurrentState(ruleCall, context);
        // dumpParents(context);
        /*
         * found no simple rule to detect this scenario. there are too many
         * false-positives. e.g. <time.xxx(<[CURSOR])> the offset will be at cursor, but
         * the current node is the ')'.
         */
//        if ( !(context.getCurrentNode() instanceof LeafNodeWithSyntaxError)&&!(context.getCurrentNode() instanceof HiddenLeafNode) && context.getOffset() < currentNode.getEndOffset()) {
//            var t = currentNode.getRootNode().getText();
//            log.trace(t.substring(0, context.getOffset()) + "~"
//                    + t.substring(context.getOffset(), currentNode.getEndOffset()) + "#"
//                    + t.substring(currentNode.getEndOffset(), t.length()));
//            /* Cursor not at end of token. No proposals! */
//            return;
//        }
        final var ruleName = ruleCall.getRule().getName();
        final var feature  = ruleCall.eContainingFeature().getName();

        log.trace("rule {} feature {} for {}", ruleName, feature, ruleCall.getRule().getClass().getSimpleName());

        switch (ruleName) {
        case "BasicIdentifier"                                            -> {
            // createBasicIdentifierProposals(context, acceptor, pdpConfiguration);
        }
        case "BasicFunction"                                              -> {
            // createBasicFunctionProposals(context, acceptor, pdpConfiguration);
        }
        case "BasicEnvironmentAttribute", "BasicEnvironmentHeadAttribute" -> {
            if (isInsideOfPolicyBody(context) && !isInsideOfSchemaExpression(context)) {
                /*
                 * Attribute access is only allowed in policy bodies after a 'where' keyword,
                 * but not in schema expressions of variable definitions.
                 */
                // createBasicEnvironmentAttributeProposals(context, acceptor,
                // pdpConfiguration);
            }
        }
        case "KeyStep", "EscapedKeyStep"                                  -> {
//            createKeyStepProposals(context, acceptor, pdpConfiguration);
        }
        case "AttributeFinderStep"                                        -> {
            // createAttributeFinderStepProposals(context, acceptor, pdpConfiguration);
        }
        case "Step"                                                       -> {
            //
        }
        case "SaplID"                                                     -> {
//            final var grammarElement  = context.getCurrentNode().getGrammarElement();
//            final var semanticElement = context.getCurrentNode().getSemanticElement();
//            log.trace("#S {} in container {}", semanticElement);
//            log.trace("#G {} in container {}", grammarElement);
            //
        }
        default                                                           -> {/* NOOP */}
        }
    }

    private void createAttributeFinderStepProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {
        // TODO Auto-generated method stub

    }

    private void createKeyStepProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {
        log.trace("creating key step proposals...");

    }

    private boolean isInsideOfPolicyBody(final ContentAssistContext context) {
        var node = context.getCurrentNode();
        do {
            if (node.getSemanticElement() instanceof PolicyBody) {
                return true;
            }
            node = node.getParent();
        } while (node != null);
        return false;
    }

    private boolean isInsideOfSchemaExpression(final ContentAssistContext context) {
        ValueDefinition valueDefinition = null;
        var             node            = context.getCurrentNode();
        do {
            if (node.getSemanticElement() instanceof ValueDefinition definition) {
                valueDefinition = definition;
                break;
            }
            node = node.getParent();
        } while (node != null);
        if (null == valueDefinition) {
            return false;
        }
        return !isChildOrEqual(context.getCurrentNode().getSemanticElement(), valueDefinition.getEval());
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

    private void createBasicEnvironmentAttributeProposals(ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        final var proposals = NewLibraryProposalsGenerator.allEnvironmentAttributeFinders(context, pdpConfiguration);
        if (context.getPrefix().startsWith("|")) {
            final var prefixAdjustedProposals = new ArrayList<DocumentedProposal>();
            proposals.forEach(proposal -> prefixAdjustedProposals.add(new DocumentedProposal("|" + proposal.proposal(),
                    "|" + proposal.label(), proposal.documentation())));
            addDocumentedProposals(prefixAdjustedProposals, context, acceptor);
        } else {
            addDocumentedProposals(proposals, context, acceptor);
        }
    }

    /*
     * This method generates proposals for an initial identifier
     *
     * Should return: subscription variables, previously defined variable names in
     * the document, names of environment variables
     */
    private void createBasicIdentifierProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {
        /*
         * Prefix is OK. BasicIdentifier only occurs at the beginning of a
         * BasicExpression.
         */
        addProposals(VariablesProposalsGenerator.variableProposalsForContext(context, pdpConfiguration), context,
                acceptor);
    }

    /*
     * This method generates all possible functions as proposals (based on the code
     * templates).
     */
    private void createBasicFunctionProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {
        /*
         * Prefix is OK. BasicFunction only occurs at the beginning of a
         * BasicExpression.
         */
        addDocumentedProposals(NewLibraryProposalsGenerator.allFunctions(context, pdpConfiguration), context, acceptor);
    }

    private void dumpParents(final RuleCall ruleCall) {
        log.trace("traverse to parents");
        EObject node = ruleCall;
        do {
            log.trace("- N '{}'", node);
// log.trace("  S '{}'", node.eContainer());
// log.trace(" G '{}'", node.getGrammarElement());
            node = node.eContainer();
        } while (node != null);
    }

    private void dumpParents(final ContentAssistContext context) {
        var node = context.getCurrentNode();
        do {
            // log.trace("- N '{}'", node);
            log.trace("  S '{}'", node.getSemanticElement());
            // log.trace(" G '{}'", node.getGrammarElement());
            node = node.getParent();
        } while (node != null);
    }

    private void dumpSiblings(final ContentAssistContext context) {
        var node = context.getCurrentNode();
        log.trace("traverse to previous siblings");
        do {
            // log.trace("+ N '{}'", node);
            log.trace("  S '{}'", node.getSemanticElement());
            // log.trace(" G '{}'", node.getGrammarElement());
            node = node.getPreviousSibling();
        } while (node != null);
    }

    private void dumpCurrentState(final RuleCall ruleCall, final ContentAssistContext context) {

        final var feature = ruleCall.eContainingFeature().getName();
        log.trace(
                "Prefix: '{}' Rule: '{}' CurrentModel: '{}' Feature: '{}' cFeature: '{}' eContainer rc: '{}' eContainer cn-sem: '{}' ",
                context.getPrefix(), ruleCall.getRule().getName(), context.getCurrentModel().eClass().getName(),
                ruleCall.eContainmentFeature().getName(), ruleCall.eContainer().eClass().getName(),
                context.getCurrentNode().getSemanticElement().eContainer().eClass().getName());
    }

    private void addProposals(final Collection<String> proposals, ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        proposals.forEach(proposal -> addProposal(proposal, context, acceptor));
    }

    private void addProposal(final String proposal, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        if (!proposal.startsWith(context.getPrefix())) {
            /*
             * Add this check even, if the acceptor already is filtering, because there are
             * proposals, that are accepted, if the prefix is present after a '.'.
             *
             * E.g.: "import time.* ...... where a" the acceptor would accept "time.after"
             * and display it.
             */
            return;
        }
        // log.trace("prefix: '{}' proposal: '{}'", context.getPrefix(), proposal);
        final var entry = getProposalCreator().createProposal(proposal, context);
        acceptor.accept(entry, 0);
    }

    private void addDocumentedProposals(Collection<DocumentedProposal> proposals, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        proposals.forEach(proposal -> addDocumentedProposal(proposal, context, acceptor));
    }

    private void addDocumentedProposal(DocumentedProposal proposal, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        if (!proposal.proposal().startsWith(context.getPrefix())) {
            /*
             * Add this check even, if the acceptor already is filtering, because there are
             * proposals, that are accepted, if the prefix is present after a '.'.
             *
             * E.g.: "import time.* ...... where a" the acceptor would accept "time.after"
             * and display it.
             */
            return;
        }

//        log.trace("prefix: '{}' proposal: '{}' - '{}' - '{}'", context.getPrefix(), proposal.proposal(),
//                proposal.label(), proposal.documentation());
        final var entry = getProposalCreator().createProposal(proposal.proposal(), context, e -> {
            e.setLabel(proposal.label());
            e.setDocumentation(proposal.documentation());
        });
        acceptor.accept(entry, 0);
    }

}
