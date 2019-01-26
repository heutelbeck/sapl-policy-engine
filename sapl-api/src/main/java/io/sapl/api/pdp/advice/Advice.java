package io.sapl.api.pdp.advice;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Value;

@Value(staticConstructor = "of")
public class Advice {

	JsonNode jsonAdvice;

	public static List<Advice> fromJson(Iterable<JsonNode> jsonAdvices) {
		List<Advice> result = new ArrayList<>();
		jsonAdvices.iterator().forEachRemaining(node -> result.add(of(node)));
		return result;
	}

}
