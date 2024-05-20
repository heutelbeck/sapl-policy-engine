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

import java.util.Arrays;
import org.springframework.stereotype.Component;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;
import lombok.NoArgsConstructor;

@Component
@NoArgsConstructor
@FunctionLibrary(name = SqlFunctions.NAME, description = SqlFunctions.DESCRIPTION)
public class SqlFunctions {

    public static final String NAME        = "sqlFunctions";
    public static final String DESCRIPTION = "Functions for postgis and mysql.";

    private static final String CHECK_SQL_SANITY_DOC = """
            Checks if a SQL-string contains dangerous operations to prevent SQL-injection.
            Returns the string in case its "sane", throws PolicyEvaluationException otherwise""";

    @Function(docs = CHECK_SQL_SANITY_DOC)
    public Val checkSqlSanity(@Text Val sqlString) {

        var sql = sqlString.getText();

        var configuration = getConfiguration();
        if (Arrays.stream(configuration).noneMatch(sql::contains)) {
            return sqlString;
        } else {

            throw new PolicyEvaluationException(String.format("Sql string %s ist not safe.", sql));
        }

    }

    private String[] getConfiguration() {

        return new String[] { "delete", "drop", "insert", "update", "alter", "grant", "revoke", ";" };
    }

}
