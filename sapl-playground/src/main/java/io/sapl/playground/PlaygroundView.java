package io.sapl.playground;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.theme.lumo.LumoUtility.Gap;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.TracedDecision;
import io.sapl.attributes.broker.api.AttributeStreamBroker;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.pdp.EmbeddedPolicyDecisionPoint;
import io.sapl.pdp.config.VariablesAndCombinatorSource;
import io.sapl.pdp.config.fixed.FixedFunctionsAndAttributesPDPConfigurationProvider;
import io.sapl.prp.PolicyRetrievalPointSource;
import io.sapl.vaadin.DocumentChangedEvent;
import io.sapl.vaadin.JsonEditor;
import io.sapl.vaadin.JsonEditorConfiguration;
import io.sapl.vaadin.SaplEditor;
import io.sapl.vaadin.SaplEditorConfiguration;
import io.sapl.vaadin.ValidationFinishedEvent;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.eclipse.xtext.diagnostics.Severity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

    private final VariablesAndCombinatorSource variablesAndCombinatorSource = new PlaygroundVariablesAndCombinatorSource();

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

    private final Map<Tab, SaplEditor> saplEditorsByTab = new HashMap<>();
    private final AtomicInteger        policyCounter    = new AtomicInteger(1);

    public PlaygroundView(ObjectMapper mapper,
            AttributeStreamBroker attributeStreamBroker,
            FunctionContext functionContext) {
        this.mapper                   = mapper;
        this.attributeStreamBroker    = attributeStreamBroker;
        this.functionContext          = functionContext;
        this.prpSource                = new PlaygroundPolicyRetrievalPointSource(INTERPRETER);
        this.pdpConfigurationProvider = new FixedFunctionsAndAttributesPDPConfigurationProvider(attributeStreamBroker,
                functionContext, variablesAndCombinatorSource, List.of(), List.of(this::interceptDecision), prpSource);
        this.policyDecisionPoint      = new EmbeddedPolicyDecisionPoint(pdpConfigurationProvider);

        val header = buildHeader();
        val main   = buildMain();
        val footer = buildFooter();
        getContent().setSizeFull();
        getContent().getStyle().set("flex-grow", "1");
        getContent().add(header);
        getContent().add(main);
        getContent().add(footer);
    }

    private TracedDecision interceptDecision(TracedDecision tracedDecision) {
        return tracedDecision;
    }

    private Component buildMain() {
        val main = new HorizontalLayout();
        main.setSpacing(false);
        main.setWidth("100%");
        main.getStyle().set("flex-grow", "1");
        val rightSide = new VerticalLayout();
        rightSide.setSizeFull();
        val subscriptionLabel = new Span("Authorization Subscription");
        val jsonEditorConfig  = new JsonEditorConfiguration();
        jsonEditorConfig.setHasLineNumbers(true);
        jsonEditorConfig.setTextUpdateDelay(500);
        jsonEditorConfig.setDarkTheme(true);
        val jsonEditor = new JsonEditor(jsonEditorConfig);
        jsonEditor.setSizeFull();
        rightSide.add(subscriptionLabel, jsonEditor);
        main.add(playgroundLeft(), rightSide);
        return main;
    }

    private Component playgroundLeft() {
        val layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);
        layout.setSpacing(false);

        leftTabSheet = new TabSheet();
        leftTabSheet.setSizeFull();

        variablesTab = createVariablesTab();
        leftTabSheet.add(variablesTab, createVariablesEditor());

        createNewPolicyTab();

        val newPolicyButton = new Button("+ New Policy");
        newPolicyButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        newPolicyButton.addClickListener(event -> createNewPolicyTab());

        leftTabSheet.setSuffixComponent(newPolicyButton);

        layout.add(leftTabSheet);

        return layout;
    }

    private Tab createVariablesTab() {
        variablesIcon = VaadinIcon.QUESTION_CIRCLE.create();
        variablesIcon.setColor("orange");
        variablesIcon.getStyle().set("margin-right", "0.5em");

        val label      = new Span(truncateTitle("Variables"));
        val tabContent = new HorizontalLayout(variablesIcon, label);
        tabContent.setSpacing(false);
        tabContent.setAlignItems(HorizontalLayout.Alignment.CENTER);

        return new Tab(tabContent);
    }

    private Component createVariablesEditor() {
        val config = new JsonEditorConfiguration();
        config.setHasLineNumbers(true);
        config.setTextUpdateDelay(500);
        config.setDarkTheme(true);

        variablesEditor = new JsonEditor(config);
        variablesEditor.setDocument("{}");
        variablesEditor.setSizeFull();

        variablesEditor.addDocumentChangedListener(this::updateVariablesValidation);

        updateVariablesValidationIcon();

        return variablesEditor;
    }

    private void updateVariablesValidation(DocumentChangedEvent event) {
        updateVariablesValidationIcon();
    }

    private void updateVariablesValidationIcon() {
        try {
            mapper.readTree(variablesEditor.getDocument());
            variablesIcon.setIcon(VaadinIcon.CHECK_CIRCLE);
            variablesIcon.setColor("green");
        } catch (Exception e) {
            variablesIcon.setIcon(VaadinIcon.CLOSE_CIRCLE);
            variablesIcon.setColor("red");
        }
    }

    private void createNewPolicyTab() {
        val policyNumber = policyCounter.getAndIncrement();
        val policyName   = "Policy " + policyNumber;

        val config = new SaplEditorConfiguration();
        config.setHasLineNumbers(true);
        config.setTextUpdateDelay(500);
        config.setDarkTheme(true);

        val editor = new SaplEditor(config);
        editor.setDocument(DEFAULT_POLICY);
        editor.setConfigurationId("playground");
        editor.setSizeFull();

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
            leftTabSheet.remove(tab);
        });

        editor.addValidationFinishedListener(event -> updatePolicyTabValidation(event, icon, label, editor));

        saplEditorsByTab.put(tab, editor);
        leftTabSheet.add(tab, editor);
        leftTabSheet.setSelectedTab(tab);
    }

    private void updatePolicyTabValidation(ValidationFinishedEvent event, Icon icon, Span label, SaplEditor editor) {
        val issues    = event.getIssues();
        val hasErrors = Arrays.stream(issues).anyMatch(issue -> Severity.ERROR == issue.getSeverity());

        if (hasErrors) {
            icon.setIcon(VaadinIcon.CLOSE_CIRCLE);
            icon.setColor("red");
        } else {
            icon.setIcon(VaadinIcon.CHECK_CIRCLE);
            icon.setColor("green");
        }

        val document       = editor.getDocument();
        val parsedDocument = INTERPRETER.parseDocument(document);
        if (!parsedDocument.isInvalid()) {
            label.setText(truncateTitle(parsedDocument.name()));
        }
    }

    /// Truncates a title to MAX_TITLE_LENGTH characters, adding ellipsis if
    /// necessary.
    ///
    /// @param title the title to truncate
    /// @return the truncated title with "..." appended if it exceeds the maximum
    /// length
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
        val title = new Span("SAPL Playground");
        header.add(title);
        return header;
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
