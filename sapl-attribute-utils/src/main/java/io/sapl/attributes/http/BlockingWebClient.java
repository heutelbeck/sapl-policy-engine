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
package io.sapl.attributes.http;

import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.Streams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Synchronous HTTP and WebSocket transport for SAPL Policy
 * Information Points. Built on {@link HttpClient} and
 * {@link WebSocket} from {@code java.net.http}; no Reactor or
 * Spring dependency.
 * <p>
 * For request-response media types ({@code application/json} and
 * arbitrary text), the request is issued once and the response is
 * emitted as a single value, then the stream completes; repetition is
 * the caller's concern (the attribute broker re-invokes per its
 * configured poll interval). For {@code text/event-stream}, the
 * connection is held open and SSE events are emitted as parsed values.
 * For WebSocket, incoming text frames are emitted as parsed values
 * until the session is closed.
 * <p>
 * The response body, each SSE event, and each WebSocket message are
 * capped at a configurable size; an oversized payload fails closed to
 * an error value rather than buffering without bound.
 * <p>
 * Read and connect timeouts protect against slow or unresponsive
 * servers.
 */
@Slf4j
public class BlockingWebClient {

    public static final String ACCEPT_MEDIATYPE           = "accept";
    public static final String BASE_URL                   = "baseUrl";
    public static final String BODY                       = "body";
    public static final String CONTENT_MEDIATYPE          = "contentType";
    public static final String HEADERS                    = "headers";
    public static final String MAX_RESPONSE_BYTES         = "maxResponseBytes";
    public static final String PATH                       = "path";
    public static final String URL_PARAMS                 = "urlParameters";
    static final long          DEFAULT_MAX_RESPONSE_BYTES = 1_048_576L;
    // Ceiling for an in-memory response buffer, well below Integer.MAX_VALUE so the
    // +1 sentinel read can never overflow or request a multi-gigabyte allocation.
    static final long MAX_ALLOWED_RESPONSE_BYTES = 268_435_456L;

    private static final String ERROR_BASE_URL_MUST_BE_TEXT                 = "baseUrl must be a text value.";
    private static final String ERROR_FIELD_MUST_BE_NUMBER_NULL             = "%s must be a number in HTTP requestSpecification, but was: null.";
    private static final String ERROR_FIELD_MUST_BE_NUMBER_WRONG_TYPE       = "%s must be a number in HTTP requestSpecification, but was: %s.";
    private static final String ERROR_HTTP_RESPONSE_STATUS                  = "HTTP %d";
    private static final String ERROR_MALFORMED_URI                         = "Malformed request URI: %s";
    private static final String ERROR_MAX_RESPONSE_BYTES_NOT_POSITIVE       = "maxResponseBytes must be a positive number, but was: %d.";
    private static final String ERROR_NO_BASE_URL_SPECIFIED_FOR_WEB_REQUEST = "No base URL specified for web request.";
    private static final String ERROR_RESPONSE_TOO_LARGE                    = "HTTP response exceeded the configured limit of %d bytes.";

