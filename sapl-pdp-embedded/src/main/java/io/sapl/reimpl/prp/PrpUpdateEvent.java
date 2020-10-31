package io.sapl.reimpl.prp;

import java.util.Arrays;
import java.util.List;

import io.sapl.grammar.sapl.SAPL;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
public class PrpUpdateEvent {

	Update[] updates;

	public PrpUpdateEvent(List<Update> updates) {
		this.updates = updates.toArray(Update[]::new);
	}

	public PrpUpdateEvent(Update... updates) {
		this.updates = Arrays.copyOf(updates, updates.length);
	}

	public Update[] getUpdates() {
		return Arrays.copyOf(updates, updates.length);
	}
	
	/**
	 * This contains the raw document and a custom equals method to eliminate
	 * duplicate update events. E.g. file creation may lead to two subsequent
	 * identical publish events without the .distinct() making use of this equals.
	 */
	@Value
	public static class Update {
		Type type;
		@EqualsAndHashCode.Exclude
		SAPL document;
		String rawDocument;

		@Override
		public String toString() {
			return "Update(type=" + type + ", documentName="
					+ (document != null ? "'" + document.getPolicyElement().getSaplName() + "'" : "NULL PPOLICY") + ")";
		}

	}

	public static enum Type {
		PUBLISH, UNPUBLISH
	}

}
