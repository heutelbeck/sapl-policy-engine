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

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import lombok.val;

@DisplayName("ServletMutableHttpRequest")
class ServletMutableHttpRequestTests {

    private MockHttpServletRequest    delegate;
    private ServletMutableHttpRequest mutable;

    @BeforeEach
    void setUp() {
        delegate = new MockHttpServletRequest("GET", "/resource");
        delegate.addHeader("X-Tenant", "alpha");
        delegate.addHeader("Accept", "application/json");
        mutable = new ServletMutableHttpRequest(delegate);
    }

    @Nested
    @DisplayName("setHeader")
    class SetHeader {

        @Test
        @DisplayName("replaces an existing header")
        void replacesExisting() {
            mutable.setHeader("X-Tenant", "beta");
            assertThat(mutable.getHeader("X-Tenant")).isEqualTo("beta");
        }

        @Test
        @DisplayName("adds a header that was not present on the delegate")
        void addsNew() {
            mutable.setHeader("X-Trace", "abc");
            assertThat(mutable.getHeader("X-Trace")).isEqualTo("abc");
        }

        @Test
        @DisplayName("undoes a prior removeHeader on the same name")
        void undoesPriorRemove() {
            mutable.removeHeader("X-Tenant");
            mutable.setHeader("X-Tenant", "gamma");
            assertThat(mutable.getHeader("X-Tenant")).isEqualTo("gamma");
        }
    }

    @Nested
    @DisplayName("addHeader")
    class AddHeader {

        @Test
        @DisplayName("appends to existing values")
        void appendsToExisting() {
            mutable.addHeader("X-Tenant", "beta");
            assertThat(Collections.list(mutable.getHeaders("X-Tenant"))).containsExactly("alpha", "beta");
        }

        @Test
        @DisplayName("starts a new value list when delegate had none")
        void startsNew() {
            mutable.addHeader("X-Trace", "id-1");
            mutable.addHeader("X-Trace", "id-2");
            assertThat(Collections.list(mutable.getHeaders("X-Trace"))).containsExactly("id-1", "id-2");
        }

        @Test
        @DisplayName("after a remove, only the appended value remains")
        void afterRemoveOnlyAppended() {
            mutable.removeHeader("X-Tenant");
            mutable.addHeader("X-Tenant", "fresh");
            assertThat(Collections.list(mutable.getHeaders("X-Tenant"))).containsExactly("fresh");
        }
    }

    @Nested
    @DisplayName("removeHeader")
    class RemoveHeader {

        @Test
        @DisplayName("hides a delegate header from getHeader and getHeaders")
        void hidesDelegateHeader() {
            mutable.removeHeader("X-Tenant");
            assertThat(mutable.getHeader("X-Tenant")).isNull();
            assertThat(Collections.list(mutable.getHeaders("X-Tenant"))).isEmpty();
        }

        @Test
        @DisplayName("removes overridden header values")
        void removesOverride() {
            mutable.setHeader("X-Trace", "abc");
            mutable.removeHeader("X-Trace");
            assertThat(mutable.getHeader("X-Trace")).isNull();
        }
    }

    @Nested
    @DisplayName("setAttribute")
    class SetAttribute {

        @Test
        @DisplayName("makes the value visible via the standard request-attribute API")
        void visibleToDownstream() {
            mutable.setAttribute("subject-id", "42");
            assertThat(delegate.getAttribute("subject-id")).isEqualTo("42");
        }
    }

    @Nested
    @DisplayName("snapshot")
    class Snapshot {

        @Test
        @DisplayName("reflects mutations made through the typed API")
        void reflectsMutations() {
            mutable.setHeader("X-Tenant", "delta");
            val snapshot = mutable.snapshot();
            assertThat(snapshot.getHeaders().getFirst("X-Tenant")).isEqualTo("delta");
        }
    }

    @Nested
    @DisplayName("getHeaderNames")
    class GetHeaderNames {

        @Test
        @DisplayName("includes added headers and excludes removed ones")
        void includesAddedExcludesRemoved() {
            mutable.removeHeader("X-Tenant");
            mutable.setHeader("X-Trace", "abc");
            val names = Collections.list(mutable.getHeaderNames());
            assertThat(names).contains("Accept").noneMatch("X-Tenant"::equalsIgnoreCase)
                    .anyMatch("X-Trace"::equalsIgnoreCase);
        }
    }

    @Nested
    @DisplayName("getIntHeader and getDateHeader")
    class TypedHeaderAccessors {

        @Test
        @DisplayName("getIntHeader reads the overridden numeric value")
        void getIntHeaderReadsOverride() {
            mutable.setHeader("X-Count", "42");
            assertThat(mutable.getIntHeader("X-Count")).isEqualTo(42);
        }

        @Test
        @DisplayName("getIntHeader returns -1 when no header is present")
        void getIntHeaderReturnsMinusOne() {
            assertThat(mutable.getIntHeader("X-Missing")).isEqualTo(-1);
        }

        @Test
        @DisplayName("getDateHeader reads the overridden epoch value")
        void getDateHeaderReadsOverride() {
            mutable.setHeader("X-When", "1700000000000");
            assertThat(mutable.getDateHeader("X-When")).isEqualTo(1700000000000L);
        }
    }

    @Test
    @DisplayName("multiple sequential mutations interleave coherently")
    void interleavedMutations() {
        mutable.setHeader("X-A", "1");
        mutable.addHeader("X-A", "2");
        mutable.removeHeader("Accept");
        mutable.setHeader("Accept", "text/plain");
        assertThat(Collections.list(mutable.getHeaders("X-A"))).containsExactly("1", "2");
        assertThat(mutable.getHeader("Accept")).isEqualTo("text/plain");
    }

    @Test
    @DisplayName("snapshot captures both header overrides and removals")
    void snapshotCapturesOverridesAndRemovals() {
        mutable.removeHeader("Accept");
        mutable.setHeader("X-Trace", "xyz");
        val snapshotHeaders = mutable.snapshot().getHeaders();
        assertThat(snapshotHeaders.getFirst("X-Trace")).isEqualTo("xyz");
        assertThat(snapshotHeaders.get("Accept")).isNullOrEmpty();
    }

    @Test
    @DisplayName("delegate constructor wraps the given request")
    void wraps() {
        assertThat(mutable.getRequest()).isSameAs(delegate);
    }

    @Test
    @DisplayName("List-based API: setHeader applied with a single value")
    void setHeaderSingleValue() {
        mutable.setHeader("X-Foo", "bar");
        assertThat(List.copyOf(Collections.list(mutable.getHeaders("X-Foo")))).containsExactly("bar");
    }
}
