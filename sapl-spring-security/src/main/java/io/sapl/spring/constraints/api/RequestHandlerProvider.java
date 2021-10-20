package io.sapl.spring.constraints.api;

import java.util.function.LongConsumer;

import com.fasterxml.jackson.databind.JsonNode;

public interface RequestHandlerProvider extends Responsible, HasPriority {
	LongConsumer getHandler(JsonNode constraint);
}