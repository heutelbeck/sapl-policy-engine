package io.sapl.interpreter.selection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.EvaluationContext;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class JsonNodeWithParentObject extends AbstractAnnotatedJsonNode {
	private String attribute;

	public JsonNodeWithParentObject(JsonNode node, JsonNode parent, String attribute) {
		super(node, parent);
		this.attribute = attribute;
	}

	@Override
	public boolean isNodeWithoutParent() {
		return false;
	}

	@Override
	public boolean isNodeWithParentObject() {
		return true;
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
			((ObjectNode) parent).remove(attribute);
		}
	}

	@Override
	public void applyFunction(String function, Arguments arguments, boolean each, EvaluationContext ctx)
			throws PolicyEvaluationException {
		if (each) {
			applyFunctionToEachItem(function, node, arguments, ctx);
		} else {
			((ObjectNode) parent).set(attribute, applyFunctionToNode(function, node, arguments, ctx, null));
		}
	}

	@Override
	public boolean sameReference(AbstractAnnotatedJsonNode other) {
		return other.isNodeWithParentObject() && other.getNode() == getNode()
				&& getAttribute().equals(((JsonNodeWithParentObject) other).getAttribute());
	}
}
