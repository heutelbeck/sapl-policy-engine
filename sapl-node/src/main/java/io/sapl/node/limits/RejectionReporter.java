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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.jspecify.annotations.Nullable;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Makes admission rejections observable. Every rejection increments a
 * Micrometer counter (tagged with the shedding surface) when a registry is
 * available, and emits a WARN that is rate-limited so a sustained over-limit
 * flood cannot flood the log.
 */
@Slf4j
public final class RejectionReporter {

    static final String METRIC_REJECTIONS = "sapl.limits.rejections";
    static final String TAG_SURFACE       = "surface";

    private static final String WARN_REJECTED = "Shedding load on {}: {}. Suppressed {} identical warnings in the last 10s.";

    private static final long WARN_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(10);

    private final String            surface;
    private final String            limitDescription;
    private final @Nullable Counter rejections;
    private final LongSupplier      nanoTime;
    private final AtomicLong        lastWarnNanos;
    private final AtomicLong        suppressedWarnings = new AtomicLong();

    /**
     * Creates a reporter for one shedding surface.
     *
     * @param surface short identifier of the shedding surface, used as metric
     * tag and in the log message
     * @param limitDescription operator-facing description of the configured
     * limit
     * @param meterRegistry the meter registry, or null when metrics are
     * unavailable
     */
    public RejectionReporter(String surface, String limitDescription, @Nullable MeterRegistry meterRegistry) {
        this(surface, limitDescription, meterRegistry, System::nanoTime);
    }

    RejectionReporter(String surface,
            String limitDescription,
            @Nullable MeterRegistry meterRegistry,
            LongSupplier nanoTime) {
        this.surface          = surface;
        this.limitDescription = limitDescription;
        this.nanoTime         = nanoTime;
        this.lastWarnNanos    = new AtomicLong(nanoTime.getAsLong() - WARN_INTERVAL_NANOS);
        if (meterRegistry == null) {
            this.rejections = null;
        } else {
            this.rejections = Counter.builder(METRIC_REJECTIONS).tag(TAG_SURFACE, surface)
                    .description("Requests or connections shed by the configured admission limits")
                    .register(meterRegistry);
        }
    }

    /**
     * Records one shed request or connection.
     */
    public void onRejection() {
        if (rejections != null) {
            rejections.increment();
        }
        val now      = nanoTime.getAsLong();
        val lastWarn = lastWarnNanos.get();
        if (now - lastWarn >= WARN_INTERVAL_NANOS && lastWarnNanos.compareAndSet(lastWarn, now)) {
            log.warn(WARN_REJECTED, surface, limitDescription, suppressedWarnings.getAndSet(0));
        } else {
            suppressedWarnings.incrementAndGet();
        }
    }
}
