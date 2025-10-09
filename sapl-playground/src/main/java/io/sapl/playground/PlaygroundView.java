package io.sapl.playground;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import org.apache.commons.lang3.StringUtils;
import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasValue.ValueChangeEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.dataview.GridListDataView;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
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
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.pdp.config.fixed.FixedFunctionsAndAttributesPDPConfigurationProvider;
import io.sapl.pdp.interceptors.ErrorReportGenerator;
import io.sapl.pdp.interceptors.ErrorReportGenerator.OutputFormat;
import io.sapl.pdp.interceptors.ReportBuilderUtil;
import io.sapl.pdp.interceptors.ReportTextRenderUtil;
import io.sapl.prp.PolicyRetrievalPointSource;
import io.sapl.vaadin.DocumentChangedEvent;
import io.sapl.vaadin.JsonEditor;
import io.sapl.vaadin.JsonEditorConfiguration;
import io.sapl.vaadin.SaplEditor;
import io.sapl.vaadin.SaplEditorConfiguration;
import io.sapl.vaadin.ValidationFinishedEvent;
import io.sapl.vaadin.graph.JsonGraphVisualization;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.xtext.diagnostics.Severity;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Slf4j
@Route("")
@PageTitle("SAPL Playground")
@JsModule("./copytoclipboard.js")
public class PlaygroundView extends Composite<VerticalLayout> {

    private static final int    MAX_BUFFER_SIZE      = 50;
    private static final int    DEFAULT_BUFFER_SIZE  = 10;
    private static final int    MAX_TITLE_LENGTH     = 15;
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

    private final PlaygroundVariablesAndCombinatorSource variablesAndCombinatorSource = new PlaygroundVariablesAndCombinatorSource();

    private final ObjectMapper               mapper;
    private final AttributeStreamBroker      attributeStreamBroker;
    private final FunctionContext            functionContext;
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

    private final Icon validIcon   = VaadinIcon.CHECK.create();
    private final Icon invalidIcon = VaadinIcon.CLOSE_CIRCLE.create();

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
    private DocumentationDrawer                        documentationDrawer;

    private boolean scrollLock;
    private String  errorReport;

    private final Map<Tab, SaplEditor> saplEditorsByTab      = new HashMap<>();
    private final Map<Tab, TextField>  validationFieldsByTab = new HashMap<>();
    private final AtomicInteger        policyCounter         = new AtomicInteger(1);

    public PlaygroundView(ObjectMapper mapper,
            AttributeStreamBroker attributeStreamBroker,
            FunctionContext functionContext,
            PolicyInformationPointDocumentationProvider pipDocumentationProvider) {
        this.mapper                   = mapper;
        this.attributeStreamBroker    = attributeStreamBroker;
        this.functionContext          = functionContext;
        this.prpSource                = new PlaygroundPolicyRetrievalPointSource(INTERPRETER);
        this.pdpConfigurationProvider = new FixedFunctionsAndAttributesPDPConfigurationProvider(attributeStreamBroker,
                functionContext, variablesAndCombinatorSource, List.of(), List.of(this::interceptDecision), prpSource);
        this.policyDecisionPoint      = new EmbeddedPolicyDecisionPoint(pdpConfigurationProvider);

        // Create the documentation drawer
        this.documentationDrawer = new DocumentationDrawer(pipDocumentationProvider, functionContext);

        val header = buildHeader();
        val main   = buildMain();
        val footer = buildFooter();
        getContent().setSizeFull();
        getContent().getStyle().set("flex-grow", "1");

        getContent().add(header);
        getContent().add(main);
        getContent().add(footer);
        getContent().add(documentationDrawer.getToggleButton());

        validIcon.setColor("green");
        invalidIcon.setColor("red");
        handleSubscriptionValidationUpdate(false, "");
        deactivateScrollLock();
    }

    private TracedDecision interceptDecision(TracedDecision tracedDecision) {
        handleNewDecision(tracedDecision);
        return tracedDecision;
    }

    private Component buildMain() {
        val mainSplit = new SplitLayout(playgroundLeft(), playgroundRight());
        mainSplit.setSizeFull();
        mainSplit.setSplitterPosition(30.0D);
        return mainSplit;
    }

    private Component playgroundLeft() {
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

        layout.add(leftTabSheet);

        return layout;
    }

    private Component playgroundRight() {
        val layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);

        val subscriptionHeader = new H3("Authorization Subscription");
        subscriptionEditor = createSubscriptionEditor();

        val bottomLayout = new HorizontalLayout();
        bottomLayout.setWidthFull();

        val prettyPrintButton = new Button(VaadinIcon.CURLY_BRACKETS.create());
        prettyPrintButton.setAriaLabel("Format JSON");
        prettyPrintButton.setTooltipText("Format JSON.");
        prettyPrintButton.addClickListener(this::handleFormatSubscriptionJsonButtonClick);

