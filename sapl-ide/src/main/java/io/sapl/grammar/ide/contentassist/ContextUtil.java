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

import java.util.LinkedList;
import java.util.List;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.ide.editor.contentassist.ContentAssistContext;
import org.eclipse.xtext.nodemodel.ICompositeNode;
import org.eclipse.xtext.nodemodel.ILeafNode;
import org.eclipse.xtext.nodemodel.INode;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;

import io.sapl.grammar.sapl.Condition;
import io.sapl.grammar.sapl.PolicyBody;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ContextUtil {

    public static ContentAssistContext getContextWithFullPrefix(ContentAssistContext context, boolean forAttribute) {
        var offset = context.getOffset();
        var model  = context.getCurrentModel();
        if (getPolicyBody(model) == null)
            return context;

        List<String> tokens = new LinkedList<>();
        addOldPrefixToTokens(context, tokens);

        var rootNode = context.getRootNode();
        if (shouldReturnOriginalContext(context, offset))
            return context;

        int indexOfCurrentNode = findIndexOfCurrentNode(rootNode, offset);
        var currentNode        = NodeModelUtils.findLeafNodeAtOffset(rootNode, indexOfCurrentNode);
        var previousNode       = NodeModelUtils.findLeafNodeAtOffset(rootNode, indexOfCurrentNode - 1);

        if (lastCharacterBeforeCursorIsBlank(previousNode))
            return context;

        if (isNodeBeforeCursorFirstNode(rootNode, indexOfCurrentNode, currentNode)) {
            String newPrefix = getNewPrefix(currentNode.getText());
            return context.copy().setPrefix(newPrefix).toContext();
        }

        String newPrefix;

        if (forAttribute) {
            newPrefix = computeNewPrefix(context, rootNode, currentNode, tokens);
        } else {
            addTokensUntilDelimiter(context, rootNode, tokens, currentNode);
            newPrefix = getNewPrefix(tokens);
        }
        newPrefix = newPrefix.trim();

        return context.copy().setPrefix(newPrefix).toContext();
    }

    private static PolicyBody getPolicyBody(EObject model) {
        // try to move up to the policy body
        if (model.eContainer() instanceof Condition) {
            return TreeNavigationUtil.goToFirstParent(model, PolicyBody.class);
        } else {
            return TreeNavigationUtil.goToLastParent(model, PolicyBody.class);
        }
    }

    private static String computeNewPrefix(ContentAssistContext context, INode rootNode, INode currentNode,
            List<String> tokens) {
        String currentNodeText = currentNode.getText();
        if (!context.getPrefix().equals(currentNodeText)) {
            if ("|<".equals(currentNodeText)) {
                tokens.add("<");
            } else {
                var req2 = (">").equals(currentNodeText);
                if (!currentNodeText.isBlank() && !req2) {
                    tokens.add(currentNodeText);
                } else {
                    var prevNodeOffset = currentNode.getEndOffset() - currentNode.getTotalLength();
                    currentNode = NodeModelUtils.findLeafNodeAtOffset(rootNode, prevNodeOffset - 1);
                }
            }
        }
        if (tokens.isEmpty() || !"<".equals(tokens.get(0))) {
            addTokensUntilDelimiter(context, rootNode, tokens, currentNode);
        }
        return getNewPrefix(tokens);
    }

    private static void addOldPrefixToTokens(ContentAssistContext context, List<String> tokens) {
        var oldPrefix = context.getPrefix();
        if (!oldPrefix.isBlank()) {
            tokens.add(context.getPrefix());
        }
    }

    private static String getNewPrefix(String text) {
        var newPrefix = text;
        if (newPrefix.startsWith("|<")) {
            newPrefix = newPrefix.replaceFirst("\\|<", "<");
        }
        return newPrefix;
    }

    private static String getNewPrefix(List<String> tokens) {
        var sb = new StringBuilder(tokens.size());
        for (int j = tokens.size() - 1; j >= 0; j--) {
            sb.append(tokens.get(j));
        }
        return getNewPrefix(sb.toString());
    }

    private static boolean shouldReturnOriginalContext(ContentAssistContext context, int offset) {
        INode prevNode;
        int   offsetOfPrevNode;
        var   rootNode       = context.getRootNode();
        var   currentNode    = NodeModelUtils.findLeafNodeAtOffset(rootNode, offset);
        var   lastNode       = NodeModelUtils.findLeafNodeAtOffset(rootNode, offset - 1);
        var   lengthLastNode = lastNode.getLength();
        if (!".".equals(lastNode.getText()) && !"<".equals(lastNode.getText())) {
            offsetOfPrevNode = offset - 1;
        } else if (currentNode != null && !".".equals(lastNode.getText())) {
            offsetOfPrevNode = offset - lengthLastNode - 1;
        } else
            offsetOfPrevNode = offset - 1;
        prevNode = NodeModelUtils.findLeafNodeAtOffset(context.getRootNode(), offsetOfPrevNode);
        return prevNode.getText().isBlank() || ";".equals(prevNode.getText());
    }

    private static boolean lastCharacterBeforeCursorIsBlank(INode currentNode) {
        return currentNode.getText().isBlank();
    }

    private static int findIndexOfCurrentNode(INode rootNode, int offset) {
        int   i           = offset;
        INode currentNode = NodeModelUtils.findLeafNodeAtOffset(rootNode, offset);

        while (currentNode == null) {
            i--;
            currentNode = NodeModelUtils.findLeafNodeAtOffset(rootNode, i);
        }

        return i;
    }

    private static void addTokensUntilDelimiter(ContentAssistContext context, INode rootNode, List<String> tokens,
            INode leafNode) {
        String tokenText;
        String lastChar;
        var    currentNode = leafNode;
        currentNode = NodeModelUtils.findLeafNodeAtOffset(rootNode, currentNode.getEndOffset() - 1);
        tokenText   = NodeModelUtils.getTokenText(currentNode);
        var req1 = currentNode.getEndOffset() == context.getOffset();
        var req2 = context.getPrefix().equals(tokenText);
        if (!tokenText.isBlank() && !(req1 && req2))
            tokens.add(tokenText);
        do {
            currentNode = NodeModelUtils.findLeafNodeAtOffset(rootNode,
                    currentNode.getEndOffset() - currentNode.getLength() - 1);
            tokenText   = NodeModelUtils.getTokenText(currentNode);
            if (!tokenText.isBlank() && currentNode.getEndOffset() != context.getOffset())
                tokens.add(tokenText);
            else
                break;
            lastChar = NodeModelUtils.findLeafNodeAtOffset(rootNode, currentNode.getTotalOffset() - 1).getText();
        } while (!lastChar.isBlank() && !"<".equals(tokenText) && !"|<".equals(tokenText));
    }

    private static boolean isNodeBeforeCursorFirstNode(ICompositeNode rootNode, int indexOfCurrentNode,
            ILeafNode currentNode) {
        return NodeModelUtils.findLeafNodeAtOffset(rootNode, indexOfCurrentNode - currentNode.getTotalLength())
                .getText().isBlank();
    }

}
