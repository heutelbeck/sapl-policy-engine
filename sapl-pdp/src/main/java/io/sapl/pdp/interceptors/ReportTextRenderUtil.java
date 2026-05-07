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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.UndefinedValue;
import io.sapl.compiler.document.AttributeContribution;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;

/**
 * Utility class for rendering VoteReports as human-readable text.
 */
@UtilityClass
public class ReportTextRenderUtil {

    private static final String INDENT_1 = "  ";
    private static final String INDENT_2 = "    ";
    private static final String INDENT_3 = "      ";

    /**
     * Renders a VoteReport as a compact human-readable text string.
     *
     * @param report the report to render
     * @return the formatted text report
     */
    public static String textReport(VoteReport report) {
        val sb = new StringBuilder();
        appendHeader(sb, report);
        appendComponents(sb, report);
        appendDocuments(sb, report.contributingDocuments());
        appendErrors(sb, "PDP Errors:\n", "", report.errors());
        return sb.toString();
    }

    private static void appendHeader(StringBuilder sb, VoteReport report) {
        sb.append("=== PDP Decision ===\n");
        sb.append("Timestamp      : ").append(formatTimestamp(report.timestamp())).append('\n');
        sb.append("Subscription Id: ").append(report.subscriptionId()).append('\n');
        sb.append("Subscription   : ").append(report.authorizationSubscription()).append('\n');
        sb.append("Decision       : ").append(report.decision()).append('\n');
        sb.append("PDP ID         : ").append(report.pdpId()).append('\n');
        if (report.algorithm() != null) {
            sb.append("Algorithm      : ").append(report.algorithm().votingMode()).append('\n');
        }
    }

    /**
     * Formats an {@link Instant} timestamp as ISO-8601 with the system
     * timezone offset.
     */
    static String formatTimestamp(Instant timestamp) {
        return timestamp.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static void appendComponents(StringBuilder sb, VoteReport report) {
        appendIfNotEmpty(sb, "Obligations: ", report.obligations());
        appendIfNotEmpty(sb, "Advice: ", report.advice());
        if (report.resource() != null && !(report.resource() instanceof UndefinedValue)) {
            sb.append("Resource: ").append(report.resource()).append('\n');
        }
    }

    private static void appendIfNotEmpty(StringBuilder sb, String label, Collection<?> items) {
        if (items != null && !items.isEmpty()) {
            sb.append(label).append(items).append('\n');
        }
    }

    private static void appendDocuments(StringBuilder sb, Collection<ContributingDocument> documents) {
        if (documents.isEmpty()) {
            return;
        }
        sb.append("Documents:\n");
        for (var doc : documents) {
            appendDocument(sb, doc);
        }
    }

    private static void appendDocument(StringBuilder sb, ContributingDocument doc) {
        sb.append(INDENT_1).append(doc.name()).append(" -> ").append(doc.decision()).append('\n');
        appendAttributes(sb, doc.attributes());
        appendErrors(sb, INDENT_2 + "Errors:\n", INDENT_3, doc.errors());
    }

    private static void appendAttributes(StringBuilder sb, Collection<AttributeContribution> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        sb.append(INDENT_2).append("Attributes:\n");
        for (val contribution : attributes) {
            sb.append(INDENT_3).append(formatAttributeKey(contribution.key())).append(" = ")
                    .append(contribution.value()).append(" @ ").append(formatTimestamp(contribution.valueTimestamp()))
                    .append('\n');
        }
    }

    private static String formatAttributeKey(SubscriptionKey key) {
        val invocation    = key.invocation();
        val attributeName = "<" + invocation.attributeName() + ">";
        if (invocation.isEnvironmentAttributeInvocation()) {
            return attributeName;
        }
        return invocation.entity() + "." + attributeName;
    }

    private static void appendErrors(StringBuilder sb, String header, String indent, Collection<ErrorValue> errors) {
        if (errors.isEmpty()) {
            return;
        }
        sb.append(header);
        for (var err : errors) {
            sb.append(indent).append(INDENT_1).append("- ").append(err.message()).append('\n');
        }
    }

}
