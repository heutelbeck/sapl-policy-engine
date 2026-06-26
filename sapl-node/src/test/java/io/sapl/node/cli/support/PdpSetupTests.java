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
package io.sapl.node.cli.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.PrintWriter;
import java.io.Writer;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.sapl.node.cli.options.PdpOptions;
import io.sapl.node.cli.options.RemoteConnectionOptions;
import lombok.val;

/**
 * Specifications for {@link PdpSetup} remote-URL resolution.
 * <p>
 * The documented precedence for the remote PDP URL is: an explicit
 * {@code --url} flag wins over the {@code SAPL_URL} environment variable,
 * which in turn wins over the built-in default. The operator-visible
 * contract (SaplNodeCli help and the Getting Started precedence table) is
 * "flags take precedence over environment variables". These tests pin that
 * precedence across the full matrix, including the collision case where the
 * operator explicitly types a value that happens to equal the literal
 * default.
 */
@DisplayName("PdpSetup")
class PdpSetupTests {

    @Nested
    @DisplayName("remote URL precedence (flag over env over default)")
    class RemoteUrlPrecedence {

        private static final String DEFAULT = "http://localhost:8080";

        @Test
        @DisplayName("explicit flag wins when set to a non-default value, ignoring the environment")
        void whenFlagSetToNonDefaultThenFlagWins() {
            assertThat(PdpSetup.resolveUrl("http://flag-host:9000", "http://env-host:8888", DEFAULT))
                    .isEqualTo("http://flag-host:9000");
        }

        @Test
        @DisplayName("explicit flag wins even when its value equals the literal default")
        void whenFlagSetToDefaultLiteralThenFlagStillWinsOverEnv() {
            assertThat(PdpSetup.resolveUrl(DEFAULT, "http://env-host:8888", DEFAULT)).isEqualTo(DEFAULT);
        }

        @Test
        @DisplayName("environment variable is used when the flag is absent")
        void whenFlagAbsentThenEnvWins() {
            assertThat(PdpSetup.resolveUrl(null, "http://env-host:8888", DEFAULT)).isEqualTo("http://env-host:8888");
        }

        @Test
        @DisplayName("built-in default is used when neither flag nor environment is set")
        void whenNeitherFlagNorEnvThenDefault() {
            assertThat(PdpSetup.resolveUrl(null, null, DEFAULT)).isEqualTo(DEFAULT);
        }

        @Test
        @DisplayName("flag wins over both env and default when all three are present")
        void whenFlagAndEnvAndDefaultAllPresentThenFlagWins() {
            assertThat(PdpSetup.resolveUrl("http://flag-host:9000", "http://env-host:8888", DEFAULT))
                    .isEqualTo("http://flag-host:9000");
        }
    }

    @Nested
    @DisplayName("remote transport security (plaintext credentials fail closed unless --insecure)")
    class RemoteTransportSecurity {

        private static PdpOptions remoteOptions(boolean rsocket, boolean insecure) {
            val auth = new RemoteConnectionOptions.AuthOptions();
            auth.token = "a-token";
            val remote = new RemoteConnectionOptions();
            remote.remote      = true;
            remote.rsocket     = rsocket;
            remote.insecure    = insecure;
            remote.url         = "http://localhost:8080";
            remote.rsocketHost = "localhost";
            remote.rsocketPort = 7000;
            remote.auth        = auth;
            val options = new PdpOptions();
            options.remoteConnection = remote;
            return options;
        }

        private static PrintWriter discardingWriter() {
            return new PrintWriter(Writer.nullWriter());
        }

        @Test
        @DisplayName("http credentials over plaintext are refused without --insecure")
        void whenHttpCredentialsWithoutInsecureThenFailsClosed() {
            val options = remoteOptions(false, false);
            val err     = discardingWriter();
            assertThatThrownBy(() -> PdpSetup.open(options, err)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("plaintext");
        }

        @Test
        @DisplayName("http credentials over plaintext are permitted with --insecure")
        void whenHttpCredentialsWithInsecureThenBuilds() throws SSLException {
            assertThat(PdpSetup.open(remoteOptions(false, true), discardingWriter())).isNotNull();
        }

        @Test
        @DisplayName("rsocket credentials over plaintext are refused without --insecure")
        void whenRsocketCredentialsWithoutInsecureThenFailsClosed() {
            val options = remoteOptions(true, false);
            val err     = discardingWriter();
            assertThatThrownBy(() -> PdpSetup.open(options, err)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("plaintext");
        }

        @Test
        @DisplayName("rsocket credentials over plaintext are permitted with --insecure")
        void whenRsocketCredentialsWithInsecureThenBuilds() throws SSLException {
            assertThat(PdpSetup.open(remoteOptions(true, true), discardingWriter())).isNotNull();
        }
    }
}
