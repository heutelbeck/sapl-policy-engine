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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.sapl.node.SaplNodeProperties;
import lombok.val;

/**
 * Specifications for {@link AnonymousAccessStartupWarning}.
 * <p>
 * The warning's contract is operator-facing: anonymous access on a
 * non-loopback transport is a legitimate deployment choice, so the node must
 * still start, but a single factual WARN naming the transport and the address
 * must make the exposure visible in the boot log. On loopback, when an
 * authentication mode is required, or when the RSocket transport is a unix
 * socket or disabled, the warning stays silent so the boot log carries no
 * false alarm.
 */
@DisplayName("AnonymousAccessStartupWarning")
class AnonymousAccessStartupWarningTests {

    private ListAppender<ILoggingEvent> appender;
    private Logger                      logger;
    private Level                       originalLevel;

    @BeforeEach
    void setUp() {
        logger        = (Logger) LoggerFactory.getLogger(AnonymousAccessStartupWarning.class);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.WARN);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

    private static AnonymousAccessStartupWarning warning(boolean allowNoAuth, String httpAddress,
            boolean rsocketEnabled, String rsocketAddress, String rsocketSocketPath)
            throws ReflectiveOperationException {
        val properties = new SaplNodeProperties();
        properties.setAllowNoAuth(allowNoAuth);
        val sut = new AnonymousAccessStartupWarning(properties);
        set(sut, "httpAddress", httpAddress);
        set(sut, "rsocketEnabled", rsocketEnabled);
        set(sut, "rsocketAddress", rsocketAddress);
        set(sut, "rsocketSocketPath", rsocketSocketPath);
        return sut;
    }

    private static void set(AnonymousAccessStartupWarning sut, String fieldName, Object value)
            throws ReflectiveOperationException {
        Field field = AnonymousAccessStartupWarning.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(sut, value);
    }

    @Nested
    @DisplayName("when anonymous access is enabled")
    class AnonymousEnabled {

        @Test
        @DisplayName("a non-loopback HTTP bind logs a factual WARN naming the transport, the address, and allow-no-auth")
        void whenHttpBindsNonLoopbackThenWarnNamesTransportAndAddress() throws Exception {
            val sut = warning(true, "0.0.0.0", false, "127.0.0.1", null);

            sut.warnIfAnonymousAndNetworkExposed();

            assertThat(appender.list).hasSize(1).first().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("HTTP server").contains("0.0.0.0")
                        .contains("allow-no-auth");
            });
        }

        @Test
        @DisplayName("a non-loopback RSocket bind logs a factual WARN naming the RSocket transport and the address")
        void whenRsocketBindsNonLoopbackThenWarnNamesTransportAndAddress() throws Exception {
            val sut = warning(true, "127.0.0.1", true, "0.0.0.0", null);

            sut.warnIfAnonymousAndNetworkExposed();

            assertThat(appender.list).hasSize(1).first().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage()).contains("RSocket server").contains("0.0.0.0")
                        .contains("allow-no-auth");
            });
        }

        @Test
        @DisplayName("both transports exposed logs one WARN per transport")
        void whenBothTransportsExposedThenOneWarnEach() throws Exception {
            val sut = warning(true, "0.0.0.0", true, "0.0.0.0", null);

            sut.warnIfAnonymousAndNetworkExposed();

            assertThat(appender.list).hasSize(2);
        }

        @Test
        @DisplayName("loopback binds on both transports stay silent")
        void whenBothTransportsLoopbackThenSilent() throws Exception {
            val sut = warning(true, "127.0.0.1", true, "127.0.0.1", null);

            sut.warnIfAnonymousAndNetworkExposed();

            assertThat(appender.list).isEmpty();
        }

        @Test
        @DisplayName("an RSocket unix socket is not a network bind, so it stays silent even with a non-loopback address")
        void whenRsocketIsUnixSocketThenSilent() throws Exception {
            val sut = warning(true, "127.0.0.1", true, "0.0.0.0", "/run/sapl/pdp.sock");

            sut.warnIfAnonymousAndNetworkExposed();

            assertThat(appender.list).isEmpty();
        }

        @Test
        @DisplayName("a disabled RSocket transport stays silent even with a non-loopback address")
        void whenRsocketDisabledThenSilent() throws Exception {
            val sut = warning(true, "127.0.0.1", false, "0.0.0.0", null);

            sut.warnIfAnonymousAndNetworkExposed();

            assertThat(appender.list).isEmpty();
        }
    }

    @Nested
    @DisplayName("when anonymous access is disabled")
    class AnonymousDisabled {

        @Test
        @DisplayName("a non-loopback bind on both transports stays silent because no anonymous path exists")
        void whenAllowNoAuthFalseThenSilentEvenWhenExposed() throws Exception {
            val sut = warning(false, "0.0.0.0", true, "0.0.0.0", null);

            sut.warnIfAnonymousAndNetworkExposed();

            assertThat(appender.list).isEmpty();
        }
    }
}
