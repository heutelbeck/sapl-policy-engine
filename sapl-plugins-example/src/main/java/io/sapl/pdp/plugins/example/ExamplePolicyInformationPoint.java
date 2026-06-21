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

import java.time.Duration;

/**
 * Example Policy Information Point contributed by the reference plugin. Depends
 * only on {@code sapl-api}.
 */
@PolicyInformationPoint(name = ExamplePolicyInformationPoint.NAME, description = "Example PIP contributed by a plugin.")
public class ExamplePolicyInformationPoint {

    public static final String NAME = "example";

    private static final Duration POLL_INTERVAL      = Duration.ofSeconds(5);
    private static final long     BYTES_PER_MEGABYTE = 1024L * 1024L;

    /**
     * Streams the JVM's currently used heap memory in whole megabytes, sampled
     * every five seconds on a virtual thread and republished only when the value
     * changes.
     * <p>
     * Used heap is {@code Runtime.totalMemory() - Runtime.freeMemory()} reduced to
     * whole megabytes. It relies only on the portable {@link Runtime} API, so it
     * behaves the same on every platform and never produces an undefined value.
     *
     * @return a stream of the used heap size in megabytes as a numeric value
     */
    @EnvironmentAttribute(docs = "```<example.usedHeapMemory>``` JVM heap memory currently in use, in whole "
            + "megabytes, polled every 5 seconds.")
    public Stream<Value> usedHeapMemory() {
        return Streams.distinctUntilChanged(Streams.poll(POLL_INTERVAL, () -> Value.of(usedHeapMegabytes())));
    }

    private static long usedHeapMegabytes() {
        final var runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MEGABYTE;
    }

}
