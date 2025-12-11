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
package io.sapl.vaadin;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.sapl.api.SaplVersion;
import lombok.Getter;
import org.eclipse.xtext.diagnostics.Severity;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;

/**
 * A component that displays validation status with support for multiple errors.
 * Shows a summary line with icon, expandable on click/hover to show all errors.
 */
public class ValidationStatusDisplay extends VerticalLayout {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String COLOR_GREEN  = "var(--lumo-success-color, green)";
    private static final String COLOR_RED    = "var(--lumo-error-color, red)";
    private static final String COLOR_ORANGE = "var(--lumo-warning-color, orange)";

    private final HorizontalLayout summaryRow;
    private final Icon             statusIcon;
    private final Span             summaryText;
    private final Span             expandIndicator;
    private final Div              detailsPanel;

    @Getter
    private boolean expanded = false;

    private List<Issue> currentIssues = new ArrayList<>();

    public ValidationStatusDisplay() {
        setPadding(false);
        setSpacing(false);
        setWidthFull();
        getStyle().set("background", "var(--lumo-contrast-5pct)").set("border-radius", "var(--lumo-border-radius-s)")
                .set("font-family", "var(--lumo-font-family)").set("font-size", "var(--lumo-font-size-s)");

        // Summary row
        summaryRow = new HorizontalLayout();
        summaryRow.setWidthFull();
        summaryRow.setPadding(false);
        summaryRow.setSpacing(true);
        summaryRow.setAlignItems(Alignment.CENTER);
        summaryRow.getStyle().set("padding", "var(--lumo-space-xs) var(--lumo-space-s)").set("cursor", "pointer");

        statusIcon = VaadinIcon.CHECK.create();
        statusIcon.setSize("16px");
        statusIcon.setColor(COLOR_GREEN);

        summaryText = new Span("OK");
        summaryText.getStyle().set("flex-grow", "1");

        expandIndicator = new Span("");
        expandIndicator.getStyle().set("color", "var(--lumo-tertiary-text-color)");

        summaryRow.add(statusIcon, summaryText, expandIndicator);
        summaryRow.addClickListener(event -> toggleExpanded());

        // Details panel (hidden by default)
        detailsPanel = new Div();
        detailsPanel.setWidthFull();
        detailsPanel.getStyle().set("padding", "0 var(--lumo-space-s) var(--lumo-space-xs)")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)").set("max-height", "150px")
                .set("overflow-y", "auto");
        detailsPanel.setVisible(false);

        add(summaryRow, detailsPanel);

