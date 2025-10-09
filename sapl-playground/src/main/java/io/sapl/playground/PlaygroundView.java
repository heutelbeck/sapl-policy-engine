package io.sapl.playground;

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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.html.Span;
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
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.config.fixed.FixedFunctionsAndAttributesPDPConfigurationProvider;
import io.sapl.pdp.interceptors.ErrorReportGenerator;
import io.sapl.pdp.interceptors.ErrorReportGenerator.OutputFormat;
import io.sapl.pdp.interceptors.ReportBuilderUtil;
import io.sapl.pdp.interceptors.ReportTextRenderUtil;
import io.sapl.prp.PolicyRetrievalPointSource;
import io.sapl.vaadin.*;
import io.sapl.vaadin.graph.JsonGraphVisualization;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.xtext.diagnostics.Severity;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Route("")
@PageTitle("SAPL Playground")
@JsModule("./copytoclipboard.js")
public class PlaygroundView extends Composite<VerticalLayout> {

    private static final int    MAX_BUFFER_SIZE      = 50;
    private static final int    DEFAULT_BUFFER_SIZE  = 10;
    private static final int    MAX_TITLE_LENGTH     = 15;
    private static final String UNKNOWN_POLICY_NAME  = "unknown";
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

    private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();
    public static final String GREEN = "green";
    public static final String RED = "red";
    public static final String ORANGE = "orange";
    public static final String HALF_EM = "0.5em";

    private final PlaygroundVariablesAndCombinatorSource variablesAndCombinatorSource = new PlaygroundVariablesAndCombinatorSource();

    private final ObjectMapper               mapper;
    private final PolicyRetrievalPointSource prpSource;
    private final PolicyDecisionPoint        policyDecisionPoint;

    private FixedFunctionsAndAttributesPDPConfigurationProvider pdpConfigurationProvider;

    private transient ArrayList<TracedDecision> decisions = new ArrayList<>(MAX_BUFFER_SIZE);

    private TabSheet   leftTabSheet;
    private Tab        variablesTab;
    private JsonEditor variablesEditor;
    private Icon       variablesIcon;
    private TextField  variablesValidationField;
    private JsonEditor subscriptionEditor;
    private TextField  subscriptionValidationField;

    private ComboBox<PolicyDocumentCombiningAlgorithm> algorithmBox;
    private Button                                     playStopButton;
    private Button                                     scrollLockButton;
    private IntegerField                               bufferSizeField;
    private DecisionsGrid                              decisionsGrid;
    private GridListDataView<TracedDecision>           decisionsView;
    private JsonEditor                                 decisionsArea;
    private JsonEditor                                 decisionsJsonReportArea;
    private JsonEditor                                 decisionsJsonTraceArea;
    private JsonGraphVisualization                     traceGraphVisualization;
    private Div                                        errorsArea;
    private TextArea                                   reportArea;
    private Checkbox                                   clearOnNewSubscriptionCheckBox;
    private Checkbox                                   showOnlyDistinctDecisionsCheckBox;
    private final DocumentationDrawer                  documentationDrawer;

    private boolean scrollLock;
    private String  errorReport;

    private final Map<Tab, PolicyTabContext> policyTabs    = new HashMap<>();
    private final AtomicInteger              policyCounter = new AtomicInteger(1);

    // Context holder for policy tab components and state.
    private static class PolicyTabContext {
        SaplEditor editor;
        TextField  validationField;
        Icon       icon;
        Span       label;
        String     documentName;

        PolicyTabContext(SaplEditor editor, TextField validationField, Icon icon, Span label) {
            this.editor          = editor;
            this.validationField = validationField;
            this.icon            = icon;
            this.label           = label;
            this.documentName    = UNKNOWN_POLICY_NAME;
        }
    }

    public PlaygroundView(ObjectMapper mapper,
            AttributeStreamBroker attributeStreamBroker,
            FunctionContext functionContext,
            PolicyInformationPointDocumentationProvider pipDocumentationProvider) {
        this.mapper                   = mapper;
        this.prpSource                = new PlaygroundPolicyRetrievalPointSource(INTERPRETER);
        this.pdpConfigurationProvider = new FixedFunctionsAndAttributesPDPConfigurationProvider(attributeStreamBroker,
                functionContext, variablesAndCombinatorSource, List.of(), List.of(this::interceptDecision), prpSource);
        this.policyDecisionPoint      = new EmbeddedPolicyDecisionPoint(pdpConfigurationProvider);
        this.documentationDrawer      = new DocumentationDrawer(pipDocumentationProvider, functionContext);

        buildAndAddComponents();
        initializeValues();
    }

