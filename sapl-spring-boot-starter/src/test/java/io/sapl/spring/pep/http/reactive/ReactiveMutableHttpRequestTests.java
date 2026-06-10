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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import lombok.val;

@DisplayName("ReactiveMutableHttpRequest")
class ReactiveMutableHttpRequestTests {

    private MockServerHttpRequest      original;
    private ReactiveMutableHttpRequest mutable;

    @BeforeEach
    void setUp() {
        original = MockServerHttpRequest.get("/resource").header("X-Tenant", "alpha")
                .header("Accept", "application/json").build();
        mutable  = new ReactiveMutableHttpRequest(original);
    }

    @Nested
    @DisplayName("Header mutations")
    class Headers {

        @Test
        @DisplayName("setHeader replaces existing values when applied")
        void setReplaces() {
            mutable.setHeader("X-Tenant", "beta");
            val mutated = mutable.applyTo(MockServerWebExchange.from(original)).getRequest();
            assertThat(mutated).satisfies(r -> {
                assertThat(r.getHeaders().get("X-Tenant")).containsExactly("beta");
                assertThat(mutable.isModified()).isTrue();
            });
        }

        @Test
        @DisplayName("addHeader appends to existing values when applied")
        void addAppends() {
            mutable.addHeader("X-Tenant", "beta");
            val mutated = mutable.applyTo(MockServerWebExchange.from(original)).getRequest();
            assertThat(mutated.getHeaders().get("X-Tenant")).containsExactly("alpha", "beta");
        }

        @Test
        @DisplayName("removeHeader hides a delegate header")
        void removeHides() {
            mutable.removeHeader("X-Tenant");
            val mutated = mutable.applyTo(MockServerWebExchange.from(original)).getRequest();
            assertThat(mutated.getHeaders().get("X-Tenant")).isNullOrEmpty();
        }

        @Test
        @DisplayName("setHeader after remove resurrects with the new value")
        void setAfterRemove() {
            mutable.removeHeader("X-Tenant");
            mutable.setHeader("X-Tenant", "gamma");
            val mutated = mutable.applyTo(MockServerWebExchange.from(original)).getRequest();
            assertThat(mutated.getHeaders().get("X-Tenant")).containsExactly("gamma");
        }
    }

    @Nested
    @DisplayName("Attribute mutations")
    class Attributes {

        @Test
        @DisplayName("setAttribute makes the value visible on the mutated exchange")
        void attributeReachesExchange() {
            mutable.setAttribute("subject-id", "42");
            val mutated = mutable.applyTo(MockServerWebExchange.from(original));
            assertThat(mutated.<String>getAttribute("subject-id")).isEqualTo("42");
        }
    }

    @Nested
    @DisplayName("isModified bypass")
    class Modified {

        @Test
        @DisplayName("starts false on a fresh wrapper")
        void startsFalse() {
            assertThat(mutable.isModified()).isFalse();
        }

        @Test
        @DisplayName("applyTo returns the original exchange unchanged when nothing was mutated")
        void applyReturnsOriginalOnNoMutation() {
            val exchange = MockServerWebExchange.from(original);
            assertThat(mutable.applyTo(exchange)).isSameAs(exchange);
        }

        @Test
        @DisplayName("ticks for setHeader, addHeader, removeHeader, setAttribute")
        void typedSettersTickFlag() {
            assertThat(new ReactiveMutableHttpRequest(original)).satisfies(r -> {
                r.setHeader("X", "1");
                assertThat(r.isModified()).isTrue();
            });
            assertThat(new ReactiveMutableHttpRequest(original)).satisfies(r -> {
                r.addHeader("X", "1");
                assertThat(r.isModified()).isTrue();
            });
            assertThat(new ReactiveMutableHttpRequest(original)).satisfies(r -> {
                r.removeHeader("X");
                assertThat(r.isModified()).isTrue();
            });
            assertThat(new ReactiveMutableHttpRequest(original)).satisfies(r -> {
                r.setAttribute("X", "1");
                assertThat(r.isModified()).isTrue();
            });
        }
    }

    @Nested
    @DisplayName("snapshot")
    class Snapshot {

        @Test
        @DisplayName("reflects mutations made through the typed API")
        void reflectsMutations() {
            mutable.setHeader("X-Tenant", "delta");
            assertThat(mutable.snapshot().getHeaders().getFirst("X-Tenant")).isEqualTo("delta");
        }
    }
}
