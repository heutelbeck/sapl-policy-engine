package io.sapl.interpreter.selection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.grammar.sapl.Arguments;
import io.sapl.interpreter.EvaluationContext;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

@Value
@EqualsAndHashCode(callSuper=true)
@ToString(callSuper=true)
public class JsonNodeWithParentArray extends AbstractAnnotatedJsonNode {
	private int index;
	
	public JsonNodeWithParentArray(JsonNode node, JsonNode parent, int index) {
		super(node, parent);
		this.index = index;
	}

	@Override
	public boolean isNodeWithoutParent() {
		return false;
	}

	@Override
	public boolean isNodeWithParentObject() {
		return false;
	}

	@Override
	public boolean isNodeWithParentArray() {
		return true;
	}

	@Override
	public void removeFromTree(boolean each) throws PolicyEvaluationException {
		if (each) {
			removeEachItem(node);
		} else {
			((ArrayNode) parent).remove(index);
		}
	}

	@Override
	public void applyFunction(String function, Arguments arguments, boolean each, EvaluationContext ctx) throws PolicyEvaluationException {
		if (each) {
			applyFunctionToEachItem(function, node, arguments, ctx);
		} else {
			((ArrayNode) parent).set(index, applyFunctionToNode(function, node, arguments, ctx, null));
		}	
	}

	@Override
	public boolean sameReference(AbstractAnnotatedJsonNode other) {
		return other.isNodeWithParentArray() && other.getNode() == getNode()
				&& ((JsonNodeWithParentArray) other).getIndex() == getIndex();
	}
}
