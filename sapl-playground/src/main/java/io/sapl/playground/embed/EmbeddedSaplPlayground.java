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
package io.sapl.playground.embed;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.VaadinServlet;
import io.sapl.api.SaplVersion;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.compiler.document.TimestampedVote;
import io.sapl.playground.config.PermalinkConfiguration;
import io.sapl.playground.domain.PermalinkService;
import io.sapl.playground.domain.PermalinkService.PlaygroundState;
import io.sapl.playground.domain.PlaygroundPolicyDecisionPoint;
import io.sapl.playground.domain.PlaygroundValidator;
import io.sapl.vaadin.DocumentChangedEvent;
import io.sapl.vaadin.Issue;
import io.sapl.vaadin.IssueSeverity;
import io.sapl.vaadin.ValidationFinishedEvent;
import io.sapl.vaadin.ValidationStatusDisplay;
import io.sapl.vaadin.lsp.JsonEditor;
import io.sapl.vaadin.lsp.JsonEditorConfiguration;
import io.sapl.vaadin.lsp.SaplEditorLsp;
import io.sapl.vaadin.lsp.SaplEditorLspConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;
import reactor.core.Disposable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;

import java.io.Serial;
import java.util.List;
import java.util.Map;

/**
 * Stripped-down embeddable SAPL playground for use as a web component on
 * external sites. Provides a single policy editor, an authorization
 * subscription editor, an evaluate button, and read-only decision output.
 */
@Slf4j
public class EmbeddedSaplPlayground extends Composite<VerticalLayout> {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String EDITOR_CONFIGURATION_ID = "playground";

    private static final String CSS_MARGIN_TOP = "margin-top";
    private static final String CSS_SPACE_XS   = "0.25rem";
    private static final String CSS_SPACE_S    = "0.5rem";

    private static final CombiningAlgorithm COMBINING_ALGORITHM = new CombiningAlgorithm(VotingMode.UNIQUE,
            DefaultDecision.ABSTAIN, ErrorHandling.PROPAGATE);

    private static final String DEFAULT_POLICY       = "policy \"example\"\npermit\n  action == \"read\"";
    private static final String DEFAULT_SUBSCRIPTION = """
            {
              "subject": "alice",
              "action": "read",
              "resource": "document"
            }""";
    private static final String DEFAULT_VARIABLES    = "{}";

    private static final String LABEL_AUTHORIZATION_SUBSCRIPTION = "Authorization Subscription";
    private static final String LABEL_DECISION                   = "Decision";
    private static final String LABEL_EVALUATE                   = "Evaluate";
    private static final String LABEL_FORMAT_JSON                = "Format JSON";
    private static final String LABEL_FORMAT_POLICY              = "Format Policy";
    private static final String LABEL_OPEN_PLAYGROUND            = "Open in Playground";
    private static final String LABEL_STOP                       = "Stop";

    private static final String MESSAGE_CANNOT_FORMAT_JSON   = "Cannot format invalid JSON.";
    private static final String MESSAGE_FAILED_SUBSCRIBE     = "Failed to subscribe: ";
    private static final String MESSAGE_INVALID_SUBSCRIPTION = "Invalid authorization subscription";

    private static final String URL_HASH_PREFIX = "#permalink/";

    private final JsonMapper                        mapper;
    private transient PlaygroundValidator           validator;
    private transient PlaygroundPolicyDecisionPoint policyDecisionPoint;
    private transient PermalinkConfiguration        permalinkConfiguration;
    private transient PermalinkService              permalinkService;

    private SaplEditorLsp           policyEditor;
    private ValidationStatusDisplay policyValidationDisplay;
    private JsonEditor              subscriptionEditor;
    private ValidationStatusDisplay subscriptionValidationDisplay;
    private JsonEditor              decisionEditor;
    private Button                  evaluateButton;

    private volatile boolean     isSubscriptionActive;
    private transient Disposable activeSubscription;

