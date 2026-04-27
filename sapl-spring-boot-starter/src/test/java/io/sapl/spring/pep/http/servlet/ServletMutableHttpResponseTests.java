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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import lombok.val;

@DisplayName("ServletMutableHttpResponse")
class ServletMutableHttpResponseTests {

    private MockHttpServletResponse    delegate;
    private ServletMutableHttpResponse mutable;

    @BeforeEach
    void setUp() {
        delegate = new MockHttpServletResponse();
        mutable  = new ServletMutableHttpResponse(delegate);
    }

    @Nested
    @DisplayName("Buffering invariant")
    class Buffering {

        @Test
        @DisplayName("nothing reaches the delegate before commit")
        void nothingBeforeCommit() {
            mutable.setStatusCode(404);
            mutable.setHeader("X-Trace", "abc");
            mutable.setBody("hidden");
            assertThat(delegate).satisfies(d -> {
                assertThat(d.getStatus()).isEqualTo(200);
                assertThat(d.getHeader("X-Trace")).isNull();
                assertThat(d.getContentAsString()).isEmpty();
            });
        }

        @Test
        @DisplayName("commit flushes status, headers, and body to the delegate")
        void commitFlushesAll() throws Exception {
            mutable.setStatusCode(404);
            mutable.setHeader("X-Trace", "abc");
            mutable.setBody("hello");
            mutable.commit();
            assertThat(delegate).satisfies(d -> {
                assertThat(d.getStatus()).isEqualTo(404);
                assertThat(d.getHeader("X-Trace")).isEqualTo("abc");
                assertThat(d.getContentAsString()).isEqualTo("hello");
            });
        }

        @Test
        @DisplayName("commit is idempotent")
        void commitIdempotent() throws Exception {
            mutable.setBody("once");
            mutable.commit();
            mutable.commit();
            assertThat(delegate.getContentAsString()).isEqualTo("once");
            assertThat(mutable.isCommitted()).isTrue();
        }
    }

    @Nested
    @DisplayName("Status accessors")
    class Status {

        @Test
        @DisplayName("default status is 200 OK")
        void defaultIsOk() {
            assertThat(mutable).satisfies(m -> {
                assertThat(m.getStatus()).isEqualTo(200);
                assertThat(m.getStatusCode()).isEqualTo(HttpStatus.OK);
            });
        }

        @Test
        @DisplayName("setStatusCode and setStatus are equivalent")
        void setStatusEquivalent() {
            mutable.setStatusCode(HttpStatus.BAD_REQUEST);
            assertThat(mutable.getStatus()).isEqualTo(400);
            mutable.setStatus(418);
            assertThat(mutable.getStatusCode().value()).isEqualTo(418);
        }
    }

    @Nested
    @DisplayName("Header accessors")
    class Headers {

        @Test
        @DisplayName("setHeader replaces existing values")
        void setReplaces() {
            mutable.addHeader("X-A", "1");
            mutable.addHeader("X-A", "2");
            mutable.setHeader("X-A", "3");
            assertThat(mutable.getHeaders("X-A")).containsExactly("3");
        }

        @Test
        @DisplayName("addHeader appends to existing values")
        void addAppends() {
            mutable.addHeader("X-A", "1");
            mutable.addHeader("X-A", "2");
            assertThat(mutable.getHeaders("X-A")).containsExactly("1", "2");
        }

        @Test
        @DisplayName("removeHeader drops all values")
        void removeDropsAll() {
            mutable.addHeader("X-A", "1");
            mutable.removeHeader("X-A");
            assertThat(mutable).satisfies(m -> {
                assertThat(m.getHeader("X-A")).isNull();
                assertThat(m.getHeaders("X-A")).isEmpty();
            });
        }

        @Test
        @DisplayName("getHeaderNames lists all currently buffered names")
        void namesListsBuffered() {
            mutable.setHeader("X-A", "1");
            mutable.setHeader("X-B", "2");
            assertThat(mutable.getHeaderNames()).containsExactlyInAnyOrder("X-A", "X-B");
        }

        @Test
        @DisplayName("setIntHeader and addIntHeader produce numeric strings")
        void typedHeaderHelpers() {
            mutable.setIntHeader("X-Count", 7);
            mutable.addIntHeader("X-Count", 8);
            mutable.setDateHeader("X-When", 1700000000000L);
            assertThat(mutable).satisfies(m -> {
                assertThat(m.getHeaders("X-Count")).containsExactly("7", "8");
                assertThat(m.getHeader("X-When")).isEqualTo("1700000000000");
            });
        }

        @Test
        @DisplayName("headers() shares the buffer with named accessors")
        void headersViewSharesBuffer() {
            mutable.setHeader("X-A", "1");
            mutable.headers().add("X-A", "2");
            assertThat(mutable.getHeaders("X-A")).containsExactly("1", "2");
        }
    }

