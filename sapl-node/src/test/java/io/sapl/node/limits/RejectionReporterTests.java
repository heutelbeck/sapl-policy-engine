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
package io.sapl.node.limits;

import static io.sapl.node.limits.RejectionReporter.METRIC_REJECTIONS;
import static io.sapl.node.limits.RejectionReporter.TAG_SURFACE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.val;

@DisplayName("RejectionReporter")
class RejectionReporterTests {

    private ListAppender<ILoggingEvent> appender;
    private Logger                      logger;
    private Level                       originalLevel;

    @BeforeEach
    void setUp() {
        logger        = (Logger) LoggerFactory.getLogger(RejectionReporter.class);
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

    @Test
    @DisplayName("every rejection increments the surface-tagged counter so operators can alert on shed load")
    void whenRejectionsReportedThenCounterReflectsThem() {
        val registry = new SimpleMeterRegistry();
        val reporter = new RejectionReporter("test-surface", "test limit", registry);
        reporter.onRejection();
        reporter.onRejection();
        reporter.onRejection();
        val counter = registry.get(METRIC_REJECTIONS).tag(TAG_SURFACE, "test-surface").counter();
        assertThat(counter.count()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("reporting works without a meter registry so shedding never depends on metrics being wired")
    void whenNoRegistryThenReportingStillSafe() {
        val reporter = new RejectionReporter("test-surface", "test limit", null);
        assertThatCode(() -> {
            reporter.onRejection();
            reporter.onRejection();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a rejection flood produces one warning per interval carrying the suppressed count, not a log flood")
    void whenRejectionFloodThenWarningsRateLimited() {
        val clock    = new AtomicLong(0);
        val reporter = new RejectionReporter("test-surface", "test limit", null, clock::get);

        reporter.onRejection();
        reporter.onRejection();
        reporter.onRejection();
        assertThat(appender.list).as("one warning for the first rejection, the flood is suppressed").hasSize(1);

        clock.addAndGet(TimeUnit.SECONDS.toNanos(10));
        reporter.onRejection();
        assertThat(appender.list).as("next interval warns again").hasSize(2);
        assertThat(appender.list.get(1).getFormattedMessage()).as("the new warning reports the suppressed count")
                .contains("test-surface").contains("Suppressed 2");
    }
}
