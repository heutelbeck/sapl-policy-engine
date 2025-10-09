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
package io.sapl.playground;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout.Orientation;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.selection.SelectionEvent;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.pdp.interceptors.ErrorReportGenerator;
import io.sapl.pdp.interceptors.ErrorReportGenerator.OutputFormat;
import io.sapl.pdp.interceptors.ReportBuilderUtil;
import io.sapl.pdp.interceptors.ReportTextRenderUtil;
import io.sapl.vaadin.*;
import lombok.val;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

@JsModule("./copytoclipboard.js")
//@PageTitle("SAPL Playground")
//@Route(value = "", layout = MainLayout.class)
public class PlaygroundViewOld extends VerticalLayout {

    private static final int    MAX_BUFFER_SIZE      = 50;
    private static final int    DEFAULT_BUFFER_SIZE  = 10;
    private static final String DEFAULT_SUBSCRIPTION = """
            {
               "subject"     : "",
               "action"      : "",
               "resource"    : "",
               "environment" : null
            }
            """;

    private static final String DEFAULT_POLICY = """
            policy "compartmentalize read access by department"
            permit
                resource.type == "patient_record" & action == "read"
            where
                subject.role == "doctor";
                resource.department == subject.department;
            """;

    private final ObjectMapper mapper;

    private final Icon                       validIcon   = VaadinIcon.CHECK.create();
    private final Icon                       invalidIcon = VaadinIcon.CLOSE_CIRCLE.create();
    private Button                           playStopButton;
    private Button                           scrollLockButton;
    private IntegerField                     bufferSizeField;
    private SaplEditor                       saplEditor;
    private JsonEditor                       decisionsArea;
    private JsonEditor                       subscriptionEditor;
    private TextField                        subscriptionValidationField;
    private DecisionsGrid                    decisionsGrid;
    private JsonEditor                       decisionsJsonReportArea;
    private JsonEditor                       decisionsJsonTraceArea;
    private Div                              errorsArea;
    private TextArea                         reportArea;
    private GridListDataView<TracedDecision> decisionsView;
    private Checkbox                         clearOnNewSubscriptionCheckBox;
    private Checkbox                         showOnlyDistinctDecisionsCheckBox;

    private transient ArrayList<TracedDecision> decisions;
    private boolean                             scrollLock;
    private String                              errorReport;

    public PlaygroundViewOld(ObjectMapper mapper) {
        this.mapper = mapper;
        decisions   = new ArrayList<>();
        buildLayout();
        wireUpEventListeners();
        initializeComponentsValues();
        deactivateScrollLock();
        startSubscription();
//        addAttachListener(attachEvent -> bind());
//        addDetachListener(detachEvent -> playgroundViewModel.unbind());
    }

    private void wireUpEventListeners() {
        bufferSizeField.addValueChangeListener(e -> updateBufferSize(bufferSizeField.getValue()));
        decisionsGrid.addSelectionListener(this::decisionSelected);
        subscriptionEditor.addDocumentChangedListener(this::handleDocumentChanged);
        scrollLockButton.addClickListener(e -> {
            if (scrollLock) {
                deactivateScrollLock();
            } else {
                activateScrollLock();
            }
        });

        playStopButton.addClickListener(e -> {
//            if (playgroundViewModel.isSubscribed()) {
//                stopSubscription();
//            } else {
//                startSubscription();
//            }
        });
//        showOnlyDistinctDecisionsCheckBox
//                .addValueChangeListener(e -> playgroundViewModel.updateDistinctOnly(e.getValue()));
    }

    private void bind() {
    }

    private void initializeComponentsValues() {
        decisions.clear();
        decisionsView.refreshAll();
        clearDetailsView();
        subscriptionEditor.setDocument(DEFAULT_SUBSCRIPTION);
    }

    private void clearDetailsView() {
        decisionsArea.setDocument("");
        setErrorAreaContent("", "");
        decisionsJsonTraceArea.setDocument("");
        decisionsJsonReportArea.setDocument("");
        reportArea.setValue("");
    }

    private void decisionSelected(SelectionEvent<Grid<TracedDecision>, TracedDecision> selection) {
        val selectedItem = selection.getFirstSelectedItem();
        updateDetailsView(selectedItem);
    }

