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
package io.sapl.spring.serialization;

import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;

import org.springframework.boot.jackson.JsonComponent;
import org.springframework.http.server.reactive.ServerHttpRequest;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

@JsonComponent
public class ServerHttpRequestSerializer extends JsonSerializer<ServerHttpRequest> {

    static final String PARAMETERS     = "parameters";
    static final String COOKIES        = "cookies";
    static final String HEADERS        = "headers";
    static final String REQUESTED_URI  = "requestedURI";
    static final String CONTEXT_PATH   = "contextPath";
    static final String METHOD         = "method";
    static final String LOCAL_PORT     = "localPort";
    static final String LOCAL_ADDRESS  = "localAddress";
    static final String LOCAL_NAME     = "localName";
    static final String REMOTE_PORT    = "remotePort";
    static final String REMOTE_HOST    = "remoteHost";
    static final String REMOTE_ADDRESS = "remoteAddress";
    static final String SERVER_PORT    = "serverPort";
    static final String SERVER_NAME    = "serverName";
    static final String SCHEME         = "scheme";

    @Override
    public void serialize(ServerHttpRequest value, JsonGenerator gen, SerializerProvider serializers)
            throws IOException {
        gen.writeStartObject();
        gen.writeStringField(SCHEME, value.getURI().getScheme());
        gen.writeStringField(SERVER_NAME, value.getURI().getHost());
        gen.writeNumberField(SERVER_PORT, value.getURI().getPort());
        var remoteAddress = value.getRemoteAddress();
        if (remoteAddress != null) {
            gen.writeStringField(REMOTE_ADDRESS, remoteAddress.toString());
            gen.writeStringField(REMOTE_HOST, remoteAddress.getHostString());
            gen.writeNumberField(REMOTE_PORT, remoteAddress.getPort());
        }
        var localAddress = value.getLocalAddress();
        if (localAddress != null) {
            gen.writeStringField(LOCAL_NAME, localAddress.getHostString());
            gen.writeStringField(LOCAL_ADDRESS, localAddress.toString());
            gen.writeNumberField(LOCAL_PORT, localAddress.getPort());
        }
        gen.writeStringField(METHOD, value.getMethod().name());
        gen.writeStringField(CONTEXT_PATH, String.valueOf(value.getPath()));
        gen.writeStringField(REQUESTED_URI, String.valueOf(value.getURI()));
        writeHeaders(value, gen);
        writeCookies(value, gen);
        writeParameters(value, gen);
        gen.writeEndObject();
    }

    private void writeHeaders(ServerHttpRequest value, JsonGenerator gen) throws IOException {
        var headers = value.getHeaders();
        if (headers.size() == 0)
            return;
        gen.writeObjectFieldStart(HEADERS);
        for (var entry : headers.entrySet()) {
            gen.writeArrayFieldStart(entry.getKey());
            for (var val : entry.getValue())
                gen.writeString(val);
            gen.writeEndArray();
        }
        gen.writeEndObject();
    }

    private void writeCookies(ServerHttpRequest value, JsonGenerator gen) throws IOException {
        var cookies = value.getCookies();
        if (cookies.isEmpty())
            return;

        gen.writeArrayFieldStart(COOKIES);
        for (var entry : cookies.entrySet()) {
            for (var cookie : entry.getValue()) {
                gen.writeStartObject();
                gen.writeObjectField("name", cookie.getName());
                gen.writeObjectField("value", cookie.getValue());
                gen.writeEndObject();
            }
        }
        gen.writeEndArray();
    }

    private void writeParameters(ServerHttpRequest value, JsonGenerator gen) throws IOException {
        if (value.getQueryParams().isEmpty())
            return;

        gen.writeObjectFieldStart(PARAMETERS);
        for (Entry<String, List<String>> entry : value.getQueryParams().entrySet()) {
            gen.writeArrayFieldStart(entry.getKey());
            for (String val : entry.getValue()) {
                gen.writeString(val);
            }
            gen.writeEndArray();
        }
        gen.writeEndObject();
    }

}
