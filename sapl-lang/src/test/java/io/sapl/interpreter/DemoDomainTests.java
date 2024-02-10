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
package io.sapl.interpreter;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.FilterFunctionLibrary;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;

class DemoDomainTests {
    private static final ObjectMapper               MAPPER           = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DefaultSAPLInterpreter     INTERPRETER      = new DefaultSAPLInterpreter();
    private static final AnnotationAttributeContext ATTRIBUTE_CTX    = new AnnotationAttributeContext();
    private static final AnnotationFunctionContext  FUNCTION_CTX     = new AnnotationFunctionContext();
    private static final Map<String, Val>           SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());

    @BeforeEach
    public void setUp() throws InitializationException {
        FUNCTION_CTX.loadLibrary(SimpleFunctionLibrary.class);
        FUNCTION_CTX.loadLibrary(FilterFunctionLibrary.class);
        var systemUTC                   = Clock.systemUTC();
        var simpleFilterFunctionLibrary = new SimpleFilterFunctionLibrary(systemUTC);
        FUNCTION_CTX.loadLibrary(simpleFilterFunctionLibrary);
    }

    @Test
    void recursiveDescendTest() throws JsonProcessingException {
        var authorizationSubscription = MAPPER.readValue("""
                   {
                       "subject":{
                                     "authorities"   : [{"authority":"DOCTOR"}],
                                     "details"       : null,
                                     "authenticated" : true,
                                     "principal"     : "Julia",
                                     "credentials"   : null,
                                     "name"          : "Julia"
                        },
                        "action"     :   "use",
                        "resource"       :   "ui:view:patients:createPatientButton",
                        "environment"    : null
                   }
                """, AuthorizationSubscription.class);
        var policy                    = """
                   policy "all authenticated users may see patient list"
                   permit "getPatients" in action..java.name
                """;

        var expectedDecision = AuthorizationDecision.NOT_APPLICABLE;

        assertThat(
                INTERPRETER.evaluate(authorizationSubscription, policy, ATTRIBUTE_CTX, FUNCTION_CTX, SYSTEM_VARIABLES)
                        .blockFirst(),
                equalTo(expectedDecision));
    }
}
