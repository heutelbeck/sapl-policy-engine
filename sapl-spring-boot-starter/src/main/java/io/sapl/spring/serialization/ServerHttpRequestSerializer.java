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

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;

import lombok.val;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Jackson serializer for {@link ServerHttpRequest} that exposes the
 * unified policy-facing HTTP shape. Same field names and grouping as
 * {@link HttpServletRequestSerializer}, so a SAPL policy reads the same
 * fields regardless of whether the deployed PEP is on the reactive stack
 * or the servlet stack. Header keys are lowercased to match HTTP/2 wire
 * format and Spring's case-insensitive {@code HttpHeaders} contract.
 */
public class ServerHttpRequestSerializer extends StdSerializer<ServerHttpRequest> {

    public ServerHttpRequestSerializer() {
        super(ServerHttpRequest.class);
    }

    @Override
    public void serialize(ServerHttpRequest value, JsonGenerator gen, SerializationContext serializers) {
        val uri    = value.getURI();
        val scheme = uri.getScheme();
        gen.writeStartObject();
        gen.writeStringProperty(HttpServletRequestSerializer.METHOD, value.getMethod().name());
        gen.writeStringProperty(HttpServletRequestSerializer.URL, uri.toString());
        gen.writeStringProperty(HttpServletRequestSerializer.SCHEME, scheme);
        gen.writeStringProperty(HttpServletRequestSerializer.HOST, uri.getHost());
        gen.writeNumberProperty(HttpServletRequestSerializer.PORT, uri.getPort());
        gen.writeStringProperty(HttpServletRequestSerializer.PATH, value.getPath().value());
        if (uri.getRawQuery() != null) {
            gen.writeStringProperty(HttpServletRequestSerializer.QUERY, uri.getRawQuery());
            writeQueryParameters(value, gen);
        }
        gen.writeStringProperty(HttpServletRequestSerializer.CONTEXT_PATH, value.getPath().contextPath().value());
        gen.writeStringProperty(HttpServletRequestSerializer.APPLICATION_PATH,
                value.getPath().pathWithinApplication().value());
        gen.writeBooleanProperty(HttpServletRequestSerializer.IS_SECURE, "https".equalsIgnoreCase(scheme));
        writeClient(value.getRemoteAddress(), gen);
        writeServer(value.getLocalAddress(), gen);
        writeHeaders(value, gen);
        writeCookies(value, gen);
        writeForwarded(value, gen);
        writeBodyMetadata(value, gen);
        gen.writeEndObject();
    }

    private static void writeQueryParameters(ServerHttpRequest request, JsonGenerator gen) {
        val params = request.getQueryParams();
        if (params.isEmpty()) {
            return;
        }
        gen.writeName(HttpServletRequestSerializer.QUERY_PARAMETERS);
        gen.writeStartObject();
        for (val entry : params.entrySet()) {
            gen.writeName(entry.getKey());
            gen.writeStartArray();
            for (val parameterValue : entry.getValue()) {
                gen.writeString(parameterValue);
            }
            gen.writeEndArray();
        }
        gen.writeEndObject();
    }

    private static void writeClient(@Nullable InetSocketAddress remoteAddress, JsonGenerator gen) {
        gen.writeName(HttpServletRequestSerializer.CLIENT);
        gen.writeStartObject();
        if (remoteAddress != null) {
            gen.writeStringProperty(HttpServletRequestSerializer.ADDRESS,
                    remoteAddress.getAddress() != null ? remoteAddress.getAddress().getHostAddress()
                            : remoteAddress.getHostString());
            gen.writeStringProperty(HttpServletRequestSerializer.HOST, remoteAddress.getHostString());
            gen.writeNumberProperty(HttpServletRequestSerializer.PORT, remoteAddress.getPort());
        }
        gen.writeEndObject();
    }

    private static void writeServer(@Nullable InetSocketAddress localAddress, JsonGenerator gen) {
        gen.writeName(HttpServletRequestSerializer.SERVER);
        gen.writeStartObject();
        if (localAddress != null) {
            gen.writeStringProperty(HttpServletRequestSerializer.ADDRESS,
                    localAddress.getAddress() != null ? localAddress.getAddress().getHostAddress()
                            : localAddress.getHostString());
            gen.writeStringProperty(HttpServletRequestSerializer.HOST, localAddress.getHostString());
            gen.writeNumberProperty(HttpServletRequestSerializer.PORT, localAddress.getPort());
        }
        gen.writeEndObject();
    }

    private static void writeHeaders(ServerHttpRequest request, JsonGenerator gen) {
        val headers = request.getHeaders();
        if (headers.isEmpty()) {
            return;
        }
        gen.writeName(HttpServletRequestSerializer.HEADERS);
        gen.writeStartObject();
        for (val entry : headers.headerSet()) {
            gen.writeName(entry.getKey().toLowerCase(Locale.ROOT));
            gen.writeStartArray();
            for (val headerValue : entry.getValue()) {
                gen.writeString(headerValue);
            }
            gen.writeEndArray();
        }
        gen.writeEndObject();
    }

    private static void writeCookies(ServerHttpRequest request, JsonGenerator gen) {
        val cookies = request.getCookies();
        if (cookies.isEmpty()) {
            return;
        }
        gen.writeName(HttpServletRequestSerializer.COOKIES);
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

    private static void writeForwarded(ServerHttpRequest request, JsonGenerator gen) {
        val parsed = ForwardedHeaders.parse(name -> request.getHeaders().getOrDefault(name, List.of()));
        if (parsed.isEmpty()) {
            return;
        }
        gen.writeName(HttpServletRequestSerializer.FORWARDED);
        gen.writeStartObject();
        if (!parsed.forChain().isEmpty()) {
            gen.writeName(HttpServletRequestSerializer.FORWARDED_FOR);
            gen.writeStartArray();
            for (val client : parsed.forChain()) {
                gen.writeString(client);
            }
            gen.writeEndArray();
        }
        if (parsed.host() != null) {
            gen.writeStringProperty(HttpServletRequestSerializer.FORWARDED_HOST, parsed.host());
        }
        if (parsed.proto() != null) {
            gen.writeStringProperty(HttpServletRequestSerializer.FORWARDED_PROTO, parsed.proto());
        }
        if (parsed.port() != null) {
            gen.writeNumberProperty(HttpServletRequestSerializer.FORWARDED_PORT, parsed.port());
        }
        gen.writeEndObject();
    }

    private static void writeBodyMetadata(ServerHttpRequest request, JsonGenerator gen) {
        val headers     = request.getHeaders();
        val contentType = headers.getContentType();
        if (contentType != null) {
            gen.writeStringProperty(HttpServletRequestSerializer.CONTENT_TYPE, contentType.toString());
            val charset = charsetOf(contentType);
            if (charset != null) {
                gen.writeStringProperty(HttpServletRequestSerializer.CHARACTER_ENCODING, charset.name());
            }
        }
        val contentLength = headers.getContentLength();
        if (contentLength >= 0) {
            gen.writeNumberProperty(HttpServletRequestSerializer.CONTENT_LENGTH, contentLength);
        }
    }

    private static @Nullable Charset charsetOf(MediaType contentType) {
        try {
            return contentType.getCharset();
        } catch (UnsupportedOperationException uoe) {
            return null;
        }
    }

}
