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
package io.sapl.spring.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class ServerHttpRequestSerializerTests {

    private static JsonMapper mapper;

    @BeforeAll
    static void setup() {
        val module = new SimpleModule();
        module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
        mapper = JsonMapper.builder().addModule(module).build();
    }

    private JsonNode serialize(ServerHttpRequest request) throws JacksonException {
        return mapper.valueToTree(request);
    }

    @Test
    void whenParametersSet_thenItIsTheSameInJson() throws JacksonException {
        val request = MockServerHttpRequest.get("/foo/bar").queryParam("key1", "value1a", "value1b")
                .queryParam("key2", "value2").build();
        val actual  = serialize(request);
        val params  = actual.get(ServerHttpRequestSerializer.PARAMETERS);
        assertThat(params.get("key1").get(0).asString()).isEqualTo("value1a");
        assertThat(params.get("key1").get(1).asString()).isEqualTo("value1b");
        assertThat(params.get("key2").get(0).asString()).isEqualTo("value2");
    }

    @Test
    void whenCookiesSet_thenItIsTheSameInJson() throws JacksonException {
        val request = MockServerHttpRequest.get("/foo/bar")
                .cookie(new HttpCookie("name1", "value1"), new HttpCookie("name2", "value2")).build();
        val actual  = serialize(request);
        val cookies = actual.get(ServerHttpRequestSerializer.COOKIES);
        assertThat(cookies).hasSize(2);
        assertThat(cookies.get(0).get("name").asString()).isEqualTo("name1");
        assertThat(cookies.get(0).get("value").asString()).isEqualTo("value1");
    }

    @Test
    void whenHeadersSet_thenItIsTheSameInJson() throws JacksonException {
        val request = MockServerHttpRequest.get("/foo/bar").header("header1", "value1a", "value1b")
                .header("header2", "value2").build();
        val actual  = serialize(request);
        val headers = actual.get(ServerHttpRequestSerializer.HEADERS);
        assertThat(headers.get("header1")).isNotNull();
        assertThat(headers.get("header2")).isNotNull();
    }

    @Test
    void whenRemoteAddressSet_thenItIsTheSameInJson() throws JacksonException {
        val expectedIp   = "123.22.233.121";
        val expectedPort = 443;
        val request      = MockServerHttpRequest.get("/foo/bar")
                .remoteAddress(new InetSocketAddress(expectedIp, expectedPort)).build();
        val actual       = serialize(request);
        assertThat(actual.get(ServerHttpRequestSerializer.REMOTE_ADDRESS).asString())
                .isEqualTo("/" + expectedIp + ":" + expectedPort);
    }

    @Test
    void whenRemoteHostSet_thenItIsTheSameInJson() throws JacksonException {
        val expectedHostname = "localhost";
        val request          = MockServerHttpRequest.get("/foo/bar")
                .remoteAddress(new InetSocketAddress(expectedHostname, 443)).build();
        val actual           = serialize(request);
        assertThat(actual.get(ServerHttpRequestSerializer.REMOTE_HOST).asString()).isEqualTo(expectedHostname);
    }

    @Test
    void whenLocalNameSet_thenItIsTheSameInJson() throws JacksonException {
        val expectedHostname = "localhost";
        val request          = MockServerHttpRequest.get("/foo/bar")
                .localAddress(new InetSocketAddress(expectedHostname, 443)).build();
        val actual           = serialize(request);
        assertThat(actual.get(ServerHttpRequestSerializer.LOCAL_NAME).asString()).isEqualTo(expectedHostname);
    }

    @Test
    void whenLocalAddressSet_thenItIsTheSameInJson() throws JacksonException {
        val expectedIp   = "123.22.233.121";
        val expectedPort = 443;
        val request      = MockServerHttpRequest.get("/foo/bar")
                .localAddress(new InetSocketAddress(expectedIp, expectedPort)).build();
        val actual       = serialize(request);
        assertThat(actual.get(ServerHttpRequestSerializer.LOCAL_ADDRESS).asString())
                .isEqualTo("/" + expectedIp + ":" + expectedPort);
    }

    @Test
    void whenLocalPortSet_thenItIsTheSameInJson() throws JacksonException {
        val expectedIp   = "123.22.233.121";
        val expectedPort = 443;
        val request      = MockServerHttpRequest.get("/foo/bar")
                .localAddress(new InetSocketAddress(expectedIp, expectedPort)).build();
        val actual       = serialize(request);
        assertThat(actual.get(ServerHttpRequestSerializer.LOCAL_PORT).asInt()).isEqualTo(expectedPort);
    }

    @Test
    void whenMethodNameSet_thenItIsTheSameInJson() throws JacksonException {
        val request = MockServerHttpRequest.get("/foo/bar").build();
        val actual  = serialize(request);
        assertThat(actual.get(ServerHttpRequestSerializer.METHOD).asString()).isEqualTo("GET");
    }

    @Test
    void whenContextPathSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "/a/b/c";
        val request  = MockServerHttpRequest.get(expected).build();
        val actual   = serialize(request);
        assertThat(actual.get(ServerHttpRequestSerializer.CONTEXT_PATH).asString()).isEqualTo(expected);
    }

    @Test
    void whenRequestedUriIsSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "https://localhost";
        val request  = MockServerHttpRequest.get(expected).build();
        val actual   = serialize(request);
        assertThat(actual.get(ServerHttpRequestSerializer.REQUESTED_URI).asString()).isEqualTo(expected);
    }

}
