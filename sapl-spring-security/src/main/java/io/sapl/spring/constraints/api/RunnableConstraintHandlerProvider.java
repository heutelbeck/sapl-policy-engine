package io.sapl.spring.constraints.api;

import com.fasterxml.jackson.databind.JsonNode;

public interface RunnableConstraintHandlerProvider extends Responsible, HasPriority {

	public static enum Signal {
		ON_CANCEL, ON_COMPLETE, ON_TERMINATE, AFTER_TERMINATE, ON_DECISION
	}

	RunnableConstraintHandlerProvider.Signal getSignal();

	Runnable getHandler(JsonNode constraint);
}