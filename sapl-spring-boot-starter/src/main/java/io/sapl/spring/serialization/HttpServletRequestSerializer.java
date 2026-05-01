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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.val;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson serializer for {@link HttpServletRequest} that exposes the
 * unified policy-facing HTTP shape. Same field names and grouping as
 * {@link ServerHttpRequestSerializer}, so a SAPL policy reads the same
 * fields regardless of whether the deployed PEP is on the servlet stack
 * or the reactive stack. Header keys are lowercased to match HTTP/2 wire
 * format and Spring's case-insensitive {@code HttpHeaders} contract.
 */
public class HttpServletRequestSerializer extends StdSerializer<HttpServletRequest> {

    static final String ADDRESS            = "address";
    static final String APPLICATION_PATH   = "applicationPath";
    static final String CHARACTER_ENCODING = "characterEncoding";
    static final String CLIENT             = "client";
    static final String CONTENT_LENGTH     = "contentLength";
    static final String CONTENT_TYPE       = "contentType";
    static final String CONTEXT_PATH       = "contextPath";
    static final String COOKIES            = "cookies";
    static final String FORWARDED          = "forwarded";
    static final String FORWARDED_FOR      = "for";
    static final String FORWARDED_HOST     = "host";
    static final String FORWARDED_PORT     = "port";
    static final String FORWARDED_PROTO    = "proto";
    static final String HEADERS            = "headers";
    static final String HOST               = "host";
    static final String IS_SECURE          = "isSecure";
    static final String METHOD             = "method";
    static final String PATH               = "path";
    static final String PORT               = "port";
    static final String QUERY              = "query";
    static final String QUERY_PARAMETERS   = "queryParameters";
    static final String SCHEME             = "scheme";
    static final String SERVER             = "server";
    static final String URL                = "url";

    public HttpServletRequestSerializer() {
        super(HttpServletRequest.class);
    }

    @Override
    public void serialize(HttpServletRequest value, JsonGenerator gen, SerializationContext serializers) {
        gen.writeStartObject();
        gen.writeStringProperty(METHOD, value.getMethod());
        gen.writeStringProperty(URL, fullUrl(value));
        gen.writeStringProperty(SCHEME, value.getScheme());
        gen.writeStringProperty(HOST, value.getServerName());
        gen.writeNumberProperty(PORT, value.getServerPort());
        gen.writeStringProperty(PATH, value.getRequestURI());
        if (value.getQueryString() != null) {
            gen.writeStringProperty(QUERY, value.getQueryString());
            writeQueryParameters(value.getQueryString(), gen);
        }
        gen.writeStringProperty(CONTEXT_PATH, value.getContextPath());
        gen.writeStringProperty(APPLICATION_PATH, applicationPath(value));
        gen.writeBooleanProperty(IS_SECURE, value.isSecure());
        writeClient(value, gen);
        writeServer(value, gen);
        writeHeaders(value, gen);
        writeCookies(value, gen);
        writeForwarded(value, gen);
        writeBodyMetadata(value, gen);
        gen.writeEndObject();
    }

    private static String fullUrl(HttpServletRequest request) {
        val url = request.getRequestURL();
        if (request.getQueryString() != null) {
            url.append('?').append(request.getQueryString());
        }
        return url.toString();
    }

    private static String applicationPath(HttpServletRequest request) {
        val path        = request.getRequestURI();
        val contextPath = request.getContextPath();
        if (contextPath == null || contextPath.isEmpty()) {
            return path;
        }
        return path.startsWith(contextPath) ? path.substring(contextPath.length()) : path;
    }

    private static void writeQueryParameters(String queryString, JsonGenerator gen) {
        val parsed = parseQuery(queryString);
        if (parsed.isEmpty()) {
            return;
        }
        gen.writeName(QUERY_PARAMETERS);
        gen.writeStartObject();
        for (val entry : parsed.entrySet()) {
            gen.writeName(entry.getKey());
            gen.writeStartArray();
            for (val parameterValue : entry.getValue()) {
                gen.writeString(parameterValue);
            }
            gen.writeEndArray();
        }
        gen.writeEndObject();
    }