    private static final String MEDIATYPE_APPLICATION_JSON  = "application/json";
    private static final String MEDIATYPE_TEXT_EVENT_STREAM = "text/event-stream";

    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10L);
    private static final Duration READ_TIMEOUT       = Duration.ofSeconds(30L);

    private static final JsonNodeFactory JSON             = JsonNodeFactory.instance;
    private static final JsonNode        APPLICATION_JSON = JSON.stringNode(MEDIATYPE_APPLICATION_JSON);

    private final JsonMapper mapper;
    private final HttpClient httpClient;
    private final long       maxResponseBytes;

    public BlockingWebClient(JsonMapper mapper, HttpClient httpClient) {
        this(mapper, httpClient, DEFAULT_MAX_RESPONSE_BYTES);
    }

    public BlockingWebClient(JsonMapper mapper, HttpClient httpClient, long maxResponseBytes) {
        this.mapper           = mapper;
        this.httpClient       = httpClient;
        this.maxResponseBytes = maxResponseBytes;
    }

    /**
     * Issues an HTTP request once and emits the response as a single
     * {@link Stream}{@code <Value>} that completes after the exchange;
     * the attribute broker re-invokes per its poll interval. The
     * response body is capped at the {@code maxResponseBytes} request
     * setting (default {@value #DEFAULT_MAX_RESPONSE_BYTES}); an
     * oversized body fails closed to an error value. Server-sent events
     * are emitted as they arrive on a held-open connection.
     */
    public Stream<Value> httpRequest(String httpMethod, ObjectValue requestSettings) {
        try {
            val baseUrl       = baseUrl(requestSettings);
            val path          = textOrDefault(requestSettings, PATH, "");
            val urlParameters = toStringMap(jsonOrDefault(requestSettings, URL_PARAMS, JSON.objectNode()));
            val headers       = jsonOrDefault(requestSettings, HEADERS, JSON.objectNode());
            val accept        = jsonOrDefault(requestSettings, ACCEPT_MEDIATYPE, APPLICATION_JSON).asString();
            val contentType   = jsonOrDefault(requestSettings, CONTENT_MEDIATYPE, APPLICATION_JSON).asString();
            val body          = jsonOrDefault(requestSettings, BODY, null);
            val maxBytes      = maxResponseBytesOrDefault(requestSettings, MAX_RESPONSE_BYTES, maxResponseBytes);

            val uri     = buildUri(baseUrl, path, urlParameters);
            val request = buildRequest(uri, httpMethod, headers, accept, contentType, body);

            if (MEDIATYPE_TEXT_EVENT_STREAM.equals(accept)) {
                return openServerSentEventStream(request, maxBytes);
            }
            // One request-response then complete. The broker re-invokes per its poll
            // interval, so the PIP never loops.
            val supplier  = jsonRequestSupplier(request, accept, maxBytes);
            val delivered = new AtomicBoolean(false);
            return Streams.fromBlockingSource(() -> delivered.compareAndSet(false, true) ? supplier.get() : null);
        } catch (RuntimeException e) {
            return Streams.error(messageOf(e));
        }
    }

    /**
     * Connects to a WebSocket endpoint and emits each incoming text
     * frame as a {@link Value}. Closing the returned stream sends a
     * normal-closure to the server.
     */
    public Stream<Value> consumeWebSocket(ObjectValue requestSettings) {
        try {
            val baseUrl  = baseUrl(requestSettings);
            val path     = textOrDefault(requestSettings, PATH, "");
            val headers  = jsonOrDefault(requestSettings, HEADERS, JSON.objectNode());
            val body     = jsonOrDefault(requestSettings, BODY, null);
            val maxBytes = maxResponseBytesOrDefault(requestSettings, MAX_RESPONSE_BYTES, maxResponseBytes);
            val uri      = createUri(baseUrl + path);
            return openWebSocket(uri, headers, body, maxBytes);
        } catch (RuntimeException e) {
            return Streams.error(messageOf(e));
        }
    }

    private Supplier<Value> jsonRequestSupplier(HttpRequest request, String accept, long maxBytes) {
        return () -> {
            try {
                val response = httpClient.send(request, BodyHandlers.ofInputStream());
                if (response.statusCode() >= 400) {
                    // Close the body to release the connection on the error branch.
                    try (val ignored = response.body()) {
                        return Value.error(ERROR_HTTP_RESPONSE_STATUS.formatted(response.statusCode()));
                    }
                }
                final byte[] bytes;
                try (val in = response.body()) {
                    // Read one byte past the limit to detect an oversized body without buffering
                    // more than the cap. Clamp before +1 so the sentinel can never overflow.
                    val limit = (int) Math.min(maxBytes, Integer.MAX_VALUE - 1L) + 1;
                    bytes = in.readNBytes(limit);
                }
                if (bytes.length > maxBytes) {
                    return Value.error(ERROR_RESPONSE_TOO_LARGE.formatted(maxBytes));
                }
                val body = new String(bytes, StandardCharsets.UTF_8);
                if (MEDIATYPE_APPLICATION_JSON.equals(accept)) {
                    return body.isBlank() ? Value.UNDEFINED : ValueJsonMarshaller.fromJsonNode(mapper.readTree(body));
                }
                return Value.of(body);
            } catch (JacksonException | IOException e) {
                return Value.error(messageOf(e));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Value.error(messageOf(e));
            }
        };
    }

    private Stream<Value> openServerSentEventStream(HttpRequest request, long maxBytes) {
        return Streams.fromCallback((emit, complete) -> {
            val stopped = new AtomicBoolean(false);
            val bodyRef = new AtomicReference<InputStream>();
            val thread  = Thread.startVirtualThread(() -> {
                            try {
                                val response = httpClient.send(request, BodyHandlers.ofInputStream());
                                // Open the body before the status check to release the connection on the error
                                // branch too.
                                try (val body = response.body()) {
                                    if (response.statusCode() >= 400) {
                                        emit.accept(Value
                                                .error(ERROR_HTTP_RESPONSE_STATUS.formatted(response.statusCode())));
                                        return;
                                    }
                                    bodyRef.set(body);
                                    pumpServerSentEvents(body, emit, stopped, maxBytes);
                                }
                            } catch (IOException | RuntimeException e) {
                                emit.accept(Value.error(messageOf(e)));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            } finally {
                                complete.run();
                            }
                        });
            return () -> {
                stopped.set(true);
                thread.interrupt();
                val body = bodyRef.get();
                if (body != null) {
                    try {
                        body.close();
                    } catch (IOException ignored) {
                        // Closing the aborted stream is best-effort; nothing to recover.
                    }
                }
            };
        });
    }

    /**
     * Reads the SSE body one line at a time, enforcing {@code maxBytes}
     * while scanning for a line terminator so a single newline-free
     * payload cannot exhaust the heap. Line terminators ({@code \n},
     * {@code \r}, {@code \r\n}) are recognized and stripped, matching the
     * previous line-stream behavior; a trailing un-terminated line is
     * still dispatched at end of stream. The cap is enforced in UTF-8
     * bytes across the in-flight accumulated event plus the line being
     * read; once it is crossed the read is aborted before any further
     * bytes are buffered.
     */
    private void pumpServerSentEvents(InputStream body, Consumer<Value> emit, AtomicBoolean stopped, long maxBytes)
            throws IOException {
        val reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8));
        val data   = new StringBuilder();
        val line   = new StringBuilder();
        while (!stopped.get()) {
            val terminated = readLine(reader, line, utf8ByteLength(data), maxBytes, emit, stopped);
            if (stopped.get()) {
                return;
            }
            if (!terminated && line.isEmpty()) {
                break;
            }
            consumeLine(line.toString(), data, emit, stopped, maxBytes);
            line.setLength(0);
        }
        if (!stopped.get()) {
            flushEvent(data, emit);
        }
    }

    /**
     * Reads a single line into {@code line}, treating {@code \n},
     * {@code \r} and {@code \r\n} as terminators and bounding the combined
     * size of the already-accumulated event ({@code carriedDataBytes})
     * plus the line being read against {@code maxBytes}. Returns
     * {@code true} if a terminator was consumed, {@code false} at end of
     * stream. Emits the too-large error and sets {@code stopped} the
     * moment the cap is crossed, before reading on.
     */
    private boolean readLine(BufferedReader reader, StringBuilder line, long carriedDataBytes, long maxBytes,
            Consumer<Value> emit, AtomicBoolean stopped) throws IOException {
        var lineBytes = 0L;
        int read;
        while ((read = reader.read()) != -1) {
            val c = (char) read;
            if (c == '\n') {
                return true;
            }
            if (c == '\r') {
                reader.mark(1);
                if (reader.read() != '\n') {
                    reader.reset();
                }
                return true;
            }
            line.append(c);
            lineBytes += charUtf8ByteLength(c);
            if (carriedDataBytes + lineBytes > maxBytes) {
                emit.accept(Value.error(ERROR_RESPONSE_TOO_LARGE.formatted(maxBytes)));
                stopped.set(true);
                return false;
            }
        }
        return false;
    }

    private void consumeLine(String line, StringBuilder data, Consumer<Value> emit, AtomicBoolean stopped,
            long maxBytes) {
        if (line.isEmpty()) {
            flushEvent(data, emit);
        } else if (line.startsWith("data:")) {
            appendDataLine(data, line);
            if (utf8ByteLength(data) > maxBytes) {
                emit.accept(Value.error(ERROR_RESPONSE_TOO_LARGE.formatted(maxBytes)));
                stopped.set(true);
            }
        }
    }

    private void flushEvent(StringBuilder data, Consumer<Value> emit) {
        if (!data.isEmpty()) {
            emit.accept(parseServerSentEventData(data.toString()));
            data.setLength(0);
        }
    }

    private static void appendDataLine(StringBuilder data, String line) {
        if (!data.isEmpty()) {
            data.append('\n');
        }
        data.append(line.substring(5).stripLeading());
    }

    private Value parseServerSentEventData(String data) {
        try {
            return ValueJsonMarshaller.fromJsonNode(mapper.readTree(data));
        } catch (JacksonException e) {
            return Value.error(messageOf(e));
        }
    }

    private Stream<Value> openWebSocket(URI uri, JsonNode headers, JsonNode body, long maxBytes) {
        return Streams.fromCallback((emit, complete) -> {
            val accumulator = new StringBuilder();
            val builder     = httpClient.newWebSocketBuilder();
            applyWebSocketHeaders(builder, headers);

            val listener = new WebSocketListener(accumulator, emit, complete, mapper, maxBytes);
            val wsRef    = new AtomicReference<WebSocket>();
            try {
                CompletableFuture<WebSocket> future = builder.buildAsync(uri, listener).toCompletableFuture();
                val                          ws     = future.get();
                wsRef.set(ws);
                if (body != null) {
                    ws.sendText(body.asString(), true);
                }
            } catch (ExecutionException e) {
                emit.accept(Value.error(messageOf(e.getCause() != null ? e.getCause() : e)));
                complete.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                complete.run();
            }

            return () -> {
                val ws = wsRef.get();
                if (ws != null && !ws.isOutputClosed()) {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "client-close");
                }
            };
        });
    }

    private void applyWebSocketHeaders(WebSocket.Builder builder, JsonNode headers) {
        headers.properties().forEach(field -> {
            val key   = field.getKey();
            val value = field.getValue();
            if (value.isString()) {
                builder.header(key, value.asString());
            } else if (value.isArray()) {
                value.iterator().forEachRemaining(e -> builder.header(key, e.asString()));
            }
        });
    }

    private static URI buildUri(String baseUrl, String path, Map<String, String> queryParams) {
        val uri = baseUrl + path;
        if (queryParams.isEmpty()) {
            return createUri(uri);
        }
        val sb = new StringBuilder(uri);
        sb.append(uri.contains("?") ? '&' : '?');
        var first = true;
        for (val entry : queryParams.entrySet()) {
            if (!first) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append('=');
            sb.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return createUri(sb.toString());
    }

    private static URI createUri(String uri) {
        try {
            return URI.create(uri);
        } catch (IllegalArgumentException e) {
            // The query string and any userinfo can carry secrets (e.g. the Traccar
            // token). Never let them reach the ErrorValue, report, or logs.
            throw new IllegalArgumentException(ERROR_MALFORMED_URI.formatted(redactSecrets(uri)));
        }
    }

    private static String redactSecrets(String uri) {
        // None of userinfo, path, query, or fragment is safe to surface, so the URI is
        // reduced structurally to scheme + host[:port] with everything else redacted.
        val schemeEnd = uri.indexOf("://");
        if (schemeEnd < 0) {
            val colon = uri.indexOf(':');
            return colon >= 0 ? uri.substring(0, colon + 1) + "<redacted>" : "<redacted>";
        }
        val authorityStart = schemeEnd + 3;
        var authorityEnd   = authorityStart;
        while (authorityEnd < uri.length()) {
            val c = uri.charAt(authorityEnd);
            if (c == '/' || c == '?' || c == '#') {
                break;
            }
            authorityEnd++;
        }
        val authority = uri.substring(authorityStart, authorityEnd);
        val at        = authority.lastIndexOf('@');
        val hostPort  = at >= 0 ? authority.substring(at + 1) : authority;
        val prefix    = uri.substring(0, authorityStart) + (at >= 0 ? "<redacted>@" : "") + hostPort;
        return authorityEnd < uri.length() ? prefix + "/<redacted>" : prefix;
    }

    private HttpRequest buildRequest(URI uri, String method, JsonNode headers, String accept, String contentType,
            JsonNode body) {
        var builder = HttpRequest.newBuilder().uri(uri).timeout(READ_TIMEOUT).header("Accept", accept);
        builder = applyHttpHeaders(builder, headers);

        val upper = method.toUpperCase(Locale.ROOT);
        return switch (upper) {
        case "GET"    -> builder.GET().build();
        case "DELETE" -> builder.DELETE().build();
        case "POST"   -> withBody(builder, contentType, body).POST(bodyPublisher(body)).build();
        case "PUT"    -> withBody(builder, contentType, body).PUT(bodyPublisher(body)).build();
        case "PATCH"  -> withBody(builder, contentType, body).method("PATCH", bodyPublisher(body)).build();
        default       -> builder.method(upper, bodyPublisher(body)).build();
        };
    }

    private HttpRequest.Builder withBody(HttpRequest.Builder builder, String contentType, JsonNode body) {
        if (body != null) {
            return builder.header("Content-Type", contentType);
        }
        return builder;
    }

    private HttpRequest.BodyPublisher bodyPublisher(JsonNode body) {
        if (body == null) {
            return BodyPublishers.noBody();
        }
        if (body.isString()) {
            return BodyPublishers.ofString(body.asString());
        }
        return BodyPublishers.ofString(body.toString());
    }

    private HttpRequest.Builder applyHttpHeaders(HttpRequest.Builder builder, JsonNode headers) {
        for (val field : headers.properties()) {
            val key   = field.getKey();
            val value = field.getValue();
            if (value.isArray()) {
                for (val element : value) {
                    builder.header(key, element.asString());
                }
            } else if (value.isString()) {
                builder.header(key, value.asString());
            } else {
                builder.header(key, value.toString());
            }
        }
        return builder;
    }

    private String baseUrl(ObjectValue requestSettings) {
        if (!requestSettings.containsKey(BASE_URL)) {
            throw new IllegalArgumentException(ERROR_NO_BASE_URL_SPECIFIED_FOR_WEB_REQUEST);
        }
        val value = requestSettings.get(BASE_URL);
        if (value instanceof TextValue(var s)) {
            return s;
        }
        throw new IllegalArgumentException(ERROR_BASE_URL_MUST_BE_TEXT);
    }

    private String textOrDefault(ObjectValue requestSettings, String fieldName, String defaultValue) {
        if (!requestSettings.containsKey(fieldName)) {
            return defaultValue;
        }
        val value = requestSettings.get(fieldName);
        if (value instanceof TextValue(var s)) {
            return s;
        }
        return defaultValue;
    }

    private JsonNode jsonOrDefault(ObjectValue requestSettings, String fieldName, JsonNode defaultValue) {
        if (!requestSettings.containsKey(fieldName)) {
            return defaultValue;
        }
        return ValueJsonMarshaller.toJsonNode(requestSettings.get(fieldName));
    }

    private long maxResponseBytesOrDefault(ObjectValue requestSettings, String fieldName, long defaultValue) {
        if (!requestSettings.containsKey(fieldName)) {
            return defaultValue;
        }
        val value = requestSettings.get(fieldName);
        if (value == null) {
            throw new IllegalArgumentException(ERROR_FIELD_MUST_BE_NUMBER_NULL.formatted(fieldName));
        }
        if (value instanceof NumberValue(var n)) {
            val requested = n.longValue();
            if (requested <= 0L) {
                throw new IllegalArgumentException(ERROR_MAX_RESPONSE_BYTES_NOT_POSITIVE.formatted(requested));
            }
            return Math.min(requested, MAX_ALLOWED_RESPONSE_BYTES);
        }
        throw new IllegalArgumentException(
                ERROR_FIELD_MUST_BE_NUMBER_WRONG_TYPE.formatted(fieldName, value.getClass().getSimpleName()));
    }

    private Map<String, String> toStringMap(JsonNode node) {
        return mapper.convertValue(node, new TypeReference<Map<String, String>>() {});
    }

    private static String messageOf(Throwable t) {
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    private static int utf8ByteLength(CharSequence text) {
        return text.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * UTF-8 byte length contributed by a single UTF-16 code unit. A
     * surrogate counts as two bytes so that a high plus low surrogate
     * pair sums to the four bytes of the encoded code point.
     */
    private static int charUtf8ByteLength(char c) {
        if (c < 0x80) {
            return 1;
        }
        if (c < 0x800 || Character.isSurrogate(c)) {
            return 2;
        }
        return 3;
    }

    /**
     * Static factory intended for production wiring outside of tests.
     */
    public static BlockingWebClient withDefaults(JsonMapper mapper) {
        val client = HttpClient.newBuilder().connectTimeout(CONNECTION_TIMEOUT).build();
        return new BlockingWebClient(mapper, client);
    }

    /**
     * Listener that accumulates partial text frames into a single
     * payload and emits a parsed {@link Value} when the frame is
     * complete.
     */
    @RequiredArgsConstructor
    private static final class WebSocketListener implements WebSocket.Listener {
        private final StringBuilder   accumulator;
        private final Consumer<Value> emit;
        private final Runnable        complete;
        private final JsonMapper      mapper;
        private final long            maxBytes;

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1L);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            accumulator.append(data);
            if (utf8ByteLength(accumulator) > maxBytes) {
                emit.accept(Value.error(ERROR_RESPONSE_TOO_LARGE.formatted(maxBytes)));
                accumulator.setLength(0);
                complete.run();
                webSocket.abort();
                return null;
            }
            if (last) {
                emit.accept(parsePayload(accumulator.toString()));
                accumulator.setLength(0);
            }
            webSocket.request(1L);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            complete.run();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            emit.accept(Value.error(messageOf(error)));
            complete.run();
        }

        private Value parsePayload(String payload) {
            try {
                return ValueJsonMarshaller.fromJsonNode(mapper.readTree(payload));
            } catch (JacksonException e) {
                return Value.error(messageOf(e));
            }
        }
    }
}