        subscriptionValidationField = new TextField();
        subscriptionValidationField.setReadOnly(true);
        subscriptionValidationField.setWidthFull();

        bottomLayout.add(prettyPrintButton, subscriptionValidationField);

        val decisionsHeader = new H3("Decisions");
        decisionsGrid = new DecisionsGrid();
        decisionsView = decisionsGrid.setItems(decisions);

        decisionsGrid.addSelectionListener(this::decisionSelected);

        val inspectorLayout = new VerticalLayout();
        inspectorLayout.setSizeFull();
        inspectorLayout.setPadding(false);
        inspectorLayout.setSpacing(false);
        inspectorLayout.add(controlButtons(), detailsView());

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
        editor.setDocument(DEFAULT_SUBSCRIPTION);
        editor.setWidthFull();
        editor.setHeight("200px");

        editor.addDocumentChangedListener(this::handleSubscriptionDocumentChanged);

        return editor;
    }

    private void handleFormatSubscriptionJsonButtonClick(ClickEvent<Button> event) {
        formatJsonEditor(subscriptionEditor);
    }

    private void handleFormatVariablesJsonButtonClick(ClickEvent<Button> event) {
        formatJsonEditor(variablesEditor);
    }

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

    private void handleSubscriptionDocumentChanged(DocumentChangedEvent event) {
        try {
            mapper.readTree(event.getNewValue());
            handleSubscriptionValidationUpdate(true, "OK");
        } catch (Exception e) {
            handleSubscriptionValidationUpdate(false, "Invalid Subscription");
        }

        if (Boolean.TRUE.equals(clearOnNewSubscriptionCheckBox.getValue())) {
            decisions.clear();
            decisionsView.refreshAll();
        }
    }

    private void handleSubscriptionValidationUpdate(boolean isValid, String message) {
        subscriptionValidationField.setPrefixComponent(isValid ? validIcon : invalidIcon);
        subscriptionValidationField.setValue(message);
    }

    private Component controlButtons() {
        val layout = new HorizontalLayout();
        layout.setAlignItems(HorizontalLayout.Alignment.CENTER);

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
            decisions.remove(0);
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
            traceGraphVisualization.setJsonData(trace.toPrettyString());

            setErrorAreaContentFromErrors(tracedDecision.getErrorsFromTrace());
        }
    }

    private void clearDetailsView() {
        decisionsArea.setDocument("");
        setErrorAreaContent("", "");
        decisionsJsonTraceArea.setDocument("");
        decisionsJsonReportArea.setDocument("");
        reportArea.setValue("");
        traceGraphVisualization.setJsonData("{}");
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

    private void handleNewDecision(TracedDecision decision) {
        decisions.add(decision);
        if (decisions.size() > bufferSizeField.getValue()) {
            decisions.remove(0);
        }
        decisionsView.refreshAll();
        if (scrollLock) {
            decisionsGrid.scrollToEnd();
        }
    }

    private Component detailsView() {
        val tabSheet = new TabSheet();
        tabSheet.setSizeFull();
        tabSheet.add("JSON", decisionJson());
        tabSheet.add("Errors", decisionErrors());
        tabSheet.add("Report", textReport());
        tabSheet.add("JSON Report", decisionJsonReport());
        tabSheet.add("JSON Trace", decisionJsonTrace());
        tabSheet.add("Trace Graph", traceGraph());
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

    private Component traceGraph() {
        traceGraphVisualization = new JsonGraphVisualization();
        traceGraphVisualization.setSizeFull();
        traceGraphVisualization.setJsonData("{}");
        return traceGraphVisualization;
    }

    private Component textReport() {
        val reportLayout = new HorizontalLayout();
        reportLayout.setSizeFull();
        reportLayout.setPadding(false);
        reportArea = new TextArea();
        reportArea.setSizeFull();
        reportArea.setReadOnly(true);
        reportArea.getStyle().set("font-family", "monospace");
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

    private void copyToClipboard(String content) {
        UI.getCurrent().getPage().executeJs("window.copyToClipboard($0)", content);
        Notification notification = Notification.show("Content copied to clipboard.");
        notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
    }

    private Tab createVariablesTab() {
        variablesIcon = VaadinIcon.CHECK_CIRCLE.create();
        variablesIcon.setColor("green");
        variablesIcon.getStyle().set("margin-right", "0.5em");

        val label      = new Span(truncateTitle("Variables"));
        val tabContent = new HorizontalLayout(variablesIcon, label);
        tabContent.setSpacing(false);
        tabContent.setAlignItems(HorizontalLayout.Alignment.CENTER);

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

        val bottomLayout = new HorizontalLayout();
        bottomLayout.setWidthFull();

        val prettyPrintButton = new Button(VaadinIcon.CURLY_BRACKETS.create());
        prettyPrintButton.setAriaLabel("Format JSON");
        prettyPrintButton.setTooltipText("Format JSON.");
        prettyPrintButton.addClickListener(this::handleFormatVariablesJsonButtonClick);

        variablesValidationField = new TextField();
        variablesValidationField.setReadOnly(true);
        variablesValidationField.setWidthFull();

        bottomLayout.add(prettyPrintButton, variablesValidationField);

        layout.add(variablesEditor, bottomLayout);

        variablesEditor.addDocumentChangedListener(this::updateVariablesValidation);
        variablesEditor.setDocument("{}");

        return layout;
    }

    private void updateVariablesValidation(DocumentChangedEvent event) {
        try {
            mapper.readTree(event.getNewValue());
            variablesIcon.setIcon(VaadinIcon.CHECK_CIRCLE);
            variablesIcon.setColor("green");
            variablesValidationField.setPrefixComponent(validIcon);
            variablesValidationField.setValue("OK");
        } catch (Exception e) {
            variablesIcon.setIcon(VaadinIcon.CLOSE_CIRCLE);
            variablesIcon.setColor("red");
            variablesValidationField.setPrefixComponent(invalidIcon);
            variablesValidationField.setValue("Invalid JSON");
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
        icon.setColor("orange");
        icon.getStyle().set("margin-right", "0.5em");

        val label = new Span(truncateTitle(policyName));

        val closeButton = new Button(VaadinIcon.CLOSE_SMALL.create());
        closeButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        closeButton.getStyle().set("margin-left", "0.5em");

        val tabContent = new HorizontalLayout(icon, label, closeButton);
        tabContent.setSpacing(false);
        tabContent.setAlignItems(HorizontalLayout.Alignment.CENTER);

        val tab = new Tab(tabContent);

        closeButton.addClickListener(event -> {
            saplEditorsByTab.remove(tab);
            validationFieldsByTab.remove(tab);
            leftTabSheet.remove(tab);
        });

        editor.addValidationFinishedListener(
                event -> updatePolicyTabValidation(event, icon, label, editor, validationField));

        saplEditorsByTab.put(tab, editor);
        validationFieldsByTab.put(tab, validationField);
        leftTabSheet.add(tab, layout);
        leftTabSheet.setSelectedTab(tab);

        editor.setDocument(DEFAULT_POLICY);
    }

    private void updatePolicyTabValidation(ValidationFinishedEvent event, Icon icon, Span label, SaplEditor editor,
            TextField validationField) {
        val issues    = event.getIssues();
        val hasErrors = Arrays.stream(issues).anyMatch(issue -> Severity.ERROR == issue.getSeverity());

        if (hasErrors) {
            icon.setIcon(VaadinIcon.CLOSE_CIRCLE);
            icon.setColor("red");
            validationField.setPrefixComponent(invalidIcon);
            val errorCount = Arrays.stream(issues).filter(issue -> Severity.ERROR == issue.getSeverity()).count();
            validationField.setValue(errorCount + " error(s)");
        } else {
            icon.setIcon(VaadinIcon.CHECK_CIRCLE);
            icon.setColor("green");
            validationField.setPrefixComponent(validIcon);
            validationField.setValue("OK");
        }

        val document       = editor.getDocument();
        val parsedDocument = INTERPRETER.parseDocument(document);
        if (!parsedDocument.isInvalid()) {
            label.setText(truncateTitle(parsedDocument.name()));
        }
    }

    /*
     * Truncates a title to MAX_TITLE_LENGTH characters, adding ellipsis if
     * necessary.
     *
     * @param title the title to truncate
     *
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

    private HorizontalLayout buildHeader() {
        val header = new HorizontalLayout();
        header.addClassName(Gap.MEDIUM);
        header.setWidth("100%");
        header.setHeight("min-content");
        header.setAlignItems(HorizontalLayout.Alignment.CENTER);

        val title = new Span("SAPL Playground");
        title.getStyle().set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "bold");

        algorithmBox = new ComboBox<>("Combining Algorithm");
        algorithmBox.setItems(PolicyDocumentCombiningAlgorithm.values());
        algorithmBox.setItemLabelGenerator(PlaygroundView::algorithmName);
        algorithmBox.setValue(PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES);
        algorithmBox.addValueChangeListener(this::onAlgorithmChange);
        algorithmBox.setWidth("20em");

        header.add(title, algorithmBox);
        return header;
    }

    private void onAlgorithmChange(ValueChangeEvent<PolicyDocumentCombiningAlgorithm> valueChangedEvent) {
        if (!valueChangedEvent.isFromClient())
            return;
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
}
