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
package io.sapl.node.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;

@DisplayName("RemotePdpFactory")
class RemotePdpFactoryTests {

    @Nested
    @DisplayName("basicAuth validation")
    class BasicAuthTests {

        @Test
        @DisplayName("rejects basicAuth without colon separator")
        void whenBasicAuthMissingColon_thenThrowsIllegalArgument() {
            val ctx = BenchmarkContext.remote("{}", "http://localhost:8443", "no-colon-here", null, false);
            assertThatThrownBy(() -> RemotePdpFactory.create(ctx)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(RemotePdpFactory.ERROR_BASIC_AUTH_FORMAT);
        }

        @Test
        @DisplayName("accepts basicAuth with colon separator")
        void whenBasicAuthValid_thenCreatesWithoutException() throws Exception {
            val ctx = BenchmarkContext.remote("{}", "http://localhost:8443", "user:pass", null, false);
            val pdp = RemotePdpFactory.create(ctx);
            assertThat(pdp).isNotNull();
        }

        @Test
        @DisplayName("accepts token auth")
        void whenTokenAuth_thenCreatesWithoutException() throws Exception {
            val ctx = BenchmarkContext.remote("{}", "http://localhost:8443", null, "my-token", false);
            val pdp = RemotePdpFactory.create(ctx);
            assertThat(pdp).isNotNull();
        }

        @Test
        @DisplayName("accepts no auth")
        void whenNoAuth_thenCreatesWithoutException() throws Exception {
            val ctx = BenchmarkContext.remote("{}", "http://localhost:8443", null, null, false);
            val pdp = RemotePdpFactory.create(ctx);
            assertThat(pdp).isNotNull();
        }

    }

}
