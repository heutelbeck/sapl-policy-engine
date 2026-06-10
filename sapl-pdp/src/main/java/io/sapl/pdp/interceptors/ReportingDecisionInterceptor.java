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
import io.sapl.api.pdp.DecisionInterceptor;
import io.sapl.api.pdp.SubscriptionLifecycleListener;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.compiler.document.TracedVote;
import io.sapl.compiler.document.Vote;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Instant;

/**
 * Logs authorization decisions for debugging and auditing. Implements both
 * {@link DecisionInterceptor} (renders the decision and trace) and
 * {@link SubscriptionLifecycleListener} (logs subscribe/unsubscribe events).
 * <p>
 * Output formats are independently toggleable:
 * <ul>
 * <li>Full trace - the complete trace structure as JSON via
 * {@link Vote#toTrace()}</li>
 * <li>JSON report - a condensed JSON report with essential information</li>
 * <li>Text report - human-readable formatted output for console/logs</li>
 * </ul>
 * <p>
 * The rich JSON / text reports require dependency and snapshot data only
 * present on engine-internal {@link TracedVote}; for non-engine
 * {@link TracedDecision} implementations the rich render is skipped and only
 * the trace is logged when enabled.
 */
@Slf4j
@RequiredArgsConstructor
public class ReportingDecisionInterceptor implements DecisionInterceptor, SubscriptionLifecycleListener {

    private final boolean prettyPrint;
    private final boolean printTrace;
    private final boolean printJsonReport;
    private final boolean printTextReport;
    private final boolean printSubscriptionEvents;
    private final boolean printUnsubscriptionEvents;

    @Override
    public void onDecision(TracedDecision decision, Instant timestamp, String subscriptionId,
            AuthorizationSubscription authorizationSubscription) {
        if (printTrace) {
            logTrace(decision, timestamp);
        }
        if ((printJsonReport || printTextReport) && decision instanceof TracedVote tracedVote) {
            val report = ReportBuilderUtil.extractReport(tracedVote, subscriptionId, authorizationSubscription);
            if (printJsonReport) {
                logJsonReport(report);
            }
            if (printTextReport) {
                logTextReport(report);
            }
        }
    }

    @Override
    public void onSubscribe(String subscriptionId, AuthorizationSubscription authorizationSubscription, String pdpId) {
        if (printSubscriptionEvents) {
            log.info("Subscription [{}] (pdp={}): {}", subscriptionId, pdpId, authorizationSubscription);
        }
    }

    @Override
    public void onUnsubscribe(String subscriptionId) {
        if (printUnsubscriptionEvents) {
            log.info("Unsubscription [{}]", subscriptionId);
        }
    }

    private void logTrace(TracedDecision decision, Instant timestamp) {
        val trace           = decision.trace();
        val output          = prettyPrint ? ValueJsonMarshaller.toPrettyString(trace) : trace.toString();
        val timestampString = ReportTextRenderUtil.formatTimestamp(timestamp);
        multiLineLog(timestampString + ": New Decision (trace): " + output);
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
