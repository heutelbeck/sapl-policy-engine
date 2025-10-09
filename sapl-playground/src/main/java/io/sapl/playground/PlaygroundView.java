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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
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
import io.sapl.vaadin.JsonEditor;
import io.sapl.vaadin.JsonEditorConfiguration;
import io.sapl.vaadin.SaplEditor;
import io.sapl.vaadin.SaplEditorConfiguration;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Route("")
@PageTitle("SAPL Playground")
@JsModule("./copytoclipboard.js")
public class PlaygroundView extends Composite<VerticalLayout> {

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

    private static final SAPLInterpreter INTERPERTER = new DefaultSAPLInterpreter();

    private final VariablesAndCombinatorSource variablesAndCombinatorSource = new PlaygroundVariablesAndCombinatorSource();

    private final ObjectMapper          mapper;
    private final AttributeStreamBroker attributeStreamBroker;
    private final FunctionContext       functionContext;
    private final PolicyRetrievalPointSource prpSource;
    private final PolicyDecisionPoint policyDecisionPoint;

    private FixedFunctionsAndAttributesPDPConfigurationProvider pdpConfigurationProvider;

    private transient ArrayList<TracedDecision> decisions = new ArrayList<>(MAX_BUFFER_SIZE);
    private SaplEditor                          saplEditor;
    private TextField                           saplDocumentValidationField;

    public PlaygroundView(ObjectMapper mapper,
            AttributeStreamBroker attributeStreamBroker,
            FunctionContext functionContext) {
        this.mapper                = mapper;
        this.attributeStreamBroker = attributeStreamBroker;
        this.functionContext       = functionContext;
        this.prpSource = new PlaygroundPolicyRetrievalPointSource(INTERPERTER);
        this.pdpConfigurationProvider = new FixedFunctionsAndAttributesPDPConfigurationProvider(
                attributeStreamBroker,
                functionContext,
                variablesAndCombinatorSource,
                List.of(),
                List.of(this::interceptDecision),
                prpSource );
        this.policyDecisionPoint = new EmbeddedPolicyDecisionPoint(pdpConfigurationProvider);

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
        val saplHeader       = new H3("SAPL Policy or Policy Set");
        val saplEditorConfig = new SaplEditorConfiguration();
        saplEditorConfig.setHasLineNumbers(true);
        saplEditorConfig.setTextUpdateDelay(500);
        saplEditorConfig.setDarkTheme(true);
        saplEditor = new SaplEditor(saplEditorConfig);
        saplEditor.setWidthFull();
        saplEditor.setConfigurationId("playground");
        saplDocumentValidationField = new TextField();
        saplDocumentValidationField.setReadOnly(true);
        saplDocumentValidationField.setWidthFull();
        layout.add(saplHeader, saplEditor, saplDocumentValidationField);
        return layout;
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
