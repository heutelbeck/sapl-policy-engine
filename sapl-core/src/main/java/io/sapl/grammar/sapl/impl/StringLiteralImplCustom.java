package io.sapl.grammar.sapl.impl;

import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.interpreter.EvaluationContext;

public class StringLiteralImplCustom extends io.sapl.grammar.sapl.impl.StringLiteralImpl {

	private static final int HASH_PRIME_09 = 47;
	private static final int INIT_PRIME_02 = 5;

	@Override
	public JsonNode evaluate(EvaluationContext ctx, boolean isBody, JsonNode relativeNode) {
		return JsonNodeFactory.instance.textNode(getString());
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_02;
		hash = HASH_PRIME_09 * hash + Objects.hashCode(getClass().getTypeName());
		hash = HASH_PRIME_09 * hash + Objects.hashCode(getString());
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
		final StringLiteralImplCustom otherImpl = (StringLiteralImplCustom) other;
		return Objects.equals(getString(), otherImpl.getString());
	}

}
