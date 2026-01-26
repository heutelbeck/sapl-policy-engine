/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.functions.libraries;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import org.junit.jupiter.api.DisplayName;

@DisplayName("MacFunctionLibrary")
class MacFunctionLibraryTests {

    @Test
    void whenLoadedIntoBrokerThenNoError() {
        val functionBroker = new DefaultFunctionBroker();
        assertThatCode(() -> functionBroker.loadStaticFunctionLibrary(MacFunctionLibrary.class))
                .doesNotThrowAnyException();
    }

    /* HMAC Computation Tests */

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHmacKnownVectors")
    void hmacWhenKnownInputComputesExpectedMac(BiFunction<TextValue, TextValue, Value> hmacFunction, String message,
            String key, String expectedMac, int expectedLength) {
        var result = hmacFunction.apply(Value.of(message), Value.of(key));

        assertThat(result).isEqualTo(Value.of(expectedMac)).isInstanceOf(TextValue.class)
                .extracting(v -> ((TextValue) v).value()).satisfies(hash -> assertThat(hash).hasSize(expectedLength));
    }

    private static Stream<Arguments> provideHmacKnownVectors() {
        return Stream.of(
                arguments((BiFunction<TextValue, TextValue, Value>) MacFunctionLibrary::hmacSha256, "hello world",
                        "secret", "734cc62f32841568f45715aeb9f4d7891324e6d948e4c6c60c0621cdac48623a", 64),
                arguments((BiFunction<TextValue, TextValue, Value>) MacFunctionLibrary::hmacSha384, "hello world",
                        "secret",
                        "2da3bb177b92aae98c3ab22727d7f60c905be1baff71fb4b00a6e410923e6558376590c1faf922ff51ec49be77409ac6",
                        96),
                arguments((BiFunction<TextValue, TextValue, Value>) MacFunctionLibrary::hmacSha512, "hello world",
                        "secret",
                        "6d32239b01dd1750557211629313d95e4f4fcb8ee517e443990ac1afc7562bfd74ffa6118387efd9e168ff86d1da5cef4a55edc63cc4ba289c4c3a8b4f7bdfc2",
                        128));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHmacFunctions")
    void hmacWhenEmptyMessageComputesMac(BiFunction<TextValue, TextValue, Value> hmacFunction) {
        var result = hmacFunction.apply(Value.of(""), Value.of("secret"));

        assertThat(result).isNotInstanceOf(ErrorValue.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHmacFunctions")
    void hmacWhenSameInputsProducesConsistentMac(BiFunction<TextValue, TextValue, Value> hmacFunction) {
        var message = Value.of("test message");
        var key     = Value.of("test key");

        assertThat(hmacFunction.apply(message, key)).isEqualTo(hmacFunction.apply(message, key));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHmacFunctions")
    void hmacReturnsLowercaseHex(BiFunction<TextValue, TextValue, Value> hmacFunction) {
        var result = hmacFunction.apply(Value.of("test"), Value.of("secret"));

        assertThat(result).isInstanceOf(TextValue.class).extracting(v -> ((TextValue) v).value()).satisfies(hash -> {
            assertThat(hash).isEqualTo(hash.toLowerCase()).matches("^[0-9a-f]+$");
        });
    }

    private static Stream<Arguments> provideHmacFunctions() {
        return Stream.of(arguments((BiFunction<TextValue, TextValue, Value>) MacFunctionLibrary::hmacSha256),
                arguments((BiFunction<TextValue, TextValue, Value>) MacFunctionLibrary::hmacSha384),
                arguments((BiFunction<TextValue, TextValue, Value>) MacFunctionLibrary::hmacSha512));
    }

    /* HMAC Behavior Tests */

    @Test
    void hmacSha256WhenEmptyKeyReturnsError() {
        var result = MacFunctionLibrary.hmacSha256(Value.of("hello"), Value.of(""));

        assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message().toLowerCase())
                .satisfies(msg -> assertThat(msg).containsAnyOf("key", "invalid"));
    }

    @Test
    void hmacSha256WhenDifferentKeyProducesDifferentMac() {
        var message = Value.of("test message");

        assertThat(MacFunctionLibrary.hmacSha256(message, Value.of("key1")))
                .isNotEqualTo(MacFunctionLibrary.hmacSha256(message, Value.of("key2")));
    }

    @Test
    void hmacSha256WhenDifferentMessageProducesDifferentMac() {
        var key = Value.of("secret");

        assertThat(MacFunctionLibrary.hmacSha256(Value.of("message1"), key))
                .isNotEqualTo(MacFunctionLibrary.hmacSha256(Value.of("message2"), key));
    }

    @Test
    void differentAlgorithmsProduceDifferentMacs() {
        var message    = Value.of("test message");
        var key        = Value.of("secret");
        var hmacSha256 = MacFunctionLibrary.hmacSha256(message, key);
        var hmacSha384 = MacFunctionLibrary.hmacSha384(message, key);
        var hmacSha512 = MacFunctionLibrary.hmacSha512(message, key);

        assertThat(hmacSha256).isNotEqualTo(hmacSha384).isNotEqualTo(hmacSha512);
        assertThat(hmacSha384).isNotEqualTo(hmacSha512);
    }

    /* Timing-Safe Comparison Tests */

    @ParameterizedTest(name = "[{index}] {0}")
    @CsvSource({ "abc123def456, abc123def456, true,  'identical MACs'",
            "abc123def456, abc123def457, false, 'different MACs'",
            "abc123DEF456, ABC123def456, true,  'case difference'",
            "abc123,       abc123de,     false, 'different length'",
            "'',           '',           true,  'empty strings'", "abc123,       '',           false, 'one empty'",
            "ab_c1_23,     abc123,       true,  'with underscores'" })
    void timingSafeEqualsWhenVariousInputsReturnsExpectedResult(String mac1, String mac2, boolean expected,
            String scenario) {
        var result = MacFunctionLibrary.timingSafeEquals(Value.of(mac1), Value.of(mac2));

        assertThat(result).as(scenario).isEqualTo(Value.of(expected));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({ "xyz,    abc", "abc123, abc123def" })
    void timingSafeEqualsWhenInvalidHexReturnsError(String mac1, String mac2) {
        var result = MacFunctionLibrary.timingSafeEquals(Value.of(mac1), Value.of(mac2));

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    /* HMAC Verification Tests */

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHmacVerificationScenarios")
    void isValidHmacWhenVariousScenariosReturnsExpectedResult(String message, String key, String algorithm,
            BiFunction<TextValue, TextValue, Value> macGenerator, boolean expectSuccess) {
        var messageVal  = Value.of(message);
        var keyVal      = Value.of(key);
        var expectedMac = macGenerator.apply(messageVal, keyVal);

        assertThat(MacFunctionLibrary.isValidHmac(messageVal, (TextValue) expectedMac, keyVal, Value.of(algorithm)))
                .isEqualTo(Value.of(expectSuccess));
    }

    private static Stream<Arguments> provideHmacVerificationScenarios() {
        return Stream.of(
                arguments("test message", "secret", "HmacSHA256",
                        (BiFunction<TextValue, TextValue, Value>) MacFunctionLibrary::hmacSha256, true),
                arguments("test message", "secret", "HmacSHA384",
                        (BiFunction<TextValue, TextValue, Value>) MacFunctionLibrary::hmacSha384, true),
                arguments("test message", "secret", "HmacSHA512",
                        (BiFunction<TextValue, TextValue, Value>) MacFunctionLibrary::hmacSha512, true));
    }

    @Test
    void isValidHmacWhenIncorrectMacReturnsFalse() {
        var result = MacFunctionLibrary.isValidHmac(Value.of("test message"),
                Value.of("0000000000000000000000000000000000000000000000000000000000000000"), Value.of("secret"),
                Value.of("HmacSHA256"));

        assertThat(result).isEqualTo(Value.FALSE);
    }

    @Test
    void isValidHmacWhenWrongKeyReturnsFalse() {
        var message     = Value.of("test message");
        var correctKey  = Value.of("secret");
        var expectedMac = MacFunctionLibrary.hmacSha256(message, correctKey);

        assertThat(MacFunctionLibrary.isValidHmac(message, (TextValue) expectedMac, Value.of("wrong"),
                Value.of("HmacSHA256"))).isEqualTo(Value.FALSE);
    }

    @Test
    void isValidHmacWhenInvalidAlgorithmReturnsError() {
        var result = MacFunctionLibrary.isValidHmac(Value.of("test message"), Value.of("abc123"), Value.of("secret"),
                Value.of("InvalidAlgorithm"));

        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    /* Real-world Use Case Tests */

    @Test
    void githubWebhookSignatureVerificationExample() {
        var payload   = Value.of("{\"action\":\"opened\",\"number\":1}");
        var secret    = Value.of("my_webhook_secret");
        var signature = MacFunctionLibrary.hmacSha256(payload, secret);

        assertThat(MacFunctionLibrary.isValidHmac(payload, (TextValue) signature, secret, Value.of("HmacSHA256")))
                .isEqualTo(Value.TRUE);
    }

    @Test
    void webhookSignatureWhenPayloadModifiedFailsVerification() {
        var originalPayload   = Value.of("{\"action\":\"opened\",\"number\":1}");
        var secret            = Value.of("my_webhook_secret");
        var originalSignature = MacFunctionLibrary.hmacSha256(originalPayload, secret);

        assertThat(MacFunctionLibrary.isValidHmac(Value.of("{\"action\":\"opened\",\"number\":2}"),
                (TextValue) originalSignature, secret, Value.of("HmacSHA256"))).isEqualTo(Value.FALSE);
    }
}
