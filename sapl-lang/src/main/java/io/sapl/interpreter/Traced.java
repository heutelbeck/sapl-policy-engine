package io.sapl.interpreter;

import com.fasterxml.jackson.databind.JsonNode;

public interface Traced {
	JsonNode getTrace();
}
