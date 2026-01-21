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

import io.sapl.api.model.UndefinedValue;
import lombok.experimental.UtilityClass;

/**
 * Utility class for rendering VoteReports as human-readable text.
 */
@UtilityClass
public class ReportTextRenderUtil {

    /**
     * Renders a VoteReport as a compact human-readable text string.
     *
     * @param report the report to render
     * @return the formatted text report
     */
    public static String textReport(VoteReport report) {
        var sb = new StringBuilder("--- PDP Decision ---\n");
        sb.append("Decision : ").append(report.decision()).append('\n');
        sb.append("PDP ID   : ").append(report.pdpId()).append('\n');

        if (report.algorithm() != null) {
            sb.append("Algorithm: ").append(report.algorithm().votingMode()).append('\n');
        }

        if (report.obligations() != null && !report.obligations().isEmpty()) {
            sb.append("Obligations: ").append(report.obligations()).append('\n');
        }
        if (report.advice() != null && !report.advice().isEmpty()) {
            sb.append("Advice: ").append(report.advice()).append('\n');
        }
        if (report.resource() != null && !(report.resource() instanceof UndefinedValue)) {
            sb.append("Resource: ").append(report.resource()).append('\n');
        }

        if (!report.contributingDocuments().isEmpty()) {
            sb.append("Documents:\n");
            for (var doc : report.contributingDocuments()) {
                sb.append("  ").append(doc.name()).append(" -> ").append(doc.decision()).append('\n');
            }
        }

        if (!report.errors().isEmpty()) {
            sb.append("Errors:\n");
            for (var err : report.errors()) {
                sb.append("  - ").append(err.message()).append('\n');
            }
        }

        return sb.toString();
    }
}