    @Nested
    @DisplayName("Content type and length")
    class ContentTypeAndLength {

        @Test
        @DisplayName("setContentType writes through to the headers buffer")
        void setContentTypeIsBuffered() {
            mutable.setContentType("application/json");
            assertThat(mutable.getContentType()).startsWith("application/json");
        }

        @Test
        @DisplayName("setContentType(null) removes the Content-Type header")
        void setContentTypeNullRemoves() {
            mutable.setContentType("application/json");
            mutable.setContentType(null);
            assertThat(mutable.getContentType()).isNull();
        }

        @Test
        @DisplayName("setContentLength writes the Content-Length header")
        void contentLengthIsBuffered() {
            mutable.setContentLength(123);
            assertThat(mutable.getHeader("Content-Length")).isEqualTo("123");
        }
    }

    @Nested
    @DisplayName("Body API")
    class Body {

        @Test
        @DisplayName("setBody and getBody round-trip a string")
        void roundTripString() {
            mutable.setBody("alpha");
            assertThat(mutable.getBody()).isEqualTo("alpha");
        }

        @Test
        @DisplayName("setBody replaces previous content")
        void setBodyReplaces() {
            mutable.setBody("first");
            mutable.setBody("second");
            assertThat(mutable.getBody()).isEqualTo("second");
        }

        @Test
        @DisplayName("writeBody sets Content-Type and replaces body")
        void writeBodySetsContentType() {
            mutable.writeBody("text/plain;charset=UTF-8", "hello");
            assertThat(mutable).satisfies(m -> {
                assertThat(m.getBody()).isEqualTo("hello");
                assertThat(m.getContentType()).startsWith("text/plain");
            });
        }

        @Test
        @DisplayName("getOutputStream writes accumulate into the buffer")
        void outputStreamWritesAccumulate() throws Exception {
            mutable.getOutputStream().write("hello".getBytes(StandardCharsets.UTF_8));
            mutable.getOutputStream().write(" world".getBytes(StandardCharsets.UTF_8));
            assertThat(mutable.getBody()).isEqualTo("hello world");
        }

        @Test
        @DisplayName("getWriter writes are flushed on commit")
        void writerWritesFlushedOnCommit() throws Exception {
            val writer = mutable.getWriter();
            writer.write("through writer");
            mutable.commit();
            assertThat(delegate.getContentAsString()).isEqualTo("through writer");
        }
    }

    @Nested
    @DisplayName("isModified")
    class Modified {

        @Test
        @DisplayName("starts false on a fresh response")
        void startsFalse() {
            assertThat(mutable.isModified()).isFalse();
        }

        @ParameterizedTest(name = "{0} ticks isModified")
        @MethodSource("typedSetters")
        @DisplayName("typed setters tick the modified flag")
        void typedSettersTickFlag(String name, Consumer<ServletMutableHttpResponse> setter) {
            val response = new ServletMutableHttpResponse(new MockHttpServletResponse());
            setter.accept(response);
            assertThat(response.isModified()).isTrue();
        }

        static Stream<Arguments> typedSetters() {
            return Stream.of(arguments("setStatus", (Consumer<ServletMutableHttpResponse>) r -> r.setStatus(404)),
                    arguments("setHeader", (Consumer<ServletMutableHttpResponse>) r -> r.setHeader("X", "1")),
                    arguments("addHeader", (Consumer<ServletMutableHttpResponse>) r -> r.addHeader("X", "1")),
                    arguments("removeHeader", (Consumer<ServletMutableHttpResponse>) r -> r.removeHeader("X")),
                    arguments("setBody", (Consumer<ServletMutableHttpResponse>) r -> r.setBody("x")),
                    arguments("writeBody", (Consumer<ServletMutableHttpResponse>) r -> r.writeBody("text/plain", "x")));
        }

        @Test
        @DisplayName("ticks on output stream writes")
        void outputStreamTicksFlag() throws Exception {
            mutable.getOutputStream().write('x');
            assertThat(mutable.isModified()).isTrue();
        }
    }

    @Nested
    @DisplayName("sendError and sendRedirect")
    class ErrorAndRedirect {

        @Test
        @DisplayName("sendError sets status, replaces body, ticks modified")
        void sendErrorBuffers() throws Exception {
            mutable.sendError(503, "service down");
            assertThat(mutable).satisfies(m -> {
                assertThat(m.getStatus()).isEqualTo(503);
                assertThat(m.getBody()).isEqualTo("service down");
                assertThat(m.isModified()).isTrue();
            });
        }

        @Test
        @DisplayName("sendRedirect sets 302 and Location header")
        void sendRedirectBuffers() throws Exception {
            mutable.sendRedirect("/login");
            assertThat(mutable).satisfies(m -> {
                assertThat(m.getStatus()).isEqualTo(302);
                assertThat(m.getHeader("Location")).isEqualTo("/login");
            });
        }
    }
}
