/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.server.ce.ui.views.setup;

import java.io.IOException;
import java.util.EnumSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import io.sapl.server.ce.model.setup.ApplicationConfigService;
import io.sapl.server.ce.model.setup.LoggingLevel;
import io.sapl.server.ce.model.setup.condition.SetupNotFinishedCondition;
import io.sapl.server.ce.ui.utils.ConfirmUtils;
import io.sapl.server.ce.ui.utils.ErrorComponentUtils;
import io.sapl.server.ce.ui.views.SetupLayout;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

@AnonymousAllowed
@PageTitle("Logging Setup")
@Route(value = LoggingSetupView.ROUTE, layout = SetupLayout.class)
@Conditional(SetupNotFinishedCondition.class)
public class LoggingSetupView extends VerticalLayout {

    private static final long serialVersionUID = 6526353409115754797L;

    public static final String ROUTE = "/setup/logging";

    private transient ApplicationConfigService applicationConfigService;
    private transient HttpServletRequest       httpServletRequest;

    public LoggingSetupView(@Autowired ApplicationConfigService applicationConfigService,
            @Autowired HttpServletRequest httpServletRequest) {
        this.applicationConfigService = applicationConfigService;
        this.httpServletRequest       = httpServletRequest;
    }

    @PostConstruct
    private void init() {
        if (!httpServletRequest.isSecure()) {
            add(ErrorComponentUtils.getErrorDiv(SetupLayout.INSECURE_CONNECTION_MESSAGE));
        }
        add(getLayout());
    }

