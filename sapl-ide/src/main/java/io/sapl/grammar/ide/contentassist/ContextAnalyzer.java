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

import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;
import org.eclipse.xtext.Keyword;
import org.eclipse.xtext.RuleCall;
import org.eclipse.xtext.TerminalRule;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import io.sapl.grammar.sapl.Arguments;
import io.sapl.grammar.sapl.AttributeFinderStep;
import io.sapl.grammar.sapl.BasicEnvironmentAttribute;
import io.sapl.grammar.sapl.BasicEnvironmentHeadAttribute;
import io.sapl.grammar.sapl.BasicFunction;
import io.sapl.grammar.sapl.FunctionIdentifier;
import io.sapl.grammar.sapl.HeadAttributeFinderStep;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ContextAnalyzer {
    public enum ProposalType {
        ATTRIBUTE, ENVIRONMENT_ATTRIBUTE, FUNCTION, VARIABLE_OR_FUNCTION_NAME, INDETERMINATE
    }

    public record ContextAnalysisResult(String prefix, String ctxPrefix, String functionName, ProposalType type,
            INode startNode) {};

    public static ContextAnalysisResult analyze(final ContentAssistContext context) {
        dump(context);
        final var result = prefixAnalysis(context);
        log.trace("analysis: {}", result);
        return result;
    }

    private static ContextAnalysisResult prefixAnalysis(final ContentAssistContext context) {
        final var startNode    = firstNodeForAnalysis(context);
        var       n            = startNode;
        var       type         = ProposalType.VARIABLE_OR_FUNCTION_NAME;
        var       functionName = "";
        final var sb           = new StringBuilder();
        do {
            if (null == n) {
                type = ProposalType.INDETERMINATE;
                break;
            }
            final var grammarElement  = n.getGrammarElement();
            final var semanticElement = n.getSemanticElement();
            if (".".equals(n.getText())) {
//                log.trace("accept '.'");
            } else if (grammarElement instanceof TerminalRule terminalRule) {
                if ("WS".equals(terminalRule.getName())) {
//                    log.trace("Stop at WS {}", StringEscapeUtils.escapeJava(n.getText()));
                    break;
                }
            } else if (semanticElement instanceof Arguments arguments) {
//                log.trace("found arguments: {}", arguments);
                final var parent = arguments.eContainer();
//                log.trace("parent of arguments: {}");
                if (parent instanceof BasicFunction basicFunction) {
                    functionName = nameOf(basicFunction.getIdentifier());
//                    log.trace("functionName: {}", functionName);
                    type = ProposalType.FUNCTION;
                    break;
                } else {
                    type = ProposalType.INDETERMINATE;
                    break;
                }
            } else if (semanticElement instanceof AttributeFinderStep attribute) {
                type         = ProposalType.ATTRIBUTE;
                functionName = nameOf(attribute.getIdentifier());
//                log.trace("functionName (afs): {}", functionName);
                break;
            } else if (semanticElement instanceof HeadAttributeFinderStep attribute) {
                type         = ProposalType.ATTRIBUTE;
                functionName = nameOf(attribute.getIdentifier());
//                log.trace("functionName (hafs): {}", functionName);
                break;
            } else if (semanticElement instanceof BasicEnvironmentAttribute attribute) {
                type         = ProposalType.ENVIRONMENT_ATTRIBUTE;
                functionName = nameOf(attribute.getIdentifier());
//              log.trace("functionName (bea): {}", functionName);
                break;
            } else if (semanticElement instanceof BasicEnvironmentHeadAttribute attribute) {
                type         = ProposalType.ENVIRONMENT_ATTRIBUTE;
                functionName = nameOf(attribute.getIdentifier());
//                log.trace("functionName (beha): {}", functionName);
                break;
            }
//            log.trace("add: '{}' {}", n.getText(), n.getClass().getSimpleName());
            sb.insert(0, n.getText());
            n = leftOf(n);
        } while (n != null);
        final var prefix = sb.toString();
        return new ContextAnalysisResult(prefix, context.getPrefix(), functionName, type, startNode);
    }

    private static String nameOf(FunctionIdentifier identifier) {
        if (null == identifier || null == identifier.getNameFragments()) {
            return "";
        }
        return identifier.getNameFragments().stream().collect(Collectors.joining("."));
    }

    private static INode firstNodeForAnalysis(ContentAssistContext context) {
        final var offset         = context.getOffset();
        var       n              = skipCompositeNodes(context.getCurrentNode());
        final var grammarElement = n.getGrammarElement();
        if (grammarElement instanceof TerminalRule terminalRule) {
            if ("WS".equals(terminalRule.getName())) {
                if (offset > n.getOffset()) {
                    log.trace("whitespace left of cursor. Stop");
                    n = null;
                } else {
                    log.trace("At start of Whitespace. Step left");
                    n = leftOf(n);
                }
            }
        } else if (grammarElement instanceof Keyword) {
            if (offset == n.getOffset()) {
                log.trace("At start of Keyword '{}'. Step left", n.getText());
                n = leftOf(n);
            } else if (!".".equals(n.getText())) {
                log.trace("Not start of Keyword '{}'. Stop", n.getText());
                n = null;
            }
        } else if (offset < n.getEndOffset()) {
            log.trace("Not at end of previous token. Stop.");
            n = null;
        }
        return n;
    }

    private static INode leftOf(INode n) {
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

    private static void dump(final ContentAssistContext context) {
        var n = firstNodeForAnalysis(context);
        if (null == n) {
            return;
        }
        String t = "";
        String c = "";
        String g = "";
        String r = "";
        String s = "";
        while (null != n) {
            var text  = StringEscapeUtils.escapeJava(n.getText());
            var clazz = StringEscapeUtils.escapeJava(n.getClass().getSimpleName());
            var ge    = StringEscapeUtils
                    .escapeJava(n.getGrammarElement() == null ? "null" : n.getGrammarElement().eClass().getName());
            var ru    = "";
            if (n.getGrammarElement() instanceof RuleCall ruleCall) {
                ru = ruleCall.getRule().getName();
            } else if (n.getGrammarElement() instanceof TerminalRule rule) {
                ru = rule.getName();
            }
            var se     = StringEscapeUtils
                    .escapeJava(n.getSemanticElement() == null ? "null" : n.getSemanticElement().eClass().getName());
            var length = Math.max(Math.max(Math.max(text.length(), clazz.length()), Math.max(ge.length(), se.length())),
                    ru.length());
            var format = "%" + length + "s|";
            t = String.format(format, text) + t;
            c = String.format(format, clazz) + c;
            g = String.format(format, ge) + g;
            r = String.format(format, ru) + r;
            s = String.format(format, se) + s;
            n = leftOf(n);
        }
        log.trace("Text    : {}", t);
        log.trace("Class   : {}", c);
        log.trace("Grammar : {}", g);
        log.trace("Rule    : {}", r);
        log.trace("Semantic: {}", s);
    }

}
