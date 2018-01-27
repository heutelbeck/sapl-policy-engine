/**
 * Copyright © 2017 Dominic Heutelbeck (dheutelbeck@ftk.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package io.sapl.grammar.sapl.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.PolicyEvaluationException;

public class ExpressionImplCustom extends io.sapl.grammar.sapl.impl.ExpressionImpl {

	protected static final String ARITHMETIC_OPERATION_TYPE_MISMATCH = "Type mismatch. Arithmetic operation expects number values, but got: '%s'.";
	protected static final String BOOLEAN_OPERATION_TYPE_MISMATCH = "Type mismatch. Boolean opration expects boolean values, but got: '%s'.";

	protected static final JsonNodeFactory JSON = JsonNodeFactory.instance;

	protected static void assertNumber(JsonNode node) throws PolicyEvaluationException {
		if (!node.isNumber()) {
			throw new PolicyEvaluationException(
					String.format(ARITHMETIC_OPERATION_TYPE_MISMATCH, node.getNodeType()));
		}
	}

	protected static void assertBoolean(JsonNode node) throws PolicyEvaluationException {
		if (!node.isBoolean()) {
			throw new PolicyEvaluationException(String.format(BOOLEAN_OPERATION_TYPE_MISMATCH, node.getNodeType()));
		}
	}

}
