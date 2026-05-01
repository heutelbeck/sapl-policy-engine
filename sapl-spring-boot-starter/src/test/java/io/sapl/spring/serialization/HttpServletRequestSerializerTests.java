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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.val;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

class HttpServletRequestSerializerTests {

    private static JsonMapper mapper;

    @BeforeAll
    static void setup() {
        val module = new SimpleModule();
        module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
        mapper = JsonMapper.builder().addModule(module).build();
    }

    private static JsonNode serialize(HttpServletRequest request) {
        return mapper.valueToTree(request);
    }

    @Nested
    class TopLevelUrlParts {

        @Test
        void methodIsTheRequestMethod() {
            val request = new MockHttpServletRequest();
            request.setMethod("PUT");
            assertThat(serialize(request).get("method").asString()).isEqualTo("PUT");
        }

        @Test
        void pathIsTheRequestUriPathOnly() {
            val request = new MockHttpServletRequest();
            request.setRequestURI("/orders/42");
            assertThat(serialize(request).get("path").asString()).isEqualTo("/orders/42");
        }

        @Test
        void schemeReflectsTheRequestScheme() {
            val request = new MockHttpServletRequest();
            request.setScheme("https");
            assertThat(serialize(request).get("scheme").asString()).isEqualTo("https");
        }

        @Test
        void hostIsTheServerNameField() {
            val request = new MockHttpServletRequest();
            request.setServerName("api.example.com");
            assertThat(serialize(request).get("host").asString()).isEqualTo("api.example.com");
        }

        @Test
        void portIsTheServerPortField() {
            val request = new MockHttpServletRequest();
            request.setServerPort(8443);
            assertThat(serialize(request).get("port").asInt()).isEqualTo(8443);
        }

        @Test
        void urlIncludesQueryStringWhenPresent() {
            val request = new MockHttpServletRequest();
            request.setScheme("https");
            request.setServerName("api.example.com");
            request.setServerPort(443);
            request.setRequestURI("/orders/42");
            request.setQueryString("role=admin");
            assertThat(serialize(request).get("url").asString())
                    .isEqualTo("https://api.example.com/orders/42?role=admin");
        }

        @Test
        void urlOmitsQueryStringWhenAbsent() {
            val request = new MockHttpServletRequest();
            request.setScheme("http");
            request.setServerName("localhost");
            request.setServerPort(80);
            request.setRequestURI("/orders");
            assertThat(serialize(request).get("url").asString()).isEqualTo("http://localhost/orders");
        }
    }

    @Nested
    class QueryAndParameters {

        @Test
        void queryAndParsedParametersWhenPresent() {
            val request = new MockHttpServletRequest();
            request.setQueryString("role=admin&tag=alpha&tag=beta");
            val result = serialize(request);
            assertThat(result.get("query").asString()).isEqualTo("role=admin&tag=alpha&tag=beta");
            val parsed = result.get("queryParameters");
            assertThat(parsed.get("role").get(0).asString()).isEqualTo("admin");
            assertThat(parsed.get("tag").get(0).asString()).isEqualTo("alpha");
            assertThat(parsed.get("tag").get(1).asString()).isEqualTo("beta");
        }

        @Test
        void queryAndQueryParametersAreAbsentWhenNoQueryStringIsSet() {
            val result = serialize(new MockHttpServletRequest());
            assertThat(result.get("query")).isNull();
            assertThat(result.get("queryParameters")).isNull();
        }

        @Test
        void percentEncodedValuesAreUrlDecoded() {
            val request = new MockHttpServletRequest();
            request.setQueryString("q=hello%20world");
            val parsed = serialize(request).get("queryParameters");
            assertThat(parsed.get("q").get(0).asString()).isEqualTo("hello world");
        }
    }

    @Nested
    class PathSubdivision {

        @Test
        void contextPathSurvivesUnderItsName() {
            val request = new MockHttpServletRequest();
            request.setContextPath("/myapp");
            request.setRequestURI("/myapp/orders/42");
            assertThat(serialize(request).get("contextPath").asString()).isEqualTo("/myapp");
        }

        @Test
        void applicationPathStripsContextPathPrefix() {
            val request = new MockHttpServletRequest();
            request.setContextPath("/myapp");
            request.setRequestURI("/myapp/orders/42");
            assertThat(serialize(request).get("applicationPath").asString()).isEqualTo("/orders/42");
        }

