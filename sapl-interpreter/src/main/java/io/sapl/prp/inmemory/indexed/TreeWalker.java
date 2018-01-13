package io.sapl.prp.inmemory.indexed;

import java.util.Map;
import java.util.Objects;

import io.sapl.grammar.sapl.And;
import io.sapl.grammar.sapl.BasicGroup;
import io.sapl.grammar.sapl.Expression;
import io.sapl.grammar.sapl.Not;
import io.sapl.grammar.sapl.Or;

public class TreeWalker {

	public static DisjunctiveFormula walk(final Expression expression, final Map<String, String> imports) {
		Objects.requireNonNull(expression);
		Objects.requireNonNull(imports);
		if (expression instanceof And) {
			return traverse((And) expression, imports);
		} else if (expression instanceof Or) {
			return traverse((Or) expression, imports);
		} else if (expression instanceof Not) {
			return traverse((Not) expression, imports);
		} else if (expression instanceof BasicGroup) {
			return traverse((BasicGroup) expression, imports);
		}
		return endRecursion(expression, imports);
	}

	private static DisjunctiveFormula endRecursion(final Expression node, final Map<String, String> imports) {
		return new DisjunctiveFormula(new ConjunctiveClause(new Literal(new Bool(node, imports))));
	}

	private static DisjunctiveFormula traverse(final And node, final Map<String, String> imports) {
		DisjunctiveFormula left = walk(node.getLeft(), imports);
		DisjunctiveFormula right = walk(node.getRight(), imports);
		return left.distribute(right);
	}

	private static DisjunctiveFormula traverse(final BasicGroup node, final Map<String, String> imports) {
		if (node.getFilter() == null && node.getSteps().isEmpty() && node.getSubtemplate() == null) {
			return walk(node.getExpression(), imports);
		}
		return endRecursion(node, imports);
	}

	private static DisjunctiveFormula traverse(final Not node, final Map<String, String> imports) {
		DisjunctiveFormula child = walk(node.getExpression(), imports);
		return child.negate();
	}

	private static DisjunctiveFormula traverse(final Or node, final Map<String, String> imports) {
		DisjunctiveFormula left = walk(node.getLeft(), imports);
		DisjunctiveFormula right = walk(node.getRight(), imports);
		return left.combine(right);
	}
}
