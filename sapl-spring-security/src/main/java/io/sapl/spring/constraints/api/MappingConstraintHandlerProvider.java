package io.sapl.spring.constraints.api;

import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;

public interface MappingConstraintHandlerProvider<T> extends Responsible, HasPriority, TypeSupport<T> {
	Function<T, T> getHandler(JsonNode constraint);
}