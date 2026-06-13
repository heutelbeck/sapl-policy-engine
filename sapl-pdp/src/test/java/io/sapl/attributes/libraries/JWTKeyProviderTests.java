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

import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWTKeyProvider rejects unsafe key ids before contacting the key server")
class JWTKeyProviderTests {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private static final String KEY_SERVER_CONFIG = "{\"uri\":\"https://keyserver.example/keys/{kid}\"}";

    @ParameterizedTest(name = "kid \"{0}\" is rejected")
    @ValueSource(strings = { "../../../etc/passwd", "evil/path", "a b", "kid?inject=1", "a%0d%0aHost: evil", "kid#frag",
            "user@host" })
    @DisplayName("an unsafe kid yields no key and never contacts the key server")
    void whenKidContainsUnsafeCharactersThenNoRequestAndEmpty(String maliciousKid) throws Exception {
        val httpClient = mock(HttpClient.class);
        val provider   = new JWTKeyProvider(httpClient, FIXED_CLOCK);

        val key = provider.provide(maliciousKid, MAPPER.readTree(KEY_SERVER_CONFIG));

        assertThat(key).isEmpty();
        verify(httpClient, never()).send(any(HttpRequest.class), any());
    }

    @SuppressWarnings("unchecked")
    @org.junit.jupiter.api.Test
    @DisplayName("a safe base64url kid does reach the key server")
    void whenKidIsSafeThenKeyServerIsContacted() throws Exception {
        val httpClient = mock(HttpClient.class);
        val response   = (HttpResponse<String>) mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());
        val provider = new JWTKeyProvider(httpClient, FIXED_CLOCK);

        val key = provider.provide("abc-123_XY.Z", MAPPER.readTree(KEY_SERVER_CONFIG));

        assertThat(key).isEmpty();
        verify(httpClient).send(any(HttpRequest.class), any());
    }
}