    /**
     * No-arg constructor required by {@link EmbeddedSaplPlaygroundExporter}.
     * Spring beans are resolved from the Vaadin servlet's application context.
     */
    public EmbeddedSaplPlayground() {
        val context = getSpringApplicationContext();

        this.mapper                 = context.getBean(JsonMapper.class);
        this.policyDecisionPoint    = context.getBean(PlaygroundPolicyDecisionPoint.class);
        this.validator              = context.getBean(PlaygroundValidator.class);
        this.permalinkConfiguration = context.getBean(PermalinkConfiguration.class);
        this.permalinkService       = context.getBean(PermalinkService.class);

        buildLayout();
        initializeDefaults();

        addDetachListener(event -> cleanup());
    }

    private static ApplicationContext getSpringApplicationContext() {
        return WebApplicationContextUtils
                .getRequiredWebApplicationContext(VaadinServlet.getCurrent().getServletContext());
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        attachEvent.getUI().getPushConfiguration().setPushMode(PushMode.AUTOMATIC);
    }

    /**
     * Sets the policy document content from the web component attribute.
     *
     * @param policy the SAPL policy text
     */
    public void setPolicy(String policy) {
        if (policyEditor != null && policy != null) {
            policyEditor.setDocument(policy);
        }
    }

    /**
     * Returns the current policy document content.
     *
     * @return the SAPL policy text
     */
    public String getPolicy() {
        return policyEditor != null ? policyEditor.getDocument() : "";
    }

    /**
     * Sets the authorization subscription content from the web component attribute.
     *
     * @param subscription the authorization subscription JSON
     */
    public void setSubscription(String subscription) {
        if (subscriptionEditor != null && subscription != null) {
            subscriptionEditor.setDocument(subscription);
        }
    }

    /**
     * Returns the current authorization subscription content.
     *
     * @return the authorization subscription JSON
     */
    public String getSubscription() {
        return subscriptionEditor != null ? subscriptionEditor.getDocument() : "";
    }

    private void buildLayout() {
        val content = getContent();
        content.setPadding(true);
        content.setSpacing(false);
        content.setWidthFull();

        content.add(buildPolicySection());
        content.add(buildBottomSection());
    }

    private VerticalLayout buildPolicySection() {
        val section = new VerticalLayout();
        section.setPadding(false);
        section.setSpacing(false);

        policyEditor = createSaplEditor();
        policyEditor.setWidthFull();
        policyEditor.setHeight("200px");
        policyEditor.addValidationFinishedListener(this::handlePolicyValidation);
        policyEditor.addDocumentChangedListener(event -> {
            updatePolicyRetrievalPoint();
            resubscribeIfActive();
        });

        policyValidationDisplay = new ValidationStatusDisplay();
        policyValidationDisplay.setWidthFull();

        evaluateButton = new Button(LABEL_EVALUATE, VaadinIcon.PLAY.create());
        evaluateButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        evaluateButton.addClickListener(event -> toggleEvaluation());

        val playgroundButton = new Button(LABEL_OPEN_PLAYGROUND, VaadinIcon.EXTERNAL_LINK.create());
        playgroundButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        playgroundButton.addClickListener(event -> openInPlayground());

        val controls = new HorizontalLayout();
        controls.setWidthFull();
        controls.setPadding(false);
        controls.setSpacing(true);
        controls.setAlignItems(FlexComponent.Alignment.CENTER);
        controls.getStyle().set(CSS_MARGIN_TOP, CSS_SPACE_XS);
        controls.add(createFormatPolicyButton(), policyValidationDisplay, evaluateButton, playgroundButton);
        controls.setFlexGrow(1, policyValidationDisplay);

        section.add(policyEditor, controls);
        return section;
    }

    private HorizontalLayout buildBottomSection() {
        val layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setAlignItems(FlexComponent.Alignment.STRETCH);
        layout.getStyle().set(CSS_MARGIN_TOP, CSS_SPACE_S);

        val subscriptionColumn = buildSubscriptionColumn();
        val decisionColumn     = buildDecisionColumn();

        layout.add(subscriptionColumn, decisionColumn);
        layout.setFlexGrow(1, subscriptionColumn);
        layout.setFlexGrow(1, decisionColumn);

        return layout;
    }

