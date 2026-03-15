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
package io.sapl.node;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.ConsoleAppender;
import lombok.val;

class CliLoggingTests {

    @AfterEach
    void resetLogback() {
        val context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();
    }

    @Test
    @DisplayName("configureCliLogging routes all log output to stderr at ERROR level")
    void configureCliLoggingRoutesAllLogOutputToStderrAtErrorLevel() {
        SaplNodeApplication.configureCliLogging();

        val context = (LoggerContext) LoggerFactory.getILoggerFactory();
        val root    = context.getLogger(Logger.ROOT_LOGGER_NAME);

        assertThat(root.getLevel()).isEqualTo(Level.ERROR);
        assertThat(root.iteratorForAppenders()).toIterable().hasSize(1).first().isInstanceOfSatisfying(
                ConsoleAppender.class, appender -> assertThat(appender.getTarget()).isEqualTo("System.err"));
    }

}
