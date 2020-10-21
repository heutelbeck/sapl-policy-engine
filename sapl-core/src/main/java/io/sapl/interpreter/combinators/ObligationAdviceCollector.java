/**
 * Copyright © 2020 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.combinators;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;

public class ObligationAdviceCollector {

	private EnumMap<Type, Map<Decision, ArrayNode>> obligationAdvice;

	public enum Type {

		OBLIGATION, ADVICE

	}

	public ObligationAdviceCollector() {
		obligationAdvice = new EnumMap<>(Type.class);

		Map<Decision, ArrayNode> obligationMap = new EnumMap<>(Decision.class);
		obligationMap.put(Decision.DENY, JsonNodeFactory.instance.arrayNode());
		obligationMap.put(Decision.PERMIT, JsonNodeFactory.instance.arrayNode());

		Map<Decision, ArrayNode> adviceMap = new EnumMap<>(Decision.class);
		adviceMap.put(Decision.DENY, JsonNodeFactory.instance.arrayNode());
		adviceMap.put(Decision.PERMIT, JsonNodeFactory.instance.arrayNode());

		obligationAdvice.put(Type.OBLIGATION, obligationMap);
		obligationAdvice.put(Type.ADVICE, adviceMap);
	}

	public void add(Decision decision, AuthorizationDecision authzDecision) {
		if (authzDecision.getObligations().isPresent()) {
			obligationAdvice.get(Type.OBLIGATION).get(decision).addAll(authzDecision.getObligations().get());
		}
		if (authzDecision.getAdvices().isPresent()) {
			obligationAdvice.get(Type.ADVICE).get(decision).addAll(authzDecision.getAdvices().get());
		}
	}

	public Optional<ArrayNode> get(Type type, Decision decision) {
		ArrayNode returnNode = obligationAdvice.get(type).get(decision);
		if (returnNode.size() > 0) {
			return Optional.of(returnNode);
		} else {
			return Optional.empty();
		}
	}

}