    private void updateDetailsView(Optional<TracedDecision> maybeTracedDecision) {
        if (maybeTracedDecision.isEmpty()) {
            clearDetailsView();
        } else {
            val tracedDecision = maybeTracedDecision.get();
            try {
                decisionsArea.setDocument(mapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(tracedDecision.getAuthorizationDecision()));
            } catch (JsonProcessingException e) {
                decisionsArea.setDocument("Error reading decision:\n" + tracedDecision);
            }
            val trace = tracedDecision.getTrace();
            decisionsJsonTraceArea.setDocument(trace.toPrettyString());
            val report = ReportBuilderUtil.reduceTraceToReport(trace);
            decisionsJsonReportArea.setDocument(report.toPrettyString());
            reportArea.setValue(ReportTextRenderUtil.textReport(report, false, mapper));

            setErrorAreaContentFromErrors(tracedDecision.getErrorsFromTrace());
        }
    }

    private void setErrorAreaContentFromErrors(Collection<Val> errors) {
        val html      = buildHtmlAggregatedErrorReport(errors);
        val plainText = buildPlainTextAggregatedErrorReport(errors);
        setErrorAreaContent(html, plainText);
    }

    private void setErrorAreaContent(String html, String plainText) {
        errorsArea.getElement().setProperty("innerHTML", html);
        errorReport = plainText;
    }

    private String buildHtmlAggregatedErrorReport(Collection<Val> errors) {
        if (errors.isEmpty()) {
            return "No Errors";
        }
        val sb = new StringBuilder();
        for (var error : errors) {
            sb.append(ErrorReportGenerator.errorReport(error, true, OutputFormat.HTML));
        }
        return sb.toString();
    }

    private String buildPlainTextAggregatedErrorReport(Collection<Val> errors) {
        if (errors.isEmpty()) {
            return "No Errors";
        }
        val sb = new StringBuilder();
        for (var error : errors) {
            sb.append(ErrorReportGenerator.errorReport(error, true, OutputFormat.PLAIN_TEXT));
        }
        return sb.toString();

    }

    final void handleDocumentChanged(DocumentChangedEvent event) {
        if (Boolean.TRUE.equals(clearOnNewSubscriptionCheckBox.getValue())) {
            decisions.clear();
            decisionsView.refreshAll();
        }
//        playgroundViewModel.updateSubscription(subscriptionEditor.getDocument());
    }

    private void updateBufferSize(int size) {
        while (decisions.size() > size) {
            decisions.remove(0);
        }
        decisionsView.refreshAll();
    }

    final void handleNewDecision(TracedDecision decision) {
        decisions.add(decision);
        if (decisions.size() > bufferSizeField.getValue()) {
            decisions.remove(0);
        }
        decisionsView.refreshAll();
        if (scrollLock) {
            decisionsGrid.scrollToEnd();
        }
    }

    final void handleSubscriptionValidationUpdate(boolean isValid, String message) {
        subscriptionValidationField.setPrefixComponent(isValid ? validIcon : invalidIcon);
        subscriptionValidationField.setValue(message);
    }

    private void stopSubscription() {
        playStopButton.setIcon(VaadinIcon.PLAY.create());
        playStopButton.setAriaLabel("Subscribe");
        playStopButton.setTooltipText("Start subscribing with given authorization subscription.");
    }

    private void startSubscription() {
        if (Boolean.TRUE.equals(clearOnNewSubscriptionCheckBox.getValue())) {
            decisions.clear();
            decisionsView.refreshAll();
        }
        playStopButton.setIcon(VaadinIcon.STOP.create());
        playStopButton.setAriaLabel("Unsubscribe");
        playStopButton.setTooltipText("Stop Subscribing.");
    }

    private void activateScrollLock() {
        scrollLock = true;
        scrollLockButton.setIcon(VaadinIcon.UNLOCK.create());
        scrollLockButton.setAriaLabel("Scroll Lock");
        scrollLockButton
                .setTooltipText("Scroll Lock inactive. Click to stop automatically scrolling to last decision made.");
    }

    private void deactivateScrollLock() {
        scrollLock = false;
        scrollLockButton.setIcon(VaadinIcon.LOCK.create());
        scrollLockButton.setAriaLabel("Scroll Lock");
        scrollLockButton
                .setTooltipText("Scroll Lock active. Click to start automatically scrolling to last decision made.");
    }

    private void copyToClipboard(String content) {
        UI.getCurrent().getPage().executeJs("window.copyToClipboard($0)", content);
        Notification notification = Notification.show("Content copied to clipboard.");
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
    }