        @Test
        void applicationPathEqualsPathWhenContextPathIsEmpty() {
            val request = new MockHttpServletRequest();
            request.setContextPath("");
            request.setRequestURI("/orders/42");
            assertThat(serialize(request).get("applicationPath").asString()).isEqualTo("/orders/42");
        }
    }

    @Nested
    class Connection {

        @Test
        void isSecureMirrorsTheRequestSecureFlag() {
            val request = new MockHttpServletRequest();
            request.setSecure(true);
            assertThat(serialize(request).get("isSecure").asBoolean()).isTrue();
        }

        @Test
        void clientGroupExposesAddressHostPort() {
            val request = new MockHttpServletRequest();
            request.setRemoteAddr("203.0.113.7");
            request.setRemoteHost("client.example.com");
            request.setRemotePort(54402);
            val client = serialize(request).get("client");
            assertThat(client.get("address").asString()).isEqualTo("203.0.113.7");
            assertThat(client.get("host").asString()).isEqualTo("client.example.com");
            assertThat(client.get("port").asInt()).isEqualTo(54402);
        }

        @Test
        void serverGroupExposesAddressHostPort() {
            val request = new MockHttpServletRequest();
            request.setLocalAddr("10.0.0.1");
            request.setLocalName("internal-host");
            request.setLocalPort(8443);
            val server = serialize(request).get("server");
            assertThat(server.get("address").asString()).isEqualTo("10.0.0.1");
            assertThat(server.get("host").asString()).isEqualTo("internal-host");
            assertThat(server.get("port").asInt()).isEqualTo(8443);
        }
    }

    @Nested
    class HeadersAndCookies {

        @Test
        void headersHaveLowercaseKeysAndAreMultiValued() {
            val request = new MockHttpServletRequest();
            request.addHeader("X-Custom", "value-1a");
            request.addHeader("X-Custom", "value-1b");
            request.addHeader("Authorization", "Bearer abc");
            val headers = serialize(request).get("headers");
            assertThat(headers.get("x-custom").get(0).asString()).isEqualTo("value-1a");
            assertThat(headers.get("x-custom").get(1).asString()).isEqualTo("value-1b");
            assertThat(headers.get("authorization").get(0).asString()).isEqualTo("Bearer abc");
        }

        @Test
        void cookiesAreEmittedAsObjects() {
            val request = new MockHttpServletRequest();
            request.setCookies(new Cookie("session", "abc123"), new Cookie("pref", "dark"));
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
            val result = serialize(new MockHttpServletRequest());
            assertThat(result.get("forwarded")).isNull();
        }

        @Test
        void legacyXForwardedHeadersAreParsedIntoChainHostProtoPort() {
            val request = new MockHttpServletRequest();
            request.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.7");
            request.addHeader("X-Forwarded-Host", "api.example.com");
            request.addHeader("X-Forwarded-Proto", "https");
            request.addHeader("X-Forwarded-Port", "443");
            val forwarded = serialize(request).get("forwarded");
            assertThat(forwarded.get("for").get(0).asString()).isEqualTo("198.51.100.1");
            assertThat(forwarded.get("for").get(1).asString()).isEqualTo("203.0.113.7");
            assertThat(forwarded.get("host").asString()).isEqualTo("api.example.com");
            assertThat(forwarded.get("proto").asString()).isEqualTo("https");
            assertThat(forwarded.get("port").asInt()).isEqualTo(443);
        }

        @Test
        void rfc7239ForwardedHeaderTakesPrecedenceOverLegacyXForwardedFamily() {
            val request = new MockHttpServletRequest();
            request.addHeader("Forwarded", "for=198.51.100.1;host=api.example.com;proto=https");
            request.addHeader("X-Forwarded-Host", "ignored.example.com");
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
            val request = new MockHttpServletRequest();
            request.setContentType(MediaType.APPLICATION_JSON_VALUE);
            assertThat(serialize(request).get("contentType").asString()).startsWith("application/json");
        }

        @Test
        void contentTypeIsAbsentWhenUnset() {
            assertThat(serialize(new MockHttpServletRequest()).get("contentType")).isNull();
        }

        @Test
        void contentLengthIsExposedWhenPresent() {
            val request = new MockHttpServletRequest();
            request.setContent(new byte[142]);
            assertThat(serialize(request).get("contentLength").asLong()).isEqualTo(142L);
        }

        @Test
        void characterEncodingIsExposedWhenSet() {
            val request = new MockHttpServletRequest();
            request.setCharacterEncoding("UTF-8");
            assertThat(serialize(request).get("characterEncoding").asString()).isEqualTo("UTF-8");
        }
    }
}
