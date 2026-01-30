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

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.val;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

import java.util.Enumeration;
import java.util.Locale;
import java.util.Map.Entry;

/**
 * Jackson serializer for HttpServletRequest that safely extracts request
 * metadata without calling problematic methods like getReader() or
 * getInputStream().
 */
public class HttpServletRequestSerializer extends StdSerializer<HttpServletRequest> {

    static final String AUTH_TYPE            = "authType";
    static final String CHARACTER_ENCODING   = "characterEncoding";
    static final String CONTENT_TYPE         = "contentType";
    static final String CONTEXT_PATH         = "contextPath";
    static final String COOKIES              = "cookies";
    static final String HEADERS              = "headers";
    static final String IS_SECURE            = "isSecure";
    static final String LOCAL_ADDRESS        = "localAddress";
    static final String LOCAL_NAME           = "localName";
    static final String LOCAL_PORT           = "localPort";
    static final String LOCALE               = "locale";
    static final String LOCALES              = "locales";
    static final String METHOD               = "method";
    static final String PARAMETERS           = "parameters";
    static final String PROTOCOL             = "protocol";
    static final String QUERY_STRING         = "queryString";
    static final String REMOTE_ADDRESS       = "remoteAddress";
    static final String REMOTE_HOST          = "remoteHost";
    static final String REMOTE_PORT          = "remotePort";
    static final String REQUEST_URL          = "requestURL";
    static final String REQUESTED_SESSION_ID = "requestedSessionId";
    static final String REQUESTED_URI        = "requestedURI";
    static final String SCHEME               = "scheme";
    static final String SERVER_NAME          = "serverName";
    static final String SERVER_PORT          = "serverPort";
    static final String SERVLET_PATH         = "servletPath";

    public HttpServletRequestSerializer() {
        super(HttpServletRequest.class);
    }

    @Override
    public void serialize(HttpServletRequest value, JsonGenerator gen, SerializationContext serializers) {
        gen.writeStartObject();
        if (value.getCharacterEncoding() != null)
            gen.writeStringProperty(CHARACTER_ENCODING, value.getCharacterEncoding());
        if (value.getContentType() != null)
            gen.writeStringProperty(CONTENT_TYPE, value.getContentType());
        gen.writeStringProperty(PROTOCOL, value.getProtocol());
        gen.writeStringProperty(SCHEME, value.getScheme());
        gen.writeStringProperty(SERVER_NAME, value.getServerName());
        gen.writeNumberProperty(SERVER_PORT, value.getServerPort());
        gen.writeStringProperty(REMOTE_ADDRESS, value.getRemoteAddr());
        gen.writeStringProperty(REMOTE_HOST, value.getRemoteHost());
        gen.writeNumberProperty(REMOTE_PORT, value.getRemotePort());
        gen.writeBooleanProperty(IS_SECURE, value.isSecure());
        gen.writeStringProperty(LOCAL_NAME, value.getLocalName());
        gen.writeStringProperty(LOCAL_ADDRESS, value.getLocalAddr());
        gen.writeNumberProperty(LOCAL_PORT, value.getLocalPort());
        if (value.getAuthType() != null)
            gen.writeStringProperty(AUTH_TYPE, value.getAuthType());
        gen.writeStringProperty(METHOD, value.getMethod());
        gen.writeStringProperty(CONTEXT_PATH, value.getContextPath());
        if (value.getQueryString() != null)
            gen.writeStringProperty(QUERY_STRING, value.getQueryString());
        val session = value.getSession(false);
        if (session != null && session.getId() != null)
            gen.writeStringProperty(REQUESTED_SESSION_ID, session.getId());
        gen.writeStringProperty(REQUESTED_URI, value.getRequestURI());
        gen.writeStringProperty(REQUEST_URL, value.getRequestURL().toString());
        gen.writeStringProperty(SERVLET_PATH, value.getServletPath());

        writeHeaders(value, gen);
        writeCookies(value, gen);
        writeLocales(value, gen);
        writeParameters(value, gen);
        gen.writeEndObject();
    }

    private void writeHeaders(HttpServletRequest value, JsonGenerator gen) {
        val headerNames = value.getHeaderNames();
        if (headerNames.hasMoreElements()) {
            gen.writeName(HEADERS);
            gen.writeStartObject();
            while (headerNames.hasMoreElements()) {
                val name    = headerNames.nextElement();
                val headers = value.getHeaders(name);
                if (headers.hasMoreElements()) {
                    gen.writeName(name);
                    gen.writeStartArray();
                    while (headers.hasMoreElements()) {
                        gen.writeString(headers.nextElement());
                    }
                    gen.writeEndArray();
                }
            }
            gen.writeEndObject();
        }
    }

    private void writeCookies(HttpServletRequest value, JsonGenerator gen) {
        if (value.getCookies() == null)
            return;
        gen.writeName(COOKIES);
        gen.writeStartArray();
        for (Cookie cookie : value.getCookies()) {
            gen.writeStartObject();
            gen.writeStringProperty("name", cookie.getName());
            gen.writeStringProperty("value", cookie.getValue());
            gen.writeEndObject();
        }
        gen.writeEndArray();
    }

    private void writeLocales(HttpServletRequest value, JsonGenerator gen) {
        gen.writeStringProperty(LOCALE, value.getLocale().toString());
        gen.writeName(LOCALES);
        gen.writeStartArray();
        val locales = value.getLocales();
        while (locales.hasMoreElements()) {
            gen.writeString(locales.nextElement().toString());
        }
        gen.writeEndArray();
    }

    private void writeParameters(HttpServletRequest value, JsonGenerator gen) {
        if (value.getParameterMap().isEmpty())
            return;

        gen.writeName(PARAMETERS);
        gen.writeStartObject();
        for (Entry<String, String[]> entry : value.getParameterMap().entrySet()) {
            gen.writeName(entry.getKey());
            gen.writeStartArray();
            for (val paramValue : entry.getValue()) {
                gen.writeString(paramValue);
            }
            gen.writeEndArray();
        }
        gen.writeEndObject();
    }

}