    private void initializeValues() {
        subscriptionEditor.setDocument(DEFAULT_SUBSCRIPTION);
        variablesEditor.setDocument("{}");
    }

    // Builds and adds all components to the main layout.
    private void buildAndAddComponents() {
        val header = buildHeader();
        val main   = buildMain();
        val footer = buildFooter();

        getContent().setSizeFull();
        getContent().getStyle().set("flex-grow", "1");
        getContent().add(header, main, footer, documentationDrawer.getToggleButton());

        deactivateScrollLock();
    }

    // Intercepts authorization decisions for display in the grid.
    //
    // @param tracedDecision the decision to intercept
    // @return the same decision (pass-through interceptor)
    private TracedDecision interceptDecision(TracedDecision tracedDecision) {
        handleNewDecision(tracedDecision);
        return tracedDecision;
    }

    private Component buildMain() {
        val mainSplit = new SplitLayout(buildLeftPanel(), buildRightPanel());
        mainSplit.setSizeFull();
        mainSplit.setSplitterPosition(30.0D);
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

        val newPolicyButton = new Button("+ New Policy");
        newPolicyButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        newPolicyButton.addClickListener(event -> createNewPolicyTab());
        leftTabSheet.setSuffixComponent(newPolicyButton);

        leftTabSheet.addSelectedChangeListener(event -> {
            if (event.getSelectedTab() == variablesTab && variablesEditor != null) {
                variablesEditor.getElement()
                        .executeJs("if (this.editor && this.editor.refresh) { this.editor.refresh(); }");
            }
        });

        layout.add(leftTabSheet);
        return layout;
    }

    private Component buildRightPanel() {
        val layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);

        subscriptionValidationField = new TextField();
        subscriptionValidationField.setReadOnly(true);
        subscriptionValidationField.setWidthFull();

        val subscriptionHeader = new H3("Authorization Subscription");
        subscriptionEditor = createSubscriptionEditor();

        val bottomLayout = new HorizontalLayout();
        bottomLayout.setWidthFull();

        val prettyPrintButton = new Button(VaadinIcon.CURLY_BRACKETS.create());
        prettyPrintButton.setAriaLabel("Format JSON");
        prettyPrintButton.setTooltipText("Format JSON.");
        prettyPrintButton.addClickListener(e -> formatJsonEditor(subscriptionEditor));

        bottomLayout.add(prettyPrintButton, subscriptionValidationField);

        val decisionsHeader = new H3("Decisions");
        decisionsGrid = new DecisionsGrid();
        decisionsView = decisionsGrid.setItems(decisions);
        decisionsGrid.addSelectionListener(this::handleDecisionSelected);

        val inspectorLayout = new VerticalLayout();
        inspectorLayout.setSizeFull();
        inspectorLayout.setPadding(false);
        inspectorLayout.setSpacing(false);
        inspectorLayout.add(buildControlButtons(), buildDetailsView());

        val rightSplit = new SplitLayout(decisionsGrid, inspectorLayout);
        rightSplit.setSizeFull();
        rightSplit.setSplitterPosition(40.0D);
        rightSplit.setOrientation(Orientation.VERTICAL);

