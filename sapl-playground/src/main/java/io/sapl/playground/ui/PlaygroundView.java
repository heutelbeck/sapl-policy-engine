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
package io.sapl.playground.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasValue.ValueChangeEvent;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.menubar.MenuBarVariant;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout;
import com.vaadin.flow.component.splitlayout.SplitLayout.Orientation;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.selection.SelectionEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility.Gap;
import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.interceptors.ErrorReportGenerator;
import io.sapl.pdp.interceptors.ErrorReportGenerator.OutputFormat;
import io.sapl.pdp.interceptors.ReportBuilderUtil;
import io.sapl.pdp.interceptors.ReportTextRenderUtil;
import io.sapl.playground.domain.Example;
import io.sapl.playground.domain.ExampleCategory;
import io.sapl.playground.domain.PlaygroundExamples;
import io.sapl.playground.domain.PlaygroundPolicyDecisionPoint;
import io.sapl.playground.domain.PlaygroundValidator;
import io.sapl.playground.domain.ValidationResult;
import io.sapl.playground.ui.components.DecisionsGrid;
import io.sapl.playground.ui.components.DocumentationDrawer;
import io.sapl.vaadin.*;
import io.sapl.vaadin.graph.JsonGraphVisualization;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import reactor.core.Disposable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Interactive playground for testing SAPL policies.
 * Provides editors for policies, variables, and authorization subscriptions,
 * with real-time decision evaluation and visualization.
 */
@Slf4j
@Route("")
@PageTitle("SAPL Playground")
@JsModule("./copytoclipboard.js")
@JavaScript("./fragment-reader.js")
public class PlaygroundView extends Composite<VerticalLayout> {

    private static final int MAX_BUFFER_SIZE     = 50;
    private static final int DEFAULT_BUFFER_SIZE = 10;

    private static final int MAX_POLICY_TABS         = 20;
    private static final int MAX_DOCUMENT_SIZE_BYTES = 1_000_000;
    private static final int MAX_CLIPBOARD_SIZE      = 10_000_000;

    private static final int    MAX_TITLE_LENGTH        = 15;
    private static final String UNKNOWN_POLICY_NAME     = "unknown";
    private static final String EDITOR_CONFIGURATION_ID = "playground";
    private static final double LEFT_PANEL_WIDTH        = 30.0D;
    private static final double DECISIONS_PANEL_HEIGHT  = 40.0D;

    private static final String COLOR_GREEN  = "green";
    private static final String COLOR_RED    = "red";
    private static final String COLOR_ORANGE = "orange";

    private static final String SPACING_HALF_EM = "0.5em";

    private static final String CSS_MARGIN_RIGHT = "margin-right";
    private static final String CSS_MARGIN_LEFT  = "margin-left";

    private static final String DEFAULT_SUBSCRIPTION = """
            {
               "subject"     : { "role": "doctor", "department": "cardiology"},
               "action"      : "read",
               "resource"    : { "type": "patient_record", "department": "cardiology" },
               "environment" : null
            }
            """;

    private static final String DEFAULT_POLICY = """
            policy "new policy %d"
            permit false
            """;

    private static final String DEFAULT_VARIABLES = """
            {
              "variable1" : "value1",
              "variable2" : 123,
              "systemMode" : "staging"
            }
            """;

    private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private final transient ObjectMapper                  mapper;
    private final transient PlaygroundValidator           validator;
    private final transient DocumentationDrawer           documentationDrawer;
    private final transient PlaygroundPolicyDecisionPoint policyDecisionPoint;

    private final transient ArrayList<TracedDecision> decisionBuffer = new ArrayList<>(MAX_BUFFER_SIZE);

    private TabSheet   leftTabSheet;
    private Tab        variablesTab;
    private JsonEditor variablesEditor;
    private TextField  variablesValidationField;

    private JsonEditor subscriptionEditor;
    private TextField  subscriptionValidationField;

    private Button                           playStopButton;
    private Button                           scrollLockButton;
    private IntegerField                     bufferSizeField;
    private DecisionsGrid                    decisionsGrid;
    private GridListDataView<TracedDecision> decisionsGridView;
    private Checkbox                         clearOnNewSubscriptionCheckBox;

    private JsonEditor             decisionJsonEditor;
    private JsonEditor             decisionJsonReportEditor;
    private JsonEditor             decisionJsonTraceEditor;
    private JsonGraphVisualization traceGraphVisualization;
    private Div                    errorsDisplayArea;
    private TextArea               reportTextArea;

    private ComboBox<PolicyDocumentCombiningAlgorithm> combiningAlgorithmComboBox;

    private boolean              isScrollLockActive;
    private String               currentErrorReportText;
    private volatile boolean     isSubscriptionActive = false;
    private transient Disposable activeSubscription;

    private final Map<Tab, PolicyTabContext> policyTabContexts = new HashMap<>();
    private final AtomicInteger              policyTabCounter  = new AtomicInteger(1);
    private final AtomicInteger              newPolicyCounter  = new AtomicInteger(1);

    private static class PolicyTabContext {
        SaplEditor editor;
        TextField  validationField;
        Icon       statusIcon;
        Span       titleLabel;
        String     documentName;

        PolicyTabContext(SaplEditor editor, TextField validationField, Icon statusIcon, Span titleLabel) {
            this.editor          = editor;
            this.validationField = validationField;
            this.statusIcon      = statusIcon;
            this.titleLabel      = titleLabel;
            this.documentName    = UNKNOWN_POLICY_NAME;
        }
    }

