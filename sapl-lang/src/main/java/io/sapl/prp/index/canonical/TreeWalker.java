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
package io.sapl.prp.index.canonical;

import com.google.common.base.Preconditions;
import io.sapl.grammar.sapl.*;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TreeWalker {

    public static DisjunctiveFormula walk(final Expression expression) {
        if (Preconditions.checkNotNull(expression) instanceof EagerAnd) {
            return traverse((EagerAnd) expression);
        } else if (expression instanceof EagerOr eagerOr) {
            return traverse(eagerOr);
        } else if (expression instanceof Not not) {
            return traverse(not);
        } else if (expression instanceof BasicGroup basicGroup) {
            return traverse(basicGroup);
        }
        return endRecursion(expression);
    }

    static DisjunctiveFormula endRecursion(final Expression node) {
        return new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(node))));
    }

    private static DisjunctiveFormula traverse(final EagerAnd node) {
        DisjunctiveFormula left  = walk(node.getLeft());
        DisjunctiveFormula right = walk(node.getRight());
        return left.distribute(right);
    }

    static DisjunctiveFormula traverse(final BasicGroup node) {
        if (null == node.getFilter() && node.getSteps().isEmpty() && node.getSubtemplate() == null) {
            return walk(node.getExpression());
        }
        return endRecursion(node);
    }

    private static DisjunctiveFormula traverse(final Not node) {
        DisjunctiveFormula child = walk(node.getExpression());
        return child.negate();
    }

    private static DisjunctiveFormula traverse(final EagerOr node) {
        DisjunctiveFormula left  = walk(node.getLeft());
        DisjunctiveFormula right = walk(node.getRight());
        return left.combine(right);
    }

}
