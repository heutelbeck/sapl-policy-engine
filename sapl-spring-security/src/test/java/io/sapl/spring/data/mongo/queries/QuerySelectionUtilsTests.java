/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.data.mongo.queries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.BasicQuery;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuerySelectionUtilsTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void when_addSelectionPartToQuery_then_returnQueryWithSelection() throws JsonProcessingException {
        // GIVEN
        final var selection  = MAPPER.readTree("""
                {
                		"type": "whitelist",
                		"columns": ["firstname"]
                }
                """);
        final var basicQuery = new BasicQuery("{'firstname': 'Susi'}, {'firstname': 0}");

        // WHEN
        final var result = QuerySelectionUtils.addSelectionPartToQuery(selection, basicQuery);

        // THEN
        assertEquals("Query: { \"firstname\" : \"Susi\"}, Fields: { \"firstname\" : 1}, Sort: {}", result.toString());
    }

}
