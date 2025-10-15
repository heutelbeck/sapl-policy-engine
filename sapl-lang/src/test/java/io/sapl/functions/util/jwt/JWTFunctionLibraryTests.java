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

import java.text.ParseException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JWTFunctionLibraryTests {

    private static final JsonNodeFactory JSON              = JsonNodeFactory.instance;
    private static final ObjectMapper    MAPPER            = new ObjectMapper();
    private static final String          MALFORMED_TOKEN   = "NOT A WELL FORMED TOKEN";
    private static final String          WELL_FORMED_TOKEN = "eyJraWQiOiI3ZGRkYzMwNy1kZGE0LTQ4ZjUtYmU1Yi00MDZlZGFmYjc5ODgiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMSIsImF1ZCI6Im1pc2thdG9uaWMtY2xpZW50IiwibmJmIjoxNjM1MjUxNDE1LCJzY29wZSI6WyJmYWN1bHR5LnJlYWQiLCJib29rcy5yZWFkIl0sImlzcyI6Imh0dHA6XC9cL2F1dGgtc2VydmVyOjkwMDAiLCJleHAiOjE2MzUyNTE3MTUsImlhdCI6MTYzNTI1MTQxNX0.V0-bViu4pFVufOzrn8yTQO9TnDAbE-qEKW8DnBKNLKCn2BlrQHbLYNSCpc4RdFU-cj32OwNn3in5cFPtiL5CTiD-lRXxnnc5WaNPNW2FchYag0zc252UdfV0Hs2sOAaNJ8agJ_uv0fFupMRS340gNDFFZthmjhTrDHGErZU7qxc1Lk2NF7-TGngre66-5W3NZzBsexkDO9yDLP11StjF63705juPFL2hTdgAIqLpsIOMwfrgoAsl0-6P98ecRwtGZKK4rEjUxBwghxCu1gm7eZiYoet4K28wPoBzF3hso4LG789N6GJt5HBIKpob9Q6G1ZJhMgieLeXH__9jvw1e0w";

    @Test
    void wellFormedTokenIsParsed() {
        final var sut    = new JWTFunctionLibrary(MAPPER);
        final var actual = sut.parseJwt(Val.of(WELL_FORMED_TOKEN));
        assertThat(actual.get().get("payload").get("sub").asText(), is("user1"));
    }

    @Test
    void malformedTokenIsNotParsed() {
        final var sut       = new JWTFunctionLibrary(MAPPER);
        final var malformed = Val.of(MALFORMED_TOKEN);
        assertThrows(ParseException.class, () -> sut.parseJwt(malformed));
    }

    @Test
    @SuppressWarnings("unchecked")
    void payloadNotAnObjectWorks() {
        final var mapper = mock(ObjectMapper.class);
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(JSON.textNode("not an object"));
        final var sut    = new JWTFunctionLibrary(mapper);
        final var actual = sut.parseJwt(Val.of(WELL_FORMED_TOKEN));
        assertThat(actual.get().get("payload").asText(), is("not an object"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void payloadNoNbfWorks() {
        final var mapper  = mock(ObjectMapper.class);
        final var payload = JSON.objectNode();
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(payload);
        final var sut    = new JWTFunctionLibrary(mapper);
        final var actual = sut.parseJwt(Val.of(WELL_FORMED_TOKEN));
        assertThat(actual.get().get("payload"), is(payload));
    }

    @Test
    @SuppressWarnings("unchecked")
    void payloadNbfNotANumberWorks() {
        final var mapper  = mock(ObjectMapper.class);
        final var payload = JSON.objectNode();
        payload.set("nbf", JSON.textNode("not a number"));
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(payload);
        final var sut    = new JWTFunctionLibrary(mapper);
        final var actual = sut.parseJwt(Val.of(WELL_FORMED_TOKEN));
        assertThat(actual.get().get("payload"), is(payload));
    }

    @ParameterizedTest
    @SuppressWarnings("unchecked")
    @ValueSource(strings = { "nbf", "exp", "iat" })
    void payloadConverted(String payloadKey) {
        final var mapper  = mock(ObjectMapper.class);
        final var payload = JSON.objectNode();
        payload.set(payloadKey, JSON.numberNode(0L));
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(payload);
        final var sut    = new JWTFunctionLibrary(mapper);
        final var actual = sut.parseJwt(Val.of(WELL_FORMED_TOKEN));
        assertThat(actual.get().get("payload").get(payloadKey).asText(), is("1970-01-01T00:00:00Z"));
    }

}
