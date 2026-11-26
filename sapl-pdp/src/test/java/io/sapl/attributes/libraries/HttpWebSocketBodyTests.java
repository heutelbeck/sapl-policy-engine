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

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Poll;
import io.sapl.api.model.Value;
import io.sapl.attributes.http.BlockingWebClient;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HTTP WebSocket request bodies")
class HttpWebSocketBodyTests {

    @Mock
    private HttpClient        httpClient;
    @Mock
    private WebSocket.Builder builder;
    @Mock
    private WebSocket         socket;

    @BeforeEach
    void setUp() {
        when(httpClient.newWebSocketBuilder()).thenReturn(builder);
        when(builder.buildAsync(any(URI.class), any(WebSocket.Listener.class)))
                .thenReturn(CompletableFuture.completedFuture(socket));
    }

    @MethodSource
    @ParameterizedTest(name = "{0}")
    @DisplayName("sends configured text or JSON without confusing null with an absent body")
    void whenWebSocketBodyConfiguredThenSendsItsRepresentation(String name, ObjectValue fields, String expected) {
        val context  = new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val settings = ObjectValue.builder().put("baseUrl", Value.of("ws://office.example.test/rooms")).putAll(fields)
                .build();
        val pip      = new HttpPolicyInformationPoint(new BlockingWebClient(JsonMapper.builder().build(), httpClient));
        val listener = ArgumentCaptor.forClass(WebSocket.Listener.class);

        try (val values = pip.websocket(context, settings)) {
            assertThat(values.tryNext()).isInstanceOf(Poll.Empty.class);
            verify(builder).buildAsync(any(URI.class), listener.capture());
            if (fields.containsKey("body")) {
                verify(socket).sendText(expected, true);
            } else {
                verify(socket, never()).sendText(any(), anyBoolean());
            }
            listener.getValue().onText(socket, "true", true);
            assertThat(values.tryNext()).isEqualTo(Poll.value(Value.TRUE));
        }
    }

    static Stream<Arguments> whenWebSocketBodyConfiguredThenSendsItsRepresentation() {
        return Stream.of(arguments("absent", Value.EMPTY_OBJECT, ""),
                arguments("text", json("{\"body\":\"hello\"}"), "hello"),
                arguments("object", json("{\"body\":{\"room\":7}}"), "{\"room\":7}"),
                arguments("array", json("{\"body\":[1,2]}"), "[1,2]"),
                arguments("null", json("{\"body\":null}"), "null"),
                arguments("boolean", json("{\"body\":true}"), "true"), arguments("number", json("{\"body\":7}"), "7"));
    }
}
