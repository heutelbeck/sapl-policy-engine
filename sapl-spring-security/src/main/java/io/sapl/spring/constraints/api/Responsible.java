package io.sapl.spring.constraints.api;

import com.fasterxml.jackson.databind.JsonNode;

public interface Responsible {
	boolean isResponsible(JsonNode constraint);
}