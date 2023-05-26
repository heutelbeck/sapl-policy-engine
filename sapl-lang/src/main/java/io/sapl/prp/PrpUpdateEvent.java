/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.prp;

import java.util.Arrays;
import java.util.List;

import io.sapl.grammar.sapl.SAPL;
import lombok.EqualsAndHashCode;
import lombok.Value;

@Value
public class PrpUpdateEvent {

	Update[] updates;

	public PrpUpdateEvent(List<Update> updates) {
		if (updates == null) {
			this.updates = new Update[] {};
		}
		else {
			this.updates = updates.toArray(Update[]::new);
		}
	}

	public PrpUpdateEvent(Update... updates) {
		this.updates = Arrays.copyOf(updates, updates.length);
	}

	public Update[] getUpdates() {
		return Arrays.copyOf(updates, updates.length);
	}

	/**
	 * This contains the raw document and a custom equals method to eliminate duplicate
	 * update events. E.g. file creation may lead to two subsequent identical publish
	 * events without the .distinctUntilChanged() making use of the {@code equals} method
	 * of this class.
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
					+ (document != null ? "'" + document.getPolicyElement().getSaplName() + "'" : "NULL POLICY") + ")";
		}

	}

	public enum Type {

		PUBLISH, WITHDRAW, INCONSISTENT, CONSISTENT

	}

}
