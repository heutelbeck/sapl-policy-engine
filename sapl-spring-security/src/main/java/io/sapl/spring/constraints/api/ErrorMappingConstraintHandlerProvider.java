package io.sapl.spring.constraints.api;

import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

public interface ErrorMappingConstraintHandlerProvider extends Responsible, HasPriority {
	Function<Throwable, Throwable> getHandler(JsonNode constraint);
}