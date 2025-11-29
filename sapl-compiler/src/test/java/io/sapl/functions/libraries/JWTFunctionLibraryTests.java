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
package io.sapl.functions.libraries;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JWTFunctionLibraryTests {

    private static final JsonNodeFactory JSON   = JsonNodeFactory.instance;
    private static final ObjectMapper    MAPPER = new ObjectMapper();

    private static final String WELL_FORMED_TOKEN = "eyJraWQiOiI3ZGRkYzMwNy1kZGE0LTQ4ZjUtYmU1Yi00MDZlZGFmYjc5ODgiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMSIsImF1ZCI6Im1pc2thdG9uaWMtY2xpZW50IiwibmJmIjoxNjM1MjUxNDE1LCJzY29wZSI6WyJmYWN1bHR5LnJlYWQiLCJib29rcy5yZWFkIl0sImlzcyI6Imh0dHA6XC9cL2F1dGgtc2VydmVyOjkwMDAiLCJleHAiOjE2MzUyNTE3MTUsImlhdCI6MTYzNTI1MTQxNX0.V0-bViu4pFVufOzrn8yTQO9TnDAbE-qEKW8DnBKNLKCn2BlrQHbLYNSCpc4RdFU-cj32OwNn3in5cFPtiL5CTiD-lRXxnnc5WaNPNW2FchYag0zc252UdfV0Hs2sOAaNJ8agJ_uv0fFupMRS340gNDFFZthmjhTrDHGErZU7qxc1Lk2NF7-TGngre66-5W3NZzBsexkDO9yDLP11StjF63705juPFL2hTdgAIqLpsIOMwfrgoAsl0-6P98ecRwtGZKK4rEjUxBwghxCu1gm7eZiYoet4K28wPoBzF3hso4LG789N6GJt5HBIKpob9Q6G1ZJhMgieLeXH__9jvw1e0w";

    private static JWTFunctionLibrary createLibrary() {
        return new JWTFunctionLibrary(MAPPER);
    }

    @Test
    void when_parsingWellFormedToken_then_headerAndPayloadExtracted() {
        val result = createLibrary().parseJwt(Value.of(WELL_FORMED_TOKEN));

        assertThat(result).isNotInstanceOf(ErrorValue.class);

        val token   = (ObjectValue) result;
        val payload = (ObjectValue) token.get("payload");
        val header  = (ObjectValue) token.get("header");

        assertThat(payload).isNotNull().containsEntry("sub", Value.of("user1"));
        assertThat(header).isNotNull().containsEntry("alg", Value.of("RS256")).containsEntry("kid",
                Value.of("7dddc307-dda4-48f5-be5b-406edafb7988"));
    }

    @Test
    void when_parsingWellFormedToken_then_epochTimestampsConvertedToIso() {
        val result = createLibrary().parseJwt(Value.of(WELL_FORMED_TOKEN));

        assertThat(result).isNotInstanceOf(ErrorValue.class);

        val payload = (ObjectValue) ((ObjectValue) result).get("payload");

        assertThat(payload).isNotNull().containsEntry("nbf", Value.of("2021-10-26T12:30:15Z"))
                .containsEntry("exp", Value.of("2021-10-26T12:35:15Z"))
                .containsEntry("iat", Value.of("2021-10-26T12:30:15Z"));
    }

    @Test
    void when_parsingWellFormedToken_then_scopesExtracted() {
        val result = createLibrary().parseJwt(Value.of(WELL_FORMED_TOKEN));

        assertThat(result).isNotInstanceOf(ErrorValue.class);

        val payload = (ObjectValue) ((ObjectValue) result).get("payload");
        assertThat(payload).isNotNull();
        val scopes = (ArrayValue) payload.get("scope");

        assertThat(scopes).isNotNull().hasSize(2).containsExactly(Value.of("faculty.read"), Value.of("books.read"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "This is not a JWT token at all", "eyJhbGciOiJSUzI1NiJ9.incomplete",
            "eyJhbGciOiJSUzI1NiJ9eyJzdWIiOiJ0ZXN0In0", "", "...", "a.b.c.d", "header.payload", "x" })
    void when_parsingMalformedToken_then_returnsError(String malformedToken) {
        var result = createLibrary().parseJwt(Value.of(malformedToken));

        assertThat(result).isInstanceOf(ErrorValue.class).extracting(v -> ((ErrorValue) v).message())
                .satisfies(msg -> assertThat(msg).contains("Failed to parse JWT"));
    }

    @Test
    void when_payloadIsNotAnObject_then_parsesSuccessfully() {
        val mapper = mock(ObjectMapper.class);
        when(mapper.convertValue(any(Object.class), eq(JsonNode.class)))
                .thenReturn(JSON.textNode("SOL command structure"));
        val library = new JWTFunctionLibrary(mapper);
        val result  = library.parseJwt(Value.of(WELL_FORMED_TOKEN));

        assertThat(result).isNotInstanceOf(ErrorValue.class);

        val payload = (TextValue) ((ObjectValue) result).get("payload");

        assertThat(payload).isNotNull();
        assertThat(payload.value()).isEqualTo("SOL command structure");
    }

    @Test
    void when_payloadHasNoTimeClaims_then_parsesSuccessfully() {
        val mapper  = mock(ObjectMapper.class);
        val payload = JSON.objectNode();
        payload.put("sub", "perry.rhodan");
        payload.put("role", "commander");
        when(mapper.convertValue(any(Object.class), eq(JsonNode.class))).thenReturn(payload);

        val library = new JWTFunctionLibrary(mapper);
        val result  = library.parseJwt(Value.of(WELL_FORMED_TOKEN));

        assertThat(result).isNotInstanceOf(ErrorValue.class);

        val payloadValue = (ObjectValue) ((ObjectValue) result).get("payload");

        assertThat(payloadValue).isNotNull().containsEntry("sub", Value.of("perry.rhodan"));
    }

    @Test
    void when_payloadHasNonNumericTimeClaims_then_preservesOriginalValue() {
        val mapper  = mock(ObjectMapper.class);
        val payload = JSON.objectNode();
        payload.put("sub", "atlan");
        payload.set("nbf", JSON.textNode("not a number"));
        when(mapper.convertValue(any(Object.class), eq(JsonNode.class))).thenReturn(payload);

        val library = new JWTFunctionLibrary(mapper);
        val result  = library.parseJwt(Value.of(WELL_FORMED_TOKEN));

        assertThat(result).isNotInstanceOf(ErrorValue.class);

        val payloadValue = (ObjectValue) ((ObjectValue) result).get("payload");

        assertThat(payloadValue).isNotNull().containsEntry("nbf", Value.of("not a number"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "nbf", "exp", "iat" })
    void when_parsingTimeClaim_then_convertedFromEpochToIso(String claimName) {
        val mapper  = mock(ObjectMapper.class);
        val payload = JSON.objectNode();
        payload.set(claimName, JSON.numberNode(0L));
        when(mapper.convertValue(any(Object.class), eq(JsonNode.class))).thenReturn(payload);

        val library = new JWTFunctionLibrary(mapper);
        val result  = library.parseJwt(Value.of(WELL_FORMED_TOKEN));

        assertThat(result).isNotInstanceOf(ErrorValue.class);

        val payloadValue = (ObjectValue) ((ObjectValue) result).get("payload");

        assertThat(payloadValue).isNotNull().containsEntry(claimName, Value.of("1970-01-01T00:00:00Z"));
    }

    @Test
    void when_parsingMultipleTimeClaims_then_allConvertedToIso() {
        val mapper  = mock(ObjectMapper.class);
        val payload = JSON.objectNode();
        payload.set("nbf", JSON.numberNode(1000000000L));
        payload.set("exp", JSON.numberNode(2000000000L));
        payload.set("iat", JSON.numberNode(1500000000L));
        when(mapper.convertValue(any(Object.class), eq(JsonNode.class))).thenReturn(payload);

        val library = new JWTFunctionLibrary(mapper);
        val result  = library.parseJwt(Value.of(WELL_FORMED_TOKEN));

        assertThat(result).isNotInstanceOf(ErrorValue.class);

        val payloadValue = (ObjectValue) ((ObjectValue) result).get("payload");

        assertThat(payloadValue).isNotNull().containsEntry("nbf", Value.of("2001-09-09T01:46:40Z"))
                .containsEntry("exp", Value.of("2033-05-18T03:33:20Z"))
                .containsEntry("iat", Value.of("2017-07-14T02:40:00Z"));
    }

    @Test
    void when_payloadHasMixedClaims_then_allHandledCorrectly() {
        val mapper  = mock(ObjectMapper.class);
        val payload = JSON.objectNode();
        payload.put("sub", "gucky");
        payload.put("species", "mousebeaver");
        payload.put("cellActivator", true);
        payload.set("nbf", JSON.numberNode(1635251415L));
        payload.set("exp", JSON.numberNode(1635251715L));
        when(mapper.convertValue(any(Object.class), eq(JsonNode.class))).thenReturn(payload);

        val library = new JWTFunctionLibrary(mapper);
        val result  = library.parseJwt(Value.of(WELL_FORMED_TOKEN));

        assertThat(result).isNotInstanceOf(ErrorValue.class);

        val payloadValue = (ObjectValue) ((ObjectValue) result).get("payload");

        assertThat(payloadValue).isNotNull().containsEntry("sub", Value.of("gucky"))
                .containsEntry("species", Value.of("mousebeaver")).containsEntry("cellActivator", Value.TRUE)
                .containsEntry("nbf", Value.of("2021-10-26T12:30:15Z"))
                .containsEntry("exp", Value.of("2021-10-26T12:35:15Z"));
    }

}
