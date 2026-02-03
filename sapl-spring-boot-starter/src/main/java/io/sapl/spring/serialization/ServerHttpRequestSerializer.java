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

import lombok.val;
import org.springframework.http.server.reactive.ServerHttpRequest;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson serializer for ServerHttpRequest that safely extracts request
 * metadata for reactive web applications.
 */
public class ServerHttpRequestSerializer extends StdSerializer<ServerHttpRequest> {

    static final String CONTEXT_PATH   = "contextPath";
    static final String COOKIES        = "cookies";
    static final String HEADERS        = "headers";
    static final String LOCAL_ADDRESS  = "localAddress";
    static final String LOCAL_NAME     = "localName";
    static final String LOCAL_PORT     = "localPort";
    static final String METHOD         = "method";
    static final String PARAMETERS     = "parameters";
    static final String REMOTE_ADDRESS = "remoteAddress";
    static final String REMOTE_HOST    = "remoteHost";
    static final String REMOTE_PORT    = "remotePort";
    static final String REQUESTED_URI  = "requestedURI";
    static final String SCHEME         = "scheme";
    static final String SERVER_NAME    = "serverName";
    static final String SERVER_PORT    = "serverPort";

    public ServerHttpRequestSerializer() {
        super(ServerHttpRequest.class);
    }

    @Override
    public void serialize(ServerHttpRequest value, JsonGenerator gen, SerializationContext serializers) {
        gen.writeStartObject();
        gen.writeStringProperty(SCHEME, value.getURI().getScheme());
        gen.writeStringProperty(SERVER_NAME, value.getURI().getHost());
        gen.writeNumberProperty(SERVER_PORT, value.getURI().getPort());
        val remoteAddress = value.getRemoteAddress();
        if (remoteAddress != null) {
            gen.writeStringProperty(REMOTE_ADDRESS, remoteAddress.toString());
            gen.writeStringProperty(REMOTE_HOST, remoteAddress.getHostString());
            gen.writeNumberProperty(REMOTE_PORT, remoteAddress.getPort());
        }
        val localAddress = value.getLocalAddress();
        if (localAddress != null) {
            gen.writeStringProperty(LOCAL_NAME, localAddress.getHostString());
            gen.writeStringProperty(LOCAL_ADDRESS, localAddress.toString());
            gen.writeNumberProperty(LOCAL_PORT, localAddress.getPort());
        }
        gen.writeStringProperty(METHOD, value.getMethod().name());
        gen.writeStringProperty(CONTEXT_PATH, String.valueOf(value.getPath()));
        gen.writeStringProperty(REQUESTED_URI, String.valueOf(value.getURI()));
        writeHeaders(value, gen);
        writeCookies(value, gen);
        writeParameters(value, gen);
        gen.writeEndObject();
    }

    private void writeHeaders(ServerHttpRequest value, JsonGenerator gen) {
        val headers = value.getHeaders();
        if (headers.isEmpty())
            return;
        gen.writeName(HEADERS);
        gen.writeStartObject();
        for (val entry : headers.headerSet()) {
            gen.writeName(entry.getKey());
            gen.writeStartArray();
            for (val headerValue : entry.getValue())
                gen.writeString(headerValue);
            gen.writeEndArray();
        }
        gen.writeEndObject();
    }

    private void writeCookies(ServerHttpRequest value, JsonGenerator gen) {
        val cookies = value.getCookies();
        if (cookies.isEmpty())
            return;

        gen.writeName(COOKIES);
        gen.writeStartArray();
        for (val entry : cookies.entrySet()) {
            for (val cookie : entry.getValue()) {
                gen.writeStartObject();
                gen.writeStringProperty("name", cookie.getName());
                gen.writeStringProperty("value", cookie.getValue());
                gen.writeEndObject();
            }
        }
        gen.writeEndArray();
    }

    private void writeParameters(ServerHttpRequest value, JsonGenerator gen) {
        if (value.getQueryParams().isEmpty())
            return;

        gen.writeName(PARAMETERS);
        gen.writeStartObject();
        for (val entry : value.getQueryParams().entrySet()) {
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
