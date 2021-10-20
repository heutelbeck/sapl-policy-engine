package io.sapl.spring.constraints.api;

import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;

public interface ConsumerConstraintHandlerProvider<T> extends Responsible, HasPriority, TypeSupport<T> {
	Consumer<T> getHandler(JsonNode constraint);
}