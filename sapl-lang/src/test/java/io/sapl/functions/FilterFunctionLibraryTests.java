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

import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.hamcrest.Matchers.valUndefined;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    private static final ObjectMapper               MAPPER           = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DefaultSAPLInterpreter     INTERPRETER      = new DefaultSAPLInterpreter();
    private static final AnnotationAttributeContext ATTRIBUTE_CTX    = new AnnotationAttributeContext();
    private static final Map<String, JsonNode>      SYSTEM_VARIABLES = Collections.unmodifiableMap(new HashMap<>());

    private AnnotationFunctionContext functionCtx;

    @BeforeEach
    void setUp() throws InitializationException {
        functionCtx = new AnnotationFunctionContext();
        functionCtx.loadLibrary(FilterFunctionLibrary.class);
    }

    @Test
    void blackenTooManyArguments() {
        var val1 = Val.of("abcde");
        var val2 = Val.of(2);
        var val3 = Val.of(2);
        var val4 = Val.of(2);
        var val5 = Val.of(2);
        var val6 = Val.of(2);
        assertThrows(IllegalArgumentException.class,
                () -> FilterFunctionLibrary.blacken(val1, val2, val3, val4, val5, val6));
    }

    @Test
    void blackenNoArguments() {
        assertThrows(IllegalArgumentException.class, FilterFunctionLibrary::blacken);
    }

    @Test
    void blackenNoString() {
        var val1 = Val.of(2);
        assertThrows(IllegalArgumentException.class, () -> FilterFunctionLibrary.blacken(val1));
    }

    @Test
    void blackenReplacementNoString() {
        var val1 = Val.of("abcde");
        var val2 = Val.of(2);
        var val3 = Val.of(2);
        var val4 = Val.of(2);
        var val5 = Val.of(2);

        assertThrows(IllegalArgumentException.class, () -> FilterFunctionLibrary.blacken(val1, val2, val3, val4, val5));
    }

    @Test
    void blackenLengthWithInvalidNumber() {
        var val1 = Val.of("abcde");
        var val2 = Val.of(2);
        var val3 = Val.of(2);
        var val4 = Val.of(2);
        var val5 = Val.of(-1);

        assertThrows(IllegalArgumentException.class, () -> FilterFunctionLibrary.blacken(val1, val2, val3, val4, val5));
    }

    @Test
    void blackenLengthWithInvalidType() {
        var val1 = Val.of("abcde");
        var val2 = Val.of(2);
        var val3 = Val.of(2);
        var val4 = Val.of(2);
        var val5 = Val.of("a");

        assertThrows(IllegalArgumentException.class, () -> FilterFunctionLibrary.blacken(val1, val2, val3, val4, val5));
    }

    @Test
    void blackenReplacementNegativeRight() {
        var val1 = Val.of("abcde");
        var val2 = Val.of(2);
        var val3 = Val.of(-2);

        assertThrows(IllegalArgumentException.class, () -> FilterFunctionLibrary.blacken(val1, val2, val3));
    }

    @Test
    void blackenReplacementNegativeLeft() {
        var val1 = Val.of("abcde");
        var val2 = Val.of(-2);
        var val3 = Val.of(2);

        assertThrows(IllegalArgumentException.class, () -> FilterFunctionLibrary.blacken(val1, val2, val3));
    }

    @Test
    void blackenReplacementRightNoNumber() {
        var val1 = Val.of("abcde");
        var val2 = Val.of(-2);
        var val3 = Val.NULL;

        assertThrows(IllegalArgumentException.class, () -> FilterFunctionLibrary.blacken(val1, val2, val3));
    }

    @Test
    void blackenReplacementLeftNoNumber() {
        var val1 = Val.of("abcde");
        var val2 = Val.NULL;
        var val3 = Val.of(2);

        assertThrows(IllegalArgumentException.class, () -> FilterFunctionLibrary.blacken(val1, val2, val3));
    }

    @Test
    void blackenWorking() {
        var given         = Val.of("abcde");
        var discloseLeft  = Val.of(1);
        var discloseRight = Val.of(1);
        var replacement   = Val.of("*");
        var actual        = FilterFunctionLibrary.blacken(given, discloseLeft, discloseRight, replacement);

        assertTrue(actual.getText().startsWith("a"));
        assertTrue(actual.getText().endsWith("e"));

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

    @ParameterizedTest
    @CsvSource({ "2, a**e", "5, a*****e", "0, ae", })
    void blackenWorkingLength(int blackenLength, String expected) {
        var text          = Val.of("abcde");
        var discloseLeft  = Val.of(1);
        var discloseRight = Val.of(1);
        var replacement   = Val.of("*");
        var length        = Val.of(blackenLength);

        var result = FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight, replacement, length);

        assertThat(result, is(val(expected)));
    }

    @Test
    void blackenNonNumberLength() {
        var text          = Val.of("abcde");
        var discloseLeft  = Val.of(1);
        var discloseRight = Val.of(1);
        var replacement   = Val.of("*");
        var length        = Val.of("NOT A NUMBER");

        assertThrows(IllegalArgumentException.class,
                () -> FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight, replacement, length));
    }

    @Test
    void blackenNegativeLength() {
        var text          = Val.of("abcde");
        var discloseLeft  = Val.of(1);
        var discloseRight = Val.of(1);
        var replacement   = Val.of("*");
        var length        = Val.of(-1);

        assertThrows(IllegalArgumentException.class,
                () -> FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight, replacement, length));
    }

    @Test
    void blackenReplacementDefault() {
        var text          = Val.of("abcde");
        var discloseLeft  = Val.of(1);
        var discloseRight = Val.of(1);
        var result        = FilterFunctionLibrary.blacken(text, discloseLeft, discloseRight);

        assertTrue(result.getText().startsWith("a"));
        assertTrue(result.getText().endsWith("e"));
        assertTrue(result.getText().contains("X"));

    }

    @Test
    void blackenDiscloseRightDefault() {
        var text         = Val.of("abcde");
        var discloseLeft = Val.of(2);

        var result = FilterFunctionLibrary.blacken(text, discloseLeft);

        assertTrue(result.getText().startsWith("ab"));
        assertTrue(result.getText().endsWith("X"));
    }

    @Test
    void blackenDiscloseLeftDefault() {
        var text   = Val.of("abcde");
        var result = FilterFunctionLibrary.blacken(text);

        assertTrue(result.getText().chars().allMatch(ch -> ch == 'X'));
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
                .create(INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, functionCtx,
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
                .create(INTERPRETER.evaluate(authzSubscription, policyDefinition, ATTRIBUTE_CTX, functionCtx,
                        SYSTEM_VARIABLES))
                .assertNext(authzDecision -> assertThat(authzDecision, is(expectedAuthzDecision))).verifyComplete();
    }

}
