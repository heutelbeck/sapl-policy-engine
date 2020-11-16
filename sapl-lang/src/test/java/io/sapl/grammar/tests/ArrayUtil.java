package io.sapl.grammar.tests;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.interpreter.Val;

public class ArrayUtil {

	public static Val numberArray(Integer... vals) {
		var array = Val.JSON.arrayNode();
		for (var val : vals) {
			array.add(val);
		}
		return Val.of(array);
	}

	public static Val numberArrayRange(int from, int to) {
		var array = Val.JSON.arrayNode();
		if (from < to) {
			for (int i = from; i <= to; i++) {
				array.add(i);
			}
		} else {
			for (int i = from; i >= to; i--) {
				array.add(i);
			}
		}
		return Val.of(array);
	}

	public static boolean arraysMatchWithSetSemantics(Val result, Val expected) {
		if (result.getArrayNode().size() != expected.getArrayNode().size())
			return false;
		var iter = expected.getArrayNode().elements();
		while (iter.hasNext()) {
			var element = iter.next();
			if (!containsElement(result.getArrayNode(), element))
				return false;
		}
		return true;
	}

	private static boolean containsElement(ArrayNode arrayNode, JsonNode element) {
		var iter = arrayNode.elements();
		while (iter.hasNext()) {
			var arrayElement = iter.next();
			if (element.equals(arrayElement))
				return true;
		}
		return false;
	}

}
