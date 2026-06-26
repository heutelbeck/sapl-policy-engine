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
package io.sapl.node.cli.benchmark;

import org.jspecify.annotations.Nullable;

/**
 * Outcome of a load test run. The {@code result} carries the throughput and
 * latency distribution, or is {@code null} when the server could not be
 * reached. The {@code failures} count is the number of requests that returned
 * a non-success status or errored in transport during the measurement.
 *
 * @param result the measured result, or null when the server was unreachable
 * @param failures the number of failed requests during measurement
 */
public record LoadtestOutcome(@Nullable BenchmarkResult result, long failures) {}
