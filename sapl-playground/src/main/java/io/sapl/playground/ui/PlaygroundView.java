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
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasValue.ValueChangeEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
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
            policy "compartmentalize read access by department"
            permit
                resource.type == "patient_record" & action == "read"
            where
                subject.role == "doctor";
                resource.department == subject.department;
            """;

    private static final String DEFAULT_VARIABLES = """
            {
              "variable1" : "value1",
              "variable2" : 123,
              "systemMode" : "staging"
            }
            """;

    /**
     * Static SAPL interpreter for parsing policy documents.
     * Thread-safe as DefaultSAPLInterpreter is stateless for parsing operations.
     */
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

    private boolean              isScrollLockActive;
    private String               currentErrorReportText;
    private volatile boolean     isSubscriptionActive = false;
    private transient Disposable activeSubscription;

    private final Map<Tab, PolicyTabContext> policyTabContexts = new HashMap<>();
    private final AtomicInteger              policyTabCounter  = new AtomicInteger(1);

    /**
     * Holds the components and state for a single policy editor tab.
     */
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

    /**
     * Cleans up resources when the view is detached.
     * Disposes of active subscriptions to prevent memory leaks.
     */
    private void cleanup() {
        stopSubscription();
    }

    /**
     * Sets default values in editors after components are built.
     */
    private void initializeDefaultValues() {
        subscriptionEditor.setDocument(DEFAULT_SUBSCRIPTION);
        variablesEditor.setDocument(DEFAULT_VARIABLES);
    }

    /**
     * Builds and adds all components to the main layout.
     */
    private void buildAndAddComponents() {
        val header = buildHeader();
        val main   = buildMainContent();
        val footer = buildFooter();

        getContent().setSizeFull();
        getContent().getStyle().set("flex-grow", "1");
        getContent().add(header, main, footer, documentationDrawer.getToggleButton());

        deactivateScrollLock();
    }

    /**
     * Intercepts authorization decisions for display in the grid.
     * Executes on the UI thread to safely update Vaadin components.
     * Rate limiting is handled by PIP layer (max 1 update/second).
     *
     * @param tracedDecision the decision to intercept
     */
    private void interceptDecision(final TracedDecision tracedDecision) {
        getUI().ifPresent(ui -> ui.access(() -> handleNewDecision(tracedDecision)));
    }

    /**
     * Builds the main content area with left and right panels.
     *
     * @return the main content component
     */
    private Component buildMainContent() {
        val mainSplit = new SplitLayout(buildLeftPanel(), buildRightPanel());
        mainSplit.setSizeFull();
        mainSplit.setSplitterPosition(LEFT_PANEL_WIDTH);
        return mainSplit;
    }

    /**
     * Builds the left panel containing policy and variable editors.
     *
     * @return the left panel component
     */
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

    /**
     * Creates the button for adding new policy tabs.
     *
     * @return the configured button
     */
    private Button createNewPolicyButton() {
        val button = new Button("+ New Policy");
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        button.addClickListener(event -> createNewPolicyTab());
        return button;
    }

    /**
     * Refreshes the variables editor when its tab is selected.
     * Required to properly render CodeMirror when switching tabs.
     *
     * @param selectedTab the currently selected tab
     */
    private void refreshVariablesEditorIfSelected(Tab selectedTab) {
        if (selectedTab == variablesTab && variablesEditor != null) {
            variablesEditor.getElement()
                    .executeJs("if (this.editor && this.editor.refresh) { this.editor.refresh(); }");
        }
    }

    /**
     * Builds the right panel containing subscription editor and decisions grid.
     *
     * @return the right panel component
     */
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

    /**
     * Builds the authorization subscription editor section.
     *
     * @return the subscription section component
     */
    private Component buildSubscriptionSection() {
        val container = new VerticalLayout();
        container.setPadding(false);
        container.setSpacing(true);

        subscriptionValidationField = new TextField();
        subscriptionValidationField.setReadOnly(true);
        subscriptionValidationField.setWidthFull();

        val header = new H3("Authorization Subscription");
        subscriptionEditor = createSubscriptionEditor();
        val controlsLayout = createSubscriptionControlsLayout();

        container.add(header, subscriptionEditor, controlsLayout);
        return container;
    }

    /**
     * Creates the controls layout for subscription (format button and validation
     * field).
     *
     * @return the controls layout
     */
    private Component createSubscriptionControlsLayout() {
        val layout = new HorizontalLayout();
        layout.setWidthFull();

        val formatButton = createFormatJsonButton(() -> formatJsonEditor(subscriptionEditor));
        layout.add(formatButton, subscriptionValidationField);

        return layout;
    }

    /**
     * Builds the decisions display section.
     *
     * @return the decisions section component
     */
    private Component buildDecisionsSection() {
        val container = new VerticalLayout();
        container.setSizeFull();
        container.setPadding(false);
        container.setSpacing(false);

        val header = new H3("Decisions");
        decisionsGrid     = new DecisionsGrid();
        decisionsGridView = decisionsGrid.setItems(decisionBuffer);
        decisionsGrid.addSelectionListener(this::handleDecisionSelected);

        val inspectorLayout = new VerticalLayout();
        inspectorLayout.setSizeFull();
        inspectorLayout.setPadding(false);
        inspectorLayout.setSpacing(false);
        inspectorLayout.add(buildControlButtons(), buildDecisionDetailsView());

        val splitLayout = new SplitLayout(decisionsGrid, inspectorLayout);
        splitLayout.setSizeFull();
        splitLayout.setSplitterPosition(DECISIONS_PANEL_HEIGHT);
        splitLayout.setOrientation(Orientation.VERTICAL);

        container.add(header, splitLayout);
        return container;
    }

    /**
     * Creates the subscription JSON editor with event listeners.
     *
     * @return the configured editor
     */
    private JsonEditor createSubscriptionEditor() {
        val editor = createJsonEditor(true, 500);
        editor.setWidthFull();
        editor.setHeight("200px");
        editor.addDocumentChangedListener(this::handleSubscriptionDocumentChanged);
        return editor;
    }

    /**
     * Handles changes to the subscription document and triggers validation.
     *
     * @param event the document changed event
     */
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

    /**
     * Validates that a document does not exceed the maximum size limit.
     * Treats null or empty documents as invalid.
     *
     * @param document the document content to validate
     * @param documentType the type of document for error messages
     * @return true if document size is invalid, false if valid
     */
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

    /**
     * Clears the decision buffer and refreshes the grid.
     */
    private void clearDecisionBuffer() {
        decisionBuffer.clear();
        decisionsGridView.refreshAll();
    }

    /**
     * Resubscribes to the PDP if currently subscribed.
     */
    private void resubscribeIfActive() {
        if (isSubscriptionActive) {
            subscribe();
        }
    }

    /**
     * Formats JSON content in an editor with pretty-printing.
     *
     * @param editor the JSON editor to format
     */
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

    /**
     * Shows a simple notification to the user.
     *
     * @param message the message to display
     */
    private void showNotification(String message) {
        Notification.show(message);
    }

    /**
     * Builds the control buttons for subscription management.
     *
     * @return the controls layout
     */
    private Component buildControlButtons() {
        val layout = new HorizontalLayout();
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

    /**
     * Creates the play/stop subscription button.
     *
     * @return the configured button
     */
    private Button createPlayStopButton() {
        val button = new Button(VaadinIcon.PLAY.create());
        button.addThemeVariants(ButtonVariant.LUMO_ICON);
        button.setAriaLabel("Subscribe");
        button.setTooltipText("Start subscribing with authorization subscription.");
        button.addClickListener(event -> togglePlayStop());
        return button;
    }

    /**
     * Creates the scroll lock toggle button.
     *
     * @return the configured button
     */
    private Button createScrollLockButton() {
        val button = new Button(VaadinIcon.UNLOCK.create());
        button.addClickListener(event -> toggleScrollLock());
        return button;
    }

    /**
     * Creates the buffer size configuration field.
     *
     * @return the configured field
     */
    private IntegerField createBufferSizeField() {
        val field = new IntegerField();
        field.setMin(1);
        field.setMax(MAX_BUFFER_SIZE);
        field.setValue(DEFAULT_BUFFER_SIZE);
        field.setWidth("6.5em");
        field.setStepButtonsVisible(true);
        field.addValueChangeListener(event -> updateBufferSize(event.getValue()));
        return field;
    }

    /**
     * Toggles between play and stop states.
     */
    private void togglePlayStop() {
        if (isSubscriptionActive) {
            stopSubscription();
        } else {
            startSubscription();
        }
    }

    /**
     * Toggles scroll lock on/off.
     */
    private void toggleScrollLock() {
        if (isScrollLockActive) {
            deactivateScrollLock();
        } else {
            activateScrollLock();
        }
    }

    /**
     * Starts the PDP subscription with the current authorization subscription.
     */
    private void startSubscription() {
        isSubscriptionActive = true;
        updatePlayStopButtonForActiveState();

        if (Boolean.TRUE.equals(clearOnNewSubscriptionCheckBox.getValue())) {
            clearDecisionBuffer();
        }

        subscribe();
    }

    /**
     * Stops the active PDP subscription and cleans up resources.
     */
    private void stopSubscription() {
        if (activeSubscription != null && !activeSubscription.isDisposed()) {
            activeSubscription.dispose();
        }
        activeSubscription = null;

        isSubscriptionActive = false;
        updatePlayStopButtonForActiveState();
    }

    /**
     * Subscribes to the PDP with the current authorization subscription.
     * Disposes of any existing subscription first.
     * Handles errors by stopping subscription and notifying the user.
     */
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

    /**
     * Parses the authorization subscription from the editor.
     *
     * @return the parsed AuthorizationSubscription, or null if parsing fails
     */
    private AuthorizationSubscription parseAuthorizationSubscriptionFromEditor() {
        val subscriptionJson = subscriptionEditor.getDocument();
        try {
            return mapper.readValue(subscriptionJson, AuthorizationSubscription.class);
        } catch (JsonProcessingException exception) {
            log.debug("Failed to parse authorization subscription: {}", exception.getMessage());
            return null;
        }
    }

    /**
     * Updates the play/stop button appearance based on subscription state.
     */
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

    /**
     * Retrieves the current buffer size with null safety.
     *
     * @return the buffer size, or DEFAULT_BUFFER_SIZE if null
     */
    private int getBufferSize() {
        val size = bufferSizeField.getValue();
        return size != null ? size : DEFAULT_BUFFER_SIZE;
    }

    /**
     * Updates the buffer size and removes excess decisions.
     *
     * @param size the new buffer size
     */
    private void updateBufferSize(Integer size) {
        if (size == null) {
            return;
        }

        while (decisionBuffer.size() > size) {
            decisionBuffer.removeFirst();
        }
        decisionsGridView.refreshAll();
    }

    /**
     * Activates scroll lock to prevent automatic scrolling.
     */
    private void activateScrollLock() {
        isScrollLockActive = true;
        scrollLockButton.setIcon(VaadinIcon.UNLOCK.create());
        scrollLockButton.setAriaLabel("Scroll Lock");
        scrollLockButton
                .setTooltipText("Scroll Lock inactive. Click to stop automatically scrolling to last decision made.");
    }

    /**
     * Deactivates scroll lock to enable automatic scrolling.
     */
    private void deactivateScrollLock() {
        isScrollLockActive = false;
        scrollLockButton.setIcon(VaadinIcon.LOCK.create());
        scrollLockButton.setAriaLabel("Scroll Lock");
        scrollLockButton
                .setTooltipText("Scroll Lock active. Click to start automatically scrolling to last decision made.");
    }

    /**
     * Handles decision selection in the grid.
     *
     * @param selection the selection event
     */
    private void handleDecisionSelected(SelectionEvent<Grid<TracedDecision>, TracedDecision> selection) {
        updateDecisionDetailsView(selection.getFirstSelectedItem());
    }

    /**
     * Updates the decision details view with the selected decision.
     *
     * @param maybeTracedDecision optional traced decision
     */
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

    /**
     * Displays the decision JSON in the editor.
     *
     * @param tracedDecision the decision to display
     */
    private void displayDecisionJson(TracedDecision tracedDecision) {
        try {
            val prettyJson = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(tracedDecision.getAuthorizationDecision());
            decisionJsonEditor.setDocument(prettyJson);
        } catch (JsonProcessingException exception) {
            decisionJsonEditor.setDocument("Error reading decision:\n" + tracedDecision);
        }
    }

    /**
     * Displays the decision trace in the editor.
     *
     * @param tracedDecision the decision to display
     */
    private void displayDecisionTrace(TracedDecision tracedDecision) {
        val trace = tracedDecision.getTrace();
        decisionJsonTraceEditor.setDocument(trace.toPrettyString());
        traceGraphVisualization.setJsonData(trace.toPrettyString());
    }

    /**
     * Displays the decision report in both JSON and text formats.
     *
     * @param tracedDecision the decision to display
     */
    private void displayDecisionReport(TracedDecision tracedDecision) {
        val trace  = tracedDecision.getTrace();
        val report = ReportBuilderUtil.reduceTraceToReport(trace);

        decisionJsonReportEditor.setDocument(report.toPrettyString());
        reportTextArea.setValue(ReportTextRenderUtil.textReport(report, false, mapper));
    }

    /**
     * Displays decision errors in the errors area using safe DOM manipulation.
     * Avoids innerHTML to prevent potential HTML injection attacks.
     *
     * @param tracedDecision the decision to display
     */
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

    /**
     * Clears all decision details displays.
     */
    private void clearDecisionDetailsView() {
        decisionJsonEditor.setDocument("");
        decisionJsonTraceEditor.setDocument("");
        decisionJsonReportEditor.setDocument("");
        reportTextArea.setValue("");
        traceGraphVisualization.setJsonData("{}");
        errorsDisplayArea.removeAll();
        currentErrorReportText = "";
    }

    /**
     * Builds an aggregated error report from a collection of errors.
     *
     * @param errors the errors to aggregate
     * @return the formatted error report
     */
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

    /**
     * Handles a new decision from the PDP interceptor.
     * Adds to buffer with size management and updates the display.
     *
     * @param decision the new decision
     */
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

    /**
     * Builds the decision details tabbed view.
     *
     * @return the details view component
     */
    private Component buildDecisionDetailsView() {
        val tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.add("JSON", createDecisionJsonTab());
        tabSheet.add("Errors", createDecisionErrorsTab());
        tabSheet.add("Report", createTextReportTab());
        tabSheet.add("JSON Report", createDecisionJsonReportTab());
        tabSheet.add("JSON Trace", createDecisionJsonTraceTab());
        tabSheet.add("Trace Graph", createTraceGraphTab());
        return tabSheet;
    }

    /**
     * Creates the decision JSON display tab.
     *
     * @return the tab component
     */
    private Component createDecisionJsonTab() {
        decisionJsonEditor = createReadOnlyJsonEditor();
        return createLayoutWithClipboard(decisionJsonEditor, decisionJsonEditor::getDocument);
    }

    /**
     * Creates the decision JSON report display tab.
     *
     * @return the tab component
     */
    private Component createDecisionJsonReportTab() {
        decisionJsonReportEditor = createReadOnlyJsonEditor();
        return createLayoutWithClipboard(decisionJsonReportEditor, decisionJsonReportEditor::getDocument);
    }

    /**
     * Creates the decision JSON trace display tab.
     *
     * @return the tab component
     */
    private Component createDecisionJsonTraceTab() {
        decisionJsonTraceEditor = createReadOnlyJsonEditor();
        return createLayoutWithClipboard(decisionJsonTraceEditor, decisionJsonTraceEditor::getDocument);
    }

    /**
     * Creates the trace graph visualization tab.
     *
     * @return the tab component
     */
    private Component createTraceGraphTab() {
        traceGraphVisualization = new JsonGraphVisualization();
        traceGraphVisualization.setSizeFull();
        traceGraphVisualization.setJsonData("{}");
        return traceGraphVisualization;
    }

    /**
     * Creates the text report display tab.
     *
     * @return the tab component
     */
    private Component createTextReportTab() {
        reportTextArea = new TextArea();
        reportTextArea.setSizeFull();
        reportTextArea.setReadOnly(true);
        reportTextArea.getStyle().set("font-family", "monospace");
        return createLayoutWithClipboard(reportTextArea, reportTextArea::getValue);
    }

    /**
     * Creates the decision errors display tab.
     *
     * @return the tab component
     */
    private Component createDecisionErrorsTab() {
        errorsDisplayArea = new Div();
        errorsDisplayArea.setSizeFull();
        errorsDisplayArea.getStyle().set("overflow", "auto");
        return createLayoutWithClipboard(errorsDisplayArea, () -> currentErrorReportText);
    }

    /**
     * Creates a horizontal layout with a component and a clipboard button.
     *
     * @param component the main component to display
     * @param contentSupplier supplies the content to copy to clipboard
     * @return the layout containing both components
     */
    private Component createLayoutWithClipboard(Component component, Supplier<String> contentSupplier) {
        val layout = new HorizontalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.add(component, createClipboardButton(contentSupplier));
        return layout;
    }

    /**
     * Creates a clipboard button that copies content when clicked.
     *
     * @param contentSupplier supplies the content to copy
     * @return the configured button
     */
    private Button createClipboardButton(Supplier<String> contentSupplier) {
        val button = new Button(VaadinIcon.CLIPBOARD.create());
        button.getStyle().set("position", "absolute").set("top", "15px").set("right", "25px").set("z-index", "100");
        button.setAriaLabel("Copy to clipboard.");
        button.setTooltipText("Copy to clipboard.");
        button.addClickListener(event -> copyToClipboard(contentSupplier.get()));
        return button;
    }

    /**
     * Copies content to the system clipboard with size validation.
     *
     * @param content the content to copy
     */
    private void copyToClipboard(String content) {
        if (content == null || content.length() > MAX_CLIPBOARD_SIZE) {
            showNotification("Content too large to copy to clipboard");
            return;
        }

        UI.getCurrent().getPage().executeJs("window.copyToClipboard($0)", content);
        val notification = Notification.show("Content copied to clipboard.");
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
    }

    /**
     * Creates the variables tab header.
     *
     * @return the tab component
     */
    private Tab createVariablesTab() {
        val variablesValidationIcon = createIcon(VaadinIcon.CHECK_CIRCLE, COLOR_GREEN);
        variablesValidationIcon.getStyle().set(CSS_MARGIN_RIGHT, SPACING_HALF_EM);

        val label      = new Span(truncateTitle("Variables"));
        val tabContent = new HorizontalLayout(variablesValidationIcon, label);
        tabContent.setSpacing(false);
        tabContent.setAlignItems(FlexComponent.Alignment.CENTER);

        return new Tab(tabContent);
    }

    /**
     * Creates the variables editor layout.
     *
     * @return the editor layout component
     */
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

    /**
     * Creates the controls layout for variables (format button and validation
     * field).
     *
     * @return the controls layout
     */
    private Component createVariablesControlsLayout() {
        val layout = new HorizontalLayout();
        layout.setWidthFull();

        val formatButton = createFormatJsonButton(() -> formatJsonEditor(variablesEditor));
        layout.add(formatButton, variablesValidationField);

        return layout;
    }

    /**
     * Creates a format JSON button with the given action.
     *
     * @param formatAction the action to execute on click
     * @return the configured button
     */
    private Button createFormatJsonButton(Runnable formatAction) {
        val button = new Button(VaadinIcon.CURLY_BRACKETS.create());
        button.setAriaLabel("Format JSON");
        button.setTooltipText("Format JSON.");
        button.addClickListener(event -> formatAction.run());
        return button;
    }

    /**
     * Handles changes to the variables document and triggers validation.
     *
     * @param event the document changed event
     */
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

    /**
     * Parses variables from JSON string to Map.
     * Filters out variables with invalid names.
     *
     * @param variablesJson the JSON string containing variables
     * @return map of variable names to Val objects
     */
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
     * Creates a new policy editor tab.
     * Enforces maximum tab limit.
     */
    private void createNewPolicyTab() {
        if (policyTabContexts.size() >= MAX_POLICY_TABS) {
            showNotification("Maximum number of policy tabs (" + MAX_POLICY_TABS + ") reached");
            return;
        }

        val policyNumber = policyTabCounter.getAndIncrement();
        val policyName   = "Policy " + policyNumber;

        val components = createPolicyTabComponents(policyName);

        leftTabSheet.add(components.tab, components.editorLayout);
        leftTabSheet.setSelectedTab(components.tab);

        components.context.editor
                .addValidationFinishedListener(event -> handlePolicyValidation(components.context, event));
        components.context.editor.setDocument(DEFAULT_POLICY);
    }

    /**
     * Holds tab, context, and layout for a newly created policy tab.
     */
    private record PolicyTabComponents(Tab tab, PolicyTabContext context, VerticalLayout editorLayout) {}

    /**
     * Creates all components for a new policy tab in one coordinated method.
     * Eliminates brittle component extraction by index.
     *
     * @param policyName the name for the policy tab
     * @return components bundle with tab, context, and layout
     */
    private PolicyTabComponents createPolicyTabComponents(String policyName) {
        val editor          = createSaplEditor();
        val validationField = createValidationTextField();

        val editorLayout = new VerticalLayout();
        editorLayout.setSizeFull();
        editorLayout.setPadding(false);
        editorLayout.setSpacing(true);
        editorLayout.add(editor, validationField);

        val statusIcon = createIcon(VaadinIcon.QUESTION_CIRCLE, COLOR_ORANGE);
        statusIcon.getStyle().set(CSS_MARGIN_RIGHT, SPACING_HALF_EM);

        val titleLabel  = new Span(truncateTitle(policyName));
        val closeButton = createTabCloseButton();

        val tabContent = new HorizontalLayout(statusIcon, titleLabel, closeButton);
        tabContent.setSpacing(false);
        tabContent.setAlignItems(FlexComponent.Alignment.CENTER);

        val tab     = new Tab(tabContent);
        val context = new PolicyTabContext(editor, validationField, statusIcon, titleLabel);

        policyTabContexts.put(tab, context);
        closeButton.addClickListener(event -> handlePolicyTabClose(tab));

        return new PolicyTabComponents(tab, context, editorLayout);
    }

    /**
     * Creates a SAPL policy editor.
     *
     * @return the configured editor
     */
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

    /**
     * Creates a validation text field.
     *
     * @return the configured field
     */
    private TextField createValidationTextField() {
        val field = new TextField();
        field.setReadOnly(true);
        field.setWidthFull();
        return field;
    }

    /**
     * Creates a close button for policy tabs.
     *
     * @return the configured button
     */
    private Button createTabCloseButton() {
        val button = new Button(VaadinIcon.CLOSE_SMALL.create());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        button.getStyle().set(CSS_MARGIN_LEFT, SPACING_HALF_EM);
        return button;
    }

    /**
     * Handles closing of a policy tab.
     *
     * @param tab the tab to close
     */
    private void handlePolicyTabClose(Tab tab) {
        policyTabContexts.remove(tab);
        leftTabSheet.remove(tab);
        checkForPolicyNameCollisions();
        updatePolicyRetrievalPoint();
    }

    /**
     * Handles validation updates for policy documents.
     *
     * @param context the policy tab context
     * @param event the validation finished event
     */
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

    /**
     * Collects all policy documents from the policy tabs.
     *
     * @return list of policy document strings
     */
    private List<String> collectAllPolicyDocuments() {
        val documents = new ArrayList<String>();
        for (var context : policyTabContexts.values()) {
            documents.add(context.editor.getDocument());
        }
        return documents;
    }

    /**
     * Updates the Policy Retrieval Point with the current set of policy documents.
     * The PDP automatically re-evaluates when the PRP changes.
     */
    private void updatePolicyRetrievalPoint() {
        val documents = collectAllPolicyDocuments();
        this.policyDecisionPoint.updatePrp(documents);
    }

    /**
     * Updates the document name for a policy tab based on parsing results.
     *
     * @param context the policy tab context
     * @param parsedDocument the parsed SAPL document
     */
    private void updatePolicyDocumentName(PolicyTabContext context, io.sapl.prp.Document parsedDocument) {
        if (!parsedDocument.isInvalid()) {
            context.documentName = parsedDocument.name();
        }
        context.titleLabel.setText(truncateTitle(context.documentName));
    }

    /**
     * Updates the validation state for a policy tab.
     *
     * @param context the policy tab context
     * @param hasErrors whether the policy has errors
     * @param issues the validation issues
     */
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

    /**
     * Checks all policy tabs for name collisions and updates their visual
     * indicators.
     * Tabs with duplicate names receive a warning icon and collision message.
     */
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

    /**
     * Groups policy tabs by their document names.
     *
     * @return map of document names to tabs with that name
     */
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

    /**
     * Finds document names that appear multiple times.
     *
     * @param documentNamesToTabs map of names to tabs
     * @return set of duplicate names
     */
    private Set<String> findDuplicateDocumentNames(Map<String, List<Tab>> documentNamesToTabs) {
        return documentNamesToTabs.entrySet().stream().filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /**
     * Sets collision warning state on a policy context.
     *
     * @param context the policy tab context
     */
    private void setCollisionWarningState(PolicyTabContext context) {
        context.statusIcon.setIcon(VaadinIcon.WARNING);
        context.statusIcon.setColor(COLOR_ORANGE);
        val result = ValidationResult.warning("Name collision: '" + context.documentName + "'");
        updateValidationField(context.validationField, result);
    }

    /**
     * Restores normal validation state after a collision is resolved.
     * Re-parses the document to check for errors.
     *
     * @param context the policy tab context
     */
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

    /**
     * Truncates a title to MAX_TITLE_LENGTH characters, adding ellipsis if
     * necessary.
     *
     * @param title the title to truncate
     * @return the truncated title with "..." appended if it exceeds the maximum
     * length
     */
    private String truncateTitle(String title) {
        if (title == null) {
            return "";
        }
        if (title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }
        return title.substring(0, MAX_TITLE_LENGTH) + "...";
    }

    /**
     * Builds the application header with title and algorithm selector.
     *
     * @return the header component
     */
    private HorizontalLayout buildHeader() {
        val header = new HorizontalLayout();
        header.addClassName(Gap.MEDIUM);
        header.setWidth("100%");
        header.setHeight("min-content");
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        val title = new Span("SAPL Playground");
        title.getStyle().set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "bold");

        val combiningAlgorithmComboBox = createCombiningAlgorithmComboBox();

        header.add(title, combiningAlgorithmComboBox);
        return header;
    }

    /**
     * Creates the combining algorithm selection combo box.
     *
     * @return the configured combo box
     */
    private ComboBox<PolicyDocumentCombiningAlgorithm> createCombiningAlgorithmComboBox() {
        val comboBox = new ComboBox<PolicyDocumentCombiningAlgorithm>("Combining Algorithm");
        comboBox.setItems(PolicyDocumentCombiningAlgorithm.values());
        comboBox.setItemLabelGenerator(PlaygroundView::formatAlgorithmName);
        comboBox.addValueChangeListener(this::handleAlgorithmChange);
        comboBox.setWidth("20em");
        comboBox.setValue(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES);
        return comboBox;
    }

    /**
     * Handles changes to the combining algorithm selection.
     *
     * @param event the value change event
     */
    private void handleAlgorithmChange(ValueChangeEvent<PolicyDocumentCombiningAlgorithm> event) {
        this.policyDecisionPoint.setCombiningAlgorithm(event.getValue());
    }

    /**
     * Formats an algorithm enum value for display.
     *
     * @param algorithm the algorithm to format
     * @return the formatted name
     */
    private static String formatAlgorithmName(PolicyDocumentCombiningAlgorithm algorithm) {
        return StringUtils.capitalize(algorithm.toString().replace('_', ' ').toLowerCase());
    }

    /**
     * Builds the application footer.
     *
     * @return the footer component
     */
    private HorizontalLayout buildFooter() {
        val footer = new HorizontalLayout();
        footer.addClassName(Gap.MEDIUM);
        footer.setWidth("100%");
        footer.setHeight("min-content");
        val footerText = new Span("The Footer");
        footer.add(footerText);
        return footer;
    }

    /**
     * Creates a read-only JSON editor with standard configuration.
     *
     * @return the configured editor
     */
    private JsonEditor createReadOnlyJsonEditor() {
        val editor = createJsonEditor(false, 0);
        editor.getElement().getStyle().set("width", "100%");
        return editor;
    }

    /**
     * Creates a JSON editor with specified configuration.
     *
     * @param hasLineNumbers whether to show line numbers
     * @param textUpdateDelay delay in ms before text update events
     * @return the configured editor
     */
    private JsonEditor createJsonEditor(boolean hasLineNumbers, int textUpdateDelay) {
        val config = new JsonEditorConfiguration();
        config.setHasLineNumbers(hasLineNumbers);
        config.setTextUpdateDelay(textUpdateDelay);
        config.setDarkTheme(true);
        config.setReadOnly(!hasLineNumbers);
        config.setLint(hasLineNumbers);
        return new JsonEditor(config);
    }

    /**
     * Creates an icon with specified type and color.
     *
     * @param iconType the Vaadin icon type
     * @param color the color for the icon
     * @return the configured icon
     */
    private Icon createIcon(VaadinIcon iconType, String color) {
        val icon = iconType.create();
        icon.setColor(color);
        return icon;
    }

    /**
     * Updates a validation field with a validation result.
     *
     * @param field the validation field to update
     * @param result the validation result
     */
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
