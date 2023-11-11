/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.extension.jwt;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.interpreter.Val;

class JWTFunctionLibraryTests {

    private final static JsonNodeFactory JSON              = JsonNodeFactory.instance;
    private final static ObjectMapper    MAPPER            = new ObjectMapper();
    private final static String          MALFORMED_TOKEN   = "NOT A WELL FORMED TOKEN";
    private final static String          WELL_FORMED_TOKEN = "eyJraWQiOiI3ZGRkYzMwNy1kZGE0LTQ4ZjUtYmU1Yi00MDZlZGFmYjc5ODgiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMSIsImF1ZCI6Im1pc2thdG9uaWMtY2xpZW50IiwibmJmIjoxNjM1MjUxNDE1LCJzY29wZSI6WyJmYWN1bHR5LnJlYWQiLCJib29rcy5yZWFkIl0sImlzcyI6Imh0dHA6XC9cL2F1dGgtc2VydmVyOjkwMDAiLCJleHAiOjE2MzUyNTE3MTUsImlhdCI6MTYzNTI1MTQxNX0.V0-bViu4pFVufOzrn8yTQO9TnDAbE-qEKW8DnBKNLKCn2BlrQHbLYNSCpc4RdFU-cj32OwNn3in5cFPtiL5CTiD-lRXxnnc5WaNPNW2FchYag0zc252UdfV0Hs2sOAaNJ8agJ_uv0fFupMRS340gNDFFZthmjhTrDHGErZU7qxc1Lk2NF7-TGngre66-5W3NZzBsexkDO9yDLP11StjF63705juPFL2hTdgAIqLpsIOMwfrgoAsl0-6P98ecRwtGZKK4rEjUxBwghxCu1gm7eZiYoet4K28wPoBzF3hso4LG789N6GJt5HBIKpob9Q6G1ZJhMgieLeXH__9jvw1e0w";

    @Test
    void wellFormedTokenIsParsed() {
        var sut    = new JWTFunctionLibrary(MAPPER);
        var actual = sut.parseJwt(Val.of(WELL_FORMED_TOKEN));
        assertThat(actual.get().get("payload").get("sub").asText(), is("user1"));
    }

    @Test
    void malformedTokenIsNotParsed() {
        var sut    = new JWTFunctionLibrary(MAPPER);
        var actual = sut.parseJwt(Val.of(MALFORMED_TOKEN));
        assertThat(actual.isError(), is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void payloadNotAnObjectWorks() {
        var mapper = mock(ObjectMapper.class);
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(JSON.textNode("not an object"));
        var sut    = new JWTFunctionLibrary(mapper);
        var actual = sut.parseJwt(Val.of(WELL_FORMED_TOKEN));
        assertThat(actual.get().get("payload").asText(), is("not an object"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void payloadNoNbfWorks() {
        var mapper  = mock(ObjectMapper.class);
        var payload = JSON.objectNode();
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(payload);
        var sut    = new JWTFunctionLibrary(mapper);
        var actual = sut.parseJwt(Val.of(WELL_FORMED_TOKEN));
        assertThat(actual.get().get("payload"), is(payload));
    }

    @Test
    @SuppressWarnings("unchecked")
    void payloadNbfNotANumberWorks() {
        var mapper  = mock(ObjectMapper.class);
        var payload = JSON.objectNode();
        payload.set("nbf", JSON.textNode("not a number"));
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(payload);
        var sut    = new JWTFunctionLibrary(mapper);
        var actual = sut.parseJwt(Val.of(WELL_FORMED_TOKEN));
        assertThat(actual.get().get("payload"), is(payload));
    }

    @ParameterizedTest
    @SuppressWarnings("unchecked")
    @ValueSource(strings = { "nbf", "exp", "iat" })
    void payloadConverted(String payloadKey) {
        var mapper  = mock(ObjectMapper.class);
        var payload = JSON.objectNode();
        payload.set(payloadKey, JSON.numberNode(0L));
        when(mapper.convertValue(any(), any(Class.class))).thenReturn(payload);
        var sut    = new JWTFunctionLibrary(mapper);
        var actual = sut.parseJwt(Val.of(WELL_FORMED_TOKEN));
        assertThat(actual.get().get("payload").get(payloadKey).asText(), is("1970-01-01T00:00:00Z"));
    }

}
