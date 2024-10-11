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
package io.sapl.springdatar2dbc.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

class ConvertToSQLTests {

    @Test
    void when_getSorting_then_returnSortASCAsString() {
        // GIVEN
        final var query      = "SELECT * FROM PERSON WHERE age > 22";
        final var parameters = new Object[] { 12, Sort.by(Sort.Direction.ASC, "age") };
        final var expected   = " ORDER BY age ASC";

        // WHEN
        final var result = ConvertToSQL.getSorting(query, parameters);

        // THEN
        assertEquals(result.toString(), expected);
    }

    @Test
    void when_getSorting_then_returnSortDESCAsString() {
        // GIVEN
        final var query      = "SELECT * FROM PERSON WHERE age > 22";
        final var parameters = new Object[] { 12, Sort.by(Sort.Direction.DESC, "age") };
        final var expected   = " ORDER BY age DESC";

        // WHEN
        final var result = ConvertToSQL.getSorting(query, parameters);

        // THEN
        assertEquals(result.toString(), expected);
    }

    @Test
    void when_getSorting_then_returnEmptyString() {
        // GIVEN
        final var query      = "SELECT * FROM PERSON WHERE age > 22";
        final var parameters = new Object[] { 12, Sort.unsorted() };
        final var expected   = "";

        // WHEN
        final var result = ConvertToSQL.getSorting(query, parameters);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_getSorting_then_returnEmptyString1() {
        // GIVEN
        final var query      = "SELECT * FROM PERSON WHERE age > 22";
        final var parameters = new Object[] {};
        final var expected   = "";

        // WHEN
        final var result = ConvertToSQL.getSorting(query, parameters);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_getSorting_then_returnEmptyString2() {
        // GIVEN
        final var query      = "SELECT * FROM PERSON WHERE age > 22 ORDER BY age DESC";
        final var parameters = new Object[] { 12, Sort.by(Sort.Direction.DESC, "age") };
        final var expected   = "";

        // WHEN
        final var result = ConvertToSQL.getSorting(query, parameters);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_conditions_then_returnConditionsAsString() {
        // GIVEN
        final var saplConditionList = List.of(new SqlCondition(PropositionalConnectives.AND, "age > 22"),
                new SqlCondition(PropositionalConnectives.OR, "firstname = 'Juni'"),
                new SqlCondition(PropositionalConnectives.AND, "admin == true"));
        final var expected          = "age > 22 OR firstname = 'Juni' AND admin == true";

        // WHEN
        final var result = ConvertToSQL.conditions(saplConditionList);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_conditions_then_returnEmptyString() {
        // GIVEN
        final var saplConditionList = new ArrayList<SqlCondition>();
        final var expected          = "";

        // WHEN
        final var result = ConvertToSQL.conditions(saplConditionList);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting1() {
        // GIVEN
        final var sortPartTree = Sort.by(Sort.Direction.DESC, "age");
        final var arguments    = new Object[] { 12, PageRequest.of(2, 2, Sort.unsorted()) };
        final var expected     = " ORDER BY age DESC LIMIT 2 OFFSET 2";

        // WHEN
        final var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting2() {
        // GIVEN
        Sort      sortPartTree = null;
        final var arguments    = new Object[] { 12, PageRequest.of(2, 2, Sort.by(Sort.Direction.DESC, "age")) };
        final var expected     = " ORDER BY age DESC LIMIT 2 OFFSET 2";

        // WHEN
        final var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting3() {
        // GIVEN
        Sort      sortPartTree = null;
        final var arguments    = new Object[] { 12 };
        final var expected     = "";

        // WHEN
        final var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting4() {
        // GIVEN
        final var sortPartTree = Sort.unsorted();
        final var arguments    = new Object[] { 12, PageRequest.of(2, 2, Sort.by(Sort.Direction.DESC, "age")) };
        final var expected     = " ORDER BY age DESC LIMIT 2 OFFSET 2";

        // WHEN
        final var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting5() {
        // GIVEN
        final var sortPartTree = Sort.by(Sort.Direction.ASC, "firstname");
        final var arguments    = new Object[] { 12, PageRequest.of(2, 2, Sort.by(Sort.Direction.ASC, "age")) };
        final var expected     = " ORDER BY firstname ASC LIMIT 2 OFFSET 2";

        // WHEN
        final var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting6() {
        // GIVEN
        final var sortPartTree = Sort.by(Sort.Direction.ASC, "firstname");
        final var arguments    = new Object[] { 12, Sort.by(Sort.Direction.ASC, "age") };
        final var expected     = " ORDER BY firstname ASC, age ASC";

        // WHEN
        final var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting7() {
        // GIVEN
        Sort      sortPartTree = null;
        final var arguments    = new Object[] { 12, Sort.by(Sort.Direction.ASC, "age") };
        final var expected     = " ORDER BY age ASC";

        // WHEN
        final var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting8() {
        // GIVEN
        Sort      sortPartTree = null;
        final var arguments    = new Object[] { 12, Sort.unsorted() };
        final var expected     = "";

        // WHEN
        final var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting9() {
        // GIVEN
        Sort      sortPartTree = Sort.unsorted();
        final var arguments    = new Object[] { 12, Sort.unsorted() };
        final var expected     = "";

        // WHEN
        final var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }
}