    private Component getLayout() {
        Button saveConfig = new Button("Save Logging Settings");
        saveConfig.setEnabled(applicationConfigService.getApiAuthenticationConfig().isValidConfig());
        saveConfig.addClickListener(e -> persistLoggingConfig());
        saveConfig.setEnabled(true);

        Select<LoggingLevel> saplLoggingLevel = new Select<>();
        saplLoggingLevel.setLabel("SAPL logging level");
        saplLoggingLevel.setItems(EnumSet.allOf(LoggingLevel.class));
        saplLoggingLevel.setValue(applicationConfigService.getLoggingConfig().getSaplLoggingLevel());
        saplLoggingLevel.setHelperText(saplLoggingLevel.getValue().getDescription());
        saplLoggingLevel.addValueChangeListener(e -> {
            applicationConfigService.getLoggingConfig().setSaplLoggingLevel(e.getValue());
            saplLoggingLevel.setHelperText(e.getValue().getDescription());
        });

        Select<LoggingLevel> saplServerLoggingLevel = new Select<>();
        saplServerLoggingLevel.setLabel("SAPL Server CE logging level");
        saplServerLoggingLevel.setItems(EnumSet.allOf(LoggingLevel.class));
        saplServerLoggingLevel.setValue(applicationConfigService.getLoggingConfig().getSaplServerLoggingLevel());
        saplServerLoggingLevel.setHelperText(saplServerLoggingLevel.getValue().getDescription());
        saplServerLoggingLevel.addValueChangeListener(e -> {
            applicationConfigService.getLoggingConfig().setSaplServerLoggingLevel(e.getValue());
            saplServerLoggingLevel.setHelperText(e.getValue().getDescription());
        });

        Select<LoggingLevel> springLoggingLevel = new Select<>();
        springLoggingLevel.setLabel("Spring logging level");
        springLoggingLevel.setItems(EnumSet.allOf(LoggingLevel.class));
        springLoggingLevel.setValue(applicationConfigService.getLoggingConfig().getSpringLoggingLevel());
        springLoggingLevel.setHelperText(springLoggingLevel.getValue().getDescription());
        springLoggingLevel.addValueChangeListener(e -> {
            applicationConfigService.getLoggingConfig().setSpringLoggingLevel(e.getValue());
            springLoggingLevel.setHelperText(e.getValue().getDescription());
        });

        var pInfo = new Paragraph(
                "To enhance the performance of the SAPL Server CE, consider adjusting the logging level to "
                        + "`WARN`. This will reduce the number of log messages and improve latency. However, "
                        + "it is important to note that decisions made by the PDP will still be "
                        + "logged with the `INFO` level.");

        var pdpLogInfo = new H3("The following options enable or disable different levels of logging for decisions.");

        HorizontalLayout printTraceLayout = new HorizontalLayout();
        printTraceLayout.setPadding(true);
        printTraceLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
        var printTrace = new Checkbox("print-trace");
        printTrace.setWidth("350px");
        printTrace.setValue(applicationConfigService.getLoggingConfig().isPrintTrace());
        printTrace.addValueChangeListener(e -> applicationConfigService.getLoggingConfig().setPrintTrace(e.getValue()));
        printTraceLayout.add(printTrace);
        printTraceLayout.add(new Text("This is the most fine-grained explanation of a decision made "
                + "by the PDP each individual calculation step is documented. "
                + "The trace is in JSON format and may become very large. "
                + "Recommended only as a last resort for troubleshooting."));

        HorizontalLayout printJsonReportLayout = new HorizontalLayout();
        printJsonReportLayout.setPadding(true);
        printJsonReportLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
        var printJsonReport = new Checkbox("print-json-report");
        printJsonReport.setWidth("400px");
        printJsonReport.setValue(applicationConfigService.getLoggingConfig().isPrintJsonReport());
        printJsonReport.addValueChangeListener(
                e -> applicationConfigService.getLoggingConfig().setPrintJsonReport(e.getValue()));
        printJsonReportLayout.add(printJsonReport);
        printJsonReportLayout.add(new Text("This is a JSON report summarizing the applied algorithms "
                + "and results of each evaluated policy (set) in the "
                + "decision-making process. It includes lists of all errors and values "
                + "of policy information point attributes encountered "
                + "during the evaluation of each policy (set)."));

        HorizontalLayout printTextReportLayout = new HorizontalLayout();
        printTextReportLayout.setPadding(true);
        printTextReportLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
        var printTextReport = new Checkbox("print-text-report");
        printTextReport.setWidth("250px");
        printTextReport.setValue(applicationConfigService.getLoggingConfig().isPrintTextReport());
        printTextReport.addValueChangeListener(
                e -> applicationConfigService.getLoggingConfig().setPrintTextReport(e.getValue()));
        printTextReportLayout.add(printTextReport);
        printTextReportLayout.add(new Text("This will log a human-readable textual report based on the "
                + "same data as the 'print-json-report' option generates."));

        HorizontalLayout prettyPrintReportsLayout = new HorizontalLayout();
        prettyPrintReportsLayout.setPadding(true);
        prettyPrintReportsLayout.setAlignItems(FlexComponent.Alignment.BASELINE);
        var prettyPrintReports = new Checkbox("pretty-print-reports");
        prettyPrintReports.setWidth("250px");
        prettyPrintReports.setValue(applicationConfigService.getLoggingConfig().isPrettyPrintReports());
        prettyPrintReports.addValueChangeListener(
                e -> applicationConfigService.getLoggingConfig().setPrettyPrintReports(e.getValue()));
        prettyPrintReportsLayout.add(prettyPrintReports);
        prettyPrintReportsLayout.add(new Text("This option can enable formatting of JSON data while "
                + "printing JSON during reporting and tracing."));

        var loggingLayout = new FormLayout(saplLoggingLevel, saplServerLoggingLevel, springLoggingLevel, pInfo,
                pdpLogInfo, printTraceLayout, printJsonReportLayout, printTextReportLayout, prettyPrintReportsLayout,
                saveConfig);
        loggingLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1, FormLayout.ResponsiveStep.LabelsPosition.TOP),
                new FormLayout.ResponsiveStep("490px", 2, FormLayout.ResponsiveStep.LabelsPosition.TOP));
        loggingLayout.setColspan(pInfo, 2);
        loggingLayout.setColspan(pdpLogInfo, 2);
        loggingLayout.setColspan(printTraceLayout, 2);
        loggingLayout.setColspan(printJsonReportLayout, 2);
        loggingLayout.setColspan(printTextReportLayout, 2);
        loggingLayout.setColspan(prettyPrintReportsLayout, 2);
        loggingLayout.setColspan(saveConfig, 2);

        return loggingLayout;
    }

    private void persistLoggingConfig() {
        try {
            applicationConfigService.persistLoggingConfig();
            ConfirmUtils.inform("saved", "Logging setup successfully saved");
        } catch (IOException ioe) {
            ConfirmUtils.inform("IO-Error",
                    "Error while writing application.yml-File. Please make sure that the file is not in use and can be written. Otherwise configure the application.yml-file manually. Error: "
                            + ioe.getLocalizedMessage());
        }
    }
}
