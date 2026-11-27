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
package io.sapl.vaadin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.html.Span;

import lombok.val;
import tools.jackson.databind.node.JsonNodeFactory;

@DisplayName("Validation status display")
class ValidationStatusDisplayTests {

    private static final String LONG_MESSAGE = "Wrong operator '='. '=' only defines variables. To compare two values, use '=='.";

    @Test
    @DisplayName("a single error shows its whole message in the summary line, which shortens it to the width it has")
    void whenOneErrorIsReportedThenTheSummaryHoldsTheWholeMessageAndShortensItToItsWidth() {
        val display = new ValidationStatusDisplay();

        display.setIssues(List.of(errorWith(LONG_MESSAGE)));

        val summary = summaryTextOf(display);
        assertThat(summary).satisfies(text -> {
            assertThat(text.getText()).isEqualTo("1 error: " + LONG_MESSAGE);
            assertThat(text.getStyle().get("text-overflow")).isEqualTo("ellipsis");
            assertThat(text.getStyle().get("white-space")).isEqualTo("nowrap");
            assertThat(text.getStyle().get("overflow")).isEqualTo("hidden");
            assertThat(text.getStyle().get("min-width")).isEqualTo("0");
        });
    }

    private static Issue errorWith(String description) {
        val json = JsonNodeFactory.instance.objectNode();
        json.put("description", description);
        json.put("severity", "ERROR");
        return new Issue(json);
    }

    // The summary line is the first row of the display: the status icon, the
    // summary text and the indicator for the details.
    private static Span summaryTextOf(ValidationStatusDisplay display) {
        val summaryRow = display.getChildren().findFirst().orElseThrow();
        return summaryRow.getChildren().filter(Span.class::isInstance).map(Span.class::cast).findFirst().orElseThrow();
    }
}
