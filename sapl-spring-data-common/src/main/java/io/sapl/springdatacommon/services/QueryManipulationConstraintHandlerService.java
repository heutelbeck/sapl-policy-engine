/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static io.sapl.springdatacommon.utils.Utilities.*;

@Getter
@AllArgsConstructor
public class QueryManipulationConstraintHandlerService {

    private List<RecordConstraintData> queryManipulationRecords;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public JsonNode[] getQueryManipulationObligations() {

        final var queryManipulationObligations = new ArrayList<JsonNode>();

        if (queryManipulationRecords.isEmpty()) {
            return new JsonNode[0];
        }

        queryManipulationRecords.stream().forEach(constraintDataRecord -> {
            if (constraintDataRecord.type() == ConstraintHandlerType.MONGO_QUERY_MANIPULATION
                    || constraintDataRecord.type() == ConstraintHandlerType.R2DBC_QUERY_MANIPULATION) {
                queryManipulationObligations.add(constraintDataRecord.obligation());
            }
        });

        return queryManipulationObligations.toArray(new JsonNode[0]);

    }

    /**
     * Extracts the query CONDITION of all obligations to apply the corresponding
     * QueryManipulation.
     *
     * @return all query CONDITIONS.
     */
    public ArrayNode getConditions() {

        final var conditions = MAPPER.createArrayNode();

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

        final var selections = MAPPER.createArrayNode();

        if (queryManipulationRecords.isEmpty()) {
            return selections;
        }

        queryManipulationRecords.stream().forEach(constraintDataRecord -> {
            if (constraintDataRecord.obligation().has(SELECTION)) {
                selections.add(getSelection(constraintDataRecord.obligation(),
                        ConstraintHandlerType.getQueryManipulationSelectionStructure()));
            }
        });

        return selections;
    }

    /**
     * Extracts the query TRANSFORMATIONS of all obligations to apply the
     * corresponding QueryManipulation.
     *
     * @return all query TRANSFORMATIONS.
     */
    public ArrayNode getTransformations() {

        final var transformations = MAPPER.createArrayNode();

        if (queryManipulationRecords.isEmpty()) {
            return transformations;
        }

        queryManipulationRecords.stream().forEach(constraintDataRecord -> {
            if (constraintDataRecord.obligation().has(TRANSFORMATIONS)
                    && constraintDataRecord.obligation().get(TRANSFORMATIONS).isObject()) {

                constraintDataRecord.obligation().get(TRANSFORMATIONS).properties().forEach(entry -> {
                    final var keyValuePair = JsonNodeFactory.instance.objectNode();
                    keyValuePair.put(entry.getKey(), entry.getValue().asText());
                    transformations.add(keyValuePair);
                });
            }
        });

        return transformations;
    }

    public String getAlias() {
        final var alias = new ArrayList<String>();

        queryManipulationRecords.stream().forEach(constraintDataRecord -> {
            if (constraintDataRecord.obligation().has(ALIAS)
                    && constraintDataRecord.obligation().get(ALIAS).isTextual()) {
                alias.add(constraintDataRecord.obligation().get(ALIAS).textValue());
            }
        });

        if (alias.isEmpty()) {
            return "";
        } else {
            return alias.get(0);
        }
    }

    /**
     * Extracts the query SELECTION of an obligation to apply the corresponding
     * QueryManipulation.
     *
     * @param obligation which contains query SELECTION.
     * @return all query SELECTION.
     */
    private JsonNode getSelection(JsonNode obligation, JsonNode template) {

        final var              schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909);
        final var              schema        = schemaFactory.getSchema(template);
        Set<ValidationMessage> errors        = schema.validate(obligation);

        if (errors.isEmpty()) {
            return obligation.get(SELECTION);
        }

        throw new AccessDeniedException("Unhandable Obligation: " + obligation.toPrettyString());
    }
}