    public PlaygroundView(ObjectMapper mapper,
            PlaygroundPolicyDecisionPoint policyDecisionPoint,
            PlaygroundValidator validator,
            DocumentationDrawer documentationDrawer) {
        this.mapper              = mapper;
        this.validator           = validator;
        this.policyDecisionPoint = policyDecisionPoint;
        this.documentationDrawer = documentationDrawer;

        buildAndAddComponents();
        initializeDefaultValues();

        addDetachListener(event -> cleanup());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        checkInitialFragment();
    }

    private void cleanup() {
        stopSubscription();
    }

    private void initializeDefaultValues() {
        subscriptionEditor.setDocument(DEFAULT_SUBSCRIPTION);
        variablesEditor.setDocument(DEFAULT_VARIABLES);
    }

    private void buildAndAddComponents() {
        val header = buildHeader();
        val main   = buildMainContent();

        getContent().setSizeFull();
        getContent().getStyle().set("flex-grow", "1");
        getContent().add(header, main, documentationDrawer.getToggleButton());

        deactivateScrollLock();
    }

    private void interceptDecision(final TracedDecision tracedDecision) {
        getUI().ifPresent(ui -> ui.access(() -> handleNewDecision(tracedDecision)));
    }

    private Component buildMainContent() {
        val mainSplit = new SplitLayout(buildLeftPanel(), buildRightPanel());
        mainSplit.setSizeFull();
        mainSplit.setSplitterPosition(LEFT_PANEL_WIDTH);
        return mainSplit;
    }

    private Component buildLeftPanel() {
        val layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);

        leftTabSheet = new TabSheet();
        leftTabSheet.setSizeFull();

        variablesTab = createVariablesTab();
        leftTabSheet.add(variablesTab, createVariablesEditorLayout());
        createNewPolicyTab();

        val newPolicyButton = createNewPolicyButton();
        leftTabSheet.setSuffixComponent(newPolicyButton);

        leftTabSheet.addSelectedChangeListener(event -> refreshVariablesEditorIfSelected(event.getSelectedTab()));

