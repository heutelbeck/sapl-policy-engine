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
package io.sapl.attributes.libraries;

import io.sapl.api.model.ValueJsonMarshaller;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("JWT payload display")
class JWTTokenMetadataTests {

    @ParameterizedTest(name = "{0}")
    @MethodSource("payloads")
    @DisplayName("decoded payload numbers remain exact and whole time claims are displayed as UTC")
    void whenPayloadIsDisplayedThenNumbersArePreserved(String description, String input, String expected) {
        val result = JWTPolicyInformationPoint.displayPayload(input);

        assertThat(result).isEqualTo(ValueJsonMarshaller.json(expected));
    }

    private static Stream<Arguments> payloads() {
        return Stream.of(
                arguments("decimal claim", "{\"ratio\":0.123456789012345678}", "{\"ratio\":0.123456789012345678}"),
                arguments("integer identifier", "{\"invoiceId\":1234567890123456789012}",
                        "{\"invoiceId\":1234567890123456789012}"),
                arguments("nested claims", "{\"entries\":[{\"amount\":1.234567890123456789}],\"active\":true}",
                        "{\"entries\":[{\"amount\":1.234567890123456789}],\"active\":true}"),
                arguments("epoch", "{\"nbf\":0}", "{\"nbf\":\"1970-01-01T00:00:00Z\"}"),
                arguments("ordinary time", "{\"iat\":1700000000}", "{\"iat\":\"2023-11-14T22:13:20Z\"}"),
                arguments("final supported second", "{\"exp\":253402300799}", "{\"exp\":\"9999-12-31T23:59:59Z\"}"),
                arguments("whole decimal time", "{\"iat\":1700000000.0}", "{\"iat\":\"2023-11-14T22:13:20Z\"}"),
                arguments("fractional time", "{\"nbf\":1700000000.25,\"iat\":1700000000.5,\"exp\":1700003600.75}",
                        "{\"nbf\":1700000000.25,\"iat\":1700000000.5,\"exp\":1700003600.75}"),
                arguments("precise fractional time", "{\"exp\":1700000000.000000001}",
                        "{\"exp\":1700000000.000000001}"),
                arguments("negative time", "{\"nbf\":-1}", "{\"nbf\":-1}"),
                arguments("time beyond display range", "{\"exp\":253402300800}", "{\"exp\":253402300800}"),
                arguments("large numeric time", "{\"exp\":1234567890123456789012}", "{\"exp\":1234567890123456789012}"),
                arguments("null time", "{\"iat\":null}", "{\"iat\":null}"),
                arguments("text time", "{\"exp\":\"later\"}", "{\"exp\":\"later\"}"),
                arguments("missing time", "{\"roles\":[\"staff\"]}", "{\"roles\":[\"staff\"]}"),
                arguments("array payload", "[]", "{}"), arguments("text payload", "\"staff\"", "null"),
                arguments("null payload", "null", "null"), arguments("incomplete object", "{\"name\":}", "null"));
    }

}
