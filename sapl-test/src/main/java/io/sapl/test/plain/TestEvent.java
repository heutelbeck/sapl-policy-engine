/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.plain;

/**
 * Events emitted during test execution.
 * <p>
 * Subscribe to the event flux to receive live progress updates during test
 * execution. This is useful for web applications that want to push progress
 * updates to clients via WebSocket or SSE.
 */
public sealed interface TestEvent {

    /**
     * Emitted after each scenario completes.
     *
     * @param result the scenario result
     */
    record ScenarioCompleted(ScenarioResult result) implements TestEvent {}

    /**
     * Emitted when all tests complete. Contains aggregated results.
     *
     * @param results the aggregated test results
     */
    record ExecutionCompleted(PlainTestResults results) implements TestEvent {}
}
