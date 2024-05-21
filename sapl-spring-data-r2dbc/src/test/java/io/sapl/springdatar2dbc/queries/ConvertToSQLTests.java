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
        var query      = "SELECT * FROM PERSON WHERE age > 22";
        var parameters = new Object[] { 12, Sort.by(Sort.Direction.ASC, "age") };
        var expected   = " ORDER BY age ASC";

        // WHEN
        var result = ConvertToSQL.getSorting(query, parameters);

        // THEN
        assertEquals(result.toString(), expected);
    }

    @Test
    void when_getSorting_then_returnSortDESCAsString() {
        // GIVEN
        var query      = "SELECT * FROM PERSON WHERE age > 22";
        var parameters = new Object[] { 12, Sort.by(Sort.Direction.DESC, "age") };
        var expected   = " ORDER BY age DESC";

        // WHEN
        var result = ConvertToSQL.getSorting(query, parameters);

        // THEN
        assertEquals(result.toString(), expected);
    }

    @Test
    void when_getSorting_then_returnEmptyString() {
        // GIVEN
        var query      = "SELECT * FROM PERSON WHERE age > 22";
        var parameters = new Object[] { 12, Sort.unsorted() };
        var expected   = "";

        // WHEN
        var result = ConvertToSQL.getSorting(query, parameters);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_getSorting_then_returnEmptyString1() {
        // GIVEN
        var query      = "SELECT * FROM PERSON WHERE age > 22";
        var parameters = new Object[] {};
        var expected   = "";

        // WHEN
        var result = ConvertToSQL.getSorting(query, parameters);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_getSorting_then_returnEmptyString2() {
        // GIVEN
        var query      = "SELECT * FROM PERSON WHERE age > 22 ORDER BY age DESC";
        var parameters = new Object[] { 12, Sort.by(Sort.Direction.DESC, "age") };
        var expected   = "";

        // WHEN
        var result = ConvertToSQL.getSorting(query, parameters);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_conditions_then_returnConditionsAsString() {
        // GIVEN
        var saplConditionList = List.of(new SqlCondition(PropositionalConnectives.AND, "age > 22"),
                new SqlCondition(PropositionalConnectives.OR, "firstname = 'Juni'"),
                new SqlCondition(PropositionalConnectives.AND, "admin == true"));
        var expected          = "age > 22 OR firstname = 'Juni' AND admin == true";

        // WHEN
        var result = ConvertToSQL.conditions(saplConditionList);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_conditions_then_returnEmptyString() {
        // GIVEN
        var saplConditionList = new ArrayList<SqlCondition>();
        var expected          = "";

        // WHEN
        var result = ConvertToSQL.conditions(saplConditionList);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting1() {
        // GIVEN
        var sortPartTree = Sort.by(Sort.Direction.DESC, "age");
        var arguments    = new Object[] { 12, PageRequest.of(2, 2, Sort.unsorted()) };
        var expected     = " ORDER BY age DESC LIMIT 2 OFFSET 2";

        // WHEN
        var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting2() {
        // GIVEN
        Sort sortPartTree = null;
        var  arguments    = new Object[] { 12, PageRequest.of(2, 2, Sort.by(Sort.Direction.DESC, "age")) };
        var  expected     = " ORDER BY age DESC LIMIT 2 OFFSET 2";

        // WHEN
        var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting3() {
        // GIVEN
        Sort sortPartTree = null;
        var  arguments    = new Object[] { 12 };
        var  expected     = "";

        // WHEN
        var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting4() {
        // GIVEN
        var sortPartTree = Sort.unsorted();
        var arguments    = new Object[] { 12, PageRequest.of(2, 2, Sort.by(Sort.Direction.DESC, "age")) };
        var expected     = " ORDER BY age DESC LIMIT 2 OFFSET 2";

        // WHEN
        var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting5() {
        // GIVEN
        var sortPartTree = Sort.by(Sort.Direction.ASC, "firstname");
        var arguments    = new Object[] { 12, PageRequest.of(2, 2, Sort.by(Sort.Direction.ASC, "age")) };
        var expected     = " ORDER BY firstname ASC LIMIT 2 OFFSET 2";

        // WHEN
        var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting6() {
        // GIVEN
        var sortPartTree = Sort.by(Sort.Direction.ASC, "firstname");
        var arguments    = new Object[] { 12, Sort.by(Sort.Direction.ASC, "age") };
        var expected     = " ORDER BY firstname ASC, age ASC";

        // WHEN
        var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting7() {
        // GIVEN
        Sort sortPartTree = null;
        var  arguments    = new Object[] { 12, Sort.by(Sort.Direction.ASC, "age") };
        var  expected     = " ORDER BY age ASC";

        // WHEN
        var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting8() {
        // GIVEN
        Sort sortPartTree = null;
        var  arguments    = new Object[] { 12, Sort.unsorted() };
        var  expected     = "";

        // WHEN
        var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_prepareAndMergeSortObjects_then_returnSorting9() {
        // GIVEN
        Sort sortPartTree = Sort.unsorted();
        var  arguments    = new Object[] { 12, Sort.unsorted() };
        var  expected     = "";

        // WHEN
        var result = ConvertToSQL.prepareAndMergeSortObjects(sortPartTree, arguments);

        // THEN
        assertEquals(expected, result);
    }
}