    private VerticalLayout buildSubscriptionColumn() {
        val column = new VerticalLayout();
        column.setPadding(false);
        column.setSpacing(false);

        val header = new H5(LABEL_AUTHORIZATION_SUBSCRIPTION);
        header.getStyle().set("margin", "0").set("padding", "0 0 0.25rem 0");

        subscriptionEditor = createJsonEditor(true);
        subscriptionEditor.setWidthFull();
        subscriptionEditor.setHeight("150px");
        subscriptionEditor.addDocumentChangedListener(this::handleSubscriptionChanged);

        subscriptionValidationDisplay = new ValidationStatusDisplay();
        subscriptionValidationDisplay.setWidthFull();

        val controls = createEditorControls(createFormatJsonButton(), subscriptionValidationDisplay);

        column.add(header, subscriptionEditor, controls);
        return column;
    }

    private VerticalLayout buildDecisionColumn() {
        val column = new VerticalLayout();
        column.setPadding(false);
        column.setSpacing(false);

        val header = new H5(LABEL_DECISION);
        header.getStyle().set("margin", "0").set("padding", "0 0 0.25rem 0");

        decisionEditor = createJsonEditor(false);
        decisionEditor.setWidthFull();
        decisionEditor.setHeight("150px");

        column.add(header, decisionEditor);
        return column;
    }

    private HorizontalLayout createEditorControls(Button formatButton, ValidationStatusDisplay validationDisplay) {
        val layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setPadding(false);
        layout.setSpacing(true);
        layout.setAlignItems(FlexComponent.Alignment.CENTER);
        layout.getStyle().set(CSS_MARGIN_TOP, CSS_SPACE_XS);
        layout.add(formatButton, validationDisplay);
        layout.setFlexGrow(1, validationDisplay);
        return layout;
    }

    private SaplEditorLsp createSaplEditor() {
        val config = new SaplEditorLspConfiguration();
        config.setHasLineNumbers(true);
        config.setDarkTheme(true);
        config.setWsUrl(permalinkConfiguration.getLspUrl());

        val editor = new SaplEditorLsp(config);
        editor.setConfigurationId(EDITOR_CONFIGURATION_ID);
        return editor;
    }

    private JsonEditor createJsonEditor(boolean editable) {
        val config = new JsonEditorConfiguration();
        config.setHasLineNumbers(editable);
        config.setDarkTheme(true);
        config.setReadOnly(!editable);
        config.setLint(editable);
        return new JsonEditor(config);
    }

    private Button createFormatPolicyButton() {
        val button = new Button(VaadinIcon.CURLY_BRACKETS.create());
        button.setAriaLabel(LABEL_FORMAT_POLICY);
        button.setTooltipText(LABEL_FORMAT_POLICY);
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        button.getStyle().set("transform", "scale(0.65)").set("transform-origin", "left center");
        button.addClickListener(event -> policyEditor.format());
        return button;
    }