    private void buildLayout() {
        setSpacing(false);
        setSizeFull();
        setJustifyContentMode(JustifyContentMode.START);
        setDefaultHorizontalComponentAlignment(Alignment.START);
        getStyle().set("text-align", "left");
        add(playground());
        validIcon.setColor("green");
        invalidIcon.setColor("red");
        handleSubscriptionValidationUpdate(false, "");
    }

    private Component playground() {
        val mainLayout = new VerticalLayout();
        mainLayout.setSizeFull();
        mainLayout.setMargin(false);
        mainLayout.setPadding(false);

        val playgroundLayout = new SplitLayout(playgroundLeft(), playgoundRight());
        playgroundLayout.setSizeFull();
        playgroundLayout.setSplitterPosition(30.0D);

        mainLayout.add(playgroundLayout);
        return mainLayout;
    }

    private Component playgroundLeft() {
        val layout = new VerticalLayout();
        layout.setSizeFull();

        val saplHeader       = new H3("SAPL Policy or Policy Set");
        val saplEditorConfig = new SaplEditorConfiguration();
        saplEditorConfig.setHasLineNumbers(true);
        saplEditorConfig.setTextUpdateDelay(500);
        saplEditorConfig.setDarkTheme(true);
        saplEditor = new SaplEditor(saplEditorConfig);

        val subscriptionHeader       = new H3("Authorization Subscription");
        val subscriptionEditorConfig = new JsonEditorConfiguration();
        subscriptionEditorConfig.setHasLineNumbers(true);
        subscriptionEditorConfig.setTextUpdateDelay(500);
        subscriptionEditorConfig.setDarkTheme(true);
        subscriptionEditor = new JsonEditor(subscriptionEditorConfig);

        val bottomLayout = new HorizontalLayout();
        bottomLayout.setWidthFull();

        val prettyPrintButton = new Button(VaadinIcon.CURLY_BRACKETS.create());
        prettyPrintButton.setAriaLabel("Format JSON");
        prettyPrintButton.setTooltipText("Format JSON.");
        prettyPrintButton.addClickListener(this::handleFormatJsonButtonClick);

        subscriptionValidationField = new TextField();
        subscriptionValidationField.setReadOnly(true);
        subscriptionValidationField.setWidthFull();

        bottomLayout.add(prettyPrintButton, subscriptionValidationField);

        layout.add(saplHeader, saplEditor, subscriptionHeader, subscriptionEditor, bottomLayout);
        return layout;
    }

    private void handleFormatJsonButtonClick(ClickEvent<Button> event) {
        val jsonString = subscriptionEditor.getDocument();
        try {
            val json = mapper.readTree(jsonString);
            if (json instanceof MissingNode) {
                Notification.show("Cannot format invalid JSON.");
                return;
            }
            val formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            subscriptionEditor.setDocument(formattedJson);
        } catch (JsonProcessingException e) {
            Notification.show("Cannot format invalid JSON.");
        }
    }

    private Component playgoundRight() {
        val layout = new VerticalLayout();
        layout.setSizeFull();
        val header = new H3("Decisions");
        decisionsGrid = new DecisionsGrid();
        decisionsView = decisionsGrid.setItems(decisions);
        val inspectorLayout = new VerticalLayout();
        inspectorLayout.setSizeFull();
        inspectorLayout.setPadding(false);
        inspectorLayout.setSpacing(false);
        inspectorLayout.add(controlButtons(), detailsView());
        val rightSplit = new SplitLayout(decisionsGrid, inspectorLayout);
        rightSplit.setSizeFull();
        rightSplit.setSplitterPosition(40.0D);
        rightSplit.setOrientation(Orientation.VERTICAL);
        layout.add(header, rightSplit);
        return layout;
    }

    private Component detailsView() {
        val tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.add("JSON", decisionJson());
        tabSheet.add("Errors", decisionErrors());
        tabSheet.add("Report", textReport());
        tabSheet.add("JSON Report", decisionJsonReport());
        tabSheet.add("JSON Trace", decisionJsonTrace());
        return tabSheet;
    }

