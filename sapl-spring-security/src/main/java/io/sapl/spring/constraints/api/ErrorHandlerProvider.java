package io.sapl.spring.constraints.api;

import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;

public interface ErrorHandlerProvider extends Responsible, HasPriority {
	Consumer<Throwable> getHandler(JsonNode constraint);
}