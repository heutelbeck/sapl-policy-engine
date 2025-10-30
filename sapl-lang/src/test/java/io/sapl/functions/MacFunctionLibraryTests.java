/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import io.sapl.api.interpreter.Val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class MacFunctionLibraryTests {

    /* HMAC Computation Tests */

    @ParameterizedTest
    @MethodSource("provideHmacKnownVectors")
    void hmac_whenKnownInput_computesExpectedMac(BiFunction<Val, Val, Val> hmacFunction, String message, String key,
            String expectedMac, int expectedLength) {
        var result = hmacFunction.apply(Val.of(message), Val.of(key));
        assertThat(result.getText()).isEqualTo(expectedMac);
        assertThat(result.getText()).hasSize(expectedLength);
    }

    private static Stream<Arguments> provideHmacKnownVectors() {
        return Stream.of(
                arguments((BiFunction<Val, Val, Val>) MacFunctionLibrary::hmacSha256, "hello world", "secret",
                        "734cc62f32841568f45715aeb9f4d7891324e6d948e4c6c60c0621cdac48623a", 64),
                arguments((BiFunction<Val, Val, Val>) MacFunctionLibrary::hmacSha384, "hello world", "secret",
                        "2da3bb177b92aae98c3ab22727d7f60c905be1baff71fb4b00a6e410923e6558376590c1faf922ff51ec49be77409ac6",
                        96),
                arguments((BiFunction<Val, Val, Val>) MacFunctionLibrary::hmacSha512, "hello world", "secret",
                        "6d32239b01dd1750557211629313d95e4f4fcb8ee517e443990ac1afc7562bfd74ffa6118387efd9e168ff86d1da5cef4a55edc63cc4ba289c4c3a8b4f7bdfc2",
                        128));
    }

    @ParameterizedTest
    @MethodSource("provideHmacFunctions")
    void hmac_whenEmptyMessage_computesMac(BiFunction<Val, Val, Val> hmacFunction) {
        var message = Val.of("");
        var key     = Val.of("secret");
        var result  = hmacFunction.apply(message, key);
        assertThat(result.isDefined()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("provideHmacFunctions")
    void hmac_whenSameInputs_producesConsistentMac(BiFunction<Val, Val, Val> hmacFunction) {
        var message = Val.of("test message");
        var key     = Val.of("test key");
        var result1 = hmacFunction.apply(message, key);
        var result2 = hmacFunction.apply(message, key);
        assertThat(result1.getText()).isEqualTo(result2.getText());
    }

    @ParameterizedTest
    @MethodSource("provideHmacFunctions")
    void hmac_returnsLowercaseHex(BiFunction<Val, Val, Val> hmacFunction) {
        var message = Val.of("test");
        var key     = Val.of("secret");
        var result  = hmacFunction.apply(message, key);
        assertThat(result.getText()).isEqualTo(result.getText().toLowerCase());
        assertThat(result.getText()).matches("^[0-9a-f]+$");
    }

    private static Stream<Arguments> provideHmacFunctions() {
        return Stream.of(arguments((BiFunction<Val, Val, Val>) MacFunctionLibrary::hmacSha256),
                arguments((BiFunction<Val, Val, Val>) MacFunctionLibrary::hmacSha384),
                arguments((BiFunction<Val, Val, Val>) MacFunctionLibrary::hmacSha512));
    }

    /* HMAC Behavior Tests */

    @Test
    void hmacSha256_whenEmptyKey_returnsError() {
        var message = Val.of("hello");
        var key     = Val.of("");
        var result  = MacFunctionLibrary.hmacSha256(message, key);
        assertThat(result.getMessage().toLowerCase()).containsAnyOf("key", "invalid");
    }

    @Test
    void hmacSha256_whenDifferentKey_producesDifferentMac() {
        var message = Val.of("test message");
        var key1    = Val.of("key1");
        var key2    = Val.of("key2");
        var result1 = MacFunctionLibrary.hmacSha256(message, key1);
        var result2 = MacFunctionLibrary.hmacSha256(message, key2);
        assertThat(result1.getText()).isNotEqualTo(result2.getText());
    }

    @Test
    void hmacSha256_whenDifferentMessage_producesDifferentMac() {
        var message1 = Val.of("message1");
        var message2 = Val.of("message2");
        var key      = Val.of("secret");
        var result1  = MacFunctionLibrary.hmacSha256(message1, key);
        var result2  = MacFunctionLibrary.hmacSha256(message2, key);
        assertThat(result1.getText()).isNotEqualTo(result2.getText());
    }

    @Test
    void differentAlgorithms_produceDifferentMacs() {
        var message    = Val.of("test message");
        var key        = Val.of("secret");
        var hmacSha256 = MacFunctionLibrary.hmacSha256(message, key);
        var hmacSha384 = MacFunctionLibrary.hmacSha384(message, key);
        var hmacSha512 = MacFunctionLibrary.hmacSha512(message, key);

        assertThat(hmacSha256.getText()).isNotEqualTo(hmacSha384.getText()).isNotEqualTo(hmacSha512.getText());
        assertThat(hmacSha384.getText()).isNotEqualTo(hmacSha512.getText());
    }

    /* Timing-Safe Comparison Tests */

    @ParameterizedTest
    @CsvSource({ "abc123def456, abc123def456, true,  'identical MACs'",
            "abc123def456, abc123def457, false, 'different MACs'",
            "abc123DEF456, ABC123def456, true,  'case difference'",
            "abc123,       abc123de,     false, 'different length'",
            "'',           '',           true,  'empty strings'", "abc123,       '',           false, 'one empty'",
            "ab_c1_23,     abc123,       true,  'with underscores'" })
    void timingSafeEquals_whenVariousInputs_returnsExpectedResult(String mac1, String mac2, boolean expected,
            String scenario) {
        var result = MacFunctionLibrary.timingSafeEquals(Val.of(mac1), Val.of(mac2));
        assertThat(result.getBoolean()).as(scenario).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({ "xyz,    abc", "abc123, abc123def" })
    void timingSafeEquals_whenInvalidHex_returnsError(String mac1, String mac2) {
        var result = MacFunctionLibrary.timingSafeEquals(Val.of(mac1), Val.of(mac2));
        assertThat(result.isError()).isTrue();
    }

    /* HMAC Verification Tests */

    @ParameterizedTest
    @MethodSource("provideHmacVerificationScenarios")
    void isValidHmac_whenVariousScenarios_returnsExpectedResult(String message, String key, String algorithm,
            BiFunction<Val, Val, Val> macGenerator, boolean expectSuccess) {
        var messageVal   = Val.of(message);
        var keyVal       = Val.of(key);
        var algorithmVal = Val.of(algorithm);
        var expectedMac  = macGenerator.apply(messageVal, keyVal);
        var result       = MacFunctionLibrary.isValidHmac(messageVal, expectedMac, keyVal, algorithmVal);

        assertThat(result.getBoolean()).isEqualTo(expectSuccess);
    }

    private static Stream<Arguments> provideHmacVerificationScenarios() {
        return Stream.of(
                arguments("test message", "secret", "HmacSHA256",
                        (BiFunction<Val, Val, Val>) MacFunctionLibrary::hmacSha256, true),
                arguments("test message", "secret", "HmacSHA384",
                        (BiFunction<Val, Val, Val>) MacFunctionLibrary::hmacSha384, true),
                arguments("test message", "secret", "HmacSHA512",
                        (BiFunction<Val, Val, Val>) MacFunctionLibrary::hmacSha512, true));
    }

    @Test
    void isValidHmac_whenIncorrectMac_returnsFalse() {
        var message   = Val.of("test message");
        var key       = Val.of("secret");
        var algorithm = Val.of("HmacSHA256");
        var wrongMac  = Val.of("0000000000000000000000000000000000000000000000000000000000000000");
        var result    = MacFunctionLibrary.isValidHmac(message, wrongMac, key, algorithm);
        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void isValidHmac_whenWrongKey_returnsFalse() {
        var message     = Val.of("test message");
        var correctKey  = Val.of("secret");
        var wrongKey    = Val.of("wrong");
        var algorithm   = Val.of("HmacSHA256");
        var expectedMac = MacFunctionLibrary.hmacSha256(message, correctKey);
        var result      = MacFunctionLibrary.isValidHmac(message, expectedMac, wrongKey, algorithm);
        assertThat(result.getBoolean()).isFalse();
    }

    @Test
    void isValidHmac_whenInvalidAlgorithm_returnsError() {
        var message   = Val.of("test message");
        var key       = Val.of("secret");
        var algorithm = Val.of("InvalidAlgorithm");
        var mac       = Val.of("abc123");
        var result    = MacFunctionLibrary.isValidHmac(message, mac, key, algorithm);
        assertThat(result.isError()).isTrue();
    }

    /* Real-world Use Case Tests */

    @Test
    void githubWebhookSignatureVerification_example() {
        var payload   = Val.of("{\"action\":\"opened\",\"number\":1}");
        var secret    = Val.of("my_webhook_secret");
        var signature = MacFunctionLibrary.hmacSha256(payload, secret);
        var isValid   = MacFunctionLibrary.isValidHmac(payload, signature, secret, Val.of("HmacSHA256"));
        assertThat(isValid.getBoolean()).isTrue();
    }

    @Test
    void webhookSignature_whenPayloadModified_failsVerification() {
        var originalPayload   = Val.of("{\"action\":\"opened\",\"number\":1}");
        var modifiedPayload   = Val.of("{\"action\":\"opened\",\"number\":2}");
        var secret            = Val.of("my_webhook_secret");
        var originalSignature = MacFunctionLibrary.hmacSha256(originalPayload, secret);
        var isValid           = MacFunctionLibrary.isValidHmac(modifiedPayload, originalSignature, secret,
                Val.of("HmacSHA256"));
        assertThat(isValid.getBoolean()).isFalse();
    }
}
