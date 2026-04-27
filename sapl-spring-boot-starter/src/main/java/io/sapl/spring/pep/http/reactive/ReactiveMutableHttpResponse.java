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
package io.sapl.spring.pep.http.reactive;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;

import io.sapl.spring.pep.http.MutableHttpResponse;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive-backed {@link MutableHttpResponse}. Buffers status, headers, and
 * body so that constraint handlers attached to the response or denial
 * signals can read what the controller produced and replace any of it
 * before the response is committed to the client. Call {@link #commit()}
 * once handlers have run to flush the buffer to the underlying response.
 * <p>
 * The class extends {@link ServerHttpResponseDecorator}, so it can be
 * passed to {@link org.springframework.web.server.WebFilterChain} as the
 * exchange's response. {@code writeWith} captures every emitted
 * {@link DataBuffer} into an in-memory byte buffer instead of forwarding
 * to the underlying response.
 * <p>
 * Performance: every controller byte is captured in memory and re-emitted
 * on commit. This is fine for typical HTTP responses but is unsuitable for
 * unbounded streaming bodies. Callers that do not need response-level
 * mutation should not wrap at all; the SAPL HTTP PEP web filter installs
 * this wrapper only when the active enforcement plan schedules at least
 * one handler at the response signal.
 */
public final class ReactiveMutableHttpResponse extends ServerHttpResponseDecorator implements MutableHttpResponse {

    private final HttpHeaders bufferedHeaders = new HttpHeaders();

    private @Nullable HttpStatusCode bufferedStatus;
    private byte[]                   capturedBody = new byte[0];
    private boolean                  modified     = false;
    private boolean                  bodyCaptured = false;

    public ReactiveMutableHttpResponse(ServerHttpResponse delegate) {
        super(delegate);
    }

    @Override
    public boolean setStatusCode(@Nullable HttpStatusCode status) {
        bufferedStatus = status;
        modified       = true;
        return true;
    }

    @Override
    public @NonNull HttpStatusCode getStatusCode() {
        if (bufferedStatus != null) {
            return bufferedStatus;
        }
        val delegateStatus = super.getStatusCode();
        return delegateStatus == null ? HttpStatus.OK : delegateStatus;
    }

    @Override
    public @NonNull HttpHeaders getHeaders() {
        return bufferedHeaders;
    }

    @Override
    public void setHeader(String name, String value) {
        bufferedHeaders.set(name, value);
        modified = true;
    }

    @Override
    public void addHeader(String name, String value) {
        bufferedHeaders.add(name, value);
        modified = true;
    }

    @Override
    public void removeHeader(String name) {
        bufferedHeaders.remove(name);
        modified = true;
    }

    @Override
    public HttpHeaders headers() {
        return bufferedHeaders;
    }

    @Override
    public String getBody() {
        return new String(capturedBody, charset());
    }

    @Override
    public void setBody(String body) {
        capturedBody = body.getBytes(charset());
        bodyCaptured = true;
        modified     = true;
    }

    @Override
    public void writeBody(String contentType, String body) {
        bufferedHeaders.setContentType(MediaType.parseMediaType(contentType));
        setBody(body);
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public @NonNull Mono<Void> writeWith(@NonNull Publisher<? extends DataBuffer> body) {
        return Flux.from(body).collectList().doOnNext(buffers -> {
            val total = buffers.stream().mapToInt(DataBuffer::readableByteCount).sum();
            val all   = new byte[total];
            var i     = 0;
            for (val buf : buffers) {
                val len = buf.readableByteCount();
                buf.read(all, i, len);
                DataBufferUtils.release(buf);
                i += len;
            }
            capturedBody = all;
            bodyCaptured = true;
        }).then();
    }

    @Override
    public @NonNull Mono<Void> writeAndFlushWith(@NonNull Publisher<? extends Publisher<? extends DataBuffer>> body) {
        return writeWith(Flux.from(body).flatMap(Flux::from));
    }

    @Override
    public @NonNull Mono<Void> setComplete() {
        // Captured by the wrapper; the surrounding PEP filter calls commit() to
        // flush. Forwarding to the delegate here would commit the underlying
        // response prematurely and cause subsequent writes from commit() to
        // fail with UnsupportedOperationException.
        bodyCaptured = true;
        return Mono.empty();
    }

    /**
     * Flushes the buffered status, headers, and body to the underlying
     * response. Returns a {@link Mono} that completes when the underlying
     * response has accepted the bytes. Idempotent on repeated body writes
     * is left to the underlying response semantics.
     */
    public Mono<Void> commit() {
        return Mono.defer(() -> {
            val delegate = getDelegate();
            if (bufferedStatus != null) {
                delegate.setStatusCode(bufferedStatus);
            }
            for (val entry : bufferedHeaders.headerSet()) {
                delegate.getHeaders().put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            if (capturedBody.length == 0) {
                return delegate.setComplete();
            }
            val buffer = delegate.bufferFactory().wrap(capturedBody);
            return delegate.writeWith(Mono.just(buffer));
        });
    }

    private Charset charset() {
        val type = bufferedHeaders.getContentType();
        if (type != null && type.getCharset() != null) {
            return type.getCharset();
        }
        return StandardCharsets.UTF_8;
    }
}