    private static Map<String, List<String>> parseQuery(String queryString) {
        val out = new LinkedHashMap<String, List<String>>();
        for (val pair : queryString.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            val eq    = pair.indexOf('=');
            val key   = eq < 0 ? decode(pair) : decode(pair.substring(0, eq));
            val value = eq < 0 ? "" : decode(pair.substring(eq + 1));
            out.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        return out;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void writeClient(HttpServletRequest request, JsonGenerator gen) {
        gen.writeName(CLIENT);
        gen.writeStartObject();
        gen.writeStringProperty(ADDRESS, request.getRemoteAddr());
        gen.writeStringProperty(HOST, request.getRemoteHost());
        gen.writeNumberProperty(PORT, request.getRemotePort());
        gen.writeEndObject();
    }

    private static void writeServer(HttpServletRequest request, JsonGenerator gen) {
        gen.writeName(SERVER);
        gen.writeStartObject();
        gen.writeStringProperty(ADDRESS, request.getLocalAddr());
        gen.writeStringProperty(HOST, request.getLocalName());
        gen.writeNumberProperty(PORT, request.getLocalPort());
        gen.writeEndObject();
    }

    private static void writeHeaders(HttpServletRequest request, JsonGenerator gen) {
        val headerNames = request.getHeaderNames();
        if (headerNames == null || !headerNames.hasMoreElements()) {
            return;
        }
        gen.writeName(HEADERS);
        gen.writeStartObject();
        while (headerNames.hasMoreElements()) {
            val name    = headerNames.nextElement();
            val headers = request.getHeaders(name);
            if (headers != null && headers.hasMoreElements()) {
                gen.writeName(name.toLowerCase(Locale.ROOT));
                gen.writeStartArray();
                while (headers.hasMoreElements()) {
                    gen.writeString(headers.nextElement());
                }
                gen.writeEndArray();
            }
        }
        gen.writeEndObject();
    }

    private static void writeCookies(HttpServletRequest request, JsonGenerator gen) {
        val cookies = request.getCookies();
        if (cookies == null) {
            return;
        }
        gen.writeName(COOKIES);
        gen.writeStartArray();
        for (Cookie cookie : cookies) {
            gen.writeStartObject();
            gen.writeStringProperty("name", cookie.getName());
            gen.writeStringProperty("value", cookie.getValue());
            gen.writeEndObject();
        }
        gen.writeEndArray();
    }

    private static void writeForwarded(HttpServletRequest request, JsonGenerator gen) {
        val parsed = ForwardedHeaders.parse(name -> headerValuesFor(request, name));
        if (parsed.isEmpty()) {
            return;
        }
        gen.writeName(FORWARDED);
        gen.writeStartObject();
        if (!parsed.forChain().isEmpty()) {
            gen.writeName(FORWARDED_FOR);
            gen.writeStartArray();
            for (val client : parsed.forChain()) {
                gen.writeString(client);
            }
            gen.writeEndArray();
        }
        if (parsed.host() != null) {
            gen.writeStringProperty(FORWARDED_HOST, parsed.host());
        }
        if (parsed.proto() != null) {
            gen.writeStringProperty(FORWARDED_PROTO, parsed.proto());
        }
        if (parsed.port() != null) {
            gen.writeNumberProperty(FORWARDED_PORT, parsed.port());
        }
        gen.writeEndObject();
    }

    private static List<String> headerValuesFor(HttpServletRequest request, String lowercaseName) {
        val headers = request.getHeaders(lowercaseName);
        if (headers == null || !headers.hasMoreElements()) {
            return List.of();
        }
        return Collections.list(headers);
    }

    private static void writeBodyMetadata(HttpServletRequest request, JsonGenerator gen) {
        if (request.getContentType() != null) {
            gen.writeStringProperty(CONTENT_TYPE, request.getContentType());
        }
        val contentLength = request.getContentLengthLong();
        if (contentLength >= 0) {
            gen.writeNumberProperty(CONTENT_LENGTH, contentLength);
        }
        if (request.getCharacterEncoding() != null) {
            gen.writeStringProperty(CHARACTER_ENCODING, request.getCharacterEncoding());
        }
    }

}
