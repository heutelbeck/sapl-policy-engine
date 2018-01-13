package io.sapl.grammar.sapl.impl;

import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;

import org.eclipse.emf.ecore.EObject;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.pip.AttributeException;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.selection.AbstractAnnotatedJsonNode;
import io.sapl.interpreter.selection.ArrayResultNode;
import io.sapl.interpreter.selection.JsonNodeWithoutParent;
import io.sapl.interpreter.selection.ResultNode;

public class AttributeFinderStepImplCustom extends io.sapl.grammar.sapl.impl.AttributeFinderStepImpl {

	private static final String EXTERNAL_ATTRIBUTE_IN_TARGET = "Attribute resolution error. Attribute '%s' is not allowed in target.";
	private static final String ATTRIBUTE_RESOLUTION = "Attribute resolution error. Attribute '%s' cannot be resolved.";

	private static final int HASH_PRIME_03 = 23;
	private static final int INIT_PRIME_01 = 3;

	@Override
	public ResultNode apply(AbstractAnnotatedJsonNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		return applyToJson(previousResult.asJsonWithoutAnnotations(), ctx, isBody);
	}

	@Override
	public ResultNode apply(ArrayResultNode previousResult, EvaluationContext ctx, boolean isBody,
			JsonNode relativeNode) throws PolicyEvaluationException {
		return applyToJson(previousResult.asJsonWithoutAnnotations(), ctx, isBody);
	}

	private ResultNode applyToJson(JsonNode previousResult, EvaluationContext ctx, boolean isBody)
			throws PolicyEvaluationException {
		String fullyQualifiedName = String.join(".", getIdSteps());
		if (ctx.getImports().containsKey(fullyQualifiedName)) {
			fullyQualifiedName = ctx.getImports().get(fullyQualifiedName);
		}

		if (!isBody) {
			throw new PolicyEvaluationException(String.format(EXTERNAL_ATTRIBUTE_IN_TARGET, fullyQualifiedName));
		}

		try {
			return new JsonNodeWithoutParent(ctx.getAttributeCtx().evaluate(fullyQualifiedName, previousResult,
					ctx.getVariableCtx().getVariables()));
		} catch (AttributeException e) {
			throw new PolicyEvaluationException(String.format(ATTRIBUTE_RESOLUTION, fullyQualifiedName), e);
		}
	}

	@Override
	public int hash(Map<String, String> imports) {
		int hash = INIT_PRIME_01;
		hash = HASH_PRIME_03 * hash + Objects.hashCode(getClass().getTypeName());
		for (String idStep : getIdSteps()) {
			hash = HASH_PRIME_03 * hash + Objects.hashCode(idStep);
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
		final AttributeFinderStepImplCustom otherImpl = (AttributeFinderStepImplCustom) other;
		if (getIdSteps().size() != otherImpl.getIdSteps().size()) {
			return false;
		}
		ListIterator<String> left = getIdSteps().listIterator();
		ListIterator<String> right = otherImpl.getIdSteps().listIterator();
		while (left.hasNext()) {
			String lhs = left.next();
			String rhs = right.next();
			if (!Objects.equals(lhs, rhs)) {
				return false;
			}
		}
		return true;
	}

}
