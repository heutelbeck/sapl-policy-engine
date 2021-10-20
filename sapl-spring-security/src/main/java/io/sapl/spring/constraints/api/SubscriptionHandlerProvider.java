package io.sapl.spring.constraints.api;

import java.util.function.Consumer;

import org.reactivestreams.Subscription;

import com.fasterxml.jackson.databind.JsonNode;

public interface SubscriptionHandlerProvider extends Responsible, HasPriority {
	Consumer<Subscription> getHandler(JsonNode constraint);
}