        layout.add(subscriptionHeader, subscriptionEditor, bottomLayout, decisionsHeader, rightSplit);
        return layout;
    }

    private JsonEditor createSubscriptionEditor() {
        val config = new JsonEditorConfiguration();
        config.setHasLineNumbers(true);
        config.setTextUpdateDelay(500);
        config.setDarkTheme(true);

        val editor = new JsonEditor(config);
        editor.setWidthFull();
        editor.setHeight("200px");
        log.error("Wire subscription changed listener");
        editor.addDocumentChangedListener(this::handleSubscriptionDocumentChanged);
        return editor;
    }

    // Handles changes to the subscription document and triggers validation.
    //
    // @param event the document changed event
    private void handleSubscriptionDocumentChanged(DocumentChangedEvent event) {
        validateJsonSubscription(event.getNewValue(), subscriptionValidationField);

        if (Boolean.TRUE.equals(clearOnNewSubscriptionCheckBox.getValue())) {
            decisions.clear();
            decisionsView.refreshAll();
        }
    }

    // Validates a JSON document and updates the validation field accordingly.
    //
    // @param jsonContent the JSON string to validate
    // @param validationField the field to update with validation status
    // @param errorMessage the message to display on validation failure
    private boolean validateJsonVariables(String jsonContent, TextField validationField) {
        try {
            val jsonNode = mapper.readTree(jsonContent);
            if (!jsonNode.isObject()) {
                updateValidationField(validationField, invalidIcon(), "Must be a JSON Object");
                return false;
            }
            updateValidationField(validationField, validIcon(), "OK");
            return true;
        } catch (JsonProcessingException e) {
            updateValidationField(validationField, invalidIcon(), "Invalid JSON. Last valid will be used.");
            return false;
        }
    }

    private boolean validateJsonSubscription(String jsonContent, TextField validationField) {
        try {
            val jsonNode = mapper.readTree(jsonContent);
            if (jsonNode.isObject()) {
                var missingMandatoryFields = false;
                var foundMissingFields     = new StringBuilder();
                for (val mandatoryField : List.of("subject", "action", "resource")) {
                    if (jsonNode.get(mandatoryField) == null) {
                        if (!foundMissingFields.isEmpty()) {
                            foundMissingFields.append(", ");
                        }
                        foundMissingFields.append(mandatoryField);
                        missingMandatoryFields = true;
                    }
                }
                if (missingMandatoryFields) {
                    updateValidationField(validationField, invalidIcon(),
                            "Missing mandatory fields: " + foundMissingFields);
                    return false;
                }
            } else {
                updateValidationField(validationField, invalidIcon(), "Must be a JSON Object");
                return false;
            }
            updateValidationField(validationField, validIcon(), "OK");
            return true;
        } catch (Exception e) {
            updateValidationField(validationField, invalidIcon(), "Invalid Authorization Subscription.");
            return false;
        }
    }

    // Updates a validation field with status icon and message.
    // This is the single point of truth for updating validation field states.
    //
    // @param field the validation field to update
    // @param icon the icon to display (validIcon, invalidIcon, or collisionIcon)
    // @param message the message to display
    private void updateValidationField(TextField field, Icon icon, String message) {
        field.setPrefixComponent(icon);
        field.setValue(message);
    }

    // Formats JSON content in an editor with pretty-printing.
    //
    // @param editor the JSON editor to format
    private void formatJsonEditor(JsonEditor editor) {
        val jsonString = editor.getDocument();
        try {
            val json = mapper.readTree(jsonString);
            if (json instanceof MissingNode) {
                Notification.show("Cannot format invalid JSON.");
                return;
            }
            val formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            editor.setDocument(formattedJson);
        } catch (JsonProcessingException e) {
            Notification.show("Cannot format invalid JSON.");
        }
    }

    private Component buildControlButtons() {
        val layout = new HorizontalLayout();
        layout.setAlignItems(FlexComponent.Alignment.CENTER);

        playStopButton = new Button(VaadinIcon.STOP.create());
        playStopButton.addThemeVariants(ButtonVariant.LUMO_ICON);
        playStopButton.setAriaLabel("Unsubscribe");
        playStopButton.setTooltipText("Stop Subscribing.");
        playStopButton.addClickListener(e -> togglePlayStop());

        scrollLockButton = new Button(VaadinIcon.UNLOCK.create());
        scrollLockButton.addClickListener(e -> {
            if (scrollLock) {
                deactivateScrollLock();
            } else {
                activateScrollLock();
            }
        });

        val bufferLabel = new NativeLabel("Buffer");
        bufferSizeField = new IntegerField();
        bufferSizeField.setMin(1);
        bufferSizeField.setMax(MAX_BUFFER_SIZE);
        bufferSizeField.setValue(DEFAULT_BUFFER_SIZE);
        bufferSizeField.setWidth("6.5em");
        bufferSizeField.setStepButtonsVisible(true);
        bufferSizeField.addValueChangeListener(e -> updateBufferSize(e.getValue()));

        clearOnNewSubscriptionCheckBox = new Checkbox("Auto Clear");
        clearOnNewSubscriptionCheckBox.setValue(true);

        showOnlyDistinctDecisionsCheckBox = new Checkbox("Only Distinct Decisions");
        showOnlyDistinctDecisionsCheckBox.setValue(true);

        layout.add(playStopButton, scrollLockButton, bufferLabel, bufferSizeField, clearOnNewSubscriptionCheckBox,
                showOnlyDistinctDecisionsCheckBox);
        return layout;
    }

    private void togglePlayStop() {
        // Placeholder for play/stop logic
    }

    private void updateBufferSize(Integer size) {
        if (size == null)
            return;
        while (decisions.size() > size) {
            decisions.removeFirst();
        }
        decisionsView.refreshAll();
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

    private void handleDecisionSelected(SelectionEvent<Grid<TracedDecision>, TracedDecision> selection) {
        updateDetailsView(selection.getFirstSelectedItem());
    }

    private void updateDetailsView(Optional<TracedDecision> maybeTracedDecision) {
        if (maybeTracedDecision.isEmpty()) {
            clearDetailsView();
            return;
        }

        val tracedDecision = maybeTracedDecision.get();
        try {
            decisionsArea.setDocument(mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(tracedDecision.getAuthorizationDecision()));
        } catch (JsonProcessingException e) {
            decisionsArea.setDocument("Error reading decision:\n" + tracedDecision);
        }

        val trace  = tracedDecision.getTrace();
        val report = ReportBuilderUtil.reduceTraceToReport(trace);

        decisionsJsonTraceArea.setDocument(trace.toPrettyString());
        decisionsJsonReportArea.setDocument(report.toPrettyString());
        reportArea.setValue(ReportTextRenderUtil.textReport(report, false, mapper));
        traceGraphVisualization.setJsonData(trace.toPrettyString());
        setErrorAreaContentFromErrors(tracedDecision.getErrorsFromTrace());
    }

    private void clearDetailsView() {
        decisionsArea.setDocument("");
        decisionsJsonTraceArea.setDocument("");
        decisionsJsonReportArea.setDocument("");
        reportArea.setValue("");
        traceGraphVisualization.setJsonData("{}");
        setErrorAreaContent("", "");
    }

    private void setErrorAreaContentFromErrors(Collection<Val> errors) {
        val html      = buildAggregatedErrorReport(errors, OutputFormat.HTML);
        val plainText = buildAggregatedErrorReport(errors, OutputFormat.PLAIN_TEXT);
        setErrorAreaContent(html, plainText);
    }

    private void setErrorAreaContent(String html, String plainText) {
        errorsArea.getElement().setProperty("innerHTML", html);
        errorReport = plainText;
    }

    // Builds an aggregated error report from a collection of errors.
    //
    // @param errors the errors to aggregate
    // @param format the output format (HTML or PLAIN_TEXT)
    // @return the formatted error report
    private String buildAggregatedErrorReport(Collection<Val> errors, OutputFormat format) {
        if (errors.isEmpty()) {
            return "No Errors";
        }
        val sb = new StringBuilder();
        for (var error : errors) {
            sb.append(ErrorReportGenerator.errorReport(error, true, format));
        }
        return sb.toString();
    }

    private void handleNewDecision(TracedDecision decision) {
        decisions.add(decision);
        if (decisions.size() > bufferSizeField.getValue()) {
            decisions.removeFirst();
        }
        decisionsView.refreshAll();
        if (scrollLock) {
            decisionsGrid.scrollToEnd();
        }
    }

    private Component buildDetailsView() {
        val tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.add("JSON", createDecisionJson());
        tabSheet.add("Errors", createDecisionErrors());
        tabSheet.add("Report", createTextReport());
        tabSheet.add("JSON Report", createDecisionJsonReport());
        tabSheet.add("JSON Trace", createDecisionJsonTrace());
        tabSheet.add("Trace Graph", createTraceGraph());
        return tabSheet;
    }

    private Component createDecisionJson() {
        val jsonConfig = new JsonEditorConfiguration();
        jsonConfig.setHasLineNumbers(false);
        jsonConfig.setDarkTheme(true);
        jsonConfig.setReadOnly(true);
        jsonConfig.setLint(false);
        decisionsArea = new JsonEditor(jsonConfig);
        return createHorizontalLayoutWithClipboard(decisionsArea, decisionsArea::getDocument);
    }

    private Component createDecisionJsonReport() {
        val jsonConfig = new JsonEditorConfiguration();
        jsonConfig.setHasLineNumbers(false);
        jsonConfig.setDarkTheme(true);
        jsonConfig.setReadOnly(true);
        jsonConfig.setLint(false);
        decisionsJsonReportArea = new JsonEditor(jsonConfig);
        return createHorizontalLayoutWithClipboard(decisionsJsonReportArea, decisionsJsonReportArea::getDocument);
    }

    private Component createDecisionJsonTrace() {
        val jsonConfig = new JsonEditorConfiguration();
        jsonConfig.setHasLineNumbers(false);
        jsonConfig.setDarkTheme(true);
        jsonConfig.setReadOnly(true);
        jsonConfig.setLint(false);
        decisionsJsonTraceArea = new JsonEditor(jsonConfig);
        return createHorizontalLayoutWithClipboard(decisionsJsonTraceArea, decisionsJsonTraceArea::getDocument);
    }

    private Component createTraceGraph() {
        traceGraphVisualization = new JsonGraphVisualization();
        traceGraphVisualization.setSizeFull();
        traceGraphVisualization.setJsonData("{}");
        return traceGraphVisualization;
    }

    private Component createTextReport() {
        reportArea = new TextArea();
        reportArea.setSizeFull();
        reportArea.setReadOnly(true);
        reportArea.getStyle().set("font-family", "monospace");
        return createHorizontalLayoutWithClipboard(reportArea, reportArea::getValue);
    }

    private Component createDecisionErrors() {
        errorsArea = new Div();
        errorsArea.setSizeFull();
        errorsArea.getStyle().set("font-family", "monospace").set("overflow", "auto").set("overflow-wrap", "break-word")
                .set("background-color", "#282a36");
        return createHorizontalLayoutWithClipboard(errorsArea, () -> errorReport);
    }

    // Creates a horizontal layout with a component and a clipboard button.
    //
    // @param component the main component to display
    // @param contentSupplier supplies the content to copy to clipboard
    // @return the layout containing both components
    private Component createHorizontalLayoutWithClipboard(Component component, Supplier<String> contentSupplier) {
        val layout = new HorizontalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.add(component, createClipboardButton(contentSupplier));
        return layout;
    }

    // Creates a clipboard button that copies content when clicked.
    //
    // @param contentSupplier supplies the content to copy
    // @return the configured button
    private Button createClipboardButton(Supplier<String> contentSupplier) {
        val button = new Button(VaadinIcon.CLIPBOARD.create());
        button.getStyle().set("position", "absolute").set("top", "15px").set("right", "25px").set("z-index", "100");
        button.setAriaLabel("Copy to clipboard.");
        button.setTooltipText("Copy to clipboard.");
        button.addClickListener(event -> copyToClipboard(contentSupplier.get()));
        return button;
    }

    private void copyToClipboard(String content) {
        UI.getCurrent().getPage().executeJs("window.copyToClipboard($0)", content);
        Notification notification = Notification.show("Content copied to clipboard.");
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
    }

    private Tab createVariablesTab() {
        variablesIcon = VaadinIcon.CHECK_CIRCLE.create();
        variablesIcon.setColor(GREEN);
        variablesIcon.getStyle().set("margin-right", HALF_EM);

        val label      = new Span(truncateTitle("Variables"));
        val tabContent = new HorizontalLayout(variablesIcon, label);
        tabContent.setSpacing(false);
        tabContent.setAlignItems(FlexComponent.Alignment.CENTER);

        return new Tab(tabContent);
    }

    private Component createVariablesEditorLayout() {
        val layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(true);

        val config = new JsonEditorConfiguration();
        config.setHasLineNumbers(true);
        config.setTextUpdateDelay(500);
        config.setDarkTheme(true);

        variablesEditor = new JsonEditor(config);
        variablesEditor.setSizeFull();

        variablesValidationField = new TextField();
        variablesValidationField.setReadOnly(true);
        variablesValidationField.setWidthFull();

        val prettyPrintButton = new Button(VaadinIcon.CURLY_BRACKETS.create());
        prettyPrintButton.setAriaLabel("Format JSON");
        prettyPrintButton.setTooltipText("Format JSON.");
        prettyPrintButton.addClickListener(e -> formatJsonEditor(variablesEditor));

        val bottomLayout = new HorizontalLayout();
        bottomLayout.setWidthFull();
        bottomLayout.add(prettyPrintButton, variablesValidationField);

        layout.add(variablesEditor, bottomLayout);

        variablesEditor.addDocumentChangedListener(this::handleVariablesDocumentChanged);

        return layout;
    }

    // Handles changes to the variables document and triggers validation.
    //
    // @param event the document changed event
    private void handleVariablesDocumentChanged(DocumentChangedEvent event) {
        if (validateJsonVariables(event.getNewValue(), variablesValidationField)) {
            this.variablesAndCombinatorSource.setVariables(toVariables(event.getNewValue()));
        }
    }

    private Map<String, Val> toVariables(String variablesString) {
        try {
            val variablesObject = mapper.readTree(variablesString);
            val variables       = new HashMap<String, Val>(variablesObject.size());
            variablesObject.forEachEntry((name, value) -> variables.put(name, Val.of(value)));
            return variables;
        } catch (JsonProcessingException e) {
            log.error("Unexpected invalid JSON in variables after validation.", e);
            return Map.of();
        }
    }

    private void createNewPolicyTab() {
        val policyNumber = policyCounter.getAndIncrement();
        val policyName   = "Policy " + policyNumber;

        val layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(true);

        val config = new SaplEditorConfiguration();
        config.setHasLineNumbers(true);
        config.setTextUpdateDelay(500);
        config.setDarkTheme(true);

        val editor = new SaplEditor(config);
        editor.setConfigurationId("playground");
        editor.setSizeFull();

        val validationField = new TextField();
        validationField.setReadOnly(true);
        validationField.setWidthFull();

        layout.add(editor, validationField);

        val icon = VaadinIcon.QUESTION_CIRCLE.create();
        icon.setColor(ORANGE);
        icon.getStyle().set("margin-right", HALF_EM);

        val label = new Span(truncateTitle(policyName));

        val closeButton = new Button(VaadinIcon.CLOSE_SMALL.create());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        closeButton.getStyle().set("margin-left", HALF_EM);

        val tabContent = new HorizontalLayout(icon, label, closeButton);
        tabContent.setSpacing(false);
        tabContent.setAlignItems(FlexComponent.Alignment.CENTER);

        val tab     = new Tab(tabContent);
        val context = new PolicyTabContext(editor, validationField, icon, label);
        policyTabs.put(tab, context);

        closeButton.addClickListener(event -> {
            policyTabs.remove(tab);
            leftTabSheet.remove(tab);
            checkForNameCollisions();
        });

        leftTabSheet.add(tab, layout);
        leftTabSheet.setSelectedTab(tab);

        editor.addValidationFinishedListener(event -> handlePolicyValidation(context, event));
        editor.setDocument(DEFAULT_POLICY);
    }

    // Handles validation updates for policy documents.
    //
    // @param tab the tab containing the policy
    // @param context the policy tab context
    // @param event the validation finished event
    private void handlePolicyValidation(PolicyTabContext context, ValidationFinishedEvent event) {
        val issues    = event.getIssues();
        val hasErrors = Arrays.stream(issues).anyMatch(issue -> Severity.ERROR == issue.getSeverity());

        val document       = context.editor.getDocument();
        val parsedDocument = INTERPRETER.parseDocument(document);

        updatePolicyDocumentName(context, parsedDocument);
        updatePolicyValidationState(context, hasErrors, issues);
        checkForNameCollisions();
    }

    // Updates the document name for a policy tab based on parsing results.
    //
    // @param context the policy tab context
    // @param parsedDocument the parsed SAPL document
    private void updatePolicyDocumentName(PolicyTabContext context, io.sapl.prp.Document parsedDocument) {
        if (!parsedDocument.isInvalid()) {
            context.documentName = parsedDocument.name();
        } else if (UNKNOWN_POLICY_NAME.equals(context.documentName)) {
            context.documentName = UNKNOWN_POLICY_NAME;
        }
        context.label.setText(truncateTitle(context.documentName));
    }

    // Updates the validation state for a policy tab.
    //
    // @param context the policy tab context
    // @param hasErrors whether the policy has errors
    // @param issues the validation issues
    private void updatePolicyValidationState(PolicyTabContext context, boolean hasErrors,
            io.sapl.vaadin.Issue[] issues) {
        if (hasErrors) {
            context.icon.setIcon(VaadinIcon.CLOSE_CIRCLE);
            context.icon.setColor(RED);
            val errorCount = Arrays.stream(issues).filter(issue -> Severity.ERROR == issue.getSeverity()).count();
            updateValidationField(context.validationField, invalidIcon(), errorCount + " error(s)");
        } else {
            context.icon.setIcon(VaadinIcon.CHECK_CIRCLE);
            context.icon.setColor(GREEN);
            updateValidationField(context.validationField, validIcon(), "OK");
        }
    }

    // Checks all policy tabs for name collisions and updates their visual
    // indicators accordingly.
    // Tabs with duplicate names receive a warning icon and collision message.
    private void checkForNameCollisions() {
        val nameToTabs = new HashMap<String, List<Tab>>();

        for (var entry : policyTabs.entrySet()) {
            val tab     = entry.getKey();
            val context = entry.getValue();
            if (!UNKNOWN_POLICY_NAME.equals(context.documentName)) {
                nameToTabs.computeIfAbsent(context.documentName, k -> new ArrayList<>()).add(tab);
            }
        }

        val duplicateNames = nameToTabs.entrySet().stream().filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey).collect(Collectors.toSet());

        for (var entry : policyTabs.entrySet()) {
            val context = entry.getValue();
            if (duplicateNames.contains(context.documentName)) {
                applyCollisionState(context);
            } else {
                restoreNormalValidationState(context);
            }
        }
    }

    // Applies collision warning state to a policy tab.
    //
    // @param context the policy tab context
    private void applyCollisionState(PolicyTabContext context) {
        context.icon.setIcon(VaadinIcon.WARNING);
        context.icon.setColor(ORANGE);
        updateValidationField(context.validationField, collisionIcon(),
                "Name collision: '" + context.documentName + "'");
    }

    // Restores normal validation state after a collision is resolved.
    // Re-parses the document to check for errors. Xtext validation (client-side)
    // and SAPL
    // parsing (server-side) serve different purposes: Xtext validates for IDE
    // features,
    // while parsing extracts structural information like document validity for UI
    // state management.
    //
    // @param context the policy tab context
    private void restoreNormalValidationState(PolicyTabContext context) {
        val document       = context.editor.getDocument();
        val parsedDocument = INTERPRETER.parseDocument(document);
        val hasErrors      = parsedDocument.isInvalid();

        if (hasErrors) {
            context.icon.setIcon(VaadinIcon.CLOSE_CIRCLE);
            context.icon.setColor(RED);
            updateValidationField(context.validationField, invalidIcon(), "Syntax error");
        } else {
            context.icon.setIcon(VaadinIcon.CHECK_CIRCLE);
            context.icon.setColor(GREEN);
            updateValidationField(context.validationField, validIcon(), "OK");
        }
    }

    // Truncates a title to MAX_TITLE_LENGTH characters, adding ellipsis if
    // necessary.
    //
    // @param title the title to truncate
    // @return the truncated title with "..." appended if it exceeds the maximum
    // length
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
        header.addClassName(Gap.MEDIUM);
        header.setWidth("100%");
        header.setHeight("min-content");
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        val title = new Span("SAPL Playground");
        title.getStyle().set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "bold");

        algorithmBox = new ComboBox<>("Combining Algorithm");
        algorithmBox.setItems(PolicyDocumentCombiningAlgorithm.values());
        algorithmBox.setItemLabelGenerator(PlaygroundView::algorithmName);
        algorithmBox.addValueChangeListener(this::handleAlgorithmChange);
        algorithmBox.setWidth("20em");

        algorithmBox.setValue(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES);

        header.add(title, algorithmBox);
        return header;
    }

    private void handleAlgorithmChange(ValueChangeEvent<PolicyDocumentCombiningAlgorithm> valueChangedEvent) {
        variablesAndCombinatorSource.setCombiningAlgorithm(valueChangedEvent.getValue());
    }

    private static String algorithmName(PolicyDocumentCombiningAlgorithm algorithm) {
        return StringUtils.capitalize(algorithm.toString().replace('_', ' ').toLowerCase());
    }

    private HorizontalLayout buildFooter() {
        val footer = new HorizontalLayout();
        footer.addClassName(Gap.MEDIUM);
        footer.setWidth("100%");
        footer.setHeight("min-content");
        val footerText = new Span("The Footer");
        footer.add(footerText);
        return footer;
    }

    private Icon validIcon() {
        val icon = VaadinIcon.CHECK.create();
        icon.setColor(GREEN);
        return icon;
    }

    private Icon invalidIcon() {
        val icon = VaadinIcon.CLOSE_CIRCLE.create();
        icon.setColor(RED);
        return icon;
    }

    private Icon collisionIcon() {
        val icon = VaadinIcon.WARNING.create();
        icon.setColor(ORANGE);
        return icon;
    }

}
