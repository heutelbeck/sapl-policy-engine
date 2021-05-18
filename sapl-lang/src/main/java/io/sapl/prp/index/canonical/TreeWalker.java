/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
import io.sapl.grammar.sapl.BasicGroup;
import io.sapl.grammar.sapl.EagerAnd;
import io.sapl.grammar.sapl.EagerOr;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.Not;
import lombok.experimental.UtilityClass;

import java.util.Map;

@UtilityClass
public class TreeWalker {

    public static DisjunctiveFormula walk(final Expression expression, final Map<String, String> imports) {
        Preconditions.checkNotNull(imports);
        if (Preconditions.checkNotNull(expression) instanceof EagerAnd) {
            return traverse((EagerAnd) expression, imports);
        } else if (expression instanceof EagerOr) {
            return traverse((EagerOr) expression, imports);
        } else if (expression instanceof Not) {
            return traverse((Not) expression, imports);
        } else if (expression instanceof BasicGroup) {
            return traverse((BasicGroup) expression, imports);
        }
        return endRecursion(expression, imports);
    }

    static DisjunctiveFormula endRecursion(final Expression node, final Map<String, String> imports) {
        return new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(node, imports))));
    }

    private static DisjunctiveFormula traverse(final EagerAnd node, final Map<String, String> imports) {
        DisjunctiveFormula left = walk(node.getLeft(), imports);
        DisjunctiveFormula right = walk(node.getRight(), imports);
        return left.distribute(right);
    }

    static DisjunctiveFormula traverse(final BasicGroup node, final Map<String, String> imports) {
        if (node.getFilter() == null && node.getSteps().isEmpty() && node.getSubtemplate() == null) {
            return walk(node.getExpression(), imports);
        }
        return endRecursion(node, imports);
    }

    private static DisjunctiveFormula traverse(final Not node, final Map<String, String> imports) {
        DisjunctiveFormula child = walk(node.getExpression(), imports);
        return child.negate();
    }

    private static DisjunctiveFormula traverse(final EagerOr node, final Map<String, String> imports) {
        DisjunctiveFormula left = walk(node.getLeft(), imports);
        DisjunctiveFormula right = walk(node.getRight(), imports);
        return left.combine(right);
    }

}
