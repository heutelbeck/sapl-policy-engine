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
package io.sapl.pdp.interceptors;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.pdp.internal.TracedDecision;
import io.sapl.api.pdp.internal.TracedDecisionInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Interceptor that logs authorization decisions for debugging and auditing.
 * <p>
 * Supports multiple output formats:
 * <ul>
 * <li>Full trace - the complete trace structure as JSON</li>
 * <li>JSON report - a condensed JSON report with essential information</li>
 * <li>Text report - human-readable formatted output for console/logs</li>
 * </ul>
 * <p>
 * This interceptor executes with the lowest priority (last) to ensure all other
 * interceptors have had a chance to
 * modify the decision before logging.
 */
@Slf4j
@RequiredArgsConstructor
public class ReportingDecisionInterceptor implements TracedDecisionInterceptor {

    private final ObjectMapper mapper;
    private final boolean      prettyPrint;
    private final boolean      printTrace;
    private final boolean      printJsonReport;
    private final boolean      printTextReport;

    @Override
    public Integer getPriority() {
        // Execute last to capture all modifications (higher priority = later execution)
        return Integer.MAX_VALUE;
    }

    @Override
    public TracedDecision apply(TracedDecision tracedDecision) {
        if (printTrace) {
            logTrace(tracedDecision);
        }
        if (printJsonReport || printTextReport) {
            val report = ReportBuilderUtil.extractReport(tracedDecision);
            if (printJsonReport) {
                logJsonReport(report);
            }
            if (printTextReport) {
                logTextReport(report);
            }
        }
        return tracedDecision;
    }

    private void logTrace(TracedDecision tracedDecision) {
        val trace  = tracedDecision.originalTrace();
        val prefix = "New Decision (trace) : ";
        multiLineLog(
                prefix + (prettyPrint ? "\n" : "") + ReportTextRenderUtil.prettyPrintValue(trace, prettyPrint, mapper));
    }

    private void logJsonReport(ObjectValue report) {
        val prefix = "New Decision (report): ";
        multiLineLog(prefix + (prettyPrint ? "\n" : "")
                + ReportTextRenderUtil.prettyPrintValue(report, prettyPrint, mapper));
    }

    private void logTextReport(ObjectValue report) {
        multiLineLog(ReportTextRenderUtil.textReport(report, prettyPrint, mapper));
    }

    private void multiLineLog(String message) {
        for (val line : message.replace("\r", "").split("\n")) {
            log.info("{}", line);
        }
    }
}
