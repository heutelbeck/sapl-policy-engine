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
package io.sapl.spring.data.r2dbc.queries;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sapl.spring.data.r2dbc.database.Person;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuerySelectionUtilsTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static ArrayNode selectionsBlacklist;
    private static ArrayNode selectionsWhitelist;
    private static ArrayNode selectionsAliasBlacklist;
    private static ArrayNode selectionsAlisasWhitelist;
    private static ArrayNode selectionsAliasIsEmptyWhitelist;
    private static ArrayNode transformations;

    @BeforeAll
    static void initJsonNodes() throws JsonProcessingException {

        selectionsBlacklist = MAPPER.readValue("""
                [
                	{
                		"type": "blacklist",
                		"columns": ["firstname"]
                	},
                	{
                		"type": "whitelist",
                		"columns": ["age"]
                	}
                ]
                """, ArrayNode.class);

        selectionsWhitelist = MAPPER.readValue("""
                [
                	{
                		"type": "whitelist",
                		"columns": ["age","active"]
                	}
                ]
                """, ArrayNode.class);

        selectionsAliasBlacklist = MAPPER.readValue("""
                [
                	{
                		"type": "blacklist",
                		"columns": ["firstname", "age"],
                		"alias" : "p"
                	}
                ]
                """, ArrayNode.class);

        selectionsAlisasWhitelist = MAPPER.readValue("""
                [
                	{
                		"type": "whitelist",
                		"columns": ["firstname", "age"]
                	}
                ]
                """, ArrayNode.class);

        selectionsAliasIsEmptyWhitelist = MAPPER.readValue("""
                [
                	{
                		"type": "whitelist",
                		"columns": ["firstname", "age"]
                	}
                ]
                """, ArrayNode.class);
        transformations                 = MAPPER.readValue("""
                [
                	{
                		"firstname": "UPPER"
                	},
                	{
                		"birthday": "YEAR"
                	}
                ]
                """, ArrayNode.class);

    }

    @Test
    void when_createSelectionPartForMethodNameQuery_then_createSelectionPartBlacklist() {
        // GIVEN
        final var expected = "SELECT id,age,active FROM XXXXX WHERE ";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForMethodNameQuery(selectionsBlacklist,
                transformations, Person.class);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void when_createSelectionPartForMethodNameQuery_then_createSelectionPartWhitelist() {
        // GIVEN
        final var expected = "SELECT age,active FROM XXXXX WHERE ";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForMethodNameQuery(selectionsWhitelist,
                transformations, Person.class);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void when_createSelectionPartForMethodNameQuery_then_createSelectionPartAlias1() {
        // GIVEN
        final var expected = "SELECT id,active FROM XXXXX WHERE ";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForMethodNameQuery(selectionsAliasBlacklist,
                transformations, Person.class);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void when_createSelectionPartForMethodNameQuery_then_createSelectionPartAlias2() {
        // GIVEN
        final var expected = "SELECT UPPER(firstname),age FROM XXXXX WHERE ";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForMethodNameQuery(selectionsAlisasWhitelist,
                transformations, Person.class);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void when_createSelectionPartForMethodNameQuery_then_createSelectionPartAlias3() {
        // GIVEN
        final var expected = "SELECT UPPER(firstname),age FROM XXXXX WHERE ";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForMethodNameQuery(selectionsAliasIsEmptyWhitelist,
                transformations, Person.class);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void when_createSelectionPartForMethodNameQuery_then_returnEmptyStringBecauseNoCondition() {
        // GIVEN
        final var expected = "SELECT * FROM XXXXX WHERE ";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForMethodNameQuery(MAPPER.createArrayNode(),
                transformations, Person.class);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void when_createSelectionPartForAnnotation_then_returnSelectionPart() {
        // GIVEN
        final var expected  = "SELECT id,age,active FROM Person WHERE firstname = 'Juni'";
        final var baseQuery = "SELECT * FROM Person WHERE firstname = 'Juni'";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForAnnotation(baseQuery, selectionsBlacklist,
                transformations, "", Person.class);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void when_createSelectionPartForAnnotation_then_returnQueryBecauseSelectionIsEmpty() {
        // GIVEN
        final var baseQuery = "SELECT * FROM Person WHERE firstname = 'Juni'";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForAnnotation(baseQuery, MAPPER.createArrayNode(),
                transformations, "", Person.class);

        // THEN
        assertEquals(baseQuery, actual);
    }

    @Test
    void when_createSelectionPartForAnnotation_then_returnQueryBecauseSelectionAndTransformationIsEmpty() {
        // GIVEN
        final var baseQuery = "SELECT * FROM Person WHERE firstname = 'Juni'";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForAnnotation(baseQuery, MAPPER.createArrayNode(),
                MAPPER.createArrayNode(), "", Person.class);

        // THEN
        assertEquals(baseQuery, actual);
    }

    @Test
    void when_createSelectionPartForAnnotation_then_returnQueryBecauseSelectionIsEmpty2() {
        // GIVEN
        final var baseQuery = "SELECT lastname, birthday FROM Person WHERE firstname = 'Juni'";
        final var expected  = "SELECT lastname,YEAR(birthday) FROM Person WHERE firstname = 'Juni'";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForAnnotation(baseQuery, MAPPER.createArrayNode(),
                transformations, "", Person.class);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void when_createSelectionPartForAnnotation_then_returnQueryBecauseIsAsterixAndelectionIsEmpty() {
        // GIVEN
        final var baseQuery = "SELECT lastname,firstname FROM Person WHERE firstname = 'Juni'";
        final var expected  = "SELECT id,age,active FROM Person WHERE firstname = 'Juni'";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForAnnotation(baseQuery, selectionsBlacklist,
                transformations, "", Person.class);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void when_createSelectionPartForAnnotation_then_returnQueryBecauseAliasIsNotEmpty() {
        // GIVEN
        final var baseQuery = "SELECT age,firstname FROM Person WHERE firstname = 'Juni'";
        final var expected  = "SELECT UPPER(p.firstname),p.age FROM Person WHERE firstname = 'Juni'";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForAnnotation(baseQuery, selectionsAlisasWhitelist,
                transformations, "p", Person.class);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void when_createSelectionPartForAnnotation_then_returnQueryBecauseBlacklistRemovesField() {
        // GIVEN
        final var baseQuery = "SELECT active FROM Person WHERE firstname = 'Juni'";
        final var expected  = "SELECT id,active FROM Person WHERE firstname = 'Juni'";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForAnnotation(baseQuery, selectionsAliasBlacklist,
                transformations, "", Person.class);

        // THEN
        assertEquals(expected, actual);
    }

    @Test
    void when_createSelectionPartForAnnotation_then_returnQueryBecauseBlacklistRemovesField1() {
        // GIVEN
        final var baseQuery = "SELECT active FROM Person WHERE firstname = 'Juni'";
        final var expected  = "SELECT id,active FROM Person WHERE firstname = 'Juni'";

        // WHEN
        final var actual = QuerySelectionUtils.createSelectionPartForAnnotation(baseQuery, selectionsAliasBlacklist,
                MAPPER.createArrayNode(), "", Person.class);

        // THEN
        assertEquals(expected, actual);
    }

}
