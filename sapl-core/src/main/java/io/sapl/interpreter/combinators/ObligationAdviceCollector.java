package io.sapl.interpreter.combinators;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;

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

	public void add(Decision decision, Response response) {
		if (response.getObligations().isPresent()) {
			obligationAdvice.get(Type.OBLIGATION).get(decision)
					.addAll(response.getObligations().get());
		}
		if (response.getAdvices().isPresent()) {
			obligationAdvice.get(Type.ADVICE).get(decision)
					.addAll(response.getAdvices().get());
		}
	}

	public Optional<ArrayNode> get(Type type, Decision decision) {
		ArrayNode returnNode = obligationAdvice.get(type).get(decision);
		if (returnNode.size() > 0) {
			return Optional.of(returnNode);
		}
		else {
			return Optional.empty();
		}
	}

}
