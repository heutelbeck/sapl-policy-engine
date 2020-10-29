package io.sapl.reimpl.prp;

import java.util.Set;

import io.sapl.grammar.sapl.SAPL;
import lombok.Value;

@Value
public class PrpUpdateEvent {

	Set<Update> updates;

	public PrpUpdateEvent(Set<Update> updates) {
		this.updates = Set.copyOf(updates);
	}

	public PrpUpdateEvent(Update[] updates) {
		this.updates = Set.of(updates);
	}

	@Value
	public static class Update {
		Type type;
		SAPL document;
	}

	public static enum Type {
		PUBLISH, UNPUBLISH
	}
}
