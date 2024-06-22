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
package io.sapl.geo.functions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.util.Assert;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.functionlibraries.SqlFunctions;
import io.sapl.interpreter.InitializationException;

class SqlFunctionsTests {

    private SqlFunctions sqlFunctions = new SqlFunctions();
    private Val          errorVal     = Val.error("Error validating input.");

    @Test
    void CheckForControlCharactersPass() {

        var sql        = Val.of("Select * from table where name < 'test-1' and date > 12-12-2000");
        var checkedSql = sqlFunctions.checkForControlCharacters(sql);
        Assert.equals(sql, checkedSql);
    }

    @Test
    void CheckForControlCharacters2() {

        var exp    = Val.of(
                "SELECT id, value FROM table WHERE name IN (SELECT name, someField FROM table2 WHERE id = 'someNumber')");
        var result = sqlFunctions.checkForControlCharacters(exp);
        assertEquals(exp, result);

    }

    @Test
    void CheckForControlCharactersError() {

        var sql = sqlFunctions.checkForControlCharacters(Val.of("Select * from table where name = 'test;drop table'"));

        assertEquals(errorVal, sql);

    }

    @Test
    void CheckForControlCharactersError2() {

        var sql = sqlFunctions.checkForControlCharacters(Val.of("Select * from table where name = @setvalue = 1"));

        assertEquals(errorVal, sql);

    }

    @Test
    void CheckForKeywordsPass() {

        var sql        = Val.of("Select * from table where name < 'test-1' and date > 12-12-2000");
        var checkedSql = sqlFunctions.checkForKeywords(sql);
        Assert.equals(sql, checkedSql);
    }

    @Test
    void CheckForKeywordsError() {

        var sql = sqlFunctions.checkForKeywords(Val.of("Select (drop table table1) from table where name = 'test'"));

        assertEquals(errorVal, sql);

    }

    @Test
    void CheckForKeywordsError2() {

        var sql = sqlFunctions.checkForKeywords(Val.of("Select * from table where name in (truncate table table1)"));

        assertEquals(errorVal, sql);

    }

}
