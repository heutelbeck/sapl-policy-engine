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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
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

    private static final String ERROR_BASE_URL_MUST_BE_TEXT                 = "baseUrl must be a text value.";
    private static final String ERROR_FIELD_MUST_BE_NUMBER_NULL             = "%s must be a number in HTTP requestSpecification, but was: null.";
    private static final String ERROR_FIELD_MUST_BE_NUMBER_WRONG_TYPE       = "%s must be a number in HTTP requestSpecification, but was: %s.";
    private static final String ERROR_HTTP_RESPONSE_STATUS                  = "HTTP %d";
    private static final String ERROR_MALFORMED_URI                         = "Malformed request URI: %s";
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
            val maxBytes      = longOrDefault(requestSettings, MAX_RESPONSE_BYTES, maxResponseBytes);

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
            val maxBytes = longOrDefault(requestSettings, MAX_RESPONSE_BYTES, maxResponseBytes);
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
                    return Value.error(ERROR_HTTP_RESPONSE_STATUS.formatted(response.statusCode()));
                }
                final byte[] bytes;
                try (val in = response.body()) {
                    // Read one byte past the limit to detect an oversized body without buffering
                    // more than the cap.
                    bytes = in.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
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
            val bodyRef = new AtomicReference<java.util.stream.Stream<String>>();
            val thread  = Thread.startVirtualThread(() -> {
                            try {
                                val response = httpClient.send(request, BodyHandlers.ofLines());
                                if (response.statusCode() >= 400) {
                                    emit.accept(
                                            Value.error(ERROR_HTTP_RESPONSE_STATUS.formatted(response.statusCode())));
                                    return;
                                }
                                try (val lines = response.body()) {
                                    bodyRef.set(lines);
                                    pumpServerSentEvents(lines.iterator(), emit, stopped, maxBytes);
                                }
                            } catch (IOException e) {
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
                    body.close();
                }
            };
        });
    }

    private void pumpServerSentEvents(Iterator<String> iterator, Consumer<Value> emit, AtomicBoolean stopped,
            long maxBytes) {
        val data = new StringBuilder();
        while (!stopped.get() && iterator.hasNext()) {
            val line = iterator.next();
            if (line.isEmpty()) {
                flushEvent(data, emit);
            } else if (line.startsWith("data:")) {
                appendDataLine(data, line);
                if (data.length() > maxBytes) {
                    emit.accept(Value.error(ERROR_RESPONSE_TOO_LARGE.formatted(maxBytes)));
                    stopped.set(true);
                }
            }
        }
        if (!stopped.get()) {
            flushEvent(data, emit);
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
        var redacted = uri;
        val query    = redacted.indexOf('?');
        if (query >= 0) {
            redacted = redacted.substring(0, query) + "?<redacted>";
        }
        return redacted.replaceAll("//[^/@]*@", "//<redacted>@");
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

    private long longOrDefault(ObjectValue requestSettings, String fieldName, long defaultValue) {
        if (!requestSettings.containsKey(fieldName)) {
            return defaultValue;
        }
        val value = requestSettings.get(fieldName);
        if (value == null) {
            throw new IllegalArgumentException(ERROR_FIELD_MUST_BE_NUMBER_NULL.formatted(fieldName));
        }
        if (value instanceof NumberValue(var n)) {
            return n.longValue();
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
            if (accumulator.length() > maxBytes) {
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
