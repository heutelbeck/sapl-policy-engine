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
package io.sapl.grammar.ide.contentassist.removeme;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.eclipse.xtext.Assignment;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.TerminalRule;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistEntry;
import org.eclipse.xtext.ide.editor.contentassist.IIdeContentProposalAcceptor;
import org.eclipse.xtext.ide.editor.contentassist.IdeContentProposalProvider;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.google.common.base.Splitter;

import io.sapl.grammar.ide.contentassist.LibraryProposalsGenerator;
import io.sapl.grammar.ide.contentassist.LibraryProposalsGenerator.Proposal;
import io.sapl.grammar.ide.contentassist.NewLibraryProposalsGenerator;
import io.sapl.grammar.ide.contentassist.NewLibraryProposalsGenerator.DocumentedProposal;
import io.sapl.grammar.ide.contentassist.SpringContext;
import io.sapl.grammar.ide.contentassist.TreeNavigationUtil;
import io.sapl.grammar.ide.contentassist.VariablesProposalsGenerator;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.pdp.config.PDPConfiguration;
import io.sapl.pdp.config.PDPConfigurationProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * This class enhances the auto-completion proposals that the language server
 * offers.
 */
@Slf4j
public class OLDSAPLContentProposalProvider2 extends IdeContentProposalProvider {

    private static final Collection<String> BLACKLIST_OF_KEYWORD_PROPOSALS = Set.of("null", "undefined", "true",
            "false");
    private static final Collection<String> WHITELIST_OF_KEYWORD_PROPOSALS = Set.of("as");

    private static final int MINIMUM_KEYWORD_LENGTH = 3;

    private PDPConfigurationProvider pdpConfigurationProvider;

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
    protected void _createProposals(final RuleCall ruleCall, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        lazyLoadDependencies();

        final var configurationId  = extractConfigurationIdFromRequest();
        final var pdpConfiguration = pdpConfigurationProvider.pdpConfiguration(configurationId).blockFirst();

        final var currentNode = context.getCurrentNode();
        if (context.getOffset() < currentNode.getEndOffset()) {
            // log.trace("Cursor not at end of token. No proposals");
            return;
        }

        dumpCurrentState(ruleCall, context);

        final var ruleName = ruleCall.getRule().getName();
        switch (ruleName) {
        case "BasicIdentifier"                                            -> {
            createBasicIdentifierProposals(context, acceptor, pdpConfiguration);
        }
        case "BasicFunction"                                              -> {
            createBasicFunctionProposals(context, acceptor, pdpConfiguration);
        }
        case "BasicEnvironmentAttribute", "BasicEnvironmentHeadAttribute" -> {
            if (isInsideOfPolicyBody(ruleCall)) {
                /* Attribute access is only allowed in policy bodies after a 'where' keyword. */
                createBasicEnvironmentAttributeProposals(context, acceptor, pdpConfiguration);
            }
        }
        default                                                           -> {/* NOOP */}
        }
    }

    private boolean isInsideOfPolicyBody(final RuleCall ruleCall) {
        return null != TreeNavigationUtil.goToFirstParent(ruleCall, PolicyBody.class);
    }

