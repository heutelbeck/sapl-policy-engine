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
/**
 * Decision interceptor implementations for logging, auditing, and reporting.
 * <p>
 * Provides utilities for formatting and logging traced authorization decisions:
 * <ul>
 * <li>{@link io.sapl.pdp.interceptors.ReportingDecisionInterceptor} - Main
 * interceptor for logging decisions</li>
 * <li>{@link io.sapl.pdp.interceptors.ReportBuilderUtil} - Extracts concise
 * reports from traces</li>
 * <li>{@link io.sapl.pdp.interceptors.ReportTextRenderUtil} - Renders reports
 * as human-readable text</li>
 * </ul>
 */
package io.sapl.pdp.interceptors;
