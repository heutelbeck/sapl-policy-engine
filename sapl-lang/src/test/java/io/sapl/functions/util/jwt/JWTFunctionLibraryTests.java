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
package io.sapl.functions.util.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.interpreter.Val;
import io.sapl.functions.JWTFunctionLibrary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JWTFunctionLibraryTests {

    private static final JsonNodeFactory JSON   = JsonNodeFactory.instance;
    private static final ObjectMapper    MAPPER = new ObjectMapper();

    private static final String WELL_FORMED_TOKEN = "eyJraWQiOiI3ZGRkYzMwNy1kZGE0LTQ4ZjUtYmU1Yi00MDZlZGFmYjc5ODgiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMSIsImF1ZCI6Im1pc2thdG9uaWMtY2xpZW50IiwibmJmIjoxNjM1MjUxNDE1LCJzY29wZSI6WyJmYWN1bHR5LnJlYWQiLCJib29rcy5yZWFkIl0sImlzcyI6Imh0dHA6XC9cL2F1dGgtc2VydmVyOjkwMDAiLCJleHAiOjE2MzUyNTE3MTUsImlhdCI6MTYzNTI1MTQxNX0.V0-bViu4pFVufOzrn8yTQO9TnDAbE-qEKW8DnBKNLKCn2BlrQHbLYNSCpc4RdFU-cj32OwNn3in5cFPtiL5CTiD-lRXxnnc5WaNPNW2FchYag0zc252UdfV0Hs2sOAaNJ8agJ_uv0fFupMRS340gNDFFZthmjhTrDHGErZU7qxc1Lk2NF7-TGngre66-5W3NZzBsexkDO9yDLP11StjF63705juPFL2hTdgAIqLpsIOMwfrgoAsl0-6P98ecRwtGZKK4rEjUxBwghxCu1gm7eZiYoet4K28wPoBzF3hso4LG789N6GJt5HBIKpob9Q6G1ZJhMgieLeXH__9jvw1e0w";

    @Test
    void wellFormedTokenIsParsed() {
        var library = new JWTFunctionLibrary(MAPPER);
        var result  = library.parseJwt(Val.of(WELL_FORMED_TOKEN));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get("payload").get("sub").asText()).isEqualTo("user1");
        assertThat(result.get().get("header").get("alg").asText()).isEqualTo("RS256");
        assertThat(result.get().get("header").get("kid").asText()).isEqualTo("7dddc307-dda4-48f5-be5b-406edafb7988");
    }

    @Test
    void wellFormedTokenConvertsEpochTimestampsToIso() {
        var library = new JWTFunctionLibrary(MAPPER);
        var result  = library.parseJwt(Val.of(WELL_FORMED_TOKEN));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get("payload").get("nbf").asText()).isEqualTo("2021-10-26T12:30:15Z");
        assertThat(result.get().get("payload").get("exp").asText()).isEqualTo("2021-10-26T12:35:15Z");
        assertThat(result.get().get("payload").get("iat").asText()).isEqualTo("2021-10-26T12:30:15Z");
    }

    @Test
    void wellFormedTokenExtractsScopes() {
        var library = new JWTFunctionLibrary(MAPPER);
        var result  = library.parseJwt(Val.of(WELL_FORMED_TOKEN));

        assertThat(result.isDefined()).isTrue();
        var scopes = result.get().get("payload").get("scope");
        assertThat(scopes.isArray()).isTrue();
        assertThat(scopes).hasSize(2);
        assertThat(scopes.get(0).asText()).isEqualTo("faculty.read");
        assertThat(scopes.get(1).asText()).isEqualTo("books.read");
    }

    @Test
    void malformedTokenReturnsError() {
        var library = new JWTFunctionLibrary(MAPPER);
        var result  = library.parseJwt(Val.of("This is not a JWT token at all"));

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("Failed to parse JWT");
    }

    @Test
    void incompleteTokenReturnsError() {
        var library = new JWTFunctionLibrary(MAPPER);
        var result  = library.parseJwt(Val.of("eyJhbGciOiJSUzI1NiJ9.incomplete"));

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("Failed to parse JWT");
    }

    @Test
    void tokenWithoutDotsReturnsError() {
        var library = new JWTFunctionLibrary(MAPPER);
        var result  = library.parseJwt(Val.of("eyJhbGciOiJSUzI1NiJ9eyJzdWIiOiJ0ZXN0In0"));

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("Failed to parse JWT");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "...", "a.b.c.d", "header.payload", "x" })
    void variousMalformedTokensReturnError(String malformedToken) {
        var library = new JWTFunctionLibrary(MAPPER);
        var result  = library.parseJwt(Val.of(malformedToken));

        assertThat(result.isError()).isTrue();
        assertThat(result.getMessage()).contains("Failed to parse JWT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void payloadNotAnObjectWorks() {
        var mapper = mock(ObjectMapper.class);
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(JSON.textNode("SOL command structure"));
        var library = new JWTFunctionLibrary(mapper);
        var result  = library.parseJwt(Val.of(WELL_FORMED_TOKEN));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get("payload").asText()).isEqualTo("SOL command structure");
    }

    @Test
    @SuppressWarnings("unchecked")
    void payloadWithoutTimeClaimsWorks() {
        var mapper  = mock(ObjectMapper.class);
        var payload = JSON.objectNode();
        payload.put("sub", "perry.rhodan");
        payload.put("role", "commander");
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(payload);

        var library = new JWTFunctionLibrary(mapper);
        var result  = library.parseJwt(Val.of(WELL_FORMED_TOKEN));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get("payload")).isEqualTo(payload);
        assertThat(result.get().get("payload").get("sub").asText()).isEqualTo("perry.rhodan");
    }

    @Test
    @SuppressWarnings("unchecked")
    void payloadWithNonNumericTimeClaimsWorks() {
        var mapper  = mock(ObjectMapper.class);
        var payload = JSON.objectNode();
        payload.put("sub", "atlan");
        payload.set("nbf", JSON.textNode("not a number"));
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(payload);

        var library = new JWTFunctionLibrary(mapper);
        var result  = library.parseJwt(Val.of(WELL_FORMED_TOKEN));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get("payload")).isEqualTo(payload);
        assertThat(result.get().get("payload").get("nbf").asText()).isEqualTo("not a number");
    }

    @ParameterizedTest
    @SuppressWarnings("unchecked")
    @ValueSource(strings = { "nbf", "exp", "iat" })
    void timeClaimsAreConvertedFromEpochToIso(String claimName) {
        var mapper  = mock(ObjectMapper.class);
        var payload = JSON.objectNode();
        payload.set(claimName, JSON.numberNode(0L));
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(payload);

        var library = new JWTFunctionLibrary(mapper);
        var result  = library.parseJwt(Val.of(WELL_FORMED_TOKEN));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get("payload").get(claimName).asText()).isEqualTo("1970-01-01T00:00:00Z");
    }

    @Test
    @SuppressWarnings("unchecked")
    void allTimeClaimsConvertedTogether() {
        var mapper  = mock(ObjectMapper.class);
        var payload = JSON.objectNode();
        payload.set("nbf", JSON.numberNode(1000000000L));
        payload.set("exp", JSON.numberNode(2000000000L));
        payload.set("iat", JSON.numberNode(1500000000L));
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(payload);

        var library = new JWTFunctionLibrary(mapper);
        var result  = library.parseJwt(Val.of(WELL_FORMED_TOKEN));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get("payload").get("nbf").asText()).isEqualTo("2001-09-09T01:46:40Z");
        assertThat(result.get().get("payload").get("exp").asText()).isEqualTo("2033-05-18T03:33:20Z");
        assertThat(result.get().get("payload").get("iat").asText()).isEqualTo("2017-07-14T02:40:00Z");
    }

    @Test
    void payloadWithMixedClaimsWorks() {
        var mapper  = mock(ObjectMapper.class);
        var payload = JSON.objectNode();
        payload.put("sub", "gucky");
        payload.put("species", "mousebeaver");
        payload.put("cellActivator", true);
        payload.set("nbf", JSON.numberNode(1635251415L));
        payload.set("exp", JSON.numberNode(1635251715L));
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(payload);

        var library = new JWTFunctionLibrary(mapper);
        var result  = library.parseJwt(Val.of(WELL_FORMED_TOKEN));

        assertThat(result.isDefined()).isTrue();
        assertThat(result.get().get("payload").get("sub").asText()).isEqualTo("gucky");
        assertThat(result.get().get("payload").get("species").asText()).isEqualTo("mousebeaver");
        assertThat(result.get().get("payload").get("cellActivator").asBoolean()).isTrue();
        assertThat(result.get().get("payload").get("nbf").asText()).isEqualTo("2021-10-26T12:30:15Z");
        assertThat(result.get().get("payload").get("exp").asText()).isEqualTo("2021-10-26T12:35:15Z");
    }

}
