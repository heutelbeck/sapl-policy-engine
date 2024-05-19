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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.util.Assert;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.functionlibraries.SqlFunctions;
import io.sapl.interpreter.InitializationException;


class SqlFunctionsTests {

	private SqlFunctions sqlFunctions = new SqlFunctions();
	
    @Test
    void CheckSqlSanityPass() throws InitializationException {
        
    	var sql = Val.of("Select * from table where name = 'test'");
        var checkedSql =  sqlFunctions.checkSqlSanity(sql);
        Assert.equals(sql, checkedSql);
    }
    
    @Test
    void CheckSqlSanityException() throws InitializationException {
        
    	var sql = Val.of("Select * from table where name = 'test';drop table");
        Assertions.assertThrows(PolicyEvaluationException.class, () -> sqlFunctions.checkSqlSanity(sql));
    }
    
}
