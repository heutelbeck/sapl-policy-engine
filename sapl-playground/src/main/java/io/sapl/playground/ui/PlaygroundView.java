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
import com.vaadin.flow.component.*;
import com.vaadin.flow.component.HasValue.ValueChangeEvent;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dependency.JavaScript;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.dialog.Dialog;
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
import io.sapl.playground.config.PermalinkConfiguration;
import io.sapl.playground.domain.*;
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

    private static final int DEFAULT_BUFFER_SIZE     = 10;
    private static final int EXAMPLE_PREFIX_LENGTH   = 8;
    private static final int MAX_BUFFER_SIZE         = 50;
    private static final int MAX_CLIPBOARD_SIZE      = 10_000_000;
    private static final int MAX_DOCUMENT_SIZE_BYTES = 1_000_000;
    private static final int MAX_FRAGMENT_LENGTH     = 64_000;
    private static final int MAX_POLICY_TABS         = 20;
    private static final int MAX_TITLE_LENGTH        = 15;
    private static final int PERMALINK_PREFIX_LENGTH = 10;

    private static final double DECISIONS_PANEL_HEIGHT = 40.0D;
    private static final double LEFT_PANEL_WIDTH       = 66.67D;

    private static final String EDITOR_CONFIGURATION_ID = "playground";

    private static final String COLOR_GREEN  = "green";
    private static final String COLOR_ORANGE = "orange";
    private static final String COLOR_RED    = "red";

    private static final String CSS_BACKGROUND_COLOR = "background-color";
    private static final String CSS_BORDER_BOTTOM    = "border-bottom";
    private static final String CSS_COLOR            = "color";
    private static final String CSS_FLEX_GROW        = "flex-grow";
    private static final String CSS_FONT_FAMILY      = "font-family";
    private static final String CSS_FONT_SIZE        = "font-size";
    private static final String CSS_FONT_WEIGHT      = "font-weight";
    private static final String CSS_GAP              = "gap";
    private static final String CSS_MARGIN           = "margin";
    private static final String CSS_MARGIN_LEFT      = "margin-left";
    private static final String CSS_MARGIN_RIGHT     = "margin-right";
    private static final String CSS_MARGIN_TOP       = "margin-top";
    private static final String CSS_MIN_HEIGHT       = "min-height";
    private static final String CSS_OVERFLOW         = "overflow";
    private static final String CSS_OVERFLOW_WRAP    = "overflow-wrap";
    private static final String CSS_PADDING          = "padding";
    private static final String CSS_POSITION         = "position";
    private static final String CSS_RIGHT            = "right";
    private static final String CSS_TEXT_ALIGN       = "text-align";
    private static final String CSS_TOP              = "top";
    private static final String CSS_WHITE_SPACE      = "white-space";
    private static final String CSS_WIDTH            = "width";
    private static final String CSS_Z_INDEX          = "z-index";

    private static final String CSS_VALUE_ABSOLUTE            = "absolute";
    private static final String CSS_VALUE_AUTO                = "auto";
    private static final String CSS_VALUE_BREAK_WORD          = "break-word";
    private static final String CSS_VALUE_CENTER              = "center";
    private static final String CSS_VALUE_CONTRAST_5PCT       = "var(--lumo-contrast-5pct)";
    private static final String CSS_VALUE_CONTRAST_10PCT_LINE = "1px solid var(--lumo-contrast-10pct)";
    private static final String CSS_VALUE_ERROR_BG            = "#282a36";
    private static final String CSS_VALUE_ERROR_FG            = "#f8f8f2";
    private static final String CSS_VALUE_FONT_SIZE_XL        = "var(--lumo-font-size-xl)";
    private static final String CSS_VALUE_MONOSPACE           = "monospace";
    private static final String CSS_VALUE_ONE                 = "1";
    private static final String CSS_VALUE_ONE_EM              = "1em";
    private static final String CSS_VALUE_PADDING_BOTTOM_XS   = "0 0 var(--lumo-space-xs) 0";
    private static final String CSS_VALUE_PRE_WRAP            = "pre-wrap";
    private static final String CSS_VALUE_SIZE_0_25EM         = "0.25em";
    private static final String CSS_VALUE_SIZE_0_875EM        = "0.875em";
    private static final String CSS_VALUE_SIZE_100            = "100";
    private static final String CSS_VALUE_SIZE_100PCT         = "100%";
    private static final String CSS_VALUE_SIZE_15PX           = "15px";
    private static final String CSS_VALUE_SIZE_16EM           = "16em";
    private static final String CSS_VALUE_SIZE_200PX          = "200px";
    private static final String CSS_VALUE_SIZE_25PX           = "25px";
    private static final String CSS_VALUE_SIZE_2_5EM          = "2.5em";
    private static final String CSS_VALUE_SIZE_600PX          = "600px";
    private static final String CSS_VALUE_SPACE_M             = "var(--lumo-space-m)";
    private static final String CSS_VALUE_SPACE_S             = "var(--lumo-space-s)";
    private static final String CSS_VALUE_SPACE_XS            = "var(--lumo-space-xs)";
    private static final String CSS_VALUE_TAB_PADDING         = "0.5em 0.75em";
    private static final String CSS_VALUE_WEIGHT_600          = "600";

    private static final String FRAGMENT_PREFIX_EXAMPLE   = "example/";
    private static final String FRAGMENT_PREFIX_PERMALINK = "permalink/";

    private static final String JS_COPY_TO_CLIPBOARD     = "window.copyToClipboard($0)";
    private static final String JS_GET_URL_FRAGMENT      = "return window.getUrlFragment()";
    private static final String JS_REFRESH_CODEMIRROR    = "if (this.editor && this.editor.refresh) { this.editor.refresh(); }";
    private static final String JS_SET_FRAGMENT_EXAMPLE  = "window.location.hash = 'example/' + $0";
    private static final String JS_SETUP_HASH_LISTENER   = """
            window.playgroundHashListener = function() {
                $0.$server.handleHashChange(window.location.hash.substring(1));
            };
            window.addEventListener('hashchange', window.playgroundHashListener);
            """;
    private static final String JS_CLEANUP_HASH_LISTENER = """
            if (window.playgroundHashListener) {
                window.removeEventListener('hashchange', window.playgroundHashListener);
                delete window.playgroundHashListener;
            }
            """;

    private static final String LABEL_AUTO_CLEAR                 = "Auto Clear";
    private static final String LABEL_AUTHORIZATION_SUBSCRIPTION = "Authorization Subscription";
    private static final String LABEL_BUFFER                     = "Buffer";
    private static final String LABEL_CLOSE                      = "Close";
    private static final String LABEL_COMBINING_ALGORITHM        = "Combining Algorithm";
    private static final String LABEL_COPY_TO_CLIPBOARD          = "Copy to Clipboard";
    private static final String LABEL_DECISIONS                  = "Decisions";
    private static final String LABEL_ERRORS                     = "Errors";
    private static final String LABEL_EXAMPLES                   = "Examples";
    private static final String LABEL_FORMAT_JSON                = "Format JSON";
    private static final String LABEL_JSON                       = "JSON";
    private static final String LABEL_JSON_REPORT                = "JSON Report";
    private static final String LABEL_JSON_TRACE                 = "JSON Trace";
    private static final String LABEL_LOAD_EXAMPLE               = "Load Example";
    private static final String LABEL_NEW_POLICY                 = "+ New Policy";
    private static final String LABEL_REPORT                     = "Report";
    private static final String LABEL_SAPL_HOMEPAGE              = "SAPL Homepage";
    private static final String LABEL_SAPL_LOGO                  = "SAPL Logo";
    private static final String LABEL_SAPL_PLAYGROUND            = "SAPL Playground";
    private static final String LABEL_SCROLL_LOCK                = "Scroll Lock";
    private static final String LABEL_SHARE                      = "Share";
    private static final String LABEL_SHARE_PLAYGROUND_STATE     = "Share Playground State";
    private static final String LABEL_SUBSCRIBE                  = "Subscribe";
    private static final String LABEL_TRACE_GRAPH                = "Trace Graph";
    private static final String LABEL_UNSUBSCRIBE                = "Unsubscribe";
    private static final String LABEL_VARIABLES                  = "Variables";

    private static final String MESSAGE_CANNOT_BE_EMPTY           = " cannot be empty";
    private static final String MESSAGE_CANNOT_FORMAT_JSON        = "Cannot format invalid JSON.";
    private static final String MESSAGE_CONTENT_COPIED            = "Content copied to clipboard.";
    private static final String MESSAGE_CONTENT_TOO_LARGE         = "Content too large to copy to clipboard";
    private static final String MESSAGE_DOCUMENT_TOO_LARGE        = "Document too large";
    private static final String MESSAGE_ERROR_READING_DECISION    = "Error reading decision:\n";
    private static final String MESSAGE_ERRORS_SUFFIX             = " error(s)";
    private static final String MESSAGE_EXAMPLE_LOAD_CONFIRMATION = "This will replace current policies, subscription, and variables.";
    private static final String MESSAGE_EXAMPLE_TOO_MANY_POLICIES = "Example has too many policies. Maximum: ";
    private static final String MESSAGE_EXCEEDS_MAX_SIZE          = " exceeds maximum size of ";
    private static final String MESSAGE_FAILED_CREATE_PERMALINK   = "Failed to create shareable link: ";
    private static final String MESSAGE_FAILED_LOAD_PERMALINK     = "Failed to load permalink. Loading default state instead.";
    private static final String MESSAGE_FAILED_SUBSCRIBE          = "Failed to subscribe: ";
    private static final String MESSAGE_INVALID_SUBSCRIPTION      = "Invalid authorization subscription";
    private static final String MESSAGE_LOADED_EXAMPLE            = "Loaded example: ";
    private static final String MESSAGE_LOADED_PERMALINK          = "Loaded state from permalink";
    private static final String MESSAGE_MAX_TABS_REACHED          = "Maximum number of policy tabs (";
    private static final String MESSAGE_NAME_COLLISION_PREFIX     = "Name collision: '";
    private static final String MESSAGE_NAME_COLLISION_SUFFIX     = "'";
    private static final String MESSAGE_NO_ERRORS                 = "No Errors";
    private static final String MESSAGE_REACHED_SUFFIX            = ") reached";
    private static final String MESSAGE_SHARE_EXPLANATION         = "Share this link to preserve and share the current playground state including all policies, subscription, variables, and combining algorithm.";
    private static final String MESSAGE_STATE_TOO_MANY_POLICIES   = "State has too many policies. Maximum: ";
    private static final String MESSAGE_SUBSCRIPTION_ERROR        = "Subscription error: ";
    private static final String MESSAGE_SUFFIX_KB                 = "KB";
    private static final String MESSAGE_SYNTAX_ERROR              = "Syntax error";

    private static final String POLICY_NAME_PREFIX  = "Policy ";
    private static final String POLICY_NAME_UNKNOWN = "unknown";

    private static final String RESOURCE_LOGO = "logo-header.png";

    private static final String TARGET_BLANK = "_blank";

    private static final String TOOLTIP_COPY_TO_CLIPBOARD     = "Copy to clipboard.";
    private static final String TOOLTIP_CREATE_SHAREABLE_LINK = "Create shareable link";
    private static final String TOOLTIP_FORMAT_JSON           = "Format JSON.";
    private static final String TOOLTIP_SCROLL_LOCK_ACTIVE    = "Scroll Lock active. Click to start automatically scrolling to last decision made.";
    private static final String TOOLTIP_SCROLL_LOCK_INACTIVE  = "Scroll Lock inactive. Click to stop automatically scrolling to last decision made.";
    private static final String TOOLTIP_START_SUBSCRIBING     = "Start subscribing with authorization subscription.";
    private static final String TOOLTIP_STOP_SUBSCRIBING      = "Stop Subscribing.";

    private static final String URL_SAPL_HOMEPAGE = "https://sapl.io";
    private static final String URL_HASH_PREFIX   = "#permalink/";

    private static final String DEFAULT_POLICY = """
            policy "new policy %d"
            permit false
            """;

    private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    private final transient ObjectMapper                  mapper;
    private final transient PlaygroundValidator           validator;
    private final transient DocumentationDrawer           documentationDrawer;
    private final transient PlaygroundPolicyDecisionPoint policyDecisionPoint;
    private final transient PermalinkService              permalinkService;
    private final transient PermalinkConfiguration        permalinkConfiguration;

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

    private final Map<Tab, PolicyTabContext> policyTabContexts = new LinkedHashMap<>();
    private final AtomicInteger              policyTabCounter  = new AtomicInteger(1);
    private final AtomicInteger              newPolicyCounter  = new AtomicInteger(1);

    /**
     * Container for policy tab components and state.
     * Maintains references to UI components and document metadata for each policy
     * tab.
     * Used to coordinate updates between the editor, validation display, and tab
     * title.
     */
    private static class PolicyTabContext {
        /** SAPL policy editor component */
        SaplEditor editor;
        /** Validation feedback text field */
        TextField  validationField;
        /** Visual status indicator icon (check/error/warning) */
        Icon       statusIcon;
        /** Tab title label displaying policy name */
        Span       titleLabel;
        /** Parsed policy document name from SAPL content */
        String     documentName;

        /**
         * Creates a new policy tab context with the specified components.
         *
         * @param editor the SAPL editor component
         * @param validationField the validation feedback field
         * @param statusIcon the status indicator icon
         * @param titleLabel the tab title label
         */
        PolicyTabContext(SaplEditor editor, TextField validationField, Icon statusIcon, Span titleLabel) {
            this.editor          = editor;
            this.validationField = validationField;
            this.statusIcon      = statusIcon;
            this.titleLabel      = titleLabel;
            this.documentName    = POLICY_NAME_UNKNOWN;
        }
    }

    public PlaygroundView(ObjectMapper mapper,
            PlaygroundPolicyDecisionPoint policyDecisionPoint,
            PlaygroundValidator validator,
            DocumentationDrawer documentationDrawer,
            PermalinkService permalinkService,
            PermalinkConfiguration permalinkConfiguration) {
        this.mapper                 = mapper;
        this.validator              = validator;
        this.policyDecisionPoint    = policyDecisionPoint;
        this.documentationDrawer    = documentationDrawer;
        this.permalinkService       = permalinkService;
        this.permalinkConfiguration = permalinkConfiguration;

        buildAndAddComponents();
        initializeDefaultValues();

        addDetachListener(event -> cleanup());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        checkInitialFragment();
        setupHashChangeListener();
    }

    /**
     * Cleans up resources when view is detached.
     * Stops any active subscriptions to prevent memory leaks.
     */
    private void cleanup() {
        stopSubscription();
        cleanupHashChangeListener();
    }

    /**
     * Sets default values for subscription and variables editors.
     * Loads the default time-based example on startup.
     */
    private void initializeDefaultValues() {
        val defaultExample = PlaygroundExamples.DEFAULT_SETTINGS;

        subscriptionEditor.setDocument(defaultExample.subscription());
        variablesEditor.setDocument(defaultExample.variables());

        setCombiningAlgorithmQuietly(defaultExample.combiningAlgorithm());

        Tab firstTab = null;
        if (!defaultExample.policies().isEmpty()) {
            for (val policy : defaultExample.policies()) {
                val tab = createAndAddPolicyTab(policy);
                if (firstTab == null) {
                    firstTab = tab;
                }
            }
        }

        if (firstTab != null) {
            leftTabSheet.setSelectedTab(firstTab);
        }
    }

    /**
     * Builds the main component structure and adds to the view.
     * Creates header, main content area, and documentation drawer toggle.
     * Initializes scroll lock state.
     */
    private void buildAndAddComponents() {
        val header = buildHeader();
        val main   = buildMainContent();

        getContent().setSizeFull();
        getContent().setPadding(false);
        getContent().setSpacing(false);

        getContent().add(header, main, documentationDrawer.getToggleButton());

        deactivateScrollLock();
    }

    /**
     * Handles new authorization decisions from the PDP.
     * Ensures UI updates occur on the UI thread.
     *
     * @param tracedDecision the decision to process
     */
    private void interceptDecision(final TracedDecision tracedDecision) {
        getUI().ifPresent(ui -> ui.access(() -> handleNewDecision(tracedDecision)));
    }

    /**
     * Creates the main content split layout.
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
     * Creates the left panel containing policy and variables editors.
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

        val newPolicyButton = createNewPolicyButton();
        leftTabSheet.setSuffixComponent(newPolicyButton);

        leftTabSheet.addSelectedChangeListener(event -> refreshVariablesEditorIfSelected(event.getSelectedTab()));

        layout.add(leftTabSheet);
        return layout;
    }

    /**
     * Creates button for adding new policy tabs.
     *
     * @return the new policy button
     */
    private Button createNewPolicyButton() {
        val button = new Button(LABEL_NEW_POLICY);
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        button.addClickListener(event -> createNewPolicyTab());
        return button;
    }

    /**
     * Refreshes the variables editor when its tab is selected.
     * Ensures the CodeMirror editor properly reflows.
     *
     * @param selectedTab the currently selected tab
     */
    private void refreshVariablesEditorIfSelected(Tab selectedTab) {
        if (selectedTab == variablesTab && variablesEditor != null) {
            variablesEditor.getElement().executeJs(JS_REFRESH_CODEMIRROR);
        }
    }

    /**
     * Creates the right panel with subscription and decisions sections.
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
     * Creates the authorization subscription section.
     *
     * @return the subscription section component
     */
    private Component buildSubscriptionSection() {
        val container = new VerticalLayout();
        container.setPadding(false);
        container.setSpacing(false);
        container.getStyle().set(CSS_PADDING, CSS_VALUE_SPACE_M);

        subscriptionValidationField = new TextField();
        subscriptionValidationField.setReadOnly(true);
        subscriptionValidationField.setWidthFull();

        val header = new H5(LABEL_AUTHORIZATION_SUBSCRIPTION);
        header.getStyle().set(CSS_MARGIN, CSS_VALUE_ONE).set(CSS_PADDING, CSS_VALUE_PADDING_BOTTOM_XS)
                .set(CSS_BORDER_BOTTOM, CSS_VALUE_CONTRAST_10PCT_LINE);

        subscriptionEditor = createSubscriptionEditor();
        subscriptionEditor.getStyle().set(CSS_MARGIN_TOP, CSS_VALUE_SPACE_XS);

        val controlsLayout = createSubscriptionControlsLayout();
        controlsLayout.getStyle().set(CSS_MARGIN_TOP, CSS_VALUE_SPACE_XS);

        container.add(header, subscriptionEditor, controlsLayout);
        return container;
    }

    /**
     * Creates controls layout for subscription editor.
     *
     * @return the controls component
     */
    private Component createSubscriptionControlsLayout() {
        val layout = new HorizontalLayout();
        layout.setWidthFull();

        val formatButton = createFormatJsonButton(() -> formatJsonEditor(subscriptionEditor));
        layout.add(formatButton, subscriptionValidationField);

        return layout;
    }

    /**
     * Creates the decisions section with grid and inspector.
     *
     * @return the decisions section component
     */
    private Component buildDecisionsSection() {
        val container = new VerticalLayout();
        container.setSizeFull();
        container.setPadding(false);
        container.setSpacing(false);
        container.getStyle().set(CSS_PADDING, CSS_VALUE_SPACE_M);

        val header = new H5(LABEL_DECISIONS);
        header.getStyle().set(CSS_MARGIN, CSS_VALUE_ONE).set(CSS_PADDING, CSS_VALUE_PADDING_BOTTOM_XS)
                .set(CSS_BORDER_BOTTOM, CSS_VALUE_CONTRAST_10PCT_LINE);

        decisionsGrid     = new DecisionsGrid();
        decisionsGridView = decisionsGrid.setItems(decisionBuffer);
        decisionsGrid.addSelectionListener(this::handleDecisionSelected);
        decisionsGrid.getStyle().set(CSS_MARGIN_TOP, CSS_VALUE_SPACE_XS);

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

    /**
     * Creates the subscription JSON editor.
     *
     * @return the subscription editor
     */
    private JsonEditor createSubscriptionEditor() {
        val editor = createJsonEditor(true, 500);
        editor.setWidthFull();
        editor.setHeight(CSS_VALUE_SIZE_200PX);
        editor.addDocumentChangedListener(this::handleSubscriptionDocumentChanged);
        return editor;
    }

    /**
     * Handles changes to the subscription document.
     *
     * @param event the document change event
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
     * Checks if document size exceeds limits.
     *
     * @param document the document to check
     * @param documentType the type of document for error messages
     * @return true if document size is invalid
     */
    private boolean hasInvalidDocumentSize(String document, String documentType) {
        if (document == null || document.isEmpty()) {
            showNotification(documentType + MESSAGE_CANNOT_BE_EMPTY);
            return true;
        }

        if (document.length() > MAX_DOCUMENT_SIZE_BYTES) {
            val sizeInKb = MAX_DOCUMENT_SIZE_BYTES / 1024;
            showNotification(documentType + MESSAGE_EXCEEDS_MAX_SIZE + sizeInKb + MESSAGE_SUFFIX_KB);
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
     * Resubscribes to PDP if subscription is active.
     */
    private void resubscribeIfActive() {
        if (isSubscriptionActive) {
            subscribe();
        }
    }

    /**
     * Formats JSON content in an editor.
     *
     * @param editor the editor to format
     */
    private void formatJsonEditor(JsonEditor editor) {
        val jsonString = editor.getDocument();
        try {
            val json = mapper.readTree(jsonString);
            if (json instanceof MissingNode) {
                showNotification(MESSAGE_CANNOT_FORMAT_JSON);
                return;
            }
            val formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            editor.setDocument(formattedJson);
        } catch (JsonProcessingException exception) {
            showNotification(MESSAGE_CANNOT_FORMAT_JSON);
        }
    }

    /**
     * Shows a notification message to the user.
     *
     * @param message the message to display
     */
    private void showNotification(String message) {
        Notification.show(message);
    }

    /**
     * Creates control buttons for decision playback.
     *
     * @return the control buttons component
     */
    private Component buildControlButtons() {
        val layout = new HorizontalLayout();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.getStyle().set(CSS_PADDING, CSS_VALUE_SPACE_XS).set(CSS_BORDER_BOTTOM, CSS_VALUE_CONTRAST_10PCT_LINE);

        playStopButton   = createPlayStopButton();
        scrollLockButton = createScrollLockButton();
        bufferSizeField  = createBufferSizeField();

        clearOnNewSubscriptionCheckBox = new Checkbox(LABEL_AUTO_CLEAR);
        clearOnNewSubscriptionCheckBox.setValue(true);

        val bufferLabel = new NativeLabel(LABEL_BUFFER);

        layout.add(playStopButton, scrollLockButton, bufferLabel, bufferSizeField, clearOnNewSubscriptionCheckBox);
        return layout;
    }

    /**
     * Creates the play/stop button.
     *
     * @return the play/stop button
     */
    private Button createPlayStopButton() {
        val button = new Button(VaadinIcon.PLAY.create());
        button.addThemeVariants(ButtonVariant.LUMO_ICON);
        button.setAriaLabel(LABEL_SUBSCRIBE);
        button.setTooltipText(TOOLTIP_START_SUBSCRIBING);
        button.addClickListener(event -> togglePlayStop());
        return button;
    }

    /**
     * Creates the scroll lock button.
     *
     * @return the scroll lock button
     */
    private Button createScrollLockButton() {
        val button = new Button(VaadinIcon.UNLOCK.create());
        button.addClickListener(event -> toggleScrollLock());
        return button;
    }

    /**
     * Creates the buffer size field.
     *
     * @return the buffer size field
     */
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

    /**
     * Toggles play/stop state of subscription.
     */
    private void togglePlayStop() {
        if (isSubscriptionActive) {
            stopSubscription();
        } else {
            startSubscription();
        }
    }

    /**
     * Toggles scroll lock state.
     */
    private void toggleScrollLock() {
        if (isScrollLockActive) {
            deactivateScrollLock();
        } else {
            activateScrollLock();
        }
    }

    /**
     * Starts subscription to PDP decisions.
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
     * Stops active subscription to PDP.
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
     * Subscribes to PDP for authorization decisions.
     * Creates a new subscription to the policy decision point with the current
     * authorization subscription. Handles errors and completion gracefully.
     */
    private void subscribe() {
        disposeExistingSubscription();

        val authorizationSubscription = parseAuthorizationSubscriptionFromEditor();
        if (authorizationSubscription == null) {
            showNotification(MESSAGE_INVALID_SUBSCRIPTION);
            stopSubscription();
            return;
        }

        try {
            activeSubscription = policyDecisionPoint.decide(authorizationSubscription).subscribe(
                    this::interceptDecision, this::handleSubscriptionError, this::handleSubscriptionComplete);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            log.error("Failed to create subscription", exception);
            stopSubscription();
            showNotification(MESSAGE_FAILED_SUBSCRIBE + exception.getMessage());
        }
    }

    /**
     * Disposes of existing subscription if present.
     */
    private void disposeExistingSubscription() {
        if (activeSubscription != null && !activeSubscription.isDisposed()) {
            activeSubscription.dispose();
        }
    }

    /**
     * Handles subscription errors from the PDP.
     * Logs error and updates UI to inform user.
     *
     * @param error the subscription error
     */
    private void handleSubscriptionError(Throwable error) {
        log.error("Error in PDP subscription", error);
        getUI().ifPresent(userInterface -> userInterface.access(() -> {
            stopSubscription();
            showNotification(MESSAGE_SUBSCRIPTION_ERROR + error.getMessage());
        }));
    }

    /**
     * Handles subscription completion from the PDP.
     * Cleans up subscription reference.
     */
    private void handleSubscriptionComplete() {
        log.debug("PDP subscription completed");
        activeSubscription = null;
    }

    /**
     * Parses authorization subscription from editor content.
     *
     * @return the parsed subscription or null if invalid
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
     * Updates play/stop button appearance based on subscription state.
     */
    private void updatePlayStopButtonForActiveState() {
        if (isSubscriptionActive) {
            playStopButton.setIcon(VaadinIcon.STOP.create());
            playStopButton.setAriaLabel(LABEL_UNSUBSCRIBE);
            playStopButton.setTooltipText(TOOLTIP_STOP_SUBSCRIBING);
        } else {
            playStopButton.setIcon(VaadinIcon.PLAY.create());
            playStopButton.setAriaLabel(LABEL_SUBSCRIBE);
            playStopButton.setTooltipText(TOOLTIP_START_SUBSCRIBING);
        }
    }

    /**
     * Gets the current buffer size from the field.
     *
     * @return the buffer size
     */
    private int getBufferSize() {
        val size = bufferSizeField.getValue();
        return size != null ? size : DEFAULT_BUFFER_SIZE;
    }

    /**
     * Updates buffer size and trims excess decisions.
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
     * Activates scroll lock to prevent auto-scrolling.
     */
    private void activateScrollLock() {
        isScrollLockActive = true;
        scrollLockButton.setIcon(VaadinIcon.UNLOCK.create());
        scrollLockButton.setAriaLabel(LABEL_SCROLL_LOCK);
        scrollLockButton.setTooltipText(TOOLTIP_SCROLL_LOCK_INACTIVE);
    }

    /**
     * Deactivates scroll lock to enable auto-scrolling.
     */
    private void deactivateScrollLock() {
        isScrollLockActive = false;
        scrollLockButton.setIcon(VaadinIcon.LOCK.create());
        scrollLockButton.setAriaLabel(LABEL_SCROLL_LOCK);
        scrollLockButton.setTooltipText(TOOLTIP_SCROLL_LOCK_ACTIVE);
    }

    /**
     * Handles selection of decision in the grid.
     *
     * @param selection the selection event
     */
    private void handleDecisionSelected(SelectionEvent<Grid<TracedDecision>, TracedDecision> selection) {
        updateDecisionDetailsView(selection.getFirstSelectedItem());
    }

    /**
     * Updates the decision details view with selected decision.
     *
     * @param maybeTracedDecision the optional traced decision
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
     * Displays decision JSON in the editor.
     *
     * @param tracedDecision the decision to display
     */
    private void displayDecisionJson(TracedDecision tracedDecision) {
        try {
            val prettyJson = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(tracedDecision.getAuthorizationDecision());
            decisionJsonEditor.setDocument(prettyJson);
        } catch (JsonProcessingException exception) {
            decisionJsonEditor.setDocument(MESSAGE_ERROR_READING_DECISION + tracedDecision);
        }
    }

    /**
     * Displays decision trace information.
     *
     * @param tracedDecision the decision to display
     */
    private void displayDecisionTrace(TracedDecision tracedDecision) {
        val trace = tracedDecision.getTrace();
        decisionJsonTraceEditor.setDocument(trace.toPrettyString());
        traceGraphVisualization.setJsonData(trace.toPrettyString());
    }

    /**
     * Displays decision report information.
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
     * Displays errors from the decision.
     *
     * @param tracedDecision the decision to display
     */
    private void displayDecisionErrors(TracedDecision tracedDecision) {
        val errors          = tracedDecision.getErrorsFromTrace();
        val plainTextReport = buildAggregatedErrorReport(errors);

        errorsDisplayArea.removeAll();
        val pre = new Pre(plainTextReport);
        pre.getStyle().set(CSS_WHITE_SPACE, CSS_VALUE_PRE_WRAP).set(CSS_FONT_FAMILY, CSS_VALUE_MONOSPACE)
                .set(CSS_OVERFLOW, CSS_VALUE_AUTO).set(CSS_OVERFLOW_WRAP, CSS_VALUE_BREAK_WORD)
                .set(CSS_BACKGROUND_COLOR, CSS_VALUE_ERROR_BG).set(CSS_COLOR, CSS_VALUE_ERROR_FG)
                .set(CSS_PADDING, CSS_VALUE_ONE_EM);
        errorsDisplayArea.add(pre);

        currentErrorReportText = plainTextReport;
    }

    /**
     * Clears all decision detail displays.
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
     * Builds aggregated error report from collection of errors.
     *
     * @param errors the errors to report
     * @return the aggregated report text
     */
    private String buildAggregatedErrorReport(Collection<Val> errors) {
        if (errors.isEmpty()) {
            return MESSAGE_NO_ERRORS;
        }

        val reportBuilder = new StringBuilder();
        for (var error : errors) {
            reportBuilder.append(ErrorReportGenerator.errorReport(error, true, OutputFormat.PLAIN_TEXT));
        }
        return reportBuilder.toString();
    }

    /**
     * Handles new decision by adding to buffer and updating display.
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
     * Creates the decision details view with multiple tabs.
     *
     * @return the details view component
     */
    private Component buildDecisionDetailsView() {
        val tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.getStyle().set(CSS_OVERFLOW, CSS_VALUE_AUTO);
        tabSheet.add(LABEL_JSON, createDecisionJsonTab());
        tabSheet.add(LABEL_ERRORS, createDecisionErrorsTab());
        tabSheet.add(LABEL_REPORT, createTextReportTab());
        tabSheet.add(LABEL_JSON_REPORT, createDecisionJsonReportTab());
        tabSheet.add(LABEL_JSON_TRACE, createDecisionJsonTraceTab());
        tabSheet.add(LABEL_TRACE_GRAPH, createTraceGraphTab());
        return tabSheet;
    }

    /**
     * Creates the decision JSON tab.
     *
     * @return the JSON tab component
     */
    private Component createDecisionJsonTab() {
        decisionJsonEditor = createReadOnlyJsonEditor();
        return createLayoutWithClipboard(decisionJsonEditor, decisionJsonEditor::getDocument);
    }

    /**
     * Creates the decision JSON report tab.
     *
     * @return the JSON report tab component
     */
    private Component createDecisionJsonReportTab() {
        decisionJsonReportEditor = createReadOnlyJsonEditor();
        return createLayoutWithClipboard(decisionJsonReportEditor, decisionJsonReportEditor::getDocument);
    }

    /**
     * Creates the decision JSON trace tab.
     *
     * @return the JSON trace tab component
     */
    private Component createDecisionJsonTraceTab() {
        decisionJsonTraceEditor = createReadOnlyJsonEditor();
        return createLayoutWithClipboard(decisionJsonTraceEditor, decisionJsonTraceEditor::getDocument);
    }

    /**
     * Creates the trace graph visualization tab.
     *
     * @return the trace graph tab component
     */
    private Component createTraceGraphTab() {
        traceGraphVisualization = new JsonGraphVisualization();
        traceGraphVisualization.setSizeFull();
        traceGraphVisualization.setJsonData("{}");
        return traceGraphVisualization;
    }

    /**
     * Creates the text report tab.
     *
     * @return the text report tab component
     */
    private Component createTextReportTab() {
        reportTextArea = new TextArea();
        reportTextArea.setSizeFull();
        reportTextArea.setReadOnly(true);
        reportTextArea.getStyle().set(CSS_FONT_FAMILY, CSS_VALUE_MONOSPACE);
        return createLayoutWithClipboard(reportTextArea, reportTextArea::getValue);
    }

    /**
     * Creates the errors display tab.
     *
     * @return the errors tab component
     */
    private Component createDecisionErrorsTab() {
        errorsDisplayArea = new Div();
        errorsDisplayArea.setSizeFull();
        errorsDisplayArea.getStyle().set(CSS_OVERFLOW, CSS_VALUE_AUTO).set(CSS_MIN_HEIGHT, CSS_VALUE_SIZE_200PX);
        return createLayoutWithClipboard(errorsDisplayArea, () -> currentErrorReportText);
    }

    /**
     * Creates layout with component and clipboard button.
     *
     * @param component the main component
     * @param contentSupplier supplies content for clipboard
     * @return the layout with clipboard button
     */
    private Component createLayoutWithClipboard(Component component, Supplier<String> contentSupplier) {
        val layout = new HorizontalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.add(component, createClipboardButton(contentSupplier));
        return layout;
    }

    /**
     * Creates clipboard copy button.
     *
     * @param contentSupplier supplies content to copy
     * @return the clipboard button
     */
    private Button createClipboardButton(Supplier<String> contentSupplier) {
        val button = new Button(VaadinIcon.CLIPBOARD.create());
        button.getStyle().set(CSS_POSITION, CSS_VALUE_ABSOLUTE).set(CSS_TOP, CSS_VALUE_SIZE_15PX)
                .set(CSS_RIGHT, CSS_VALUE_SIZE_25PX).set(CSS_Z_INDEX, CSS_VALUE_SIZE_100);
        button.setAriaLabel(TOOLTIP_COPY_TO_CLIPBOARD);
        button.setTooltipText(TOOLTIP_COPY_TO_CLIPBOARD);
        button.addClickListener(event -> copyToClipboard(contentSupplier.get()));
        return button;
    }

    /**
     * Copies content to clipboard using JavaScript.
     *
     * @param content the content to copy
     */
    private void copyToClipboard(String content) {
        if (content == null || content.length() > MAX_CLIPBOARD_SIZE) {
            showNotification(MESSAGE_CONTENT_TOO_LARGE);
            return;
        }

        UI.getCurrent().getPage().executeJs(JS_COPY_TO_CLIPBOARD, content);
        val notification = Notification.show(MESSAGE_CONTENT_COPIED);
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
    }

    /**
     * Creates the variables tab with validation icon.
     *
     * @return the variables tab
     */
    private Tab createVariablesTab() {
        val variablesValidationIcon = createIcon(VaadinIcon.CHECK_CIRCLE, COLOR_GREEN);
        variablesValidationIcon.setSize(CSS_VALUE_SIZE_0_875EM);
        variablesValidationIcon.getStyle().set(CSS_MARGIN_RIGHT, CSS_VALUE_SIZE_0_25EM);

        val label      = new Span(truncateTitle(LABEL_VARIABLES));
        val tabContent = new HorizontalLayout(variablesValidationIcon, label);
        tabContent.setSpacing(false);
        tabContent.setAlignItems(FlexComponent.Alignment.CENTER);

        return new Tab(tabContent);
    }

    /**
     * Creates the variables editor layout.
     *
     * @return the variables editor component
     */
    private Component createVariablesEditorLayout() {
        val layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);
        layout.getStyle().set(CSS_PADDING, CSS_VALUE_SPACE_S);

        variablesEditor = createJsonEditor(true, 500);
        variablesEditor.setSizeFull();

        variablesValidationField = new TextField();
        variablesValidationField.setReadOnly(true);
        variablesValidationField.setWidthFull();
        variablesValidationField.getStyle().set(CSS_MARGIN_TOP, CSS_VALUE_SPACE_XS);

        val controlsLayout = createVariablesControlsLayout();

        layout.add(variablesEditor, controlsLayout);

        variablesEditor.addDocumentChangedListener(this::handleVariablesDocumentChanged);

        return layout;
    }

    /**
     * Creates controls layout for variables editor.
     *
     * @return the controls component
     */
    private Component createVariablesControlsLayout() {
        val layout = new HorizontalLayout();
        layout.setWidthFull();

        val formatButton = createFormatJsonButton(() -> formatJsonEditor(variablesEditor));
        layout.add(formatButton, variablesValidationField);

        return layout;
    }

    /**
     * Creates format JSON button.
     *
     * @param formatAction the action to execute on click
     * @return the format button
     */
    private Button createFormatJsonButton(Runnable formatAction) {
        val button = new Button(VaadinIcon.CURLY_BRACKETS.create());
        button.setAriaLabel(LABEL_FORMAT_JSON);
        button.setTooltipText(TOOLTIP_FORMAT_JSON);
        button.addClickListener(event -> formatAction.run());
        return button;
    }

    /**
     * Handles changes to variables document.
     *
     * @param event the document change event
     */
    private void handleVariablesDocumentChanged(DocumentChangedEvent event) {
        if (hasInvalidDocumentSize(event.getNewValue(), LABEL_VARIABLES)) {
            return;
        }

        val validationResult = validator.validateVariablesJson(event.getNewValue());
        updateValidationField(variablesValidationField, validationResult);

        if (validationResult.isValid()) {
            this.policyDecisionPoint.setVariables(parseVariablesFromJson(event.getNewValue()));
        }
    }

    /**
     * Parses variables from JSON string.
     *
     * @param variablesJson the JSON string containing variables
     * @return map of variable names to values
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

        val components = createPolicyTabComponents(POLICY_NAME_PREFIX + policyTabCounter.getAndIncrement());

        leftTabSheet.add(components.tab, components.editorLayout);

        components.context.editor
                .addValidationFinishedListener(event -> handlePolicyValidation(components.context, event));
        components.context.editor.setDocument(policyDocument);

        val parsedDocument = INTERPRETER.parseDocument(policyDocument);
        if (!parsedDocument.isInvalid()) {
            components.context.documentName = parsedDocument.name();
            components.context.titleLabel.setText(truncateTitle(parsedDocument.name()));
            components.context.statusIcon.setIcon(VaadinIcon.CHECK_CIRCLE);
            components.context.statusIcon.setColor(COLOR_GREEN);
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
            showNotification(MESSAGE_MAX_TABS_REACHED + MAX_POLICY_TABS + MESSAGE_REACHED_SUFFIX);
            return;
        }
        leftTabSheet.setSelectedTab(tab);
    }

    /**
     * Container for policy tab components.
     */
    private record PolicyTabComponents(Tab tab, PolicyTabContext context, VerticalLayout editorLayout) {}

    /**
     * Creates all components for a policy tab.
     *
     * @param policyName the initial name for the policy
     * @return the policy tab components
     */
    private PolicyTabComponents createPolicyTabComponents(String policyName) {
        val editor          = createSaplEditor();
        val validationField = createValidationTextField();

        val editorLayout = new VerticalLayout();
        editorLayout.setSizeFull();
        editorLayout.setPadding(false);
        editorLayout.setSpacing(false);
        editorLayout.getStyle().set(CSS_PADDING, CSS_VALUE_SPACE_S);

        validationField.getStyle().set(CSS_MARGIN_TOP, CSS_VALUE_SPACE_XS);
        editorLayout.add(editor, validationField);

        val statusIcon = createIcon(VaadinIcon.QUESTION_CIRCLE, COLOR_ORANGE);
        statusIcon.setSize(CSS_VALUE_SIZE_0_875EM);
        statusIcon.getStyle().set(CSS_MARGIN_RIGHT, CSS_VALUE_SIZE_0_25EM);

        val titleLabel  = new Span(truncateTitle(policyName));
        val closeButton = createTabCloseButton();

        val tabContent = new HorizontalLayout(statusIcon, titleLabel, closeButton);
        tabContent.setSpacing(false);
        tabContent.setPadding(false);
        tabContent.getStyle().set(CSS_GAP, CSS_VALUE_SIZE_0_25EM);
        tabContent.setAlignItems(FlexComponent.Alignment.CENTER);

        val tab = new Tab(tabContent);
        tab.getStyle().set(CSS_PADDING, CSS_VALUE_TAB_PADDING);
        val context = new PolicyTabContext(editor, validationField, statusIcon, titleLabel);

        policyTabContexts.put(tab, context);
        closeButton.addClickListener(event -> handlePolicyTabClose(tab));

        return new PolicyTabComponents(tab, context, editorLayout);
    }

    /**
     * Creates a SAPL policy editor.
     *
     * @return the SAPL editor
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
     * @return the validation field
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
     * @return the close button
     */
    private Button createTabCloseButton() {
        val button = new Button(VaadinIcon.CLOSE_SMALL.create());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        button.getStyle().set(CSS_MARGIN_LEFT, CSS_VALUE_SIZE_0_25EM);
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
     * Handles policy validation completion.
     *
     * @param context the policy tab context
     * @param event the validation event
     */
    private void handlePolicyValidation(PolicyTabContext context, ValidationFinishedEvent event) {
        val document = context.editor.getDocument();

        if (hasInvalidDocumentSize(document, "Policy")) {
            val result = ValidationResult.error(MESSAGE_DOCUMENT_TOO_LARGE);
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
     * Collects all policy documents from tabs.
     * Documents are returned in the same order as tabs appear in the UI.
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
     * Updates the policy retrieval point with current policies.
     * Collects all policy documents from tabs and updates the PDP's policy store.
     * Called whenever policies change (added, removed, or edited).
     */
    private void updatePolicyRetrievalPoint() {
        val documents = collectAllPolicyDocuments();
        this.policyDecisionPoint.updatePrp(documents);
    }

    /**
     * Updates policy document name in tab context.
     *
     * @param context the tab context
     * @param parsedDocument the parsed policy document
     */
    private void updatePolicyDocumentName(PolicyTabContext context, io.sapl.prp.Document parsedDocument) {
        if (!parsedDocument.isInvalid()) {
            context.documentName = parsedDocument.name();
        }
        context.titleLabel.setText(truncateTitle(context.documentName));
    }

    /**
     * Updates validation state display for policy.
     *
     * @param context the tab context
     * @param hasErrors whether errors exist
     * @param issues the validation issues
     */
    private void updatePolicyValidationState(PolicyTabContext context, boolean hasErrors,
            io.sapl.vaadin.Issue[] issues) {
        if (hasErrors) {
            val errorCount = PlaygroundValidator.countErrorSeverityIssues(issues);
            val result     = ValidationResult.error(errorCount + MESSAGE_ERRORS_SUFFIX);
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
     * Checks for and displays policy name collisions.
     * Groups policies by document name and identifies duplicates.
     * Updates validation state for all tabs - showing warnings for collisions
     * and restoring normal state for unique names.
     * Name collisions can cause ambiguity in policy evaluation.
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
     * Groups tabs by their document names.
     *
     * @return map of document names to tabs
     */
    private Map<String, List<Tab>> groupTabsByDocumentName() {
        val nameToTabs = new HashMap<String, List<Tab>>();

        for (var entry : policyTabContexts.entrySet()) {
            val tab     = entry.getKey();
            val context = entry.getValue();
            if (!POLICY_NAME_UNKNOWN.equals(context.documentName)) {
                nameToTabs.computeIfAbsent(context.documentName, k -> new ArrayList<>()).add(tab);
            }
        }

        return nameToTabs;
    }

    /**
     * Finds duplicate document names.
     *
     * @param documentNamesToTabs map of names to tabs
     * @return set of duplicate names
     */
    private Set<String> findDuplicateDocumentNames(Map<String, List<Tab>> documentNamesToTabs) {
        return documentNamesToTabs.entrySet().stream().filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    /**
     * Sets collision warning state on tab context.
     *
     * @param context the tab context
     */
    private void setCollisionWarningState(PolicyTabContext context) {
        context.statusIcon.setIcon(VaadinIcon.WARNING);
        context.statusIcon.setColor(COLOR_ORANGE);
        val result = ValidationResult
                .warning(MESSAGE_NAME_COLLISION_PREFIX + context.documentName + MESSAGE_NAME_COLLISION_SUFFIX);
        updateValidationField(context.validationField, result);
    }

    /**
     * Restores normal validation state after collision cleared.
     *
     * @param context the tab context
     */
    private void restoreNormalValidationState(PolicyTabContext context) {
        val document       = context.editor.getDocument();
        val parsedDocument = INTERPRETER.parseDocument(document);
        val hasErrors      = parsedDocument.isInvalid();

        if (hasErrors) {
            context.statusIcon.setIcon(VaadinIcon.CLOSE_CIRCLE);
            context.statusIcon.setColor(COLOR_RED);
            val result = ValidationResult.error(MESSAGE_SYNTAX_ERROR);
            updateValidationField(context.validationField, result);
        } else {
            context.statusIcon.setIcon(VaadinIcon.CHECK_CIRCLE);
            context.statusIcon.setColor(COLOR_GREEN);
            val result = ValidationResult.success();
            updateValidationField(context.validationField, result);
        }
    }

    /**
     * Truncates title text to maximum length.
     *
     * @param title the title to truncate
     * @return the truncated title
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
     * Builds the application header.
     *
     * @return the header component
     */
    private HorizontalLayout buildHeader() {
        val header = new HorizontalLayout();
        header.setWidthFull();
        header.setPadding(true);
        header.setSpacing(true);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.getStyle().set(CSS_BACKGROUND_COLOR, CSS_VALUE_CONTRAST_5PCT);

        val logo = new Image(RESOURCE_LOGO, LABEL_SAPL_LOGO);
        logo.setHeight(CSS_VALUE_SIZE_2_5EM);

        val title = new Span(LABEL_SAPL_PLAYGROUND);
        title.getStyle().set(CSS_FONT_SIZE, CSS_VALUE_FONT_SIZE_XL).set(CSS_FONT_WEIGHT, CSS_VALUE_WEIGHT_600)
                .set(CSS_TEXT_ALIGN, CSS_VALUE_CENTER).set(CSS_FLEX_GROW, CSS_VALUE_ONE);

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

    /**
     * Creates homepage link button.
     *
     * @return the homepage button
     */
    private Button createHomepageLink() {
        val button = new Button(LABEL_SAPL_HOMEPAGE, VaadinIcon.EXTERNAL_LINK.create());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        button.addClickListener(event -> getUI().ifPresent(ui -> ui.getPage().open(URL_SAPL_HOMEPAGE, TARGET_BLANK)));
        return button;
    }

    /**
     * Creates share button for generating permalinks.
     *
     * @return the share button
     */
    private Button createShareButton() {
        val button = new Button(LABEL_SHARE, VaadinIcon.LINK.create());
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        button.setTooltipText(TOOLTIP_CREATE_SHAREABLE_LINK);
        button.addClickListener(event -> handleShareButtonClick());
        return button;
    }

    /**
     * Handles share button click by creating and displaying permalink.
     */
    private void handleShareButtonClick() {
        try {
            val state        = collectCurrentPlaygroundState();
            val encoded      = permalinkService.encode(state);
            val permalinkUrl = buildPermalinkUrl(encoded);
            showShareDialog(permalinkUrl);
        } catch (PermalinkService.PermalinkException exception) {
            log.error("Failed to create permalink", exception);
            showNotification(MESSAGE_FAILED_CREATE_PERMALINK + exception.getMessage());
        }
    }

    /**
     * Collects current playground state from all editors.
     *
     * @return the playground state
     */
    private PermalinkService.PlaygroundState collectCurrentPlaygroundState() {
        val policies            = collectAllPolicyDocuments();
        val subscription        = subscriptionEditor.getDocument();
        val variables           = variablesEditor.getDocument();
        val combiningAlgorithm  = combiningAlgorithmComboBox.getValue();
        val selectedPolicyIndex = getSelectedPolicyIndex();

        return new PermalinkService.PlaygroundState(policies, subscription, variables, combiningAlgorithm,
                selectedPolicyIndex);
    }

    /**
     * Gets the index of the currently selected policy tab.
     * Returns null if variables tab is selected or no tab is selected.
     *
     * @return the zero-based index of the selected policy tab, or null
     */
    private Integer getSelectedPolicyIndex() {
        val selectedTab = leftTabSheet.getSelectedTab();
        if (selectedTab == null || selectedTab == variablesTab) {
            return null;
        }

        var index = 0;
        for (var tab : policyTabContexts.keySet()) {
            if (tab.equals(selectedTab)) {
                return index;
            }
            index++;
        }

        return null;
    }

    /**
     * Builds full permalink URL from encoded state.
     *
     * @param encoded the encoded state string
     * @return the complete permalink URL
     */
    private String buildPermalinkUrl(String encoded) {
        return permalinkConfiguration.getBaseUrl() + URL_HASH_PREFIX + encoded;
    }

    /**
     * Shows dialog with permalink URL and copy functionality.
     *
     * @param permalinkUrl the permalink URL to display
     */
    private void showShareDialog(String permalinkUrl) {
        val dialog = new Dialog();
        dialog.setHeaderTitle(LABEL_SHARE_PLAYGROUND_STATE);

        val layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(true);
        layout.setWidth(CSS_VALUE_SIZE_600PX);

        val explanation = new Paragraph(MESSAGE_SHARE_EXPLANATION);

        val urlField = new TextField();
        urlField.setValue(permalinkUrl);
        urlField.setWidthFull();
        urlField.setReadOnly(true);

        val copyButton = new Button(LABEL_COPY_TO_CLIPBOARD, VaadinIcon.CLIPBOARD.create());
        copyButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        copyButton.addClickListener(event -> {
            copyToClipboard(permalinkUrl);
            dialog.close();
        });

        val closeButton = new Button(LABEL_CLOSE, event -> dialog.close());

        val buttonLayout = new HorizontalLayout(copyButton, closeButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        buttonLayout.setWidthFull();

        layout.add(explanation, urlField, buttonLayout);
        dialog.add(layout);
        dialog.open();
    }

    /**
     * Creates combining algorithm selection combobox.
     *
     * @return the combobox
     */
    private ComboBox<PolicyDocumentCombiningAlgorithm> createCombiningAlgorithmComboBox() {
        val comboBox = new ComboBox<PolicyDocumentCombiningAlgorithm>();
        comboBox.setPlaceholder(LABEL_COMBINING_ALGORITHM);
        comboBox.setItems(PolicyDocumentCombiningAlgorithm.values());
        comboBox.setItemLabelGenerator(PlaygroundView::formatAlgorithmName);
        comboBox.addValueChangeListener(this::handleAlgorithmChange);
        comboBox.setWidth(CSS_VALUE_SIZE_16EM);
        comboBox.setValue(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES);
        return comboBox;
    }

    /**
     * Creates examples menu with categorized examples.
     *
     * @return the examples menu bar
     */
    private MenuBar createExamplesMenu() {
        val menuBar = new MenuBar();
        menuBar.addThemeVariants(MenuBarVariant.LUMO_TERTIARY);
        val examplesItem = menuBar.addItem(LABEL_EXAMPLES);
        val mainSubMenu  = examplesItem.getSubMenu();

        for (val category : PlaygroundExamples.getAllCategories()) {
            val categoryIcon = category.icon().create();
            categoryIcon.setSize(CSS_VALUE_ONE_EM);
            val categoryLabel = new Span(categoryIcon, new Text(" " + category.name()));
            val categoryItem  = mainSubMenu.addItem(categoryLabel);
            val categoryMenu  = categoryItem.getSubMenu();

            for (val example : category.examples()) {
                categoryMenu.addItem(example.displayName(), event -> loadExampleWithConfirmation(example));
            }
        }

        return menuBar;
    }

    /**
     * Checks for URL fragment on initial page load.
     * Handles both example and permalink fragments.
     */
    private void checkInitialFragment() {
        getUI().ifPresent(ui -> ui.getPage().executeJs(JS_GET_URL_FRAGMENT).then(String.class, this::processFragment));
    }

    /**
     * Sets up JavaScript listener for hash changes in the browser.
     * Allows handling of URL changes while the view is already open.
     */
    private void setupHashChangeListener() {
        getUI().ifPresent(ui -> ui.getPage().executeJs(JS_SETUP_HASH_LISTENER, getElement()));
    }

    /**
     * Removes JavaScript listener for hash changes.
     * Called during cleanup to prevent memory leaks.
     */
    private void cleanupHashChangeListener() {
        getUI().ifPresent(ui -> ui.getPage().executeJs(JS_CLEANUP_HASH_LISTENER));
    }

    /**
     * Handles hash change events from the browser.
     * Called via JavaScript when the URL fragment changes.
     * Validates input to prevent XSS/injection attacks.
     * Modern browsers support hash fragments up to 64KB safely.
     *
     * @param fragment the new URL fragment without the # prefix
     */
    @ClientCallable
    public void handleHashChange(String fragment) {
        if (!isValidFragment(fragment)) {
            return;
        }

        processFragment(fragment);
    }

    /**
     * Validates a URL fragment for security and length constraints.
     * Checks for null, empty, excessive length, and invalid characters.
     *
     * @param fragment the fragment to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidFragment(String fragment) {
        if (fragment == null || fragment.isEmpty()) {
            return false;
        }

        if (fragment.length() > MAX_FRAGMENT_LENGTH) {
            log.warn("Fragment exceeds maximum length: {}", fragment.length());
            return false;
        }

        if (!fragment.matches("^[a-zA-Z0-9/_-]*$")) {
            log.warn("Fragment contains invalid characters");
            return false;
        }

        return true;
    }

    /**
     * Processes URL fragment and loads appropriate state.
     *
     * @param fragment the URL fragment to process
     */
    private void processFragment(String fragment) {
        if (fragment != null && !fragment.isEmpty()) {
            if (fragment.startsWith(FRAGMENT_PREFIX_EXAMPLE)) {
                val slug = fragment.substring(EXAMPLE_PREFIX_LENGTH);
                loadExampleBySlugWithoutConfirmation(slug);
            } else if (fragment.startsWith(FRAGMENT_PREFIX_PERMALINK)) {
                val encoded = fragment.substring(PERMALINK_PREFIX_LENGTH);
                loadPermalinkState(encoded);
            }
        }
    }

    /**
     * Loads playground state from encoded permalink string.
     *
     * @param encoded the encoded permalink string
     */
    private void loadPermalinkState(String encoded) {
        try {
            val state = permalinkService.decode(encoded);
            loadPlaygroundState(state);
            showNotification(MESSAGE_LOADED_PERMALINK);
        } catch (PermalinkService.PermalinkException exception) {
            log.error("Failed to load permalink", exception);
            showNotification(MESSAGE_FAILED_LOAD_PERMALINK);
            initializeDefaultValues();
        }
    }

    /**
     * Loads playground state into editors.
     *
     * @param state the playground state to load
     */
    private void loadPlaygroundState(PermalinkService.PlaygroundState state) {
        stopSubscription();
        clearDecisionBuffer();
        removeAllPolicyTabs();

        val createdTabs = new ArrayList<Tab>();
        for (val policy : state.policies()) {
            val tab = createAndAddPolicyTab(policy);
            if (tab == null) {
                showNotification(MESSAGE_STATE_TOO_MANY_POLICIES + MAX_POLICY_TABS);
                break;
            }
            createdTabs.add(tab);
        }

        selectAppropriateTab(createdTabs, state.selectedPolicyIndex());

        setCombiningAlgorithmQuietly(state.combiningAlgorithm());
        variablesEditor.setDocument(state.variables());
        subscriptionEditor.setDocument(state.subscription());
    }

    /**
     * Selects the appropriate tab after loading state.
     * Prefers the previously selected policy tab, falls back to first policy tab.
     *
     * @param createdTabs list of created policy tabs
     * @param selectedPolicyIndex the index of the previously selected policy tab
     */
    private void selectAppropriateTab(List<Tab> createdTabs, Integer selectedPolicyIndex) {
        if (createdTabs.isEmpty()) {
            return;
        }

        Tab tabToSelect;
        if (selectedPolicyIndex != null && selectedPolicyIndex >= 0 && selectedPolicyIndex < createdTabs.size()) {
            tabToSelect = createdTabs.get(selectedPolicyIndex);
        } else {
            tabToSelect = createdTabs.getFirst();
        }

        leftTabSheet.setSelectedTab(tabToSelect);
    }

    /**
     * Loads example with user confirmation.
     *
     * @param example the example to load
     */
    private void loadExampleWithConfirmation(Example example) {
        val dialog = new ConfirmDialog();
        dialog.setHeader(LABEL_LOAD_EXAMPLE + ": " + example.displayName());
        dialog.setText(example.description() + "\n\n" + MESSAGE_EXAMPLE_LOAD_CONFIRMATION);

        dialog.setCancelable(true);
        dialog.setConfirmText(LABEL_LOAD_EXAMPLE);
        dialog.addConfirmListener(event -> {
            loadExample(example);
            updateUrlFragment(example.slug());
        });

        dialog.open();
    }

    /**
     * Loads example by slug without confirmation.
     *
     * @param slug the example slug
     */
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

        val createdTabs = new ArrayList<Tab>();
        for (val policy : example.policies()) {
            val tab = createAndAddPolicyTab(policy);
            if (tab == null) {
                showNotification(MESSAGE_EXAMPLE_TOO_MANY_POLICIES + MAX_POLICY_TABS);
                break;
            }
            createdTabs.add(tab);
        }

        if (!createdTabs.isEmpty()) {
            leftTabSheet.setSelectedTab(createdTabs.getFirst());
        }

        setCombiningAlgorithmQuietly(example.combiningAlgorithm());
        variablesEditor.setDocument(example.variables());
        subscriptionEditor.setDocument(example.subscription());

        showNotification(MESSAGE_LOADED_EXAMPLE + example.displayName());
    }

    /**
     * Removes all policy tabs from the tab sheet.
     */
    private void removeAllPolicyTabs() {
        val tabsToRemove = policyTabContexts.keySet().stream().toList();
        for (val tab : tabsToRemove) {
            policyTabContexts.remove(tab);
            leftTabSheet.remove(tab);
        }
    }

    /**
     * Sets combining algorithm without triggering change event.
     *
     * @param algorithm the algorithm to set
     */
    private void setCombiningAlgorithmQuietly(PolicyDocumentCombiningAlgorithm algorithm) {
        if (combiningAlgorithmComboBox != null) {
            combiningAlgorithmComboBox.setValue(algorithm);
            policyDecisionPoint.setCombiningAlgorithm(algorithm);
        }
    }

    /**
     * Updates the URL fragment for sharing.
     *
     * @param slug the example slug
     */
    private void updateUrlFragment(String slug) {
        getUI().ifPresent(ui -> ui.getPage().executeJs(JS_SET_FRAGMENT_EXAMPLE, slug));
    }

    /**
     * Handles combining algorithm change.
     *
     * @param event the change event
     */
    private void handleAlgorithmChange(ValueChangeEvent<PolicyDocumentCombiningAlgorithm> event) {
        this.policyDecisionPoint.setCombiningAlgorithm(event.getValue());
    }

    /**
     * Formats algorithm name for display.
     *
     * @param algorithm the algorithm to format
     * @return the formatted name
     */
    private static String formatAlgorithmName(PolicyDocumentCombiningAlgorithm algorithm) {
        return StringUtils.capitalize(algorithm.toString().replace('_', ' ').toLowerCase());
    }

    /**
     * Creates read-only JSON editor.
     *
     * @return the JSON editor
     */
    private JsonEditor createReadOnlyJsonEditor() {
        val editor = createJsonEditor(false, 0);
        editor.getElement().getStyle().set(CSS_WIDTH, CSS_VALUE_SIZE_100PCT);
        return editor;
    }

    /**
     * Creates JSON editor with specified configuration.
     *
     * @param hasLineNumbers whether to show line numbers
     * @param textUpdateDelay delay before triggering update events
     * @return the configured JSON editor
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
     * Creates icon with specified type and color.
     *
     * @param iconType the icon type
     * @param color the icon color
     * @return the configured icon
     */
    private Icon createIcon(VaadinIcon iconType, String color) {
        val icon = iconType.create();
        icon.setColor(color);
        return icon;
    }

    /**
     * Updates validation field with result.
     *
     * @param field the field to update
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