        // Show success state initially
        showSuccess();
    }

    /**
     * Updates the display with the given issues.
     *
     * @param issues
     * list of validation issues (may be empty for success)
     */
    public void setIssues(List<Issue> issues) {
        this.currentIssues = issues != null ? new ArrayList<>(issues) : new ArrayList<>();

        var errorCount = currentIssues.stream().filter(issue -> Severity.ERROR == issue.getSeverity()).count();

        var warningCount = currentIssues.stream().filter(issue -> Severity.WARNING == issue.getSeverity()).count();

        if (errorCount > 0) {
            showErrors(errorCount, warningCount);
        } else if (warningCount > 0) {
            showWarnings(warningCount);
        } else {
            showSuccess();
        }

        updateDetailsPanel();
        updateExpandIndicator();
    }

    /**
     * Updates the display with the given issues array.
     *
     * @param issues
     * array of validation issues
     */
    public void setIssues(Issue[] issues) {
        setIssues(issues != null ? List.of(issues) : List.of());
    }

    private void showSuccess() {
        statusIcon.getElement().setAttribute("icon", "vaadin:check");
        statusIcon.setColor(COLOR_GREEN);
        summaryText.setText("OK");
        setExpanded(false);
    }

    private void showErrors(long errorCount, long warningCount) {
        statusIcon.getElement().setAttribute("icon", "vaadin:close-circle");
        statusIcon.setColor(COLOR_RED);

        var message = new StringBuilder();
        message.append(errorCount).append(" error").append(errorCount > 1 ? "s" : "");
        if (warningCount > 0) {
            message.append(", ").append(warningCount).append(" warning").append(warningCount > 1 ? "s" : "");
        }

        // Add first error preview if single error
        if (errorCount == 1 && warningCount == 0) {
            var firstError = currentIssues.stream().filter(i -> Severity.ERROR == i.getSeverity()).findFirst();
            firstError.ifPresent(issue -> {
                var desc = issue.getDescription();
                if (desc != null && !desc.isBlank()) {
                    message.append(": ").append(truncate(desc, 60));
                }
            });
        }

        summaryText.setText(message.toString());
    }

    private void showWarnings(long warningCount) {
        statusIcon.getElement().setAttribute("icon", "vaadin:warning");
        statusIcon.setColor(COLOR_ORANGE);

        var message = new StringBuilder();
        message.append(warningCount).append(" warning").append(warningCount > 1 ? "s" : "");

        // Add first warning preview if single warning
        if (warningCount == 1) {
            var firstWarning = currentIssues.stream().filter(i -> Severity.WARNING == i.getSeverity()).findFirst();
            firstWarning.ifPresent(issue -> {
                var desc = issue.getDescription();
                if (desc != null && !desc.isBlank()) {
                    message.append(": ").append(truncate(desc, 60));
                }
            });
        }

        summaryText.setText(message.toString());
    }

    private void updateDetailsPanel() {
        detailsPanel.removeAll();

        if (currentIssues.isEmpty()) {
            return;
        }

        for (var issue : currentIssues) {
            var issueDiv = new Div();
            issueDiv.getStyle().set("padding", "var(--lumo-space-xs) 0").set("border-bottom",
                    "1px solid var(--lumo-contrast-5pct)");

            var icon = createIssueIcon(issue.getSeverity());
            icon.setSize("14px");
            icon.getStyle().set("margin-right", "var(--lumo-space-xs)");

            var locationText = formatLocation(issue);
            var descText     = issue.getDescription() != null ? issue.getDescription() : "Unknown issue";

            var content = new HorizontalLayout();
            content.setPadding(false);
            content.setSpacing(false);
            content.setAlignItems(Alignment.START);

            var textSpan = new Span();
            if (!locationText.isEmpty()) {
                var locationSpan = new Span(locationText);
                locationSpan.getStyle().set("color", "var(--lumo-tertiary-text-color)").set("margin-right",
                        "var(--lumo-space-xs)");
                textSpan.add(locationSpan);
            }
            textSpan.add(new Span(descText));

            content.add(icon, textSpan);
            issueDiv.add(content);
            detailsPanel.add(issueDiv);
        }
    }

    private void updateExpandIndicator() {
        if (currentIssues.isEmpty()) {
            expandIndicator.setText("");
        } else if (currentIssues.size() > 1 || expanded) {
            expandIndicator.setText(expanded ? "▲" : "▼");
        } else {
            expandIndicator.setText("▼");
        }
    }

    private void toggleExpanded() {
        if (!currentIssues.isEmpty()) {
            setExpanded(!expanded);
        }
    }

    /**
     * Sets whether the details panel is expanded.
     *
     * @param expanded
     * true to show details, false to hide
     */
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
        detailsPanel.setVisible(expanded && !currentIssues.isEmpty());
        updateExpandIndicator();
    }

    private Icon createIssueIcon(Severity severity) {
        return switch (severity) {
        case ERROR   -> {
            var icon = VaadinIcon.CLOSE_CIRCLE.create();
            icon.setColor(COLOR_RED);
            yield icon;
        }
        case WARNING -> {
            var icon = VaadinIcon.WARNING.create();
            icon.setColor(COLOR_ORANGE);
            yield icon;
        }
        default      -> {
            var icon = VaadinIcon.INFO_CIRCLE.create();
            icon.setColor("var(--lumo-primary-color)");
            yield icon;
        }
        };
    }

    private String formatLocation(Issue issue) {
        var line   = issue.getLine();
        var column = issue.getColumn();

        if (line != null && column != null) {
            return "Line " + line + ":" + column;
        } else if (line != null) {
            return "Line " + line;
        }
        return "";
    }

    private String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
