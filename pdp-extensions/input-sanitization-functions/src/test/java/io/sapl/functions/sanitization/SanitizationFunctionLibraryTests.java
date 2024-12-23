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
package io.sapl.functions.sanitization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class SanitizationFunctionLibraryTests {

    @Test
    void CheckForControlCharactersPass() {
        final var sql        = Val.of("Select * from table where name < 'test-1' and date > 12-12-2000");
        final var checkedSql = SanitizationFunctionLibrary.assertNoSqlControlChars(sql);
        assertEquals(sql, checkedSql);
    }

    @Test
    void CheckForControlCharacters2() {
        final var sql    = Val.of(
                "SELECT id, value FROM table WHERE name IN (SELECT name, someField FROM table2 WHERE id = 'someNumber')");
        final var result = SanitizationFunctionLibrary.assertNoSqlControlChars(sql);
        assertEquals(sql, result);
    }

    @Test
    void CheckForControlCharactersError() {
        final var sql = SanitizationFunctionLibrary
                .assertNoSqlControlChars(Val.of("Select * from table where name = 'test;drop table'"));
        assertEquals(Val.error(SanitizationFunctionLibrary.CONTROL_CHARACTERS_FOUND_ERROR), sql);
    }

    @Test
    void CheckForControlCharactersError2() {
        final var sql = SanitizationFunctionLibrary
                .assertNoSqlControlChars(Val.of("Select * from table where name = @setvalue = 1"));
        assertEquals(Val.error(SanitizationFunctionLibrary.CONTROL_CHARACTERS_FOUND_ERROR), sql);
    }

    @Test
    void CheckForKeywordsPass() {
        final var sql        = Val.of("Select * from table where name < 'test-1' and date > 12-12-2000");
        final var checkedSql = SanitizationFunctionLibrary.assertNoSqlKeywords(sql);
        assertEquals(sql, checkedSql);
    }

    @Test
    void CheckForKeywordsError() {
        final var sql = SanitizationFunctionLibrary
                .assertNoSqlKeywords(Val.of("Select (drop table table1) from table where name = 'test'"));
        assertEquals(Val.error(SanitizationFunctionLibrary.KEYWORD_FOUND_ERROR), sql);
    }

    @Test
    void CheckForKeywordsErrorNoSql() {
        final var sql = SanitizationFunctionLibrary
                .assertNoSql(Val.of("Select (drop table table1) from table where name = 'test'"));
        assertEquals(Val.error(SanitizationFunctionLibrary.KEYWORD_FOUND_ERROR), sql);
    }

    @Test
    void CheckForControlCharactersErrorNoSql() {
        final var sql = SanitizationFunctionLibrary
                .assertNoSql(Val.of("Select * from table where name = 'test;drop table'"));
        assertEquals(Val.error(SanitizationFunctionLibrary.CONTROL_CHARACTERS_FOUND_ERROR), sql);
    }

    @Test
    void CheckForKeywordsError2() {
        final var sql = SanitizationFunctionLibrary
                .assertNoSqlKeywords(Val.of("Select * from table where name in (TRUNCATE table table1)"));
        assertEquals(Val.error(SanitizationFunctionLibrary.KEYWORD_FOUND_ERROR), sql);
    }
}
