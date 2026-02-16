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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.attributes.libraries.ReactiveWebClient;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("Traccar secrets resolution")
class TraccarSecretsTests {

    private static final ObjectValue BASE_CONFIG = (ObjectValue) json("""
            {
                "baseUrl": "http://localhost:8082"
            }
            """);

    private static ObjectValue secretsWithBasicAuth(String userName, String password) {
        return ObjectValue.builder().put("traccar",
                ObjectValue.builder().put("userName", Value.of(userName)).put("password", Value.of(password)).build())
                .build();
    }

    private static ObjectValue secretsWithToken(String token) {
        return ObjectValue.builder().put("traccar", ObjectValue.builder().put("token", Value.of(token)).build())
                .build();
    }

    private static ObjectValue secretsWithTokenAndBasicAuth(String token, String userName, String password) {
        return ObjectValue.builder().put("traccar", ObjectValue.builder().put("token", Value.of(token))
                .put("userName", Value.of(userName)).put("password", Value.of(password)).build()).build();
    }

    @Test
    @DisplayName("when basic auth in secrets then auth header is set")
    void whenBasicAuthInSecrets_thenAuthHeaderSet() {
        val mockWebClient = Mockito.mock(ReactiveWebClient.class);
        val pip           = new TraccarPolicyInformationPoint(mockWebClient);
        val secrets       = secretsWithBasicAuth("user@example.com", "secret");
        when(mockWebClient.httpRequest(Mockito.any(), Mockito.any())).thenReturn(Flux.just(json("{}")));

        val attributeStream = pip.server(BASE_CONFIG, secrets).next();
        StepVerifier.create(attributeStream).expectNextMatches(v -> !(v instanceof ErrorValue)).verifyComplete();

        Mockito.verify(mockWebClient).httpRequest(Mockito.any(), Mockito.argThat(settings -> {
            val headers = settings.get("headers");
            return headers instanceof ObjectValue headerObj && headerObj.containsKey("Authorization");
        }));
    }

    @Test
    @DisplayName("when token in secrets then query parameter is used")
    void whenTokenInSecrets_thenQueryParameterUsed() {
        val mockWebClient = Mockito.mock(ReactiveWebClient.class);
        val pip           = new TraccarPolicyInformationPoint(mockWebClient);
        val secrets       = secretsWithToken("my-api-token");
        when(mockWebClient.httpRequest(Mockito.any(), Mockito.any())).thenReturn(Flux.just(json("{}")));

        val attributeStream = pip.server(BASE_CONFIG, secrets).next();
        StepVerifier.create(attributeStream).expectNextMatches(v -> !(v instanceof ErrorValue)).verifyComplete();

        Mockito.verify(mockWebClient).httpRequest(Mockito.any(), Mockito.argThat(settings -> {
            val urlParams = settings.get("urlParameters");
            return urlParams instanceof ObjectValue paramsObj && paramsObj.containsKey("token");
        }));
    }

    @Test
    @DisplayName("when token and basic auth present then token takes precedence")
    void whenTokenAndBasicAuth_thenTokenTakesPrecedence() {
        val mockWebClient = Mockito.mock(ReactiveWebClient.class);
        val pip           = new TraccarPolicyInformationPoint(mockWebClient);
        val secrets       = secretsWithTokenAndBasicAuth("my-api-token", "user@example.com", "secret");
        when(mockWebClient.httpRequest(Mockito.any(), Mockito.any())).thenReturn(Flux.just(json("{}")));

        val attributeStream = pip.server(BASE_CONFIG, secrets).next();
        StepVerifier.create(attributeStream).expectNextMatches(v -> !(v instanceof ErrorValue)).verifyComplete();

        Mockito.verify(mockWebClient).httpRequest(Mockito.any(), Mockito.argThat(settings -> {
            val urlParams = settings.get("urlParameters");
            val headers   = settings.get("headers");
            return urlParams instanceof ObjectValue paramsObj && paramsObj.containsKey("token") && headers == null;
        }));
    }

    @Test
    @DisplayName("when no secrets then error is returned")
    void whenNoSecrets_thenErrorReturned() {
        val mockWebClient = Mockito.mock(ReactiveWebClient.class);
        val pip           = new TraccarPolicyInformationPoint(mockWebClient);

        val attributeStream = pip.server(BASE_CONFIG, Value.EMPTY_OBJECT).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    @DisplayName("when credentials in config only then not used for auth")
    void whenCredentialsInConfigOnly_thenNotUsedForAuth() {
        val mockWebClient   = Mockito.mock(ReactiveWebClient.class);
        val pip             = new TraccarPolicyInformationPoint(mockWebClient);
        val configWithCreds = (ObjectValue) json("""
                {
                    "baseUrl": "http://localhost:8082",
                    "userName": "user@example.com",
                    "password": "secret"
                }
                """);

        val attributeStream = pip.server(configWithCreds, Value.EMPTY_OBJECT).next();
        StepVerifier.create(attributeStream).expectNextMatches(ErrorValue.class::isInstance).verifyComplete();
    }

    @Test
    @DisplayName("createBasicAuthHeader produces valid header")
    void whenValidCredentials_thenBasicAuthHeaderCorrect() {
        val secrets = (ObjectValue) json("""
                {
                    "userName": "admin@test.de",
                    "password": "secret123"
                }
                """);
        val result  = TraccarPolicyInformationPoint.createBasicAuthHeader(secrets);
        assertThat(result).isNotInstanceOf(ErrorValue.class)
                .satisfies(v -> assertThat(v.toString()).startsWith("\"Basic "));
    }

    @Test
    @DisplayName("createBasicAuthHeader returns error for missing userName")
    void whenMissingUserName_thenBasicAuthHeaderReturnsError() {
        val secrets = (ObjectValue) json("""
                {
                    "password": "secret123"
                }
                """);
        val result  = TraccarPolicyInformationPoint.createBasicAuthHeader(secrets);
        assertThat(result).isInstanceOf(ErrorValue.class);
    }

    @Test
    @DisplayName("createBasicAuthHeader returns error for missing password")
    void whenMissingPassword_thenBasicAuthHeaderReturnsError() {
        val secrets = (ObjectValue) json("""
                {
                    "userName": "admin@test.de"
                }
                """);
        val result  = TraccarPolicyInformationPoint.createBasicAuthHeader(secrets);
        assertThat(result).isInstanceOf(ErrorValue.class);
    }
}
