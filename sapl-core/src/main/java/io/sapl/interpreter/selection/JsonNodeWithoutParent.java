package io.sapl.interpreter.selection;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.EvaluationContext;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class JsonNodeWithoutParent extends AbstractAnnotatedJsonNode {

	private static final String REFERENCE_CANNOT_BE_COMPARED = "Reference of a JsonNodeWithoutParent cannot be compared.";
	private static final String FILTER_ROOT_ELEMENT = "The root element cannot be filtered.";

	public JsonNodeWithoutParent(JsonNode node) {
		super(node, null);
	}

	@Override
	public boolean isNodeWithoutParent() {
		return true;
	}

	@Override
	public boolean isNodeWithParentObject() {
		return false;
	}

	@Override
	public boolean isNodeWithParentArray() {
		return false;
	}

	@Override
	public void removeFromTree(boolean each) throws PolicyEvaluationException {
		if (each) {
			removeEachItem(node);
		} else {
			throw new PolicyEvaluationException(FILTER_ROOT_ELEMENT);
		}
	}

	@Override
	public void applyFunction(String function, Arguments arguments, boolean each, EvaluationContext ctx)
			throws PolicyEvaluationException {
		if (each) {
			applyFunctionToEachItem(function, node, arguments, ctx);
		} else {
			throw new PolicyEvaluationException(FILTER_ROOT_ELEMENT);
		}
	}

	@Override
	public boolean sameReference(AbstractAnnotatedJsonNode other)  throws PolicyEvaluationException {
		throw new PolicyEvaluationException(REFERENCE_CANNOT_BE_COMPARED);
	}
}
