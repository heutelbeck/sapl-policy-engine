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

import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map.Entry;

import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

@JsonComponent
public class HttpServletRequestSerializer extends JsonSerializer<HttpServletRequest> {

    static final String PARAMETERS           = "parameters";
    static final String LOCALES              = "locales";
    static final String LOCALE               = "locale";
    static final String COOKIES              = "cookies";
    static final String HEADERS              = "headers";
    static final String SERVLET_PATH         = "servletPath";
    static final String REQUEST_URL          = "requestURL";
    static final String REQUESTED_URI        = "requestedURI";
    static final String REQUESTED_SESSION_ID = "requestedSessionId";
    static final String QUERY_STRING         = "queryString";
    static final String CONTEXT_PATH         = "contextPath";
    static final String METHOD               = "method";
    static final String AUTH_TYPE            = "authType";
    static final String LOCAL_PORT           = "localPort";
    static final String LOCAL_ADDRESS        = "localAddress";
    static final String LOCAL_NAME           = "localName";
    static final String IS_SECURE            = "isSecure";
    static final String REMOTE_PORT          = "remotePort";
    static final String REMOTE_HOST          = "remoteHost";
    static final String REMOTE_ADDRESS       = "remoteAddress";
    static final String SERVER_PORT          = "serverPort";
    static final String SERVER_NAME          = "serverName";
    static final String SCHEME               = "scheme";
    static final String PROTOCOL             = "protocol";
    static final String CONTENT_TYPE         = "Content-Type";
    static final String CHARACTER_ENCODING   = "characterEncoding";

    @Override
    public void serialize(HttpServletRequest value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeStartObject();
        if (value.getCharacterEncoding() != null)
            gen.writeStringField(CHARACTER_ENCODING, value.getCharacterEncoding());
        if (value.getContentType() != null)
            gen.writeStringField(CONTENT_TYPE, value.getContentType());
        gen.writeStringField(PROTOCOL, value.getProtocol());
        gen.writeStringField(SCHEME, value.getScheme());
        gen.writeStringField(SERVER_NAME, value.getServerName());
        gen.writeNumberField(SERVER_PORT, value.getServerPort());
        gen.writeStringField(REMOTE_ADDRESS, value.getRemoteAddr());
        gen.writeStringField(REMOTE_HOST, value.getRemoteHost());
        gen.writeNumberField(REMOTE_PORT, value.getRemotePort());
        gen.writeBooleanField(IS_SECURE, value.isSecure());
        gen.writeStringField(LOCAL_NAME, value.getLocalName());
        gen.writeStringField(LOCAL_ADDRESS, value.getLocalAddr());
        gen.writeNumberField(LOCAL_PORT, value.getLocalPort());
        if (value.getAuthType() != null)
            gen.writeStringField(AUTH_TYPE, value.getAuthType());
        gen.writeStringField(METHOD, value.getMethod());
        gen.writeStringField(CONTEXT_PATH, value.getContextPath());
        if (value.getQueryString() != null)
            gen.writeStringField(QUERY_STRING, value.getQueryString());
        final var session = value.getSession();
        if (session != null && session.getId() != null)
            gen.writeStringField(REQUESTED_SESSION_ID, session.getId());
        gen.writeStringField(REQUESTED_URI, value.getRequestURI());
        gen.writeStringField(REQUEST_URL, value.getRequestURL().toString());
        gen.writeStringField(SERVLET_PATH, value.getServletPath());

        writeHeaders(value, gen);
        writeCookies(value, gen);
        writeLocales(value, gen);
        writeParameters(value, gen);
        gen.writeEndObject();
    }

    private void writeHeaders(HttpServletRequest value, JsonGenerator gen) throws IOException {
        Enumeration<String> headerNames = value.getHeaderNames();
        if (headerNames.hasMoreElements()) {
            gen.writeObjectFieldStart(HEADERS);
            while (headerNames.hasMoreElements()) {
                String              name    = headerNames.nextElement();
                Enumeration<String> headers = value.getHeaders(name);
                if (headerNames.hasMoreElements()) {
                    gen.writeArrayFieldStart(name);
                    while (headers.hasMoreElements()) {
                        gen.writeString(headers.nextElement());
                    }
                    gen.writeEndArray();
                }
            }
            gen.writeEndObject();
        }
    }

    private void writeCookies(HttpServletRequest value, JsonGenerator gen) throws IOException {
        if (value.getCookies() == null)
            return;
        gen.writeArrayFieldStart(COOKIES);
        for (Cookie cookie : value.getCookies()) {
            gen.writeStartObject();
            gen.writeObjectField("name", cookie.getName());
            gen.writeObjectField("value", cookie.getValue());
            gen.writeEndObject();
        }
        gen.writeEndArray();
    }

    private void writeLocales(HttpServletRequest value, JsonGenerator gen) throws IOException {
        gen.writeStringField(LOCALE, value.getLocale().toString());
        Enumeration<Locale> locales = value.getLocales();
        gen.writeArrayFieldStart(LOCALES);
        while (locales.hasMoreElements()) {
            gen.writeString(locales.nextElement().toString());
        }
        gen.writeEndArray();
    }

    private void writeParameters(HttpServletRequest value, JsonGenerator gen) throws IOException {
        if (value.getParameterMap().isEmpty())
            return;

        gen.writeObjectFieldStart(PARAMETERS);
        for (Entry<String, String[]> entry : value.getParameterMap().entrySet()) {
            gen.writeArrayFieldStart(entry.getKey());
            for (String val : entry.getValue()) {
                gen.writeString(val);
            }
            gen.writeEndArray();
        }
        gen.writeEndObject();
    }

}
