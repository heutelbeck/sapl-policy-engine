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
package io.sapl.springdatamongoreactive.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.repository.query.parser.PartTree;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;

class SaplPartTreeCriteriaCreatorTests {

    @Test
    void when_create_then_createCriteriaDefinition() {
        // GIVEN
        var          manipulatedPartTree         = new PartTree("findAllByAgeAfter", TestUser.class);
        List<Object> allParametersValueAsObjects = List.of(14);
        var          criteria                    = new Criteria().andOperator(Criteria.where("age").gt(14));

        // WHEN
        var result = SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);

        // THEN
        assertEquals(result, criteria);
    }

    @Test
    void when_create_then_createCriteriaDefinition2() {
        // GIVEN
        var          manipulatedPartTree         = new PartTree("findAllByAgeAfterAndFirstname", TestUser.class);
        List<Object> allParametersValueAsObjects = List.of(14, "Juni");
        var          criteria                    = new Criteria().andOperator(Criteria.where("age").gt(14),
                Criteria.where("firstname").is("Juni"));

        // WHEN
        var result = SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);

        // THEN
        assertEquals(result, criteria);
    }

    @Test
    void when_create_then_createCriteriaDefinition3() {
        // GIVEN
        var          manipulatedPartTree         = new PartTree("findAllByAgeBeforeAndFirstnameLikeAndAdminIs",
                TestUser.class);
        List<Object> allParametersValueAsObjects = List.of(14, "Juni", true);
        var          criteria                    = new Criteria().andOperator(Criteria.where("age").lt(14),
                Criteria.where("firstname").regex("Juni"), Criteria.where("admin").is(true));

        // WHEN
        var result = SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);

        // THEN
        assertEquals(result, criteria);
    }

    @Test
    void when_create_then_createCriteriaDefinition4() {
        // GIVEN
        var          manipulatedPartTree         = new PartTree(
                "findAllByAgeBeforeAndFirstnameLikeAndAdminIsOrFirstnameIs", TestUser.class);
        List<Object> allParametersValueAsObjects = List.of(14, "Juni", true, "August");
        var          criteria                    = new Criteria().andOperator(Criteria.where("age").lt(14),
                Criteria.where("firstname").regex("Juni"), Criteria.where("admin").is(true))
                .orOperator(Criteria.where("firstname").is("August"));

        // WHEN
        var result = SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);

        // THEN
        assertEquals(result, criteria);
    }

    @Test
    void when_create_then_createCriteriaDefinition5() {
        // GIVEN
        var          manipulatedPartTree         = new PartTree("findAllByAgeIsNot", TestUser.class);
        List<Object> allParametersValueAsObjects = List.of(14);
        var          criteria                    = new Criteria().andOperator(Criteria.where("age").ne(14));

        // WHEN
        var result = SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);

        // THEN
        assertEquals(result, criteria);
    }

    @Test
    void when_create_then_createCriteriaDefinition6() {
        // GIVEN
        var          manipulatedPartTree         = new PartTree("findAllByAgeIsGreaterThan", TestUser.class);
        List<Object> allParametersValueAsObjects = List.of(14);
        var          criteria                    = new Criteria().andOperator(Criteria.where("age").gt(14));

        // WHEN
        var result = SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);

        // THEN
        assertEquals(result, criteria);
    }

    @Test
    void when_create_then_createCriteriaDefinition7() {
        // GIVEN
        var          manipulatedPartTree         = new PartTree("findAllByAgeIsGreaterThanEqual", TestUser.class);
        List<Object> allParametersValueAsObjects = List.of(14);
        var          criteria                    = new Criteria().andOperator(Criteria.where("age").gte(14));

        // WHEN
        var result = SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);

        // THEN
        assertEquals(result, criteria);
    }

    @Test
    void when_create_then_createCriteriaDefinition8() {
        // GIVEN
        var          manipulatedPartTree         = new PartTree("findAllByAgeIsLessThanEqual", TestUser.class);
        List<Object> allParametersValueAsObjects = List.of(14);
        var          criteria                    = new Criteria().andOperator(Criteria.where("age").lte(14));

        // WHEN
        var result = SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);

        // THEN
        assertEquals(result, criteria);
    }

    @Test
    void when_create_then_createCriteriaDefinition9() {
        // GIVEN
        var          manipulatedPartTree         = new PartTree("findAllByAgeIsLessThan", TestUser.class);
        List<Object> allParametersValueAsObjects = List.of(14);
        var          criteria                    = new Criteria().andOperator(Criteria.where("age").lt(14));

        // WHEN
        var result = SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);

        // THEN
        assertEquals(result, criteria);
    }

    @Test
    void when_create_then_createCriteriaDefinition10() {
        // GIVEN
        var          manipulatedPartTree         = new PartTree("findAllByAgeIsNull", TestUser.class);
        List<Object> allParametersValueAsObjects = List.of(23);
        var          criteria                    = new Criteria().andOperator(Criteria.where("age").is(null));

        // WHEN
        var result = SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);

        // THEN
        assertEquals(result, criteria);
    }

    @Test
    void when_create_then_createCriteriaDefinition11() {
        // GIVEN
        var          manipulatedPartTree         = new PartTree("findAllByAgeIsNotNull", TestUser.class);
        List<Object> allParametersValueAsObjects = List.of(123);
        var          criteria                    = new Criteria().andOperator(Criteria.where("age").ne(null));

        // WHEN
        var result = SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);

        // THEN
        assertEquals(result, criteria);
    }

    @Test
    void when_create_then_throwIllegalArgumentException() {
        // GIVEN
        var          manipulatedPartTree         = new PartTree("findAllByAgeNear", TestUser.class);
        List<Object> allParametersValueAsObjects = List.of(12);

        var accessIllegalArgumentException = assertThrows(IllegalArgumentException.class, () -> {
            SaplPartTreeCriteriaCreator.create(allParametersValueAsObjects, manipulatedPartTree);
        });

        var errorMessage = "Unsupported part type: NEAR (1): [IsNear, Near]";
        // WHEN

        // THEN
        assertEquals(errorMessage, accessIllegalArgumentException.getMessage());
    }
}
