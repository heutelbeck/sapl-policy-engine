package io.sapl.grammar.sapl.impl;

import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Pair;
import io.sapl.interpreter.EvaluationContext;

public class ObjectImplCustom extends io.sapl.grammar.sapl.impl.ObjectImpl {

	private static final int HASH_PRIME_14 = 71;
	private static final int INIT_PRIME_02 = 5;

	@Override
	public JsonNode evaluate(EvaluationContext ctx, boolean isBody, JsonNode relativeNode)
			throws PolicyEvaluationException {
		ObjectNode result = JsonNodeFactory.instance.objectNode();
		for (Pair pair : getMembers()) {
			result.set(pair.getKey(), pair.getValue().evaluate(ctx, isBody, relativeNode));
		}
		return result;
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_02;
		hash = HASH_PRIME_14 * hash + Objects.hashCode(getClass().getTypeName());
		for (Pair pair : getMembers()) {
			hash = HASH_PRIME_14 * hash + (Objects.hashCode(pair.getKey())
					^ ((pair.getValue() == null) ? 0 : pair.getValue().hash(imports)));
		}
		return hash;
	}

	@Override
	public boolean isEqualTo(EObject other, Map<String, String> otherImports, Map<String, String> imports) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		final ObjectImplCustom otherImpl = (ObjectImplCustom) other;
		if (getMembers().size() != otherImpl.getMembers().size()) {
			return false;
		}
		ListIterator<Pair> left = getMembers().listIterator();
		ListIterator<Pair> right = otherImpl.getMembers().listIterator();
		while (left.hasNext()) {
			Pair lhs = left.next();
			Pair rhs = right.next();
			if ((lhs == null) != (rhs == null)) {
				return false;
			}
			if (lhs == null) {
				continue;
			}
			if (!Objects.equals(lhs.getKey(), rhs.getKey())) {
				return false;
			}
			if ((lhs.getValue() == null) ? (lhs.getValue() != rhs.getValue())
					: !lhs.getValue().isEqualTo(rhs.getValue(), otherImports, imports)) {
				return false;
			}
		}
		return true;
	}

}
