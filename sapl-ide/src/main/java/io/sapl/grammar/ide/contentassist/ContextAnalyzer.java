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

import io.sapl.grammar.sapl.*;
import lombok.experimental.UtilityClass;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.TerminalRule;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import java.util.Set;
import java.util.stream.Collectors;

@UtilityClass
public class ContextAnalyzer {
    private static final Set<String> SKIPPABLE_KEYWORDS = Set.of(".", "subject", "action", "resource", "environment");

    public enum ProposalType {
        ATTRIBUTE,
        ENVIRONMENT_ATTRIBUTE,
        FUNCTION,
        VARIABLE_OR_FUNCTION_NAME,
        INDETERMINATE
    }

    public record ContextAnalysisResult(
            String prefix,
            String ctxPrefix,
            String functionName,
            ProposalType type,
            INode startNode) {}

    public static ContextAnalysisResult analyze(final ContentAssistContext context) {
        final var startNode = firstNodeForAnalysis(context);
        if (null == startNode) {
            return new ContextAnalysisResult("", context.getPrefix(), "", ProposalType.INDETERMINATE, null);
        }
        var       n            = startNode;
        var       type         = ProposalType.VARIABLE_OR_FUNCTION_NAME;
        var       functionName = "";
        var       reachedEnd   = false;
        final var sb           = new StringBuilder();
        do {
            final var grammarElement  = n.getGrammarElement();
            final var semanticElement = n.getSemanticElement();
            if (".".equals(n.getText())) {
                /* NOOP */
            } else if (grammarElement instanceof final TerminalRule terminalRule
                    && "WS".equals(terminalRule.getName())) {
                reachedEnd = true;
            } else if (semanticElement instanceof final Arguments arguments) {
                final var parent = arguments.eContainer();
                if (parent instanceof final BasicFunction basicFunction) {
                    functionName = nameOf(basicFunction.getIdentifier());
                    type         = ProposalType.FUNCTION;
                    reachedEnd   = true;
                } else {
                    type       = ProposalType.INDETERMINATE;
                    reachedEnd = true;
                }
            } else if (semanticElement instanceof final AttributeFinderStep attribute) {
                type         = ProposalType.ATTRIBUTE;
                functionName = nameOf(attribute.getIdentifier());
                reachedEnd   = true;
            } else if (semanticElement instanceof final HeadAttributeFinderStep attribute) {
                type         = ProposalType.ATTRIBUTE;
                functionName = nameOf(attribute.getIdentifier());
                reachedEnd   = true;
            } else if (semanticElement instanceof final BasicEnvironmentAttribute attribute) {
                type         = ProposalType.ENVIRONMENT_ATTRIBUTE;
                functionName = nameOf(attribute.getIdentifier());
                reachedEnd   = true;
            } else if (semanticElement instanceof final BasicEnvironmentHeadAttribute attribute) {
                type         = ProposalType.ENVIRONMENT_ATTRIBUTE;
                functionName = nameOf(attribute.getIdentifier());
                reachedEnd   = true;
            }
            if (!reachedEnd) {
                sb.insert(0, n.getText());
                n = leftOf(n);
            }
        } while (!reachedEnd && n != null);
        final var prefix = sb.toString();
        return new ContextAnalysisResult(prefix, context.getPrefix(), functionName, type, startNode);
    }

    private static String nameOf(FunctionIdentifier identifier) {
        if (null == identifier || null == identifier.getNameFragments()) {
            return "";
        }
        return identifier.getNameFragments().stream().collect(Collectors.joining("."));
    }

    static INode firstNodeForAnalysis(ContentAssistContext context) {
        final var offset         = context.getOffset();
        var       n              = skipCompositeNodes(context.getCurrentNode());
        final var grammarElement = n.getGrammarElement();
        if (grammarElement instanceof final TerminalRule terminalRule) {
            if ("WS".equals(terminalRule.getName())) {
                if (offset > n.getOffset()) {
                    n = null;
                } else {
                    n = leftOf(n);
                }
            }
        } else if (grammarElement instanceof Keyword) {
            if (offset == n.getOffset()) {
                n = leftOf(n);
            } else if (!SKIPPABLE_KEYWORDS.contains(n.getText())) {
                n = null;
            }
        } else if (offset < n.getEndOffset()) {
            n = null;
        }
        return n;
    }

    static INode leftOf(INode n) {
        if (null == n) {
            return n;
        }
        ILeafNode leftNode = null;
        var       offset   = n.getOffset();
        do {
            leftNode = NodeModelUtils.findLeafNodeAtOffset(n.getRootNode(), offset);
            offset--;
        } while (leftNode == n || leftNode instanceof ICompositeNode);
        return leftNode;
    }

    private static INode skipCompositeNodes(INode n) {
        var offset = n.getOffset();
        while (n instanceof ICompositeNode) {
            n = NodeModelUtils.findLeafNodeAtOffset(n.getRootNode(), offset);
            offset--;
        }
        return n;
    }

}
