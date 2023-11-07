/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.hamcrest.Matchers.valUndefined;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import reactor.test.StepVerifier;

class FilterFunctionLibraryTests {

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final DefaultSAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private static final AnnotationAttributeContext ATTRIBUTE_CTX = new AnnotationAttributeContext();

    private static final AnnotationFunctionContext FUNCTION_CTX = new AnnotationFunctionContext();

    private static final Map<String, JsonNode> SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());

    private static final FilterFunctionLibrary LIBRARY = new FilterFunctionLibrary();

    @BeforeEach
    void setUp() throws InitializationException {
        FUNCTION_CTX.loadLibrary(LIBRARY);
    }

    @Test
    void blackenTooManyArguments() {
        var text                     = Val.of("abcde");
        var discloseLeft             = Val.of(2);
        var discloseRight            = Val.of(2);
        var replacement              = Val.of("x");
        var theArgumentThatIsTooMuch = Val.of(2);
        assertThrows(IllegalArgumentException.class, () -> FilterFunctionLibrary.blacken(text, discloseLeft,
                discloseRight, replacement, theArgumentThatIsTooMuch));
    }

    @Test
    void blackenNoString() {
        var notText = Val.of(2);
        assertThrows(IllegalArgumentException.class, () -> FilterFunctionLibrary.blacken(notText));
    }

    @Test
    void blackenReplacementNoString() {
        var text          = Val.of("abcde");
        var discloseLeft  = Val.of(2);
        var discloseRight = Val.of(2);
        var replacement   = Val.of(2);
        assertThrows(IllegalArgumentException.class,
                () -> FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight, replacement));
    }

    @Test
    void blackenReplacementNegativeRight() {
        var text          = Val.of("abcde");
        var discloseLeft  = Val.of(2);
        var discloseRight = Val.of(-2);
        assertThrows(IllegalArgumentException.class,
                () -> FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight));
    }

    @Test
    void blackenReplacementNegativeLeft() {
        var text          = Val.of("abcde");
        var discloseLeft  = Val.of(-2);
        var discloseRight = Val.of(2);
        assertThrows(IllegalArgumentException.class,
                () -> FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight));
    }

    @Test
    void blackenReplacementRightNoNumber() {
        var text          = Val.of("abcde");
        var discloseLeft  = Val.of(2);
        var discloseRight = Val.NULL;
        assertThrows(IllegalArgumentException.class,
                () -> FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight));
    }

    @Test
    void blackenReplacementLeftNoNumber() {
        var text          = Val.of("abcde");
        var discloseLeft  = Val.NULL;
        var discloseRight = Val.of(2);
        assertThrows(IllegalArgumentException.class,
                () -> FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight));
    }

    @Test
    void blackenWorking() {
        var given         = Val.of("abcde");
        var discloseLeft  = Val.of(1);
        var discloseRight = Val.of(1);
        var replacement   = Val.of("*");
        var actual        = FilterFunctionLibrary.blacken(given, discloseLeft, discloseRight, replacement);
        assertThat(actual, is(val("a***e")));
    }

    @Test
    void blackenWorkingAllVisible() {
        var text          = Val.of("abcde");
        var discloseLeft  = Val.of(3);
        var discloseRight = Val.of(3);
        var replacement   = Val.of("*");

        var result = FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight, replacement);

        assertThat(result, is(val("abcde")));
    }

    @Test
    void blackenReplacementDefault() {
        var text          = Val.of("abcde");
        var discloseLeft  = Val.of(1);
        var discloseRight = Val.of(1);
        var result        = FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight);
        assertThat(result, is(val("aXXXe")));
    }

    @Test
    void blackenDiscloseRightDefault() {
        var text         = Val.of("abcde");
        var discloseLeft = Val.of(2);

        var result = FilterFunctionLibrary.blacken(text, discloseLeft);
        assertThat(result, is(val("abXXX")));
    }

    @Test
    void blackenDiscloseLeftDefault() {
        var text   = Val.of("abcde");
        var result = FilterFunctionLibrary.blacken(text);
        assertThat(result, is(val("XXXXX")));
    }

    @Test
    void remove() {
        var result = FilterFunctionLibrary.remove(Val.of("abcde"));
        assertThat(result, is(valUndefined()));
    }

    @Test
    void blackenInPolicy() throws JsonProcessingException {
        var authzSubscription     = MAPPER.readValue(
                "{ \"resource\": {	\"array\": [ null, true ], \"key1\": \"abcde\" } }",
                AuthorizationSubscription.class);
        var policyDefinition      = "policy \"test\"	permit transform resource |- { @.key1 : filter.blacken(1) }";
        var expectedResource      = MAPPER.readValue("{	\"array\": [ null, true ], \"key1\": \"aXXXX\" }",
                JsonNode.class);
        var expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
                Optional.empty(), Optional.empty());

        StepVerifier
                .create(INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
                        SYSTEM_VARIABLES))
                .assertNext(authzDecision -> assertThat(authzDecision, is(expectedAuthzDecision))).verifyComplete();
    }

    @Test
    void replace() {
        var result = FilterFunctionLibrary.replace(Val.NULL, Val.of(1));
        assertThat(result, is(val(1)));
    }

    @Test
    void replaceInPolicy() throws JsonProcessingException {
        var authzSubscription     = MAPPER.readValue(
                "{ \"resource\": {	\"array\": [ null, true ], \"key1\": \"abcde\" } }",
                AuthorizationSubscription.class);
        var policyDefinition      = "policy \"test\" permit transform resource |- { @.array[1] : filter.replace(\"***\"), @.key1 : filter.replace(null) }";
        var expectedResource      = MAPPER.readValue("{	\"array\": [ null, \"***\" ], \"key1\": null }",
                JsonNode.class);
        var expectedAuthzDecision = new AuthorizationDecision(Decision.PERMIT, Optional.of(expectedResource),
                Optional.empty(), Optional.empty());

        StepVerifier
                .create(INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, FUNCTION_CTX,
                        SYSTEM_VARIABLES))
                .assertNext(authzDecision -> assertThat(authzDecision, is(expectedAuthzDecision))).verifyComplete();
    }

}
