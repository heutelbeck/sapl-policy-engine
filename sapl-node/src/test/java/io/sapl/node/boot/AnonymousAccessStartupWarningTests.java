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
package io.sapl.node.boot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import io.sapl.node.testing.LogCapture;
import lombok.val;

/**
 * Anonymous access is a legitimate choice when an outer layer owns the trust
 * boundary, so the node starts, but every start warns that it serves requests
 * without authentication and names the source of the opt-in. A transport
 * bound beyond loopback adds a warning naming the transport and the address.
 * Without the opt-in nothing is logged.
 */
@DisplayName("AnonymousAccessStartupWarning")
class AnonymousAccessStartupWarningTests {

    private static final String SOURCE = "io.sapl.node.allow-no-auth=true in test";

    private static final String WARN_ANONYMOUS = "WARN Server replies to requests without authentication because of "
            + SOURCE + ".";

    private static final String WARN_EXPOSED = "WARN The %s server is bound to a non-loopback address (%s) while anonymous access is enabled because of "
            + SOURCE
            + ". Unauthenticated clients that can reach this address may request authorization decisions. Enable an authentication mode, or restrict the bind address to loopback if this exposure is unintended.";

    @Nested
    @DisplayName("when anonymous access is allowed")
    class AnonymousAllowed {

        @Test
        @DisplayName("loopback binds on both transports log only the warning of anonymous access with its source")
        void whenBothTransportsLoopbackThenOnlyAnonymousWarning() throws Exception {
            assertThat(lines(true, "127.0.0.1", true, "127.0.0.1", null)).containsExactly(WARN_ANONYMOUS);
        }

        @Test
        @DisplayName("a non-loopback HTTP bind adds a warning naming the transport, the address and the source")
        void whenHttpBindsNonLoopbackThenExposureWarning() throws Exception {
            assertThat(lines(true, "0.0.0.0", false, "127.0.0.1", null)).containsExactly(WARN_ANONYMOUS,
                    WARN_EXPOSED.formatted("HTTP", "0.0.0.0"));
        }

        @Test
        @DisplayName("both transports exposed add one warning each")
        void whenBothTransportsExposedThenOneExposureWarningEach() throws Exception {
            assertThat(lines(true, "0.0.0.0", true, "0.0.0.0", null)).containsExactly(WARN_ANONYMOUS,
                    WARN_EXPOSED.formatted("HTTP", "0.0.0.0"), WARN_EXPOSED.formatted("RSocket", "0.0.0.0"));
        }

        @Test
        @DisplayName("an RSocket unix socket is no network bind and adds no exposure warning")
        void whenRsocketIsUnixSocketThenNoExposureWarning() throws Exception {
            assertThat(lines(true, "127.0.0.1", true, "0.0.0.0", "/run/sapl/pdp.sock")).containsExactly(WARN_ANONYMOUS);
        }

        @Test
        @DisplayName("a disabled RSocket transport adds no exposure warning")
        void whenRsocketDisabledThenNoExposureWarning() throws Exception {
            assertThat(lines(true, "127.0.0.1", false, "0.0.0.0", null)).containsExactly(WARN_ANONYMOUS);
        }
    }

    @Test
    @DisplayName("without the opt-in nothing is logged, even on exposed transports")
    void whenAnonymousNotAllowedThenSilent() throws Exception {
        assertThat(lines(false, "0.0.0.0", true, "0.0.0.0", null)).isEmpty();
    }

    private static Iterable<String> lines(boolean allowNoAuth, String httpAddress, boolean rsocketEnabled,
            String rsocketAddress, @Nullable String rsocketSocketPath) throws ReflectiveOperationException {
        val settings    = new HashMap<String, Object>(
                Map.of("io.sapl.node.allow-no-auth", String.valueOf(allowNoAuth)));
        val environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addFirst(new MapPropertySource("test", settings));
        val sut = new AnonymousAccessStartupWarning(environment);
        set(sut, "httpAddress", httpAddress);
        set(sut, "rsocketEnabled", rsocketEnabled);
        set(sut, "rsocketAddress", rsocketAddress);
        set(sut, "rsocketSocketPath", rsocketSocketPath);
        try (val log = LogCapture.of(AnonymousAccessStartupWarning.class)) {
            sut.warnOfAnonymousAccess();
            return log.lines();
        }
    }

    private static void set(AnonymousAccessStartupWarning sut, String fieldName, @Nullable Object value)
            throws ReflectiveOperationException {
        Field field = AnonymousAccessStartupWarning.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(sut, value);
    }

}
