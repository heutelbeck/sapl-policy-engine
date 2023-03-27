/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.grammar.sapl.impl.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.fge.jackson.JsonNumEquals;
import com.google.common.base.Equivalence;

import io.sapl.api.interpreter.Val;

public class ArrayUtil {

	private final static Equivalence<JsonNode> EQ = JsonNumEquals.getInstance();

	public static Val numberArray(Integer... values) {
		var array = Val.JSON.arrayNode();
		for (var val : values) {
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
		}
		else {
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
			if (EQ.equivalent(element, arrayElement))
				return true;
		}
		return false;
	}

}
