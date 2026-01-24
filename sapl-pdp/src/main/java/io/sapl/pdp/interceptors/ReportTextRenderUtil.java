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

import java.util.Collection;

import io.sapl.api.model.AttributeRecord;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.UndefinedValue;
import lombok.experimental.UtilityClass;

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
        var sb = new StringBuilder();
        appendHeader(sb, report);
        appendComponents(sb, report);
        appendDocuments(sb, report.contributingDocuments());
        appendErrors(sb, "PDP Errors:\n", "", report.errors());
        return sb.toString();
    }

    private static void appendHeader(StringBuilder sb, VoteReport report) {
        sb.append("--- PDP Decision ---\n");
        sb.append("Decision : ").append(report.decision()).append('\n');
        sb.append("PDP ID   : ").append(report.pdpId()).append('\n');
        if (report.algorithm() != null) {
            sb.append("Algorithm: ").append(report.algorithm().votingMode()).append('\n');
        }
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

    private static void appendAttributes(StringBuilder sb, Collection<AttributeRecord> attributes) {
        if (attributes.isEmpty()) {
            return;
        }
        sb.append(INDENT_2).append("Attributes:\n");
        for (var attr : attributes) {
            appendAttribute(sb, attr);
        }
    }

    private static void appendAttribute(StringBuilder sb, AttributeRecord attr) {
        var invocation = attr.invocation();
        var name       = invocation.attributeName();
        var entity     = invocation.entity();
        sb.append(INDENT_3);
        if (entity != null) {
            sb.append(entity).append(".<").append(name).append("> = ");
        } else {
            sb.append('<').append(name).append("> = ");
        }
        sb.append(attr.attributeValue()).append(" @ ").append(attr.retrievedAt()).append('\n');
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
