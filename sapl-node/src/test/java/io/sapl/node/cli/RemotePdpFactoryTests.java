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
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import lombok.val;

@DisplayName("RemotePdpFactory")
class RemotePdpFactoryTests {

    @Nested
    @DisplayName("authentication modes")
    class AuthenticationTests {

        @Test
        @DisplayName("rejects basicAuth without colon separator")
        void whenBasicAuthMissingColon_thenThrowsIllegalArgument() {
            val ctx = BenchmarkContext.remote("{}", "http://localhost:8443", "no-colon-here", null, false);
            assertThatThrownBy(() -> RemotePdpFactory.create(ctx)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage(RemotePdpFactory.ERROR_BASIC_AUTH_FORMAT);
        }

        @ParameterizedTest(name = "{0}")
        @DisplayName("creates PDP for valid auth configuration")
        @MethodSource
        void whenValidAuth_thenCreatesSuccessfully(String description, String basicAuth, String token)
                throws Exception {
            val ctx = BenchmarkContext.remote("{}", "http://localhost:8443", basicAuth, token, false);
            assertThat(RemotePdpFactory.create(ctx)).isNotNull();
        }

        static Stream<Arguments> whenValidAuth_thenCreatesSuccessfully() {
            return Stream.of(arguments("basic auth", "user:pass", null), arguments("token auth", null, "my-token"),
                    arguments("no auth", null, null));
        }

    }

}
