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
package io.sapl.spring.serialization;

import static com.spotify.hamcrest.jackson.JsonMatchers.jsonArray;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonInt;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonObject;
import static com.spotify.hamcrest.jackson.JsonMatchers.jsonText;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Collection;

import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;

import lombok.val;

class ServerHttpRequestSerializerTests {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode serialize(ServerHttpRequest invocation) throws IOException {
        val jsonGenerator      = new TokenBuffer(mapper, false);
        val serializerProvider = mapper.getSerializerProvider();
        new ServerHttpRequestSerializer().serialize(invocation, jsonGenerator, serializerProvider);
        jsonGenerator.flush();
        return new ObjectMapper().readTree(jsonGenerator.asParser());
    }

    /**
     * Adapts an Iterable matcher to a Collection matcher for Eclipse compiler
     * compatibility.
     *
     * @param matcher the iterable matcher to adapt
     * @return a collection matcher wrapping the iterable matcher
     */
    @SuppressWarnings("unchecked")
    private static Matcher<Collection<? extends JsonNode>> asCollectionMatcher(
            Matcher<Iterable<? extends JsonNode>> matcher) {
        return (Matcher<Collection<? extends JsonNode>>) (Matcher<?>) matcher;
    }

    @Test
    void whenParametersSet_thenItIsTheSameInJson() throws IOException {
        val request = MockServerHttpRequest.get("/foo/bar").queryParam("key1", "value1a", "value1b")
                .queryParam("key2", "value2").build();
        val actual  = serialize(request);
        assertThat(actual,
                is(jsonObject().where(HttpServletRequestSerializer.PARAMETERS,
                        is(jsonObject()
                                .where("key1",
                                        is(jsonArray(asCollectionMatcher(
                                                contains(jsonText("value1a"), jsonText("value1b"))))))
                                .where("key2", is(jsonArray(asCollectionMatcher(contains(jsonText("value2"))))))))));
    }

    @Test
    void whenCookiesSet_thenItIsTheSameInJson() throws IOException {
        val request = MockServerHttpRequest.get("/foo/bar")
                .cookie(new HttpCookie("name1", "value1"), new HttpCookie("name2", "value2")).build();
        val actual  = serialize(request);
        assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.COOKIES,
                is(jsonArray(asCollectionMatcher(containsInAnyOrder(
                        jsonObject().where("name", is(jsonText("name1"))).where("value", is(jsonText("value1"))),
                        jsonObject().where("name", is(jsonText("name2"))).where("value", is(jsonText("value2"))))))))));
    }

    @Test
    void whenHeadersSet_thenItIsTheSameInJson() throws IOException {
        val request = MockServerHttpRequest.get("/foo/bar").header("header1", "value1a", "value1b")
                .header("header2", "value2").build();
        val actual  = serialize(request);
        assertThat(
                actual, is(
                        jsonObject()
                                .where(HttpServletRequestSerializer.HEADERS,
                                        is(jsonObject()
                                                .where("header1",
                                                        jsonArray(asCollectionMatcher(containsInAnyOrder(
                                                                jsonText("value1a"), jsonText("value1b")))))
                                                .where("header2", jsonArray(asCollectionMatcher(
                                                        containsInAnyOrder(jsonText("value2")))))))));
    }

    @Test
    void whenRemoteAddressSet_thenItIsTheSameInJson() throws IOException {
        val expectedIp   = "123.22.233.121";
        val expectedPort = 443;
        val request      = MockServerHttpRequest.get("/foo/bar")
                .remoteAddress(new InetSocketAddress(expectedIp, expectedPort)).build();
        val actual       = serialize(request);
        assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.REMOTE_ADDRESS,
                is(jsonText("/" + expectedIp + ":" + expectedPort)))));
    }

    @Test
    void whenRemoteHostSet_thenItIsTheSameInJson() throws IOException {
        val expectedHostname = "localhost";
        val request          = MockServerHttpRequest.get("/foo/bar")
                .remoteAddress(new InetSocketAddress(expectedHostname, 443)).build();
        val actual           = serialize(request);
        assertThat(actual,
                is(jsonObject().where(HttpServletRequestSerializer.REMOTE_HOST, is(jsonText(expectedHostname)))));
    }

    @Test
    void whenLocalNameSet_thenItIsTheSameInJson() throws IOException {
        val expectedHostname = "localhost";
        val request          = MockServerHttpRequest.get("/foo/bar")
                .localAddress(new InetSocketAddress(expectedHostname, 443)).build();
        val actual           = serialize(request);
        assertThat(actual,
                is(jsonObject().where(HttpServletRequestSerializer.LOCAL_NAME, is(jsonText(expectedHostname)))));
    }

    @Test
    void whenLocalAddressSet_thenItIsTheSameInJson() throws IOException {
        val expectedIp   = "123.22.233.121";
        val expectedPort = 443;
        val request      = MockServerHttpRequest.get("/foo/bar")
                .localAddress(new InetSocketAddress(expectedIp, expectedPort)).build();
        val actual       = serialize(request);
        assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.LOCAL_ADDRESS,
                is(jsonText("/" + expectedIp + ":" + expectedPort)))));
    }

    @Test
    void whenLocalPortSet_thenItIsTheSameInJson() throws IOException {
        val expectedIp   = "123.22.233.121";
        val expectedPort = 443;
        val request      = MockServerHttpRequest.get("/foo/bar")
                .localAddress(new InetSocketAddress(expectedIp, expectedPort)).build();
        val actual       = serialize(request);
        assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.LOCAL_PORT, is(jsonInt(expectedPort)))));
    }

    @Test
    void whenMethodNameSet_thenItIsTheSameInJson() throws IOException {
        val request = MockServerHttpRequest.get("/foo/bar").build();
        val actual  = serialize(request);
        assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.METHOD, is(jsonText("GET")))));
    }

    @Test
    void whenContextPathSet_thenItIsTheSameInJson() throws IOException {
        val expected = "/a/b/c";
        val request  = MockServerHttpRequest.get(expected).build();
        val actual   = serialize(request);
        assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.CONTEXT_PATH, is(jsonText(expected)))));
    }

    @Test
    void whenRequestedUriIsSet_thenItIsTheSameInJson() throws IOException {
        val expected = "https://localhost";
        val request  = MockServerHttpRequest.get(expected).build();
        val actual   = serialize(request);
        assertThat(actual, is(jsonObject().where(HttpServletRequestSerializer.REQUESTED_URI, is(jsonText(expected)))));
    }

}
