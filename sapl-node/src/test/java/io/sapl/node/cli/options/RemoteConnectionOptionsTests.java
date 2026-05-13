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
package io.sapl.node.cli.options;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;
import picocli.CommandLine;

/**
 * Specifications for {@link RemoteConnectionOptions}.
 * <p>
 * The CLI exposes RSocket TLS as an opt-in flag because the server
 * defaults to plain TCP. These tests pin the operator-visible defaults
 * (TLS off) and the four flag combinations: rsocket plain, rsocket TLS
 * with default trust store, rsocket TLS with skip-verify, and the
 * orthogonal HTTP --insecure path.
 */
@DisplayName("RemoteConnectionOptions")
class RemoteConnectionOptionsTests {

    private static RemoteConnectionOptions parse(String... args) {
        val opts = new RemoteConnectionOptions();
        new CommandLine(opts).parseArgs(args);
        return opts;
    }

    @Nested
    @DisplayName("RSocket TLS flag wiring")
    class RsocketTlsFlag {

        @Test
        @DisplayName("--rsocket without --rsocket-tls leaves TLS off (plain TCP)")
        void whenRsocketWithoutTlsThenRsocketTlsFalse() {
            val opts = parse("--remote", "--rsocket");

            assertThat(opts).satisfies(o -> {
                assertThat(o.rsocket).isTrue();
                assertThat(o.rsocketTls).isFalse();
                assertThat(o.insecure).isFalse();
            });
        }

        @Test
        @DisplayName("--rsocket --rsocket-tls enables TLS with the system trust store")
        void whenRsocketTlsThenFlagSet() {
            val opts = parse("--remote", "--rsocket", "--rsocket-tls");

            assertThat(opts).satisfies(o -> {
                assertThat(o.rsocketTls).isTrue();
                assertThat(o.insecure).isFalse();
            });
        }

        @Test
        @DisplayName("--rsocket --rsocket-tls --insecure enables TLS with skip-verify (dev only)")
        void whenRsocketTlsAndInsecureThenBothFlagsSet() {
            val opts = parse("--remote", "--rsocket", "--rsocket-tls", "--insecure");

            assertThat(opts).satisfies(o -> {
                assertThat(o.rsocketTls).isTrue();
                assertThat(o.insecure).isTrue();
            });
        }

        @Test
        @DisplayName("--insecure on its own is the HTTPS skip-verify path and does not turn on RSocket TLS")
        void whenOnlyInsecureThenRsocketTlsFalse() {
            val opts = parse("--remote", "--insecure");

            assertThat(opts).satisfies(o -> {
                assertThat(o.rsocketTls).isFalse();
                assertThat(o.insecure).isTrue();
            });
        }
    }
}
