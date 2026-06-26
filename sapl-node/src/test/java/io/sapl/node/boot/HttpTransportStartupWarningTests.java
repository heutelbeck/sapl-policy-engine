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
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import lombok.val;

/**
 * Specifications for {@link HttpTransportStartupWarning}.
 * <p>
 * The warning's contract is operator-facing: when the HTTP server boots
 * without TLS, a single factual WARN naming the port and the cleartext
 * implication must appear in the boot log so the configuration mistake is
 * visible without grepping the YAML file. When TLS is enabled, the
 * warning must be silent so the boot log does not carry false alarms.
 */
@DisplayName("HttpTransportStartupWarning")
@ExtendWith(MockitoExtension.class)
class HttpTransportStartupWarningTests {

    @Mock
    private WebServer webServer;

    @Mock
    private ServletWebServerApplicationContext applicationContext;

    private HttpTransportStartupWarning sut;
    private ListAppender<ILoggingEvent> appender;
    private Logger                      logger;

    private Level originalLevel;

    @BeforeEach
    void setUp() {
        sut           = new HttpTransportStartupWarning();
        logger        = (Logger) LoggerFactory.getLogger(HttpTransportStartupWarning.class);
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

    private void setSslEnabled(boolean enabled) throws ReflectiveOperationException {
        Field field = HttpTransportStartupWarning.class.getDeclaredField("sslEnabled");
        field.setAccessible(true);
        field.setBoolean(sut, enabled);
    }

    @Nested
    @DisplayName("when TLS is disabled")
    class TlsDisabled {

        @Test
        @DisplayName("logs a single factual WARN naming the port and the cleartext implication")
        void whenSslDisabledThenWarnFiresWithPortAndCleartextWording() throws Exception {
            setSslEnabled(false);
            when(webServer.getPort()).thenReturn(8080);

            sut.onWebServerInitialized(new ServletWebServerInitializedEvent(webServer, applicationContext));

            assertThat(appender.list).hasSize(1).first().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                val message = event.getFormattedMessage();
                assertThat(message).contains("8080").contains("without TLS").contains("cleartext")
                        .contains("server.ssl.bundle");
            });
        }
    }

    @Nested
    @DisplayName("when TLS is enabled")
    class TlsEnabled {

        @Test
        @DisplayName("does not log so the boot log carries no false cleartext alarm")
        void whenSslEnabledThenNoWarn() throws Exception {
            setSslEnabled(true);

            sut.onWebServerInitialized(new ServletWebServerInitializedEvent(webServer, applicationContext));

            assertThat(appender.list).isEmpty();
        }
    }
}
