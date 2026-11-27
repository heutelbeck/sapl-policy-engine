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
package io.sapl.node.testing;

import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Captures what a class logs at INFO and above while a test runs. Close it to
 * restore the logger.
 */
public final class LogCapture implements AutoCloseable {

    private final Logger                      logger;
    private final Level                       originalLevel;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Class<?> type) {
        logger        = (Logger) LoggerFactory.getLogger(type);
        originalLevel = logger.getLevel();
        logger.setLevel(Level.INFO);
        appender.start();
        logger.addAppender(appender);
    }

    /**
     * Starts capturing the log of a class.
     *
     * @param type the class whose logger is captured
     * @return the capture
     */
    public static LogCapture of(Class<?> type) {
        return new LogCapture(type);
    }

    /**
     * Returns the captured lines as level and formatted message.
     *
     * @return the lines in the order they were logged
     */
    public List<String> lines() {
        return appender.list.stream().map(event -> event.getLevel() + " " + event.getFormattedMessage()).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        logger.setLevel(originalLevel);
    }

}
