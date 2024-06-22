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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import io.sapl.api.functions.Function;
import io.sapl.api.functions.FunctionLibrary;
import io.sapl.api.interpreter.Val;
import io.sapl.api.validation.Text;

@Component
@FunctionLibrary(name = SqlFunctions.NAME, description = SqlFunctions.DESCRIPTION)
public class SqlFunctions {

    public static final String NAME        = "sqlFunctions";
    public static final String DESCRIPTION = "Functions for postgis and mysql.";

    private static final String CHECK_FOR_KEYWORDS_DOC = """
            Returns Val.Error() if string contains sql-keywords except SELECT. 
            Returns the original Val otherwise. """;

    private static final String CHECK_FOR_CONTROL_CHARS = """
            Returns Val.Error() if string contains control chars except < > * = ' ( ) , - and whitespace.
            Returns the original Val otherwise.""";
    
    //true if sql contains sth. except numbers, letters, less and greater than, star, equal, prime, whitespace, brackets, comma, minus
    private static final String REGEX_CONTROL_CHARS = "^(?![0-9a-zA-Z<>*='\s(),-]*$).*$";
     
	//true if sql contains update, delete...
    private static final String REGEX_KEYWORDS = 
		   "(?i).*\\b(UPDATE|DELETE|TRUNCATE|DROP|ALTER|CREATE|INSERT|MERGE|CALL|EXEC|RENAME|SET|BEGIN|COMMIT|ROLLBACK)\\b.*";
   
    private static final String VALIDATION_ERROR = "Error validating input.";
    private final Pattern patternControlChars;
    private final Pattern patternSelect;
    
    
    public SqlFunctions() {
    	
    	patternControlChars = Pattern.compile(REGEX_CONTROL_CHARS);
    	patternSelect = Pattern.compile(REGEX_KEYWORDS);
    }
    
    
    @Function(docs = CHECK_FOR_CONTROL_CHARS)
    public Val checkForControlCharacters(@Text Val sqlString) {

       return validate(sqlString, patternControlChars);
    }

    
    @Function(docs = CHECK_FOR_KEYWORDS_DOC)
    public Val checkForKeywords(@Text Val sqlString) {

    	return validate(sqlString, patternSelect);
    	
    }
    
    
    private Val validate(Val input, Pattern pattern) {
        
    	Matcher matcher = pattern.matcher(input.getText());  
    	
    	if(matcher.matches()) {
     	   
     	   return Val.error(VALIDATION_ERROR);
        }else {
     	   return input;
        }
    }
    
    
}
