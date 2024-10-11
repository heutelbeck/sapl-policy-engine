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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OldFunctions {
//    private Optional<String> generateCurrentExpressionString(ContentAssistContext context) {
//        final var currentNode = context.getCurrentNode();
//        final var       startNode   = NodeModelUtils.findLeafNodeAtOffset(currentNode, currentNode.getOffset());
//
//        if (null == startNode) {
//            return Optional.empty();
//        }
//
//        if (isWhitespace(startNode) && startNode.getOffset() < context.getOffset()) {
//            /*
//             * if after a '.' there is WS, a number of WS chars can be merged into one node.
//             * So if the cursor position context.getOffset() is higher than the start offset
//             * of the node, there are spaces left of the cursor and we do not produce
//             * proposals.
//             */
//            log.trace("white space left of cursor. no id step proposals");
//            return Optional.empty();
//        }
//
//        if (isWhitespace(startNode)) {
//            log.trace("skip whitespace moving to the left: '{}'", startNode.getText());
//            startNode = leftOf(startNode);
//        }
//
//        final var sb            = new StringBuilder();
//        ILeafNode inspectedNode = startNode;
//
//        // log.trace("Dump:\n" + NodeModelUtils.compactDump(context.getRootNode(),
//        // true));
//        log.trace("start-> '{}' >{}< |{}|", startNode.getText(), startNode, startNode.getGrammarElement());
//        while (null != inspectedNode && !isWhitespace(inspectedNode)) {
//            sb.insert(0, inspectedNode.getText());
//            inspectedNode = leftOf(inspectedNode);
//        }
//
//        final var extendedPrefix = sb.toString();
//        log.trace("Extended variables prefix: {}", extendedPrefix);
//        return Optional.of(extendedPrefix);
//    }
//
//    private Optional<String> generateExtendedPrefixForAttributes(ContentAssistContext context) {
//        final var currentNode = context.getCurrentNode();
//        final var       startNode   = NodeModelUtils.findLeafNodeAtOffset(currentNode, currentNode.getOffset());
//
//        if (null == startNode) {
//            return Optional.empty();
//        }
//
//        if (startNode.getEndOffset() > context.getOffset()) {
//            /*
//             * if after a '.' there is WS, a number of WS chars can be merged into one node.
//             * So if the cursor position context.getOffset() is higher than the start offset
//             * of the node, there are spaces left of the cursor and we do not produce
//             * proposals.
//             *
//             * Also if the cursor is in the middle of an ID do not propose.
//             */
//            log.trace("white space left of cursor. no id step proposals");
//            return Optional.empty();
//        }
//
//        if (isWhitespace(startNode)) {
//            log.trace("skip whitespace moving to the left: '{}'", startNode.getText());
//            startNode = leftOf(startNode);
//        }
//
//        final var sb            = new StringBuilder();
//        ILeafNode inspectedNode = startNode;
//
//        // log.trace("Dump:\n" + NodeModelUtils.compactDump(context.getRootNode(),
//        // true));
//        while (null != inspectedNode && !isWhitespace(inspectedNode)) {
//            log.trace("inspect-> '{}' >{}< ge|{}| sem|{}|", inspectedNode.getText(), inspectedNode,
//                    inspectedNode.getGrammarElement(), inspectedNode.getSemanticElement());
//            if (!isId(inspectedNode) && !isDotKeyword(inspectedNode)) {
//                log.trace("'{}'->ID : {}", inspectedNode.getText(), isId(inspectedNode));
//                log.trace("'{}'->DOT: {}", inspectedNode.getText(), isDotKeyword(inspectedNode));
//                /*
//                 * The expression must start with a variable name and only have ID steps that
//                 * variable proposals make sense.
//                 */
//                return Optional.empty();
//            }
//            sb.insert(0, inspectedNode.getText());
//            inspectedNode = leftOf(inspectedNode);
//        }
//
//        final var extendedPrefix = sb.toString();
//        log.trace("Extended variables prefix: {}", extendedPrefix);
//        return Optional.of(extendedPrefix);
//    }
//
//    private boolean isPartOfIdentifierWithSteps(INode n, boolean ignoreHidden) {
//        log.trace("Inspect: '{}'", n.getText());
//        final var leaf = NodeModelUtils.findLeafNodeAtOffset(n, n.getOffset());
//        final var ge   = leaf.getGrammarElement();
//        log.trace("leaf: '{}' '{}'", leaf.isHidden(), leaf.getText());
//        log.trace("ge  : '{}'", ge);
//        if (".".equals(leaf.getText())) {
//            return true;
//        }
//        if (leaf.isHidden()) {
//            return ignoreHidden;
//        }
//        if (ge instanceof TerminalRule terminalRule) {
//            return "ID".equals(terminalRule.getName());
//        }
//        if (ge instanceof RuleCall ruleCall) {
//            return "ID".equals(ruleCall.getRule().getName());
//        }
//        if (ge instanceof Keyword) {
//            return VariablesProposalsGenerator.AUTHORIZATION_SUBSCRIPTION_VARIABLES.contains(leaf.getText().strip());
//        }
//        return false;
//    }
//
//    private List<String> prependPipe(List<String> extendedProposals) {
//        return extendedProposals.stream().map(p -> {
//            if (p.startsWith("<")) {
//                return "|" + p;
//            } else {
//                return p;
//            }
//        }).toList();
//    }
//
//    private List<String> stripLeadingBracket(List<String> extendedProposals) {
//        return extendedProposals.stream().map(p -> {
//            if (p.startsWith("<")) {
//                return p.substring(1, p.length());
//            } else {
//                return p;
//            }
//        }).toList();
//    }
//
//    private boolean isPartOfImports(INode n) {
//        final var text = n.getText();
//        if (text.isBlank()) {
//            return false;
//        }
//        if (".".equals(text)) {
//            return true;
//        }
//        final var leaf = NodeModelUtils.findLeafNodeAtOffset(n, n.getOffset());
//        final var ge   = leaf.getGrammarElement();
//        if (ge instanceof TerminalRule terminalRule) {
//            return "ID".equals(terminalRule.getName());
//        }
//        if (ge instanceof RuleCall ruleCall) {
//            return "ID".equals(ruleCall.getRule().getName());
//        }
//        if (ge instanceof Keyword) {
//            return "*".equals(leaf.getText());
//        }
//        return false;
//    }
//
//    private INode rightOf(INode n) {
//        INode leftNode = null;
//        final var   offset   = n.getOffset();
//        do {
//            offset++;
//            leftNode = NodeModelUtils.findLeafNodeAtOffset(n.getRootNode(), offset);
//        } while (leftNode == n);
//        return leftNode;
//    }
//
//    private void createSchemaExpressionProposals(ContentAssistContext context, IIdeContentProposalAcceptor acceptor,
//            PDPConfiguration pdpConfiguration) {
//        final var environmentVariableProposals = VariablesProposalsGenerator.variableProposalsForContext(context,
//                pdpConfiguration);
//        addProposals(environmentVariableProposals, context, acceptor);
//    }
//
//    private void addProposalsForRegion(final Collection<String> proposals, final String prefix, final int startOffset,
//            final int endOffset, final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {
//        proposals
//                .forEach(proposal -> addProposalForRegion(proposal, prefix, startOffset, endOffset, context, acceptor));
//    }
//
//    private void addProposalForRegion(final String proposal, final String prefix, final int startOffset,
//            final int endOffset, final ContentAssistContext context, final IIdeContentProposalAcceptor acceptor) {
//        if (!proposal.startsWith(prefix)) {
//            return;
//        }
//
//        if (proposal.equals(prefix)) {
//            return;
//        }
//
//        log.trace("Add proposal: '{}' with prefix: '{}' replaces: '{}'", proposal, prefix,
//                context.getRootNode().getText().substring(startOffset, endOffset));
//
//        /*
//         * Note: Currently the replacement region is not correctly handled if the cursor
//         * is inside of the prefix. E.g.: aa|aa.bb where | is the cursor. When inserting
//         * aa.bbxxx you end up with aa.bbxxx|aa.bb Still this is better than overwriting
//         * text on the left of the prefix.
//         */
//
////        final var newContext = context.copy().setReplaceRegion(new TextRegion(startOffset, endOffset)).setPrefix(prefix)
////                .setOffset(endOffset).toContext();
//        final var entry = getProposalCreator().createProposal(proposal, context, e -> {
//            e.setPrefix(prefix);
//        });
//        log.trace("ProposalEntry: {}", entry);
//        acceptor.accept(entry, 0);
//    }
//    private void addSimpleProposal(final String proposal, final ContentAssistContext context,
//            final IIdeContentProposalAcceptor acceptor) {
//        final var prefix = context.getPrefix();
//        log.trace("Add simple propsal {}, originalPrefix: {}", proposal, prefix);
//        if (!proposal.startsWith(prefix)) {
//            log.trace("proposal not matching extended prefix. do not add.");
//            return;
//        }
//
//        log.trace("Original Proposal: {}", proposal);
//        log.trace("Actual Prefix    : {}", prefix);
//
//        final var entry = getProposalCreator().createProposal(proposal, context);
//        acceptor.accept(entry, 0);
//    }

}