    private void createBasicEnvironmentAttributeProposals(ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        final var proposals = NewLibraryProposalsGenerator.allAttributeFinders(context, pdpConfiguration);
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

    private void dumpCurrentState(final RuleCall ruleCall, final ContentAssistContext context) {
        final var feature = ruleCall.eContainingFeature().getName();

        log.trace("Rule    : '{}' - '{}'", context.getPrefix(), ruleCall.getRule().getName());
        log.trace("Feature : '{}'", feature);
//            log.trace("Offset  : '{}'", context.getOffset());
//            log.trace("RuleCall: '{}'", ruleCall);
//            log.trace("CurNode : '{}'", context.getCurrentNode());
//        log.trace("traverse to parents");
//        var node = context.getCurrentNode();
//        do {
//            // log.trace("- N '{}'", node);
//            log.trace("  S '{}'", node.getSemanticElement());
//            // log.trace(" G '{}'", node.getGrammarElement());
//            node = node.getParent();
//        } while (node != null);
//
//        node = context.getCurrentNode();
//        log.trace("traverse to previous siblings");
//        do {
//            // log.trace("+ N '{}'", node);
//            log.trace("  S '{}'", node.getSemanticElement());
//            // log.trace(" G '{}'", node.getGrammarElement());
//            node = node.getPreviousSibling();
//        } while (node != null);
//        log.trace("------");
    }

    /*
     * This method generates the domain specific recommendations for SAPL.
     */
    // @Override
    protected void _createProposalsx(final Assignment assignment, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        lazyLoadDependencies();

        log.trace("Assignment: '{}' '{}' '{}'", assignment.getFeature(), assignment.getOperator(),
                assignment.getTerminal().eClass().getName());

        if (true)
            return;

        final var configurationId  = extractConfigurationIdFromRequest();
        final var parserRule       = GrammarUtil.containingParserRule(assignment);
        final var parserRuleName   = parserRule.getName().toLowerCase();
        final var feature          = assignment.getFeature().toLowerCase();
        final var pdpConfiguration = pdpConfigurationProvider.pdpConfiguration(configurationId).blockFirst();

        log.trace(String.format("_createProposals| configId: %2s parserRuleName: %13s, feature: %22s, prefix: '%s'",
                configurationId, parserRuleName, feature, context.getPrefix()));

        if (null == pdpConfiguration) {
            return;
        }

        switch (parserRuleName) {
        case "import"              -> createImportProposals(feature, context, acceptor, pdpConfiguration);
        case "schema"              -> createSchemaProposals(feature, context, acceptor, pdpConfiguration);
        case "policy", "policyset" -> createPolicyOrPolicySetNameStringProposal(feature, context, acceptor);
        case "basic"               -> createBasicProposals(feature, context, acceptor, pdpConfiguration);
        case "step"                -> createStepProposals(feature, context, acceptor, pdpConfiguration);
        default                    -> { /* NOOP */ }
        }
    }

    private void createBasicProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        switch (feature) {
        case "identifier"       -> createBasicIdentifierProposals(context, acceptor, pdpConfiguration);
        case "fsteps"           -> createFStepsProposals(context, acceptor, pdpConfiguration);
        case "idsteps", "steps" -> {
            createBasicIdentifierProposals(context, acceptor, pdpConfiguration);
            createIdStepProposals(context, acceptor, pdpConfiguration);
        }
        default                 -> { /* NOOP */ }
        }
    }

    private void createFStepsProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {
        final var proposals = LibraryProposalsGenerator.createFStepsProposals(context, pdpConfiguration);
        addLibraryProposals(proposals, context, acceptor);
    }

    private void createIdStepProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {
        final var proposals = LibraryProposalsGenerator.createAttributeProposals(context, pdpConfiguration);
        addLibraryProposals(proposals, context, acceptor);
    }

    private void createStepProposals(String feature, ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
            PDPConfiguration pdpConfiguration) {

        final var maybeVariablesPrefix = generateExtendedPrefixForVariables(context);
        maybeVariablesPrefix.ifPresent(extendedPrefix -> {
            addProposals(VariablesProposalsGenerator.variableProposalsForContext(context, pdpConfiguration),
                    extendedPrefix, context, acceptor);
        });
//        final var maybeAttributePrefix = generateExtendedPrefixForAttributes(context);
//        maybeAttributePrefix.ifPresent(extendedPrefix -> {
//            log.trace("TODO: add attribute proposals... {}", extendedPrefix);
//        });
    }

