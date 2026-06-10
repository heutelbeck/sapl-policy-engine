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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import lombok.val;
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

    private static JsonNode serialize(ServerHttpRequest request) {
        return mapper.valueToTree(request);
    }

    @Nested
    class TopLevelUrlParts {

        @Test
        void methodIsTheRequestMethod() {
            val request = MockServerHttpRequest.put("/x").build();
            assertThat(serialize(request).get("method").asString()).isEqualTo("PUT");
        }

        @Test
        void pathIsTheRequestPathOnly() {
            val request = MockServerHttpRequest.get("/orders/42").build();
            assertThat(serialize(request).get("path").asString()).isEqualTo("/orders/42");
        }

        @Test
        void schemeAndHostAndPortAreDerivedFromTheRequestUri() {
            val request = MockServerHttpRequest.get("https://api.example.com:8443/orders/42").build();
            val result  = serialize(request);
            assertThat(result.get("scheme").asString()).isEqualTo("https");
            assertThat(result.get("host").asString()).isEqualTo("api.example.com");
            assertThat(result.get("port").asInt()).isEqualTo(8443);
        }

        @Test
        void urlIsTheFullRequestUriIncludingQuery() {
            val request = MockServerHttpRequest.get("https://api.example.com/orders/42?role=admin").build();
            assertThat(serialize(request).get("url").asString())
                    .isEqualTo("https://api.example.com/orders/42?role=admin");
        }
    }

    @Nested
    class QueryAndParameters {

        @Test
        void queryAndParsedParametersWhenPresent() {
            val request = MockServerHttpRequest.get("/search?q=foo+bar&page=2").build();
            val result  = serialize(request);
            assertThat(result.get("query").asString()).isEqualTo("q=foo+bar&page=2");
            val parsed = result.get("queryParameters");
            assertThat(parsed.get("q").get(0).asString()).isEqualTo("foo bar");
            assertThat(parsed.get("page").get(0).asString()).isEqualTo("2");
        }

        @Test
        void queryAndQueryParametersAreAbsentWhenNoQueryStringIsPresent() {
            val request = MockServerHttpRequest.get("/search").build();
            val result  = serialize(request);
            assertThat(result.get("query")).isNull();
            assertThat(result.get("queryParameters")).isNull();
        }
    }

    @Nested
    class PathSubdivision {

        @Test
        void contextPathIsEmptyByDefault() {
            val request = MockServerHttpRequest.get("/a/b/c").build();
            assertThat(serialize(request).get("contextPath").asString()).isEmpty();
        }

        @Test
        void applicationPathIsThePathWithinTheApplication() {
            val request = MockServerHttpRequest.get("/a/b/c").build();
            assertThat(serialize(request).get("applicationPath").asString()).isEqualTo("/a/b/c");
        }
    }

    @Nested
    class Connection {

        @Test
        void isSecureIsTrueForHttps() {
            val request = MockServerHttpRequest.get("https://api.example.com/x").build();
            assertThat(serialize(request).get("isSecure").asBoolean()).isTrue();
        }

        @Test
        void isSecureIsFalseForHttp() {
            val request = MockServerHttpRequest.get("http://api.example.com/x").build();
            assertThat(serialize(request).get("isSecure").asBoolean()).isFalse();
        }

        @Test
        void clientGroupExposesAddressHostPortFromRemoteAddress() {
            val request = MockServerHttpRequest.get("/x").remoteAddress(new InetSocketAddress("203.0.113.7", 54402))
                    .build();
            val client  = serialize(request).get("client");
            assertThat(client.get("address").asString()).isEqualTo("203.0.113.7");
            assertThat(client.get("host").asString()).isNotEmpty();
            assertThat(client.get("port").asInt()).isEqualTo(54402);
        }

        @Test
        void serverGroupExposesAddressHostPortFromLocalAddress() {
            val request = MockServerHttpRequest.get("/x").localAddress(new InetSocketAddress("10.0.0.1", 8443)).build();
            val server  = serialize(request).get("server");
            assertThat(server.get("address").asString()).isEqualTo("10.0.0.1");
            assertThat(server.get("host").asString()).isNotEmpty();
            assertThat(server.get("port").asInt()).isEqualTo(8443);
        }
    }

    @Nested
    class HeadersAndCookies {

        @Test
        void headersHaveLowercaseKeysAndAreMultiValued() {
            val request = MockServerHttpRequest.get("/x").header("X-Custom", "value-1a", "value-1b")
                    .header("Authorization", "Bearer abc").build();
            val headers = serialize(request).get("headers");
            assertThat(headers.get("x-custom").get(0).asString()).isEqualTo("value-1a");
            assertThat(headers.get("x-custom").get(1).asString()).isEqualTo("value-1b");
            assertThat(headers.get("authorization").get(0).asString()).isEqualTo("Bearer abc");
        }

        @Test
        void cookiesAreEmittedAsObjects() {
            val request = MockServerHttpRequest.get("/x")
                    .cookie(new HttpCookie("session", "abc123"), new HttpCookie("pref", "dark")).build();
            val cookies = serialize(request).get("cookies");
            assertThat(cookies).hasSize(2);
            assertThat(cookies.get(0).get("name").asString()).isEqualTo("session");
            assertThat(cookies.get(0).get("value").asString()).isEqualTo("abc123");
        }
    }

    @Nested
    class Forwarded {

        @Test
        void forwardedBlockIsAbsentWhenNoForwardedHeadersPresent() {
            val request = MockServerHttpRequest.get("/x").build();
            assertThat(serialize(request).get("forwarded")).isNull();
        }

        @Test
        void legacyXForwardedHeadersAreParsedIntoChainHostProtoPort() {
            val request   = MockServerHttpRequest.get("/x").header("X-Forwarded-For", "198.51.100.1, 203.0.113.7")
                    .header("X-Forwarded-Host", "api.example.com").header("X-Forwarded-Proto", "https")
                    .header("X-Forwarded-Port", "443").build();
            val forwarded = serialize(request).get("forwarded");
            assertThat(forwarded.get("for").get(0).asString()).isEqualTo("198.51.100.1");
            assertThat(forwarded.get("for").get(1).asString()).isEqualTo("203.0.113.7");
            assertThat(forwarded.get("host").asString()).isEqualTo("api.example.com");
            assertThat(forwarded.get("proto").asString()).isEqualTo("https");
            assertThat(forwarded.get("port").asInt()).isEqualTo(443);
        }

        @Test
        void rfc7239ForwardedHeaderTakesPrecedenceOverLegacyXForwardedFamily() {
            val request   = MockServerHttpRequest.get("/x")
                    .header("Forwarded", "for=198.51.100.1;host=api.example.com;proto=https")
                    .header("X-Forwarded-Host", "ignored.example.com").build();
            val forwarded = serialize(request).get("forwarded");
            assertThat(forwarded.get("for").get(0).asString()).isEqualTo("198.51.100.1");
            assertThat(forwarded.get("host").asString()).isEqualTo("api.example.com");
            assertThat(forwarded.get("proto").asString()).isEqualTo("https");
        }
    }

    @Nested
    class BodyMetadata {

        @Test
        void contentTypeIsExposedWhenSet() {
            val request = MockServerHttpRequest.post("/upload").contentType(MediaType.APPLICATION_JSON).build();
            assertThat(serialize(request).get("contentType").asString()).startsWith("application/json");
        }

        @Test
        void contentTypeIsAbsentWhenNoContentTypeHeaderIsSet() {
            val request = MockServerHttpRequest.get("/x").build();
            assertThat(serialize(request).get("contentType")).isNull();
        }

        @Test
        void contentLengthIsExposedWhenSet() {
            val request = MockServerHttpRequest.post("/upload").contentLength(142L).build();
            assertThat(serialize(request).get("contentLength").asLong()).isEqualTo(142L);
        }

        @Test
        void characterEncodingIsTheCharsetParameterOfContentTypeWhenPresent() {
            val withCharset = new MediaType(MediaType.APPLICATION_JSON, java.nio.charset.StandardCharsets.UTF_8);
            val request     = MockServerHttpRequest.post("/upload").contentType(withCharset).build();
            assertThat(serialize(request).get("characterEncoding").asString()).isEqualTo("UTF-8");
        }
    }
}
