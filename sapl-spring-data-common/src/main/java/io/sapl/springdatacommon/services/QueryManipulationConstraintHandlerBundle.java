/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.springdatacommon.services;

import static io.sapl.springdatacommon.utils.Utilities.CONDITIONS;
import static io.sapl.springdatacommon.utils.Utilities.SELECTION;
import java.util.List;

import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class QueryManipulationConstraintHandlerBundle {

	private List<RecordConstraintData> queryManipulationRecords;

	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Extracts the query CONDITION of all obligations to apply the corresponding
	 * QueryManipulation.
	 *
	 * @return all query CONDITIONS.
	 */
	public ArrayNode getConditions() {

		var conditions = MAPPER.createArrayNode();

		if (queryManipulationRecords.isEmpty()) {
			return conditions;
		}

		queryManipulationRecords.stream().forEach(
				constraintDataRecord -> constraintDataRecord.obligation().get(CONDITIONS).forEach(conditions::add));

		return conditions;
	}

	/**
	 * Extracts the query SELECTION of all obligations to apply the corresponding
	 * QueryManipulation.
	 *
	 * @return all query SELECTIONS.
	 */
	public ArrayNode getSelections() {

		var selections = MAPPER.createArrayNode();

		if (queryManipulationRecords.isEmpty()) {
			return selections;
		}

		queryManipulationRecords.stream().forEach(constraintDataRecord -> {
			if (constraintDataRecord.obligation().has(SELECTION)) {
				selections.add(getSelection(constraintDataRecord.obligation(),
						EnumConstraintHandlerType.getQueryManipulationSelectionStructure()));
			}
		});

		return selections;
	}

	/**
	 * Extracts the query SELECTION of an obligation to apply the corresponding
	 * QueryManipulation.
	 *
	 * @param obligation which contains query SELECTION.
	 * @return all query SELECTION.
	 */
	private JsonNode getSelection(JsonNode obligation, JsonNode template) {
		if (JsonNodeStructure.compare(obligation, template)) {
			return obligation.get(SELECTION);
		}
		throw new AccessDeniedException("Unhandable Obligation: " + obligation.toPrettyString());
	}
}