    private Optional<String> generateExtendedPrefixForVariables(ContentAssistContext context) {
        final var currentNode = context.getCurrentNode();
        var       startNode   = NodeModelUtils.findLeafNodeAtOffset(currentNode, currentNode.getOffset());

        if (null == startNode) {
            return Optional.empty();
        }

        if (startNode.getEndOffset() > context.getOffset()) {
            /*
             * if after a '.' there is WS, a number of WS chars can be merged into one node.
             * So if the cursor position context.getOffset() is higher than the start offset
             * of the node, there are spaces left of the cursor and we do not produce
             * proposals.
             *
             * Also if the cursor is in the middle of an ID do not propose.
             */
//            log.trace("white space left of cursor. no id step proposals");
            return Optional.empty();
        }

        if (isWhitespace(startNode)) {
//            log.trace("skip whitespace moving to the left: '{}'", startNode.getText());
            startNode = leftOf(startNode);
        }

        final var sb            = new StringBuilder();
        ILeafNode inspectedNode = startNode;

        while (null != inspectedNode && !isWhitespace(inspectedNode)) {
//            log.trace("inspect-> '{}' >{}< ge|{}| sem|{}|", inspectedNode.getText(), inspectedNode,
//                    inspectedNode.getGrammarElement(), inspectedNode.getSemanticElement());
            if (!isId(inspectedNode) && !isDotKeyword(inspectedNode)) {
//                log.trace("'{}'->ID : {}", inspectedNode.getText(), isId(inspectedNode));
//                log.trace("'{}'->DOT: {}", inspectedNode.getText(), isDotKeyword(inspectedNode));
                /*
                 * The expression must start with a variable name and only have ID steps that
                 * variable proposals make sense.
                 */
                return Optional.empty();
            }
            sb.insert(0, inspectedNode.getText());
            inspectedNode = leftOf(inspectedNode);
        }

        final var extendedPrefix = sb.toString();
        log.trace("Extended variables prefix: {}", extendedPrefix);
        return Optional.of(extendedPrefix);
    }

    private boolean isId(ILeafNode n) {
        if (null == n) {
            return false;
        }
        if (n.getGrammarElement() instanceof TerminalRule terminalRule && "ID".equals(terminalRule.getName())) {
            return true;
        }
        if (n.getGrammarElement() instanceof RuleCall ruleCall && "ID".equals(ruleCall.getRule().getName())) {
            return true;
        }
        return false;
    }

    private boolean isDotKeyword(ILeafNode n) {
        return ".".equals(n.getText());
    }

    private boolean isWhitespace(INode n) {
        if (n instanceof TerminalRule terminalRule && "WS".equals(terminalRule.getName())) {
            return true;
        } else if (null != n && n.getGrammarElement() instanceof TerminalRule terminalRule
                && "WS".equals(terminalRule.getName())) {
            return true;
        } else {
            return false;
        }
    }

    private ILeafNode leftOf(INode n) {
        ILeafNode leftNode = null;
        var       offset   = n.getOffset();
        do {
            offset--;
            leftNode = NodeModelUtils.findLeafNodeAtOffset(n.getRootNode(), offset);
        } while (leftNode == n);
        return leftNode;
    }

    /*
     * Adds the fully qualified names and library names for attributes and functions
     * as completion options in import statements.
     */
    private void createImportProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        if (!"libsteps".equals(feature)) {
            return;
        }

        final var currentNode = context.getCurrentNode();

        if (null == currentNode) {
            return;
        }

        final var sb              = new StringBuilder();
        var       onLeftOfCurrent = currentNode;
        while (null != onLeftOfCurrent && isPartOfImport(onLeftOfCurrent)) {
            sb.insert(0, onLeftOfCurrent.getText());
            onLeftOfCurrent = leftOf(onLeftOfCurrent);
        }
        final var extendedPrefix = sb.toString();

        final var proposals        = new ArrayList<String>();
        final var attributeContext = pdpConfiguration.attributeContext();
        proposals.addAll(attributeContext.getAllFullyQualifiedFunctions());
        proposals.addAll(attributeContext.getAvailableLibraries());

        final var functionContext = pdpConfiguration.functionContext();
        proposals.addAll(functionContext.getAllFullyQualifiedFunctions());
        proposals.addAll(functionContext.getAvailableLibraries());

