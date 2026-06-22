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
package io.sapl.pip.geo.traccar;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.http.BlockingWebClient;
import io.sapl.api.stream.Streams;
import io.sapl.api.test.stream.StreamAssertions;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Traccar named-server selection")
class TraccarNamedServerTests {

    @Mock
    private BlockingWebClient mockWebClient;

    private static final ObjectValue NAMED_SERVERS_CONFIG = (ObjectValue) json("""
            {
                "defaultServerName": "prod",
                "servers": [
                    { "name": "prod", "baseUrl": "https://prod.traccar.example" },
                    { "name": "lab",  "baseUrl": "http://lab.traccar.example:8082", "allowInsecureHttp": true }
                ]
            }
            """);

    private static ObjectValue variablesWith(ObjectValue traccarConfig) {
        return ObjectValue.builder().put(TraccarPolicyInformationPoint.TRACCAR_CONFIG, traccarConfig).build();
    }

    private static ObjectValue secretsForServers() {
        return ObjectValue.builder()
                .put("traccar",
                        ObjectValue.builder()
                                .put("prod", ObjectValue.builder().put("token", Value.of("prod-token")).build())
                                .put("lab", ObjectValue.builder().put("token", Value.of("lab-token")).build()).build())
                .build();
    }

    private static AttributeAccessContext ctx(ObjectValue variables, ObjectValue secrets) {
        return new AttributeAccessContext(variables, secrets, Value.EMPTY_OBJECT);
    }

