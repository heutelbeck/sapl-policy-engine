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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.val;
import tools.jackson.core.JacksonException;
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

    private JsonNode serialize(HttpServletRequest request) throws JacksonException {
        return mapper.valueToTree(request);
    }

    @Test
    void whenProtocolSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "HTTP/2";
        val request  = new MockHttpServletRequest();
        request.setProtocol(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.PROTOCOL).asString()).isEqualTo(expected);
    }

    @Test
    void whenParametersSet_thenItIsTheSameInJson() throws JacksonException {
        val request = new MockHttpServletRequest();
        request.setParameter("key1", "value1a", "value1b");
        request.setParameter("key2", "value2");
        val result = serialize(request);
        val params = result.get(HttpServletRequestSerializer.PARAMETERS);
        assertThat(params.get("key1").get(0).asString()).isEqualTo("value1a");
        assertThat(params.get("key1").get(1).asString()).isEqualTo("value1b");
        assertThat(params.get("key2").get(0).asString()).isEqualTo("value2");
    }

    @Test
    void whenCookiesSet_thenItIsTheSameInJson() throws JacksonException {
        val request = new MockHttpServletRequest();
        request.setCookies(new Cookie("name1", "value1"), new Cookie("name2", "value2"));
        val result  = serialize(request);
        val cookies = result.get(HttpServletRequestSerializer.COOKIES);
        assertThat(cookies).hasSize(2);
        assertThat(cookies.get(0).get("name").asString()).isEqualTo("name1");
        assertThat(cookies.get(0).get("value").asString()).isEqualTo("value1");
    }

    @Test
    void whenHeadersSet_thenItIsTheSameInJson() throws JacksonException {
        val request = new MockHttpServletRequest();
        request.addHeader("header1", "value1a");
        request.addHeader("header1", "value1b");
        request.addHeader("header2", "value2");
        request.addHeader("header3", "value3");
        val result  = serialize(request);
        val headers = result.get(HttpServletRequestSerializer.HEADERS);
        assertThat(headers.get("header1")).isNotNull();
        assertThat(headers.get("header2")).isNotNull();
    }

    @Test
    void whenServerNameSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "sapl.io";
        val request  = new MockHttpServletRequest();
        request.setServerName(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.SERVER_NAME).asString()).isEqualTo(expected);
    }

    @Test
    void whenServerPortSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = 443;
        val request  = new MockHttpServletRequest();
        request.setServerPort(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.SERVER_PORT).asInt()).isEqualTo(expected);
    }

    @Test
    void whenRemoteAddressSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "123.022.233.121";
        val request  = new MockHttpServletRequest();
        request.setRemoteAddr(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.REMOTE_ADDRESS).asString()).isEqualTo(expected);
    }

    @Test
    void whenRemotePortSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = 8443;
        val request  = new MockHttpServletRequest();
        request.setRemotePort(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.REMOTE_PORT).asInt()).isEqualTo(expected);
    }

    @Test
    void whenRemoteHostSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "demo.sapl.io";
        val request  = new MockHttpServletRequest();
        request.setRemoteHost(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.REMOTE_HOST).asString()).isEqualTo(expected);
    }

    @Test
    void whenSecureSet_thenItIsTheSameInJson() throws JacksonException {
        val request = new MockHttpServletRequest();
        request.setSecure(true);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.IS_SECURE).asBoolean()).isTrue();
    }

    @Test
    void whenLocalNameSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "localhostname";
        val request  = new MockHttpServletRequest();
        request.setLocalName(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.LOCAL_NAME).asString()).isEqualTo(expected);
    }

    @Test
    void whenLocalAddressSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "98.99.100.1";
        val request  = new MockHttpServletRequest();
        request.setLocalAddr(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.LOCAL_ADDRESS).asString()).isEqualTo(expected);
    }

    @Test
    void whenLocalPortSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = 8083;
        val request  = new MockHttpServletRequest();
        request.setLocalPort(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.LOCAL_PORT).asInt()).isEqualTo(expected);
    }

    @Test
    void whenMethodNameSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "GET";
        val request  = new MockHttpServletRequest();
        request.setMethod(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.METHOD).asString()).isEqualTo(expected);
    }

    @Test
    void whenContextPathSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "/a/b/c";
        val request  = new MockHttpServletRequest();
        request.setContextPath(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.CONTEXT_PATH).asString()).isEqualTo(expected);
    }

    @Test
    void whenEncodingSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = StandardCharsets.US_ASCII.toString();
        val request  = new MockHttpServletRequest();
        request.setCharacterEncoding(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.CHARACTER_ENCODING).asString()).isEqualTo(expected);
    }

    @Test
    void whenContentTypeSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = MediaType.APPLICATION_JSON_VALUE;
        val request  = new MockHttpServletRequest();
        request.setContentType(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.CONTENT_TYPE).asString()).isEqualTo(expected);
    }

    @Test
    void whenAuthTypeSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "basic";
        val request  = new MockHttpServletRequest();
        request.setAuthType(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.AUTH_TYPE).asString()).isEqualTo(expected);
    }

    @Test
    void whenQuerySet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "a=b";
        val request  = new MockHttpServletRequest();
        request.setQueryString(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.QUERY_STRING).asString()).isEqualTo(expected);
    }

    @Test
    void whenSessionIdSet_thenItIsTheSameInJson() throws JacksonException {
        val request = new MockHttpServletRequest();
        val session = new MockHttpSession(request.getServletContext());
        request.setSession(session);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.REQUESTED_SESSION_ID).asString()).isEqualTo(session.getId());
    }

    @Test
    void whenRequestedUriIsSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "https://localhorst";
        val request  = new MockHttpServletRequest();
        request.setRequestURI(expected);
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.REQUESTED_URI).asString()).isEqualTo(expected);
    }

    @Test
    void whenRequestUrlIsSet_thenItIsTheSameInJson() throws JacksonException {
        val expected = "http://localhost";
        val request  = new MockHttpServletRequest();
        val result   = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.REQUEST_URL).asString()).isEqualTo(expected);
    }

    @Test
    void whenLocaleSet_thenItIsTheSameInJson() throws JacksonException {
        val request = new MockHttpServletRequest();
        request.setPreferredLocales(List.of(Locale.GERMAN, Locale.UK));
        val result = serialize(request);
        assertThat(result.get(HttpServletRequestSerializer.LOCALE).asString()).isEqualTo(Locale.GERMAN.toString());
        val locales = result.get(HttpServletRequestSerializer.LOCALES);
        assertThat(locales.get(0).asString()).isEqualTo(Locale.GERMAN.toString());
        assertThat(locales.get(1).asString()).isEqualTo(Locale.UK.toString());
    }

}
