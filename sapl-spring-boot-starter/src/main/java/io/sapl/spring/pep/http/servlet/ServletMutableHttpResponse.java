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
package io.sapl.spring.pep.http.servlet;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;

import io.sapl.spring.pep.http.MutableHttpResponse;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import lombok.val;

/**
 * Servlet-backed {@link MutableHttpResponse}. Buffers status, headers, and
 * body so that constraint handlers attached to the response or denial
 * signals can read what the controller produced and replace any of it
 * before the response is committed to the client. Call {@link #commit()}
 * once handlers have run to flush the buffer to the underlying servlet
 * response.
 * <p>
 * Reads through the standard servlet response API ({@code getStatus},
 * {@code getHeader}, etc.) return the buffered state, not the underlying
 * delegate. Writes through either the servlet API or the
 * {@link MutableHttpResponse} API mutate the buffer; nothing reaches the
 * client until {@link #commit()} runs. {@link #isModified()} ticks for
 * every mutation made through the typed API or through
 * {@link #getOutputStream()} or {@link #getWriter()}; bulk header changes
 * applied through the {@link #headers()} view share the same buffer but do
 * not tick the flag.
 * <p>
 * Performance: every controller byte is captured in memory and re-emitted
 * on commit. This is fine for typical HTTP responses but is unsuitable for
 * unbounded streaming bodies. Callers that do not need response-level
 * mutation should not wrap at all; the SAPL HTTP PEP filter installs this
 * wrapper only when the active enforcement plan schedules at least one
 * handler at the response signal.
 */
public final class ServletMutableHttpResponse extends HttpServletResponseWrapper implements MutableHttpResponse {

    private final HttpHeaders           headers    = new HttpHeaders();
    private final ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream();

    private int                           statusCode = HttpServletResponse.SC_OK;
    private boolean                       modified   = false;
    private boolean                       committed  = false;
    private @Nullable ServletOutputStream cachedOutputStream;
    private @Nullable PrintWriter         cachedWriter;

    public ServletMutableHttpResponse(HttpServletResponse delegate) {
        super(delegate);
    }

    @Override
    public boolean setStatusCode(HttpStatusCode status) {
        setStatus(status.value());
        return true;
    }

    @Override
    public HttpStatusCode getStatusCode() {
        return HttpStatusCode.valueOf(statusCode);
    }

    @Override
    public void setHeader(String name, String value) {
        headers.set(name, value);
        modified = true;
    }

    @Override
    public void addHeader(String name, String value) {
        headers.add(name, value);
        modified = true;
    }

    @Override
    public void removeHeader(String name) {
        headers.remove(name);
        modified = true;
    }

    @Override
    public HttpHeaders headers() {
        return headers;
    }

    @Override
    public String getBody() {
        flushPending();
        return bodyBuffer.toString(charset());
    }

    @Override
    public void setBody(String body) {
        flushPending();
        bodyBuffer.reset();
        bodyBuffer.writeBytes(body.getBytes(charset()));
        modified = true;
    }

    @Override
    public void writeBody(String contentType, String body) {
        headers.setContentType(MediaType.parseMediaType(contentType));
        setBody(body);
    }

    @Override
    public boolean isModified() {
        return modified;
    }

    @Override
    public void setStatus(int sc) {
        statusCode = sc;
        modified   = true;
    }

    @Override
    public int getStatus() {
        return statusCode;
    }

    @Override
    public boolean containsHeader(String name) {
        return headers.containsHeader(name);
    }

    @Override
    public String getHeader(String name) {
        return headers.getFirst(name);
    }

    @Override
    public Collection<String> getHeaders(String name) {
        val values = headers.get(name);
        return values == null ? List.of() : List.copyOf(values);
    }

    @Override
    public Collection<String> getHeaderNames() {
        return List.copyOf(headers.headerNames());
    }

    @Override
    public void setIntHeader(String name, int value) {
        setHeader(name, Integer.toString(value));
    }

    @Override
    public void addIntHeader(String name, int value) {
        addHeader(name, Integer.toString(value));
    }

    @Override
    public void setDateHeader(String name, long date) {
        setHeader(name, Long.toString(date));
    }

    @Override
    public void addDateHeader(String name, long date) {
        addHeader(name, Long.toString(date));
    }

    @Override
    public void setContentType(String type) {
        if (type == null) {
            headers.remove(HttpHeaders.CONTENT_TYPE);
        } else {
            headers.setContentType(MediaType.parseMediaType(type));
        }
        modified = true;
    }

    @Override
    public String getContentType() {
        val type = headers.getContentType();
        return type == null ? null : type.toString();
    }

    @Override
    public void setContentLength(int length) {
        setHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(length));
    }

    @Override
    public void setContentLengthLong(long length) {
        setHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(length));
    }

    @Override
    public ServletOutputStream getOutputStream() {
        if (cachedOutputStream == null) {
            cachedOutputStream = new BufferingServletOutputStream(bodyBuffer, this::markModified);
        }
        return cachedOutputStream;
    }

    @Override
    public PrintWriter getWriter() {
        if (cachedWriter == null) {
            cachedWriter = new PrintWriter(new OutputStreamWriter(getOutputStream(), charset()), false);
        }
        return cachedWriter;
    }

    @Override
    public void sendError(int sc) {
        sendError(sc, "");
    }

    @Override
    public void sendError(int sc, String msg) {
        statusCode = sc;
        bodyBuffer.reset();
        bodyBuffer.writeBytes(msg.getBytes(charset()));
        modified = true;
    }

    @Override
    public void sendRedirect(String location) {
        statusCode = HttpServletResponse.SC_FOUND;
        headers.setLocation(URI.create(location));
        bodyBuffer.reset();
        modified = true;
    }

    @Override
    public boolean isCommitted() {
        return committed;
    }

    @Override
    public void resetBuffer() {
        bodyBuffer.reset();
    }

    @Override
    public void reset() {
        bodyBuffer.reset();
        headers.clear();
        statusCode = HttpServletResponse.SC_OK;
    }

    /**
     * Flushes the buffered status, headers, and body to the underlying
     * servlet response. Idempotent: subsequent calls are no-ops. Closes the
     * cached writer first so any pending bytes land in the buffer before
     * the flush.
     */
    public void commit() throws IOException {
        if (committed) {
            return;
        }
        flushPending();
        val underlying = (HttpServletResponse) getResponse();
        underlying.setStatus(statusCode);
        for (val entry : headers.headerSet()) {
            for (val value : entry.getValue()) {
                underlying.addHeader(entry.getKey(), value);
            }
        }
        val body = bodyBuffer.toByteArray();
        if (body.length > 0) {
            underlying.getOutputStream().write(body);
        }
        underlying.getOutputStream().flush();
        committed = true;
    }

    private Charset charset() {
        val name = getCharacterEncoding();
        return name == null ? Charset.defaultCharset() : Charset.forName(name);
    }

    private void flushPending() {
        if (cachedWriter != null) {
            cachedWriter.flush();
        }
    }

    private void markModified() {
        modified = true;
    }

    private static final class BufferingServletOutputStream extends ServletOutputStream {

        private final ByteArrayOutputStream buffer;
        private final Runnable              onWrite;

        BufferingServletOutputStream(ByteArrayOutputStream buffer, Runnable onWrite) {
            this.buffer  = buffer;
            this.onWrite = onWrite;
        }

        @Override
        public void write(int b) {
            buffer.write(b);
            onWrite.run();
        }

        @Override
        public void write(byte[] b, int off, int len) {
            buffer.write(b, off, len);
            onWrite.run();
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            // Buffering output stream does not perform asynchronous I/O.
        }
    }
}