    @Test
    @DisplayName("when a named server is selected then its operator baseUrl and per-name secrets are used")
    void whenNamedServerSelectedThenItsBaseUrlAndPerNameSecretsUsed() {
        when(mockWebClient.httpRequest(any(), any())).thenAnswer(invocation -> Streams.just(json("{}")));
        val pip = new TraccarPolicyInformationPoint(mockWebClient);
        val ctx = ctx(variablesWith(NAMED_SERVERS_CONFIG), secretsForServers());

        try (val stream = pip.server(ctx, (TextValue) Value.of("prod"))) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isNotInstanceOf(ErrorValue.class));
        }

        verify(mockWebClient).httpRequest(any(), argThat(settings -> {
            val baseUrl   = settings.get(BlockingWebClient.BASE_URL);
            val urlParams = settings.get(BlockingWebClient.URL_PARAMS);
            return baseUrl instanceof TextValue(var url) && "https://prod.traccar.example".equals(url)
                    && urlParams instanceof ObjectValue params && params.get("token") instanceof TextValue(var token)
                    && "prod-token".equals(token);
        }));
    }

    @Test
    @DisplayName("when no server name is given then the default server is selected")
    void whenNoServerNameThenDefaultServerSelected() {
        when(mockWebClient.httpRequest(any(), any())).thenAnswer(invocation -> Streams.just(json("{}")));
        val pip = new TraccarPolicyInformationPoint(mockWebClient);
        val ctx = ctx(variablesWith(NAMED_SERVERS_CONFIG), secretsForServers());

        try (val stream = pip.server(ctx)) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isNotInstanceOf(ErrorValue.class));
        }

        verify(mockWebClient).httpRequest(any(), argThat(settings -> {
            val baseUrl = settings.get(BlockingWebClient.BASE_URL);
            return baseUrl instanceof TextValue(var url) && "https://prod.traccar.example".equals(url);
        }));
    }

    @Test
    @DisplayName("when an unknown server name is selected then an error is returned")
    void whenUnknownServerNameThenErrorReturned() {
        val pip = new TraccarPolicyInformationPoint(mockWebClient);
        val ctx = ctx(variablesWith(NAMED_SERVERS_CONFIG), secretsForServers());

        try (val stream = pip.server(ctx, (TextValue) Value.of("staging"))) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).asInstanceOf(type(ErrorValue.class))
                    .extracting(ErrorValue::message).asString().contains("staging")).awaitsCompletion();
        }
    }

    @Test
    @DisplayName("when a server baseUrl is http and allowInsecureHttp is not set then an error is returned")
    void whenServerHttpAndInsecureNotAllowedThenErrorReturned() {
        val pip          = new TraccarPolicyInformationPoint(mockWebClient);
        val insecureOnly = (ObjectValue) json("""
                {
                    "defaultServerName": "lab",
                    "servers": [
                        { "name": "lab", "baseUrl": "http://lab.traccar.example:8082" }
                    ]
                }
                """);
        val secrets      = ObjectValue.builder()
                .put("traccar",
                        ObjectValue.builder()
                                .put("lab", ObjectValue.builder().put("token", Value.of("lab-token")).build()).build())
                .build();

        try (val stream = pip.server(ctx(variablesWith(insecureOnly), secrets), (TextValue) Value.of("lab"))) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isInstanceOf(ErrorValue.class))
                    .awaitsCompletion();
        }
    }

    @Test
    @DisplayName("when a server baseUrl is http and allowInsecureHttp is set then the request proceeds")
    void whenServerHttpAndInsecureAllowedThenRequestProceeds() {
        when(mockWebClient.httpRequest(any(), any())).thenAnswer(invocation -> Streams.just(json("{}")));
        val pip = new TraccarPolicyInformationPoint(mockWebClient);
        val ctx = ctx(variablesWith(NAMED_SERVERS_CONFIG), secretsForServers());

        try (val stream = pip.server(ctx, (TextValue) Value.of("lab"))) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isNotInstanceOf(ErrorValue.class));
        }

        verify(mockWebClient).httpRequest(any(), argThat(settings -> {
            val baseUrl = settings.get(BlockingWebClient.BASE_URL);
            return baseUrl instanceof TextValue(var url) && "http://lab.traccar.example:8082".equals(url);
        }));
    }

    @Test
    @DisplayName("when a back-compat single-object config and flat secret are used then they act as the default server")
    void whenSingleObjectConfigAndFlatSecretThenActAsDefaultServer() {
        when(mockWebClient.httpRequest(any(), any())).thenAnswer(invocation -> Streams.just(json("{}")));
        val pip         = new TraccarPolicyInformationPoint(mockWebClient);
        val flatConfig  = (ObjectValue) json("""
                {
                    "baseUrl": "https://single.traccar.example"
                }
                """);
        val flatSecrets = ObjectValue.builder()
                .put("traccar", ObjectValue.builder().put("token", Value.of("flat-token")).build()).build();

        try (val stream = pip.server(ctx(variablesWith(flatConfig), flatSecrets))) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isNotInstanceOf(ErrorValue.class));
        }

        verify(mockWebClient).httpRequest(any(), argThat(settings -> {
            val baseUrl   = settings.get(BlockingWebClient.BASE_URL);
            val urlParams = settings.get(BlockingWebClient.URL_PARAMS);
            return baseUrl instanceof TextValue(var url) && "https://single.traccar.example".equals(url)
                    && urlParams instanceof ObjectValue params && params.get("token") instanceof TextValue(var token)
                    && "flat-token".equals(token);
        }));
    }

    @Test
    @DisplayName("a flat single-object config with a custom defaultServerName still serves no-arg calls")
    void whenSingleObjectConfigHasCustomDefaultServerNameThenNoArgStillResolves() {
        when(mockWebClient.httpRequest(any(), any())).thenAnswer(invocation -> Streams.just(json("{}")));
        val pip         = new TraccarPolicyInformationPoint(mockWebClient);
        val flatConfig  = (ObjectValue) json("""
                {
                    "baseUrl": "https://single.traccar.example",
                    "defaultServerName": "prod"
                }
                """);
        val flatSecrets = ObjectValue.builder()
                .put("traccar", ObjectValue.builder().put("token", Value.of("flat-token")).build()).build();

        try (val stream = pip.server(ctx(variablesWith(flatConfig), flatSecrets))) {
            StreamAssertions.assertThat(stream).awaitsNext(v -> assertThat(v).isNotInstanceOf(ErrorValue.class));
        }

        verify(mockWebClient).httpRequest(any(), any());
    }
}