    private Component decisionJson() {
        val layout = new HorizontalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        val jsonConfig = new JsonEditorConfiguration();
        jsonConfig.setHasLineNumbers(false);
        jsonConfig.setDarkTheme(true);
        jsonConfig.setReadOnly(true);
        jsonConfig.setLint(false);
        decisionsArea = new JsonEditor(jsonConfig);
        val clipboardButton = clipboardButton(decisionsArea::getDocument);
        layout.add(decisionsArea, clipboardButton);
        return layout;
    }

    private Component decisionJsonReport() {
        val layout = new HorizontalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        val jsonConfig = new JsonEditorConfiguration();
        jsonConfig.setHasLineNumbers(false);
        jsonConfig.setDarkTheme(true);
        jsonConfig.setReadOnly(true);
        jsonConfig.setLint(false);
        decisionsJsonReportArea = new JsonEditor(jsonConfig);
        val clipboardButton = clipboardButton(decisionsJsonReportArea::getDocument);
        layout.add(decisionsJsonReportArea, clipboardButton);
        return layout;
    }

    private Component decisionJsonTrace() {
        val layout = new HorizontalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        val jsonConfig = new JsonEditorConfiguration();
        jsonConfig.setHasLineNumbers(false);
        jsonConfig.setDarkTheme(true);
        jsonConfig.setReadOnly(true);
        jsonConfig.setLint(false);
        decisionsJsonTraceArea = new JsonEditor(jsonConfig);
        val clipboardButton = clipboardButton(decisionsJsonTraceArea::getDocument);
        layout.add(decisionsJsonTraceArea, clipboardButton);
        return layout;
    }

    private Component textReport() {
        val reportLayout = new HorizontalLayout();
        reportLayout.setSizeFull();
        reportLayout.setPadding(false);
        reportArea = new TextArea();
        reportArea.setSizeFull();
        reportArea.setReadOnly(true);
        reportArea.getStyle().set("font-family", "monospace");
        reportLayout.add(reportArea);
        val clipboardButton = clipboardButton(reportArea::getValue);
        reportLayout.add(reportArea, clipboardButton);
        return reportLayout;
    }

    private Component decisionErrors() {
        val reportLayout = new HorizontalLayout();
        reportLayout.setSizeFull();
        reportLayout.setPadding(false);
        errorsArea = new Div();
        errorsArea.setSizeFull();
        errorsArea.getStyle().set("font-family", "monospace");
        errorsArea.getStyle().set("overflow", "auto");
        errorsArea.getStyle().set("overflow-wrap", "break-word");
        errorsArea.getStyle().set("background-color", "#282a36");
        val clipboardButton = clipboardButton(() -> errorReport);
        reportLayout.add(errorsArea, clipboardButton);
        return reportLayout;
    }

    private Button clipboardButton(Supplier<String> contentSupplier) {
        val button = new Button(VaadinIcon.CLIPBOARD.create());
        button.getStyle().set("position", "absolute");
        button.getStyle().set("top", "15px");
        button.getStyle().set("right", "25px");
        button.getStyle().set("z-index", "100");
        button.setAriaLabel("Copy to clipboard.");
        button.setTooltipText("Copy to clipboard.");
        button.addClickListener(event -> copyToClipboard(contentSupplier.get()));
        return button;
    }

    private Component controlButtons() {
        val layout = new HorizontalLayout();
        layout.setAlignItems(Alignment.CENTER);
        playStopButton = new Button(VaadinIcon.PLAY.create());
        playStopButton.addThemeVariants(ButtonVariant.LUMO_ICON);

        scrollLockButton = new Button(VaadinIcon.UNLOCK.create());

        val bufferLabel = new NativeLabel("Buffer");
        bufferSizeField = new IntegerField();
        bufferSizeField.setMin(1);
        bufferSizeField.setMax(MAX_BUFFER_SIZE);
        bufferSizeField.setValue(DEFAULT_BUFFER_SIZE);
        bufferSizeField.setWidth("6.5em");
        bufferSizeField.setStepButtonsVisible(true);

        clearOnNewSubscriptionCheckBox = new Checkbox("Auto Clear");
        clearOnNewSubscriptionCheckBox.setValue(true);

        showOnlyDistinctDecisionsCheckBox = new Checkbox("Only Distinct Decisions");
        showOnlyDistinctDecisionsCheckBox.setValue(true);

        layout.add(playStopButton, scrollLockButton, bufferLabel, bufferSizeField, clearOnNewSubscriptionCheckBox,
                showOnlyDistinctDecisionsCheckBox);
        return layout;
    }

}