        layout.add(leftTabSheet);
        return layout;
    }

    private Button createNewPolicyButton() {
        val button = new Button("+ New Policy");
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        button.addClickListener(event -> createNewPolicyTab());
        return button;
    }

    private void refreshVariablesEditorIfSelected(Tab selectedTab) {
        if (selectedTab == variablesTab && variablesEditor != null) {
            variablesEditor.getElement()
                    .executeJs("if (this.editor && this.editor.refresh) { this.editor.refresh(); }");
        }
    }

    private Component buildRightPanel() {
        val layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);

        val subscriptionSection = buildSubscriptionSection();
        val decisionsSection    = buildDecisionsSection();

        layout.add(subscriptionSection, decisionsSection);
        return layout;
    }

    private Component buildSubscriptionSection() {
        val container = new VerticalLayout();
        container.setPadding(true);
        container.setSpacing(true);

        subscriptionValidationField = new TextField();
        subscriptionValidationField.setReadOnly(true);
        subscriptionValidationField.setWidthFull();

        val header = new Span("Authorization Subscription");
        header.getStyle().set("font-size", "var(--lumo-font-size-l)").set("font-weight", "600")
                .set("color", "var(--lumo-contrast-70pct)").set("margin", "0").set("padding", "0 0 0.25em 0");

        subscriptionEditor = createSubscriptionEditor();
        val controlsLayout = createSubscriptionControlsLayout();

        container.add(header, subscriptionEditor, controlsLayout);
        return container;
    }

    private Component createSubscriptionControlsLayout() {
        val layout = new HorizontalLayout();
        layout.setWidthFull();

        val formatButton = createFormatJsonButton(() -> formatJsonEditor(subscriptionEditor));
        layout.add(formatButton, subscriptionValidationField);

        return layout;
    }

    private Component buildDecisionsSection() {
        val container = new VerticalLayout();
        container.setSizeFull();
        container.setPadding(true);
        container.setSpacing(true);

        val header = new Span("Decisions");
        header.getStyle().set("font-size", "var(--lumo-font-size-l)").set("font-weight", "600")
                .set("color", "var(--lumo-contrast-70pct)").set("margin", "0").set("padding", "0 0 0.25em 0");

        decisionsGrid     = new DecisionsGrid();
        decisionsGridView = decisionsGrid.setItems(decisionBuffer);
        decisionsGrid.addSelectionListener(this::handleDecisionSelected);

        val inspectorLayout = new VerticalLayout();
        inspectorLayout.setSizeFull();
        inspectorLayout.setPadding(false);
        inspectorLayout.setSpacing(false);

        val controlButtons = buildControlButtons();
        val detailsView    = buildDecisionDetailsView();

        inspectorLayout.add(controlButtons, detailsView);
        inspectorLayout.setFlexGrow(0, controlButtons);
        inspectorLayout.setFlexGrow(1, detailsView);

        val splitLayout = new SplitLayout(decisionsGrid, inspectorLayout);
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(DECISIONS_PANEL_HEIGHT);
        splitLayout.setOrientation(Orientation.VERTICAL);

        container.add(header, splitLayout);
        return container;
    }

    private JsonEditor createSubscriptionEditor() {
        val editor = createJsonEditor(true, 500);
        editor.setWidthFull();
        editor.setHeight("200px");
        editor.addDocumentChangedListener(this::handleSubscriptionDocumentChanged);
        return editor;
    }

    private void handleSubscriptionDocumentChanged(DocumentChangedEvent event) {
        if (hasInvalidDocumentSize(event.getNewValue(), "Subscription")) {
            return;
        }

        val validationResult = validator.validateAuthorizationSubscription(event.getNewValue());
        updateValidationField(subscriptionValidationField, validationResult);

        if (Boolean.TRUE.equals(clearOnNewSubscriptionCheckBox.getValue())) {
            clearDecisionBuffer();
        }

        resubscribeIfActive();
    }

    private boolean hasInvalidDocumentSize(String document, String documentType) {
        if (document == null || document.isEmpty()) {
            showNotification(documentType + " cannot be empty");
            return true;
        }

        if (document.length() > MAX_DOCUMENT_SIZE_BYTES) {
            val sizeInKb = MAX_DOCUMENT_SIZE_BYTES / 1024;
            showNotification(documentType + " exceeds maximum size of " + sizeInKb + "KB");
            return true;
        }

        return false;
    }

    private void clearDecisionBuffer() {
        decisionBuffer.clear();
        decisionsGridView.refreshAll();
    }

    private void resubscribeIfActive() {
        if (isSubscriptionActive) {
            subscribe();
        }
    }

    private void formatJsonEditor(JsonEditor editor) {
        val jsonString = editor.getDocument();
        try {
            val json = mapper.readTree(jsonString);
            if (json instanceof MissingNode) {
                showNotification("Cannot format invalid JSON.");
                return;
            }
            val formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            editor.setDocument(formattedJson);
        } catch (JsonProcessingException exception) {
            showNotification("Cannot format invalid JSON.");
        }
    }

    private void showNotification(String message) {
        Notification.show(message);
    }

    private Component buildControlButtons() {
        val layout = new HorizontalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);

        playStopButton   = createPlayStopButton();
        scrollLockButton = createScrollLockButton();
        bufferSizeField  = createBufferSizeField();

        clearOnNewSubscriptionCheckBox = new Checkbox("Auto Clear");
        clearOnNewSubscriptionCheckBox.setValue(true);

        val bufferLabel = new NativeLabel("Buffer");

        layout.add(playStopButton, scrollLockButton, bufferLabel, bufferSizeField, clearOnNewSubscriptionCheckBox);
        return layout;
    }

    private Button createPlayStopButton() {
        val button = new Button(VaadinIcon.PLAY.create());
        button.addThemeVariants(ButtonVariant.LUMO_ICON);
        button.setAriaLabel("Subscribe");
        button.setTooltipText("Start subscribing with authorization subscription.");
        button.addClickListener(event -> togglePlayStop());
        return button;
    }

    private Button createScrollLockButton() {
        val button = new Button(VaadinIcon.UNLOCK.create());
        button.addClickListener(event -> toggleScrollLock());
        return button;
    }

    private IntegerField createBufferSizeField() {
        val field = new IntegerField();
        field.setMin(1);
        field.setMax(MAX_BUFFER_SIZE);
        field.setValue(DEFAULT_BUFFER_SIZE);
        field.setWidthFull();
        field.setMaxWidth("5em");
        field.setStepButtonsVisible(true);
        field.addValueChangeListener(event -> updateBufferSize(event.getValue()));
        return field;
    }

    private void togglePlayStop() {
        if (isSubscriptionActive) {
            stopSubscription();
        } else {
            startSubscription();
        }
    }

    private void toggleScrollLock() {
        if (isScrollLockActive) {
            deactivateScrollLock();
        } else {
            activateScrollLock();
        }
    }

    private void startSubscription() {
        isSubscriptionActive = true;
        updatePlayStopButtonForActiveState();

        if (Boolean.TRUE.equals(clearOnNewSubscriptionCheckBox.getValue())) {
            clearDecisionBuffer();
        }

        subscribe();
    }

    private void stopSubscription() {
        if (activeSubscription != null && !activeSubscription.isDisposed()) {
            activeSubscription.dispose();
        }
        activeSubscription = null;

        isSubscriptionActive = false;
        updatePlayStopButtonForActiveState();
    }

    private void subscribe() {
        try {
            if (activeSubscription != null && !activeSubscription.isDisposed()) {
                activeSubscription.dispose();
            }

            val authorizationSubscription = parseAuthorizationSubscriptionFromEditor();
            if (authorizationSubscription == null) {
                showNotification("Invalid authorization subscription");
                stopSubscription();
                return;
            }

            activeSubscription = policyDecisionPoint.decide(authorizationSubscription)
                    .subscribe(this::interceptDecision, error -> {
                        log.error("Error in PDP subscription", error);
                        getUI().ifPresent(ui -> ui.access(() -> {
                            stopSubscription();
                            showNotification("Subscription error: " + error.getMessage());
                        }));
                    }, () -> {
                        log.debug("PDP subscription completed");
                        activeSubscription = null;
                    });
        } catch (Exception exception) {
            log.error("Failed to create subscription", exception);
            stopSubscription();
            showNotification("Failed to subscribe: " + exception.getMessage());
        }
    }

    private AuthorizationSubscription parseAuthorizationSubscriptionFromEditor() {
        val subscriptionJson = subscriptionEditor.getDocument();
        try {
            return mapper.readValue(subscriptionJson, AuthorizationSubscription.class);
        } catch (JsonProcessingException exception) {
            log.debug("Failed to parse authorization subscription: {}", exception.getMessage());
            return null;
        }
    }

    private void updatePlayStopButtonForActiveState() {
        if (isSubscriptionActive) {
            playStopButton.setIcon(VaadinIcon.STOP.create());
            playStopButton.setAriaLabel("Unsubscribe");
            playStopButton.setTooltipText("Stop Subscribing.");
        } else {
            playStopButton.setIcon(VaadinIcon.PLAY.create());
            playStopButton.setAriaLabel("Subscribe");
            playStopButton.setTooltipText("Start subscribing with authorization subscription.");
        }
    }

    private int getBufferSize() {
        val size = bufferSizeField.getValue();
        return size != null ? size : DEFAULT_BUFFER_SIZE;
    }

    private void updateBufferSize(Integer size) {
        if (size == null) {
            return;
        }

        while (decisionBuffer.size() > size) {
            decisionBuffer.removeFirst();
        }
        decisionsGridView.refreshAll();
    }

    private void activateScrollLock() {
        isScrollLockActive = true;
        scrollLockButton.setIcon(VaadinIcon.UNLOCK.create());
        scrollLockButton.setAriaLabel("Scroll Lock");
        scrollLockButton
                .setTooltipText("Scroll Lock inactive. Click to stop automatically scrolling to last decision made.");
    }

    private void deactivateScrollLock() {
        isScrollLockActive = false;
        scrollLockButton.setIcon(VaadinIcon.LOCK.create());
        scrollLockButton.setAriaLabel("Scroll Lock");
        scrollLockButton
                .setTooltipText("Scroll Lock active. Click to start automatically scrolling to last decision made.");
    }

    private void handleDecisionSelected(SelectionEvent<Grid<TracedDecision>, TracedDecision> selection) {
        updateDecisionDetailsView(selection.getFirstSelectedItem());
    }

    private void updateDecisionDetailsView(Optional<TracedDecision> maybeTracedDecision) {
        if (maybeTracedDecision.isEmpty()) {
            clearDecisionDetailsView();
            return;
        }

        val tracedDecision = maybeTracedDecision.get();
        displayDecisionJson(tracedDecision);
        displayDecisionTrace(tracedDecision);
        displayDecisionReport(tracedDecision);
        displayDecisionErrors(tracedDecision);
    }

    private void displayDecisionJson(TracedDecision tracedDecision) {
        try {
            val prettyJson = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(tracedDecision.getAuthorizationDecision());
            decisionJsonEditor.setDocument(prettyJson);
        } catch (JsonProcessingException exception) {
            decisionJsonEditor.setDocument("Error reading decision:\n" + tracedDecision);
        }
    }

    private void displayDecisionTrace(TracedDecision tracedDecision) {
        val trace = tracedDecision.getTrace();
        decisionJsonTraceEditor.setDocument(trace.toPrettyString());
        traceGraphVisualization.setJsonData(trace.toPrettyString());
    }

    private void displayDecisionReport(TracedDecision tracedDecision) {
        val trace  = tracedDecision.getTrace();
        val report = ReportBuilderUtil.reduceTraceToReport(trace);

        decisionJsonReportEditor.setDocument(report.toPrettyString());
        reportTextArea.setValue(ReportTextRenderUtil.textReport(report, false, mapper));
    }

    private void displayDecisionErrors(TracedDecision tracedDecision) {
        val errors          = tracedDecision.getErrorsFromTrace();
        val plainTextReport = buildAggregatedErrorReport(errors);

        errorsDisplayArea.removeAll();
        val pre = new Pre(plainTextReport);
        pre.getStyle().set("white-space", "pre-wrap").set("font-family", "monospace").set("overflow", "auto")
                .set("overflow-wrap", "break-word").set("background-color", "#282a36").set("color", "#f8f8f2")
                .set("padding", "1em");
        errorsDisplayArea.add(pre);

        currentErrorReportText = plainTextReport;
    }

    private void clearDecisionDetailsView() {
        decisionJsonEditor.setDocument("");
        decisionJsonTraceEditor.setDocument("");
        decisionJsonReportEditor.setDocument("");
        reportTextArea.setValue("");
        traceGraphVisualization.setJsonData("{}");
        errorsDisplayArea.removeAll();
        currentErrorReportText = "";
    }

    private String buildAggregatedErrorReport(Collection<Val> errors) {
        if (errors.isEmpty()) {
            return "No Errors";
        }

        val reportBuilder = new StringBuilder();
        for (var error : errors) {
            reportBuilder.append(ErrorReportGenerator.errorReport(error, true, OutputFormat.PLAIN_TEXT));
        }
        return reportBuilder.toString();
    }

    private void handleNewDecision(TracedDecision decision) {
        decisionBuffer.add(decision);

        val bufferSize = getBufferSize();
        if (decisionBuffer.size() > bufferSize) {
            decisionBuffer.removeFirst();
        }

        decisionsGridView.refreshAll();

        if (isScrollLockActive) {
            decisionsGrid.scrollToEnd();
        }
    }

    private Component buildDecisionDetailsView() {
        val tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.getStyle().set("overflow", "auto");
        tabSheet.add("JSON", createDecisionJsonTab());
        tabSheet.add("Errors", createDecisionErrorsTab());
        tabSheet.add("Report", createTextReportTab());
        tabSheet.add("JSON Report", createDecisionJsonReportTab());
        tabSheet.add("JSON Trace", createDecisionJsonTraceTab());
        tabSheet.add("Trace Graph", createTraceGraphTab());
        return tabSheet;
    }

    private Component createDecisionJsonTab() {
        decisionJsonEditor = createReadOnlyJsonEditor();
        return createLayoutWithClipboard(decisionJsonEditor, decisionJsonEditor::getDocument);
    }

    private Component createDecisionJsonReportTab() {
        decisionJsonReportEditor = createReadOnlyJsonEditor();
        return createLayoutWithClipboard(decisionJsonReportEditor, decisionJsonReportEditor::getDocument);
    }

    private Component createDecisionJsonTraceTab() {
        decisionJsonTraceEditor = createReadOnlyJsonEditor();
        return createLayoutWithClipboard(decisionJsonTraceEditor, decisionJsonTraceEditor::getDocument);
    }

    private Component createTraceGraphTab() {
        traceGraphVisualization = new JsonGraphVisualization();
        traceGraphVisualization.setSizeFull();
        traceGraphVisualization.setJsonData("{}");
        return traceGraphVisualization;
    }

    private Component createTextReportTab() {
        reportTextArea = new TextArea();
        reportTextArea.setSizeFull();
        reportTextArea.setReadOnly(true);
        reportTextArea.getStyle().set("font-family", "monospace");
        return createLayoutWithClipboard(reportTextArea, reportTextArea::getValue);
    }

    private Component createDecisionErrorsTab() {
        errorsDisplayArea = new Div();
        errorsDisplayArea.setSizeFull();
        errorsDisplayArea.getStyle().set("overflow", "auto").set("min-height", "200px");
        return createLayoutWithClipboard(errorsDisplayArea, () -> currentErrorReportText);
    }

    private Component createLayoutWithClipboard(Component component, Supplier<String> contentSupplier) {
        val layout = new HorizontalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.add(component, createClipboardButton(contentSupplier));
        return layout;
    }

    private Button createClipboardButton(Supplier<String> contentSupplier) {
        val button = new Button(VaadinIcon.CLIPBOARD.create());
        button.getStyle().set("position", "absolute").set("top", "15px").set("right", "25px").set("z-index", "100");
        button.setAriaLabel("Copy to clipboard.");
        button.setTooltipText("Copy to clipboard.");
        button.addClickListener(event -> copyToClipboard(contentSupplier.get()));
        return button;
    }

    private void copyToClipboard(String content) {
        if (content == null || content.length() > MAX_CLIPBOARD_SIZE) {
            showNotification("Content too large to copy to clipboard");
            return;
        }

        UI.getCurrent().getPage().executeJs("window.copyToClipboard($0)", content);
        val notification = Notification.show("Content copied to clipboard.");
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
    }

    private Tab createVariablesTab() {
        val variablesValidationIcon = createIcon(VaadinIcon.CHECK_CIRCLE, COLOR_GREEN);
        variablesValidationIcon.getStyle().set(CSS_MARGIN_RIGHT, SPACING_HALF_EM);

        val label      = new Span(truncateTitle("Variables"));
        val tabContent = new HorizontalLayout(variablesValidationIcon, label);
        tabContent.setSpacing(false);
        tabContent.setAlignItems(FlexComponent.Alignment.CENTER);

        return new Tab(tabContent);
    }

    private Component createVariablesEditorLayout() {
        val layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(true);

        variablesEditor = createJsonEditor(true, 500);
        variablesEditor.setSizeFull();

        variablesValidationField = new TextField();
        variablesValidationField.setReadOnly(true);
        variablesValidationField.setWidthFull();

        val controlsLayout = createVariablesControlsLayout();

        layout.add(variablesEditor, controlsLayout);

        variablesEditor.addDocumentChangedListener(this::handleVariablesDocumentChanged);

        return layout;
    }

    private Component createVariablesControlsLayout() {
        val layout = new HorizontalLayout();
        layout.setWidthFull();

        val formatButton = createFormatJsonButton(() -> formatJsonEditor(variablesEditor));
        layout.add(formatButton, variablesValidationField);

        return layout;
    }

    private Button createFormatJsonButton(Runnable formatAction) {
        val button = new Button(VaadinIcon.CURLY_BRACKETS.create());
        button.setAriaLabel("Format JSON");
        button.setTooltipText("Format JSON.");
        button.addClickListener(event -> formatAction.run());
        return button;
    }

    private void handleVariablesDocumentChanged(DocumentChangedEvent event) {
        if (hasInvalidDocumentSize(event.getNewValue(), "Variables")) {
            return;
        }

        val validationResult = validator.validateVariablesJson(event.getNewValue());
        updateValidationField(variablesValidationField, validationResult);

        if (validationResult.isValid()) {
            this.policyDecisionPoint.setVariables(parseVariablesFromJson(event.getNewValue()));
        }
    }

    private Map<String, Val> parseVariablesFromJson(String variablesJson) {
        try {
            val variablesObject = mapper.readTree(variablesJson);
            val variables       = new HashMap<String, Val>(variablesObject.size());

            variablesObject.forEachEntry((name, value) -> {
                if (!PlaygroundValidator.isValidVariableName(name)) {
                    log.warn("Invalid variable name, skipping: {}", name);
                    return;
                }
                variables.put(name, Val.of(value));
            });

            return variables;
        } catch (JsonProcessingException exception) {
            log.error("Unexpected invalid JSON in variables after validation", exception);
            return Map.of();
        }
    }

    /**
     * Creates and adds a policy tab with the given document content.
     * Parses the policy name immediately to update the tab title.
     *
     * @param policyDocument the initial policy document content
     * @return the created tab, or null if tab limit reached
     */
    private Tab createAndAddPolicyTab(String policyDocument) {
        if (policyTabContexts.size() >= MAX_POLICY_TABS) {
            return null;
        }

        val components = createPolicyTabComponents("Policy " + policyTabCounter.getAndIncrement());

        leftTabSheet.add(components.tab, components.editorLayout);

        components.context.editor
                .addValidationFinishedListener(event -> handlePolicyValidation(components.context, event));
        components.context.editor.setDocument(policyDocument);

        val parsedDocument = INTERPRETER.parseDocument(policyDocument);
        if (!parsedDocument.isInvalid()) {
            components.context.documentName = parsedDocument.name();
            components.context.titleLabel.setText(truncateTitle(parsedDocument.name()));
        }

        return components.tab;
    }

    /**
     * Creates a new empty policy tab and selects it.
     * Shows notification if maximum tab limit is reached.
     */
    private void createNewPolicyTab() {
        val policyContent = String.format(DEFAULT_POLICY, newPolicyCounter.getAndIncrement());
        val tab           = createAndAddPolicyTab(policyContent);
        if (tab == null) {
            showNotification("Maximum number of policy tabs (" + MAX_POLICY_TABS + ") reached");
            return;
        }
        leftTabSheet.setSelectedTab(tab);
    }

    private record PolicyTabComponents(Tab tab, PolicyTabContext context, VerticalLayout editorLayout) {}

    private PolicyTabComponents createPolicyTabComponents(String policyName) {
        val editor          = createSaplEditor();
        val validationField = createValidationTextField();

        val editorLayout = new VerticalLayout();
        editorLayout.setSizeFull();
        editorLayout.setPadding(false);
        editorLayout.setSpacing(true);
        editorLayout.add(editor, validationField);

        val statusIcon = createIcon(VaadinIcon.QUESTION_CIRCLE, COLOR_ORANGE);
        statusIcon.setSize("0.875em");
        statusIcon.getStyle().set(CSS_MARGIN_RIGHT, "0.25em");

        val titleLabel  = new Span(truncateTitle(policyName));
        val closeButton = createTabCloseButton();

        val tabContent = new HorizontalLayout(statusIcon, titleLabel, closeButton);
        tabContent.setSpacing(false);
        tabContent.setPadding(false);
        tabContent.getStyle().set("gap", "0.25em");
        tabContent.setAlignItems(FlexComponent.Alignment.CENTER);

        val tab = new Tab(tabContent);
        tab.getStyle().set("padding", "0.5em 0.75em");
        val context = new PolicyTabContext(editor, validationField, statusIcon, titleLabel);

        policyTabContexts.put(tab, context);
        closeButton.addClickListener(event -> handlePolicyTabClose(tab));

        return new PolicyTabComponents(tab, context, editorLayout);
    }

    private SaplEditor createSaplEditor() {
        val config = new SaplEditorConfiguration();
        config.setHasLineNumbers(true);
        config.setTextUpdateDelay(500);
        config.setDarkTheme(true);

        val editor = new SaplEditor(config);
        editor.setConfigurationId(EDITOR_CONFIGURATION_ID);
        editor.setSizeFull();
        return editor;
    }

    private TextField createValidationTextField() {
        val field = new TextField();
        field.setReadOnly(true);
        field.setWidthFull();
        return field;
    }

    private Button createTabCloseButton() {
        val button = new Button(VaadinIcon.CLOSE_SMALL.create());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        button.getStyle().set(CSS_MARGIN_LEFT, SPACING_HALF_EM);
        return button;
    }

    private void handlePolicyTabClose(Tab tab) {
        policyTabContexts.remove(tab);
        leftTabSheet.remove(tab);
        checkForPolicyNameCollisions();
        updatePolicyRetrievalPoint();
    }

    private void handlePolicyValidation(PolicyTabContext context, ValidationFinishedEvent event) {
        val document = context.editor.getDocument();

        if (hasInvalidDocumentSize(document, "Policy")) {
            val result = ValidationResult.error("Document too large");
            updateValidationField(context.validationField, result);
            context.statusIcon.setIcon(VaadinIcon.CLOSE_CIRCLE);
            context.statusIcon.setColor(COLOR_RED);
            return;
        }

        val issues         = event.getIssues();
        val hasErrors      = PlaygroundValidator.hasErrorSeverityIssues(issues);
        val parsedDocument = INTERPRETER.parseDocument(document);

        updatePolicyDocumentName(context, parsedDocument);
        updatePolicyValidationState(context, hasErrors, issues);
        checkForPolicyNameCollisions();
        updatePolicyRetrievalPoint();
    }

    private List<String> collectAllPolicyDocuments() {
        val documents = new ArrayList<String>();
        for (var context : policyTabContexts.values()) {
            documents.add(context.editor.getDocument());
        }
        return documents;
    }

    private void updatePolicyRetrievalPoint() {
        val documents = collectAllPolicyDocuments();
        this.policyDecisionPoint.updatePrp(documents);
    }

    private void updatePolicyDocumentName(PolicyTabContext context, io.sapl.prp.Document parsedDocument) {
        if (!parsedDocument.isInvalid()) {
            context.documentName = parsedDocument.name();
        }
        context.titleLabel.setText(truncateTitle(context.documentName));
    }

    private void updatePolicyValidationState(PolicyTabContext context, boolean hasErrors,
            io.sapl.vaadin.Issue[] issues) {
        if (hasErrors) {
            val errorCount = PlaygroundValidator.countErrorSeverityIssues(issues);
            val result     = ValidationResult.error(errorCount + " error(s)");
            context.statusIcon.setIcon(VaadinIcon.CLOSE_CIRCLE);
            context.statusIcon.setColor(COLOR_RED);
            updateValidationField(context.validationField, result);
        } else {
            val result = ValidationResult.success();
            context.statusIcon.setIcon(VaadinIcon.CHECK_CIRCLE);
            context.statusIcon.setColor(COLOR_GREEN);
            updateValidationField(context.validationField, result);
        }
    }

    private void checkForPolicyNameCollisions() {
        val documentNamesToTabs = groupTabsByDocumentName();
        val duplicateNames      = findDuplicateDocumentNames(documentNamesToTabs);

        for (var entry : policyTabContexts.entrySet()) {
            val context = entry.getValue();
            if (duplicateNames.contains(context.documentName)) {
                setCollisionWarningState(context);
            } else {
                restoreNormalValidationState(context);
            }
        }
    }

    private Map<String, List<Tab>> groupTabsByDocumentName() {
        val nameToTabs = new HashMap<String, List<Tab>>();

        for (var entry : policyTabContexts.entrySet()) {
            val tab     = entry.getKey();
            val context = entry.getValue();
            if (!UNKNOWN_POLICY_NAME.equals(context.documentName)) {
                nameToTabs.computeIfAbsent(context.documentName, k -> new ArrayList<>()).add(tab);
            }
        }

        return nameToTabs;
    }

    private Set<String> findDuplicateDocumentNames(Map<String, List<Tab>> documentNamesToTabs) {
        return documentNamesToTabs.entrySet().stream().filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    private void setCollisionWarningState(PolicyTabContext context) {
        context.statusIcon.setIcon(VaadinIcon.WARNING);
        context.statusIcon.setColor(COLOR_ORANGE);
        val result = ValidationResult.warning("Name collision: '" + context.documentName + "'");
        updateValidationField(context.validationField, result);
    }

    private void restoreNormalValidationState(PolicyTabContext context) {
        val document       = context.editor.getDocument();
        val parsedDocument = INTERPRETER.parseDocument(document);
        val hasErrors      = parsedDocument.isInvalid();

        if (hasErrors) {
            context.statusIcon.setIcon(VaadinIcon.CLOSE_CIRCLE);
            context.statusIcon.setColor(COLOR_RED);
            val result = ValidationResult.error("Syntax error");
            updateValidationField(context.validationField, result);
        } else {
            context.statusIcon.setIcon(VaadinIcon.CHECK_CIRCLE);
            context.statusIcon.setColor(COLOR_GREEN);
            val result = ValidationResult.success();
            updateValidationField(context.validationField, result);
        }
    }

    private String truncateTitle(String title) {
        if (title == null) {
            return "";
        }
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, MAX_TITLE_LENGTH) + "...";
    }

    private HorizontalLayout buildHeader() {
        val header = new HorizontalLayout();
        header.setWidthFull();
        header.setPadding(true);
        header.setSpacing(true);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle().set("background-color", "var(--lumo-contrast-5pct)");

        val logo = new Image("logo-header.png", "SAPL Logo");
        logo.setHeight("2.5em");

        val title = new Span("SAPL Playground");
        title.getStyle().set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "600")
                .set("text-align", "center").set("flex-grow", "1");

        combiningAlgorithmComboBox = createCombiningAlgorithmComboBox();
        val examplesMenu = createExamplesMenu();
        val shareButton  = createShareButton();
        val homepageLink = createHomepageLink();

        val rightSection = new HorizontalLayout(combiningAlgorithmComboBox, homepageLink, examplesMenu, shareButton);
        rightSection.setAlignItems(FlexComponent.Alignment.CENTER);
        rightSection.setSpacing(true);

        header.add(logo, title, rightSection);

        return header;
    }

    private Button createHomepageLink() {
        val button = new Button("SAPL Homepage", VaadinIcon.EXTERNAL_LINK.create());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        button.addClickListener(event -> getUI().ifPresent(ui -> ui.getPage().open("https://sapl.io", "_blank")));
        return button;
    }

    private Button createShareButton() {
        val button = new Button("Share", VaadinIcon.LINK.create());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        button.setTooltipText("Create shareable link (Coming soon)");
        button.setEnabled(false);
        return button;
    }

    private ComboBox<PolicyDocumentCombiningAlgorithm> createCombiningAlgorithmComboBox() {
        val comboBox = new ComboBox<PolicyDocumentCombiningAlgorithm>();
        comboBox.setPlaceholder("Combining Algorithm");
        comboBox.setItems(PolicyDocumentCombiningAlgorithm.values());
        comboBox.setItemLabelGenerator(PlaygroundView::formatAlgorithmName);
        comboBox.addValueChangeListener(this::handleAlgorithmChange);
        comboBox.setWidth("16em");
        comboBox.setValue(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES);
        return comboBox;
    }

    private MenuBar createExamplesMenu() {
        val menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY);
        val examplesItem = menuBar.addItem("Examples");
        val mainSubMenu  = examplesItem.getSubMenu();

        for (val category : PlaygroundExamples.getAllCategories()) {
            val categoryIcon = category.icon().create();
            categoryIcon.setSize("1em");
            val categoryLabel = new Span(categoryIcon, new Text(" " + category.name()));
            val categoryItem  = mainSubMenu.addItem(categoryLabel);
            val categoryMenu  = categoryItem.getSubMenu();

            for (val example : category.examples()) {
                categoryMenu.addItem(example.displayName(), event -> loadExampleWithConfirmation(example));
            }
        }

        return menuBar;
    }

    private void checkInitialFragment() {
        getUI().ifPresent(
                ui -> ui.getPage().executeJs("return window.getUrlFragment()").then(String.class, fragment -> {
                    if (fragment != null && !fragment.isEmpty() && fragment.startsWith("example/")) {
                        val slug = fragment.substring(8);
                        loadExampleBySlugWithoutConfirmation(slug);
                    }
                }));
    }

    private void loadExampleWithConfirmation(Example example) {
        val dialog = new ConfirmDialog();
        dialog.setHeader("Load Example: " + example.displayName());
        dialog.setText(example.description() + "\n\nThis will replace current policies, subscription, and variables.");

        dialog.setCancelable(true);
        dialog.setConfirmText("Load Example");
        dialog.addConfirmListener(event -> {
            loadExample(example);
            updateUrlFragment(example.slug());
        });

        dialog.open();
    }

    private void loadExampleBySlugWithoutConfirmation(String slug) {
        PlaygroundExamples.findBySlug(slug).ifPresent(this::loadExample);
    }

    /**
     * Loads an example into the playground.
     * Stops active subscription, clears state, loads example content, and updates
     * PDP.
     *
     * @param example the example to load
     */
    private void loadExample(Example example) {
        stopSubscription();
        clearDecisionBuffer();
        removeAllPolicyTabs();

        Tab firstTab = null;
        for (val policy : example.policies()) {
            val tab = createAndAddPolicyTab(policy);
            if (tab == null) {
                showNotification("Example has too many policies. Maximum: " + MAX_POLICY_TABS);
                break;
            }
            if (firstTab == null) {
                firstTab = tab;
            }
        }

        if (firstTab != null) {
            leftTabSheet.setSelectedTab(firstTab);
        }

        setCombiningAlgorithmQuietly(example.combiningAlgorithm());
        variablesEditor.setDocument(example.variables());
        subscriptionEditor.setDocument(example.subscription());

        showNotification("Loaded example: " + example.displayName());
    }

    private void removeAllPolicyTabs() {
        val tabsToRemove = policyTabContexts.keySet().stream().toList();
        for (val tab : tabsToRemove) {
            policyTabContexts.remove(tab);
            leftTabSheet.remove(tab);
        }
    }

    private void setCombiningAlgorithmQuietly(PolicyDocumentCombiningAlgorithm algorithm) {
        combiningAlgorithmComboBox.setValue(algorithm);
        policyDecisionPoint.setCombiningAlgorithm(algorithm);
    }

    private void updateUrlFragment(String slug) {
        getUI().ifPresent(ui -> ui.getPage().executeJs("window.location.hash = 'example/' + $0", slug));
    }

    private void handleAlgorithmChange(ValueChangeEvent<PolicyDocumentCombiningAlgorithm> event) {
        this.policyDecisionPoint.setCombiningAlgorithm(event.getValue());
    }

    private static String formatAlgorithmName(PolicyDocumentCombiningAlgorithm algorithm) {
        return StringUtils.capitalize(algorithm.toString().replace('_', ' ').toLowerCase());
    }

    private JsonEditor createReadOnlyJsonEditor() {
        val editor = createJsonEditor(false, 0);
        editor.getElement().getStyle().set("width", "100%");
        return editor;
    }

    private JsonEditor createJsonEditor(boolean hasLineNumbers, int textUpdateDelay) {
        val config = new JsonEditorConfiguration();
        config.setHasLineNumbers(hasLineNumbers);
        config.setTextUpdateDelay(textUpdateDelay);
        config.setDarkTheme(true);
        config.setReadOnly(!hasLineNumbers);
        config.setLint(hasLineNumbers);
        return new JsonEditor(config);
    }

    private Icon createIcon(VaadinIcon iconType, String color) {
        val icon = iconType.create();
        icon.setColor(color);
        return icon;
    }

    private void updateValidationField(TextField field, ValidationResult result) {
        val icon = switch (result.severity()) {
        case SUCCESS -> createIcon(VaadinIcon.CHECK, COLOR_GREEN);
        case ERROR   -> createIcon(VaadinIcon.CLOSE_CIRCLE, COLOR_RED);
        case WARNING -> createIcon(VaadinIcon.WARNING, COLOR_ORANGE);
        };
        field.setPrefixComponent(icon);
        field.setValue(result.message());
    }
}
