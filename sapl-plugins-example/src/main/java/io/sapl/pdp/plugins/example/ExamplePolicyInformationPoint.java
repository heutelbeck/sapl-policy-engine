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
package io.sapl.pdp.plugins.example;

import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.Value;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.Streams;

import java.lang.management.ManagementFactory;
import java.time.Duration;

/**
 * Example Policy Information Point contributed by the reference plugin. Depends
 * only on {@code sapl-api}.
 */
@PolicyInformationPoint(name = ExamplePolicyInformationPoint.NAME, description = "Example PIP contributed by a plugin.")
public class ExamplePolicyInformationPoint {

    public static final String NAME = "example";

    private static final Duration POLL_INTERVAL = Duration.ofSeconds(5);

    /**
     * Streams the operating-system load average (last minute), sampled every
     * five seconds. It is read live from the OS through the JVM's
     * {@code OperatingSystemMXBean},
     * polled on a virtual thread, and republished only when the value actually
     * changes.
     * <p>
     * Emits {@code -1} on platforms that do not provide a load average (for example
     * Windows).
     *
     * @return a stream of the system load average as a numeric value
     */
    @EnvironmentAttribute(docs = "```<example.systemLoadAverage>``` Operating-system load average (last minute), "
            + "polled every 5 seconds. Emits -1 where the platform does not provide it (for example Windows).")
    public Stream<Value> systemLoadAverage() {
        var operatingSystem = ManagementFactory.getOperatingSystemMXBean();
        return Streams.distinctUntilChanged(
                Streams.poll(POLL_INTERVAL, () -> Value.of(operatingSystem.getSystemLoadAverage())));
    }

}
