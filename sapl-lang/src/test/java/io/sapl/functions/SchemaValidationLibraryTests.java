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
package io.sapl.functions;

import static io.sapl.functions.SchemaValidationLibrary.isCompliant;
import static io.sapl.hamcrest.Matchers.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.sapl.api.interpreter.Val;

class SchemaValidationLibraryTests {

    private static final String COMPLIANT_JSON = """
            {
                "name": "Alice",
                "age" : 25
            }
            """;

    private static final String NONCOMPLIANT_VALID_JSON = """
            {
                "name": "Alice",
                "age" : "25"
            }
            """;

    private static final String VALID_SCHEMA = """
            {
                "type": "object",
                "properties": {
                    "name": { "type": "string" },
                    "age" : { "type": "integer" }
                }
            }
            """;

    @Test
    void when_subjectIsCompliant_then_returnTrue() throws JsonProcessingException {
        var result = isCompliant(Val.ofJson(COMPLIANT_JSON), Val.ofJson(VALID_SCHEMA));
        assertThat(result, is(val(true)));
    }

    @Test
    void when_subjectIsNonCompliant_then_returnFalse() throws JsonProcessingException {
        var result = isCompliant(Val.ofJson(NONCOMPLIANT_VALID_JSON), Val.ofJson(VALID_SCHEMA));
        assertThat(result, is(val(false)));
    }

    @Test
    void when_subjectIsUndefined_then_returnFalse() throws JsonProcessingException {
        var result = isCompliant(Val.UNDEFINED, Val.ofJson(VALID_SCHEMA));
        assertThat(result, is(val(false)));
    }

    @Test
    void when_subjectIsError_then_errorPropagates() throws JsonProcessingException {
        var result = isCompliant(Val.error("test"), Val.ofJson(VALID_SCHEMA));
        assertThat(result, is(valError("test")));
    }
}
