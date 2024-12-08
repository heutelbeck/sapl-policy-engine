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
package io.sapl.geo.functionlibraries;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.util.Assert;

import io.sapl.api.interpreter.Val;

class SqlFunctionsTests {

    private Val errorVal = Val.error("Error validating input. Input-string failed sanitization");

    @Test
    void CheckForControlCharactersPass() {
        final var sql        = Val.of("Select * from table where name < 'test-1' and date > 12-12-2000");
        final var checkedSql = SqlFunctions.assertNoSqlControlChars(sql);
        Assert.equals(Val.TRUE, checkedSql);
    }

    @Test
    void CheckForControlCharacters2() {
        final var sql    = Val.of(
                "SELECT id, value FROM table WHERE name IN (SELECT name, someField FROM table2 WHERE id = 'someNumber')");
        final var result = SqlFunctions.assertNoSqlControlChars(sql);
        assertEquals(Val.TRUE, result);
    }

    @Test
    void CheckForControlCharactersError() {
        final var sql = SqlFunctions
                .assertNoSqlControlChars(Val.of("Select * from table where name = 'test;drop table'"));
        assertEquals(errorVal, sql);
    }

    @Test
    void CheckForControlCharactersError2() {
        final var sql = SqlFunctions.assertNoSqlControlChars(Val.of("Select * from table where name = @setvalue = 1"));
        assertEquals(errorVal, sql);
    }

    @Test
    void CheckForKeywordsPass() {
        final var sql        = Val.of("Select * from table where name < 'test-1' and date > 12-12-2000");
        final var checkedSql = SqlFunctions.assertNoSqlKeywords(sql);
        Assert.equals(Val.TRUE, checkedSql);
    }

    @Test
    void CheckForKeywordsError() {
        final var sql = SqlFunctions
                .assertNoSqlKeywords(Val.of("Select (drop table table1) from table where name = 'test'"));
        assertEquals(errorVal, sql);
    }

    @Test
    void CheckForKeywordsError2() {
        final var sql = SqlFunctions
                .assertNoSqlKeywords(Val.of("Select * from table where name in (TRUNCATE table table1)"));
        assertEquals(errorVal, sql);
    }
}