    private Button createFormatJsonButton() {
        val button = new Button(VaadinIcon.CURLY_BRACKETS.create());
        button.setAriaLabel(LABEL_FORMAT_JSON);
        button.setTooltipText(LABEL_FORMAT_JSON);
        button.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE, ButtonVariant.LUMO_SMALL);
        button.getStyle().set("transform", "scale(0.65)").set("transform-origin", "left center");
        button.addClickListener(event -> formatSubscriptionEditor());
        return button;
    }

    private void initializeDefaults() {
        policyDecisionPoint.setCombiningAlgorithm(COMBINING_ALGORITHM);
        policyDecisionPoint.setVariables(Map.of());
        subscriptionEditor.setDocument(DEFAULT_SUBSCRIPTION);
        policyEditor.setDocument(DEFAULT_POLICY);
    }

    private void handlePolicyValidation(ValidationFinishedEvent event) {
        policyValidationDisplay.setIssues(event.getIssues());
        updatePolicyRetrievalPoint();
    }

    private void updatePolicyRetrievalPoint() {
        val document = policyEditor.getDocument();
        if (document != null && !document.isEmpty()) {
            policyDecisionPoint.updatePolicyRetrievalPoint(List.of(document));
        }
    }

    private void handleSubscriptionChanged(DocumentChangedEvent event) {
        val result = validator.validateAuthorizationSubscription(event.getNewValue());
        if (result.isValid()) {
            subscriptionValidationDisplay.setIssues(List.of());
        } else {
            val issue = new Issue(result.message(), IssueSeverity.ERROR, null, null, null, null);
            subscriptionValidationDisplay.setIssues(List.of(issue));
        }
        resubscribeIfActive();
    }

    private void toggleEvaluation() {
        if (isSubscriptionActive) {
            stopSubscription();
        } else {
            startSubscription();
        }
    }

    private void startSubscription() {
        isSubscriptionActive = true;
        evaluateButton.setText(LABEL_STOP);
        evaluateButton.setIcon(VaadinIcon.STOP.create());
        subscribe();
    }

    private void stopSubscription() {
        if (activeSubscription != null && !activeSubscription.isDisposed()) {
            activeSubscription.dispose();
        }
        activeSubscription   = null;
        isSubscriptionActive = false;
        evaluateButton.setText(LABEL_EVALUATE);
        evaluateButton.setIcon(VaadinIcon.PLAY.create());
    }

    private void subscribe() {
        if (activeSubscription != null && !activeSubscription.isDisposed()) {
            activeSubscription.dispose();
        }

        val subscription = parseSubscription();
        if (subscription == null) {
            decisionEditor.setDocument("\"" + MESSAGE_INVALID_SUBSCRIPTION + "\"");
            stopSubscription();
            return;
        }

        try {
            activeSubscription = policyDecisionPoint.decide(subscription).subscribe(this::handleDecisionOnUiThread,
                    this::handleSubscriptionError);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            log.error("Failed to create subscription", exception);
            stopSubscription();
            decisionEditor.setDocument("\"" + MESSAGE_FAILED_SUBSCRIBE + exception.getMessage() + "\"");
        }
    }

    private void resubscribeIfActive() {
        if (isSubscriptionActive) {
            subscribe();
        }
    }

    private void handleDecisionOnUiThread(TimestampedVote timestampedVote) {
        getUI().ifPresent(ui -> ui.access(() -> displayDecision(timestampedVote)));
    }

    private void displayDecision(TimestampedVote timestampedVote) {
        try {
            val prettyJson = mapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(timestampedVote.vote().authorizationDecision());
            decisionEditor.setDocument(prettyJson);
        } catch (JacksonException exception) {
            decisionEditor.setDocument("\"Error reading decision\"");
        }
    }

    private void handleSubscriptionError(Throwable error) {
        log.error("Error in PDP subscription", error);
        getUI().ifPresent(ui -> ui.access(() -> {
            stopSubscription();
            decisionEditor.setDocument("\"" + MESSAGE_FAILED_SUBSCRIBE + error.getMessage() + "\"");
        }));
    }

    private AuthorizationSubscription parseSubscription() {
        val json = subscriptionEditor.getDocument();
        try {
            return mapper.readValue(json, AuthorizationSubscription.class);
        } catch (JacksonException exception) {
            return null;
        }
    }

    private void formatSubscriptionEditor() {
        val jsonString = subscriptionEditor.getDocument();
        try {
            val json = mapper.readTree(jsonString);
            if (json instanceof MissingNode) {
                return;
            }
            subscriptionEditor.setDocument(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
        } catch (JacksonException exception) {
            log.debug(MESSAGE_CANNOT_FORMAT_JSON);
        }
    }

    private void openInPlayground() {
        try {
            val state   = new PlaygroundState(List.of(policyEditor.getDocument()), subscriptionEditor.getDocument(),
                    DEFAULT_VARIABLES, COMBINING_ALGORITHM, 0);
            val encoded = permalinkService.encode(state);
            val url     = permalinkConfiguration.getBaseUrl() + URL_HASH_PREFIX + encoded;
            getUI().ifPresent(ui -> ui.getPage().open(url, "_blank"));
        } catch (PermalinkService.PermalinkException exception) {
            log.debug("Failed to create permalink, opening base URL", exception);
            getUI().ifPresent(ui -> ui.getPage().open(permalinkConfiguration.getBaseUrl(), "_blank"));
        }
    }

    private void cleanup() {
        stopSubscription();
    }
}
