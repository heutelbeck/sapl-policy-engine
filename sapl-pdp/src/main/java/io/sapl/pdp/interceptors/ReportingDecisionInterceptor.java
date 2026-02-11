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
package io.sapl.pdp.interceptors;

import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.compiler.document.TimestampedVote;
import io.sapl.compiler.document.Vote;
import io.sapl.pdp.VoteInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Interceptor that logs authorization decisions for debugging and auditing.
 * <p>
 * Supports multiple output formats:
 * <ul>
 * <li>Full trace - the complete trace structure as JSON via
 * {@link Vote#toTrace()}</li>
 * <li>JSON report - a condensed JSON report with essential information</li>
 * <li>Text report - human-readable formatted output for console/logs</li>
 * </ul>
 * <p>
 * This interceptor executes with the lowest priority (last) to ensure all other
 * interceptors have had a chance to modify the decision before logging.
 */
@Slf4j
@RequiredArgsConstructor
public class ReportingDecisionInterceptor implements VoteInterceptor {

    private final boolean prettyPrint;
    private final boolean printTrace;
    private final boolean printJsonReport;
    private final boolean printTextReport;

    @Override
    public int priority() {
        return Integer.MAX_VALUE;
    }

    @Override
    public void intercept(TimestampedVote vote, String subscriptionId,
            AuthorizationSubscription authorizationSubscription) {
        if (printTrace) {
            logTrace(vote);
        }
        if (printJsonReport || printTextReport) {
            val report = ReportBuilderUtil.extractReport(vote.vote(), vote.timestamp(), subscriptionId,
                    authorizationSubscription);
            if (printJsonReport) {
                logJsonReport(report);
            }
            if (printTextReport) {
                logTextReport(report);
            }
        }
    }

    private void logTrace(TimestampedVote vote) {
        val trace     = vote.vote().toTrace();
        val output    = prettyPrint ? ValueJsonMarshaller.toPrettyString(trace) : trace.toString();
        val timestamp = ReportTextRenderUtil.formatTimestamp(vote.timestamp());
        multiLineLog(timestamp + ": New Decision (trace): " + output);
    }

    private void logJsonReport(VoteReport report) {
        val reportValue = ReportBuilderUtil.toObjectValue(report);
        val output      = prettyPrint ? ValueJsonMarshaller.toPrettyString(reportValue) : reportValue.toString();
        val timestamp   = ReportTextRenderUtil.formatTimestamp(report.timestamp());
        multiLineLog(timestamp + ": New Decision (report): " + output);
    }

    private void logTextReport(VoteReport report) {
        multiLineLog(ReportTextRenderUtil.textReport(report));
    }

    private void multiLineLog(String message) {
        for (val line : message.replace("\r", "").split("\n")) {
            log.info("{}", line);
        }
    }
}
