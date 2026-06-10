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
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;

import lombok.val;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("ReactiveMutableHttpResponse")
class ReactiveMutableHttpResponseTests {

    private MockServerHttpResponse      delegate;
    private ReactiveMutableHttpResponse mutable;

    @BeforeEach
    void setUp() {
        delegate = new MockServerHttpResponse();
        mutable  = new ReactiveMutableHttpResponse(delegate);
    }

    @Nested
    @DisplayName("Buffering invariant")
    class Buffering {

        @Test
        @DisplayName("status and headers do not reach the delegate before commit")
        void nothingBeforeCommit() {
            mutable.setStatusCode(404);
            mutable.setHeader("X-Trace", "abc");
            mutable.setBody("hidden");
            assertThat(delegate).satisfies(d -> {
                assertThat(d.getStatusCode()).isNull();
                assertThat(d.getHeaders().getFirst("X-Trace")).isNull();
            });
        }

        @Test
        @DisplayName("commit flushes status, headers, and body to the delegate")
        void commitFlushesAll() {
            mutable.setStatusCode(404);
            mutable.setHeader("X-Trace", "abc");
            mutable.setBody("hello");
            StepVerifier.create(mutable.commit()).verifyComplete();
            assertThat(delegate).satisfies(d -> {
                assertThat(d.getStatusCode().value()).isEqualTo(404);
                assertThat(d.getHeaders().getFirst("X-Trace")).isEqualTo("abc");
                assertThat(d.getBodyAsString().block()).isEqualTo("hello");
            });
        }
    }

    @Nested
    @DisplayName("Status accessors")
    class Status {

        @Test
        @DisplayName("default status is 200 OK before any mutation")
        void defaultIsOk() {
            assertThat(mutable.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("setStatusCode reflects in subsequent getStatusCode")
        void setReflects() {
            mutable.setStatusCode(HttpStatus.BAD_REQUEST);
            assertThat(mutable.getStatusCode().value()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("Header accessors")
    class Headers {

        @Test
        @DisplayName("setHeader replaces existing values in the buffer")
        void setReplaces() {
            mutable.addHeader("X-A", "1");
            mutable.addHeader("X-A", "2");
            mutable.setHeader("X-A", "3");
            assertThat(mutable.headers().get("X-A")).containsExactly("3");
        }

        @Test
        @DisplayName("addHeader appends to existing values")
        void addAppends() {
            mutable.addHeader("X-A", "1");
            mutable.addHeader("X-A", "2");
            assertThat(mutable.headers().get("X-A")).containsExactly("1", "2");
        }

        @Test
        @DisplayName("removeHeader drops all values for the name")
        void removeDropsAll() {
            mutable.addHeader("X-A", "1");
            mutable.removeHeader("X-A");
            assertThat(mutable.headers().get("X-A")).isNullOrEmpty();
        }

        @Test
        @DisplayName("headers() shares the buffer with named accessors")
        void headersViewSharesBuffer() {
            mutable.setHeader("X-A", "1");
            mutable.headers().add("X-A", "2");
            assertThat(mutable.headers().get("X-A")).containsExactly("1", "2");
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
                assertThat(m.headers().getContentType().toString()).startsWith("text/plain");
            });
        }

        @Test
        @DisplayName("writeWith captures published DataBuffers into the body buffer")
        void writeWithCapturesBuffers() {
            val factory = new DefaultDataBufferFactory();
            val buffer  = factory.wrap("from controller".getBytes(StandardCharsets.UTF_8));
            StepVerifier.create(mutable.writeWith(Mono.just(buffer))).verifyComplete();
            assertThat(mutable.getBody()).isEqualTo("from controller");
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
        void typedSettersTickFlag(String name, Consumer<ReactiveMutableHttpResponse> setter) {
            val response = new ReactiveMutableHttpResponse(new MockServerHttpResponse());
            setter.accept(response);
            assertThat(response.isModified()).isTrue();
        }

        static Stream<Arguments> typedSetters() {
            return Stream.of(
                    arguments("setStatusCode",
                            (Consumer<ReactiveMutableHttpResponse>) r -> r.setStatusCode(HttpStatus.BAD_REQUEST)),
                    arguments("setHeader", (Consumer<ReactiveMutableHttpResponse>) r -> r.setHeader("X", "1")),
                    arguments("addHeader", (Consumer<ReactiveMutableHttpResponse>) r -> r.addHeader("X", "1")),
                    arguments("removeHeader", (Consumer<ReactiveMutableHttpResponse>) r -> r.removeHeader("X")),
                    arguments("setBody", (Consumer<ReactiveMutableHttpResponse>) r -> r.setBody("x")), arguments(
                            "writeBody", (Consumer<ReactiveMutableHttpResponse>) r -> r.writeBody("text/plain", "x")));
        }
    }
}
