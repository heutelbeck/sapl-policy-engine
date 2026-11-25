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
package io.sapl.attributes.http;

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@DisplayName("Server-sent event data")
class BlockingWebClientServerSentEventTests {

    private static final Duration WAIT = Duration.ofSeconds(3);

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonJsonWhitespace")
    @DisplayName("non-JSON whitespace is preserved for the JSON parser")
    void whenEventDataStartsWithNonJsonWhitespaceThenErrorValue(String name, String whitespace)
            throws InterruptedException, TimeoutException {
        val client = client("data:" + whitespace + "true\n\n");

        try (val values = client.httpRequest("GET", request())) {
            assertThat(values.awaitNext(WAIT)).isInstanceOf(ErrorValue.class);
            assertThat(values.awaitNext(WAIT)).isNull();
        }
    }

    static Stream<Arguments> nonJsonWhitespace() {
        return Stream.of(arguments("nonbreaking space", "\u00a0"), arguments("em space", "\u2003"));
    }

    @Test
    @DisplayName("indented multiline JSON preserves string content")
    void whenEventDataIsIndentedMultilineJsonThenValueIsPreserved() throws InterruptedException, TimeoutException {
        val event    = """
                data: {
                data:   "on": true,
                data:\t"label": "  ready"
                data: }

                """.replace("\n", "\r\n");
        val client   = client(event);
        val expected = ObjectValue.builder().put("on", Value.TRUE).put("label", Value.of("  ready")).build();

        try (val values = client.httpRequest("GET", request())) {
            assertThat(values.awaitNext(WAIT)).isEqualTo(expected);
            assertThat(values.awaitNext(WAIT)).isNull();
        }
    }

    private static ObjectValue request() {
        return ObjectValue.builder().put("baseUrl", Value.of("https://office.example.test/events"))
                .put("accept", Value.of("text/event-stream")).build();
    }

    private static BlockingWebClient client(String eventStream) {
        val body       = new ByteArrayInputStream(eventStream.getBytes(StandardCharsets.UTF_8));
        val httpClient = new BlockingWebClientTests.StatusInputStreamHttpClient(200, body);
        return new BlockingWebClient(JsonMapper.builder().build(), httpClient);
    }
}
