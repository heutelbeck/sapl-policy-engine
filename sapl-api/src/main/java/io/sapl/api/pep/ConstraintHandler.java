package io.sapl.api.pep;

import com.fasterxml.jackson.databind.JsonNode;

public interface ConstraintHandler {

	boolean handle(JsonNode constraint);

	boolean canHandle(JsonNode constraint);

}
