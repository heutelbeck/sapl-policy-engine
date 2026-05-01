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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import lombok.val;

/**
 * Parsed view of the standard reverse-proxy forwarding headers, exposed to
 * SAPL policies as {@code action.http.forwarded} and
 * {@code resource.http.forwarded}. Reads RFC 7239 {@code Forwarded} first;
 * falls back to the legacy {@code X-Forwarded-*} family when the standard
 * header is absent. Pure parsing: trust judgements (e.g. only honour
 * forwarded headers when the direct peer is in a trusted proxy range)
 * belong in the policy, not here.
 *
 * @param forChain the {@code for} chain left-to-right; element 0 is the
 * original client when the policy trusts the chain. Empty when no
 * {@code for} parameter is present.
 * @param host the original {@code Host} the user typed, or {@code null}
 * when no forwarded host is signalled.
 * @param proto the original scheme ({@code "http"} or {@code "https"}),
 * or {@code null} when no forwarded proto is signalled.
 * @param port the explicit forwarded port, or {@code null} when none is
 * signalled.
 */
public record ForwardedHeaders(
        List<String> forChain,
        @Nullable String host,
        @Nullable String proto,
        @Nullable Integer port) {

    private static final String FORWARDED = "forwarded";
    private static final String X_FORWARDED_FOR = "x-forwarded-for";
    private static final String X_FORWARDED_HOST = "x-forwarded-host";
    private static final String X_FORWARDED_PORT = "x-forwarded-port";
    private static final String X_FORWARDED_PROTO = "x-forwarded-proto";
    private static final String PARAM_FOR = "for";
    private static final String PARAM_HOST = "host";
    private static final String PARAM_PORT = "port";
    private static final String PARAM_PROTO = "proto";

    /** Empty parsed view used when no relevant headers were present. */
    public static final ForwardedHeaders EMPTY = new ForwardedHeaders(List.of(), null, null, null);

    /**
     * Returns true when the parsed view has nothing to expose.
     */
    public boolean isEmpty() {
        return forChain.isEmpty() && host == null && proto == null && port == null;
    }

    /**
     * Parses the headers in {@code source} into a {@link ForwardedHeaders}
     * view. The {@code source} function returns the values for a given
     * lowercase header name (each header may be multi-valued). Pure
     * parsing; never throws on malformed input - malformed segments are
     * dropped silently.
     */
    public static ForwardedHeaders parse(Function<String, List<String>> source) {
        val rfc7239 = source.apply(FORWARDED);
        if (rfc7239 != null && !rfc7239.isEmpty()) {
            val parsed = parseRfc7239(rfc7239);
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        return parseLegacy(source);
    }

    private static ForwardedHeaders parseRfc7239(List<String> headerValues) {
        val accumulator = new Rfc7239Accumulator();
        for (val headerValue : headerValues) {
            for (val element : headerValue.split(",")) {
                for (val pair : element.split(";")) {
                    processRfc7239Pair(pair, accumulator);
                }
            }
        }
        return accumulator.build();
    }

    private static void processRfc7239Pair(String pair, Rfc7239Accumulator accumulator) {
        val eq = pair.indexOf('=');
        if (eq <= 0) {
            return;
        }
        val name  = pair.substring(0, eq).trim().toLowerCase(Locale.ROOT);
        val value = stripQuotes(pair.substring(eq + 1).trim());
        if (value.isEmpty()) {
            return;
        }
        switch (name) {
        case PARAM_FOR   -> accumulator.addForwardedFor(stripPort(value));
        case PARAM_HOST  -> accumulator.setHostIfAbsent(value);
        case PARAM_PROTO -> accumulator.setProtoIfAbsent(value.toLowerCase(Locale.ROOT));
        default          -> { /* ignore unknown parameter */ }
        }
    }

    private static final class Rfc7239Accumulator {
        private final List<String> forChain = new ArrayList<>();
        private @Nullable String   host;
        private @Nullable String   proto;
        private @Nullable Integer  port;

        void addForwardedFor(String forValue) {
            forChain.add(forValue);
        }

        void setHostIfAbsent(String hostValue) {
            if (host == null) {
                host = hostValue;
            }
        }

        void setProtoIfAbsent(String protoValue) {
            if (proto == null) {
                proto = protoValue;
            }
        }

        ForwardedHeaders build() {
            return new ForwardedHeaders(List.copyOf(forChain), host, proto, port);
        }
    }

    private static ForwardedHeaders parseLegacy(Function<String, List<String>> source) {
        val forChain = parseChain(source.apply(X_FORWARDED_FOR));
        val host     = firstNonBlank(source.apply(X_FORWARDED_HOST));
        val proto    = lowercaseOrNull(firstNonBlank(source.apply(X_FORWARDED_PROTO)));
        val port     = parsePort(firstNonBlank(source.apply(X_FORWARDED_PORT)));
        return new ForwardedHeaders(forChain, host, proto, port);
    }

    private static List<String> parseChain(@Nullable List<String> headerValues) {
        if (headerValues == null || headerValues.isEmpty()) {
            return List.of();
        }
        val parts = new ArrayList<String>();
        for (val headerValue : headerValues) {
            for (val element : headerValue.split(",")) {
                val trimmed = element.trim();
                if (!trimmed.isEmpty()) {
                    parts.add(trimmed);
                }
            }
        }
        return List.copyOf(parts);
    }

    private static @Nullable String firstNonBlank(@Nullable List<String> headerValues) {
        if (headerValues == null) {
            return null;
        }
        for (val headerValue : headerValues) {
            if (headerValue != null && !headerValue.isBlank()) {
                return headerValue.trim();
            }
        }
        return null;
    }

    private static @Nullable String lowercaseOrNull(@Nullable String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static @Nullable Integer parsePort(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String stripPort(String forValue) {
        // RFC 7239 allows for=ipv4:port or for="[ipv6]:port" - keep only the
        // address part for the policy-facing chain.
        if (forValue.startsWith("[")) {
            val close = forValue.indexOf(']');
            return close < 0 ? forValue : forValue.substring(0, close + 1);
        }
        val lastColon = forValue.lastIndexOf(':');
        if (lastColon < 0 || forValue.indexOf(':') != lastColon) {
            // no colon, or multiple colons (raw IPv6) - return as-is
            return forValue;
        }
        return forValue.substring(0, lastColon);
    }
}
