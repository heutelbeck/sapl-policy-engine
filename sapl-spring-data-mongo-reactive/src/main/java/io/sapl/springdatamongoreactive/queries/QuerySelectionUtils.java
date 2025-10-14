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
package io.sapl.springdatamongoreactive.queries;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Query;

import java.util.ArrayList;

@UtilityClass
public class QuerySelectionUtils {

    /**
     * Receives the selection object from the obligation, extracts the values from
     * the "column" key and checks whether it is a whitelist or blacklist. The
     * fields of the query are then expanded with the values. The "fields" property
     * is part of the {@link org.springframework.data.mongodb.repository.Query} in
     * mongodb and defines which properties of the domain object will be part of the
     * query result.
     *
     * @param <T> is type of {@link Query}
     * @param selection is the jsonNode object of the obligation
     * @param query is the query which fields are extended.
     * @return the new query with extended fields.
     */
    public static <T extends Query> T addSelectionPartToQuery(JsonNode selection, T query) {
        final var elements  = selection.get("columns").elements();
        final var fieldList = new ArrayList<String>();

        while (elements.hasNext()) {
            final var element = elements.next();
            fieldList.add(element.asText());
        }

        if ("whitelist".equals(selection.get("type").asText())) {
            for (String field : fieldList) {
                query.fields().include(field);
            }
        } else {
            for (String field : fieldList) {
                query.fields().exclude(field);
            }
        }

        return query;
    }

}
