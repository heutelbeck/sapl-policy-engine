/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.lsp.sapl.util;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Helper class that contains methods to navigate ANTLR parse trees.
 */
@UtilityClass
public final class TreeNavigationUtil {

    /**
     * Moves up the parse tree and returns the closest parent that matches the given
     * class type.
     *
     * @param <T> Class type of the searched-for parent.
     * @param node The current node from which the search starts.
     * @param classType Class type of the searched-for parent.
     * @return Returns the first parent for the given class type, or null if no
     * match was found.
     */
    public static <T extends ParserRuleContext> T goToFirstParent(@NonNull ParseTree node,
            @NonNull Class<T> classType) {
        var current = node;
        while (current != null) {
            if (classType.isInstance(current)) {
                return classType.cast(current);
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Moves up the parse tree and returns the highest parent that matches the given
     * class type.
     *
     * @param <T> Class type of the searched-for parent.
     * @param node The current node from which the search starts.
     * @param classType Class type of the searched-for parent.
     * @return Returns the highest parent for the given class type, or null if no
     * match was found.
     */
    public static <T extends ParserRuleContext> T goToLastParent(@NonNull ParseTree node, @NonNull Class<T> classType) {
        T   lastMatch = null;
        var current   = node;
        while (current != null) {
            if (classType.isInstance(current)) {
                lastMatch = classType.cast(current);
            }
            current = current.getParent();
        }
        return lastMatch;
    }

    /**
     * Gets the character offset of a parse tree node.
     * This is equivalent to NodeModelUtils.getNode(eObject).getOffset() in Xtext.
     *
     * @param node the parse tree node
     * @return the character offset, or -1 if the node has no start token
     */
    public static int offsetOf(ParserRuleContext node) {
        if (node == null || node.getStart() == null) {
            return -1;
        }
        return node.getStart().getStartIndex();
    }

}