        addProposals(proposals, extendedPrefix, context, acceptor);
    }

    private boolean isPartOfImport(INode n) {
        final var text = n.getText();
        if (text.isBlank()) {
            return false;
        }
        if (".".equals(text)) {
            return true;
        }
        final var leaf = NodeModelUtils.findLeafNodeAtOffset(n, n.getOffset());
        final var ge   = leaf.getGrammarElement();
        if (ge instanceof TerminalRule terminalRule) {
            return "ID".equals(terminalRule.getName());
        }
        if (ge instanceof RuleCall ruleCall) {
            return "ID".equals(ruleCall.getRule().getName());
        }
        if (ge instanceof Keyword) {
            return "*".equals(leaf.getText());
        }
        return false;
    }

    /*
     * Proposals for the schema definitions in the document.
     *
     * This adds proposals for all possible variable names. I.e., subscription
     * components and environment variable names.
     */
    private void createSchemaProposals(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor, PDPConfiguration pdpConfiguration) {
        if ("subscriptionelement".equals(feature)) {
            addProposals(VariablesProposalsGenerator.AUTHORIZATION_SUBSCRIPTION_VARIABLES, context, acceptor);
        }
    }

    /*
     * Only offers to add a blank string for adding a name.
     */
    private void createPolicyOrPolicySetNameStringProposal(String feature, ContentAssistContext context,
            IIdeContentProposalAcceptor acceptor) {
        if (!"saplname".equals(feature)) {
            return;
        }
        final var entry = getProposalCreator().createProposal("\"\"", context, e -> {
            e.setKind(ContentAssistEntry.KIND_TEXT);
            e.setDescription("A name for the policy or policy set.");
        });
        acceptor.accept(entry, 0);
    }

    private void addProposals(final Collection<String> proposals, final String extendedPrefix,
            final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {
        proposals.forEach(proposal -> addProposal(proposal, extendedPrefix, context, acceptor));
    }

    private void addProposal(final String proposal, final String extendedPrefix, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        final var prefix                 = context.getPrefix();
        final var strippedExtendedPrefix = extendedPrefix.stripLeading();
        log.trace("Add propsal {}, extendedPrefix: {} originalPrefix: {}", proposal, strippedExtendedPrefix, prefix);
        if (!proposal.startsWith(strippedExtendedPrefix)) {
            return;
        }

        final var extededPrefixLeadingLength = Math.max(0, strippedExtendedPrefix.length() - prefix.length());
        final var proposalClippedToPrefix    = proposal.substring(extededPrefixLeadingLength, proposal.length());

        log.trace("Original Proposal: '{}'", proposal);
        log.trace("Extended Prefix  : '{}'", strippedExtendedPrefix);
        log.trace("Actual Prefix    : '{}'", prefix);
        log.trace("Clipped Proposal : '{}'", proposalClippedToPrefix);
        if (!prefix.equals(proposalClippedToPrefix)) {
            final var entry = getProposalCreator().createProposal(proposalClippedToPrefix, context,
                    e -> e.setLabel(proposal));
            acceptor.accept(entry, 0);
        }
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
        log.trace("prefix: '{}' proposal: '{}' - '{}' - '{}'", context.getPrefix(), proposal.proposal(),
                proposal.label(), proposal.documentation());
        final var entry = getProposalCreator().createProposal(proposal.proposal(), context, e -> {
            e.setLabel(proposal.label());
            e.setDocumentation(proposal.documentation());
        });
        acceptor.accept(entry, 0);
    }

    private void addLibraryProposals(final Collection<Proposal> proposals, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        proposals.forEach(proposal -> addProposalWithDocumentation(proposal, context, acceptor));
    }

    private void addProposalWithDocumentation(final Proposal proposal, final ContentAssistContext context,
            final IIdeContentProposalAcceptor acceptor) {
        // log.trace("prefix: '{}' proposal: '{}'", context.getPrefix(), proposal);
        final var entry = getProposalCreator().createProposal(proposal.proposal(), context, e -> {
            e.setDocumentation(proposal.documentation());
            e.setLabel(proposal.proposal());
        });
        acceptor.accept(entry, 0);
    }

}
