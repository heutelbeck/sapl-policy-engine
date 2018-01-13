package io.sapl.spring.marshall.obligation;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Value;

@Value(staticConstructor = "of")
public class Obligation {
	JsonNode jsonObligation;

	public static List<Obligation> fromJson(Iterable<JsonNode> jsonObligations) {
		List<Obligation> result = new ArrayList<>();
		jsonObligations.iterator().forEachRemaining(node -> result.add(of(node)));
		return result;
	}
}
