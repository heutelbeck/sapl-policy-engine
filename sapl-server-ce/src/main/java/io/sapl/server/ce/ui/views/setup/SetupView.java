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
package io.sapl.server.ce.ui.views.setup;

import org.springframework.context.annotation.Conditional;

import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import io.sapl.api.SaplVersion;
import io.sapl.server.ce.model.setup.condition.SetupNotFinishedCondition;
import io.sapl.server.ce.ui.utils.ErrorComponentUtils;
import io.sapl.server.ce.ui.views.SetupLayout;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

@AnonymousAllowed
@PageTitle("Setup Wizard")
@Route(value = SetupView.ROUTE, layout = SetupLayout.class)
@Conditional(SetupNotFinishedCondition.class)
public class SetupView extends VerticalLayout {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    public static final String           ROUTE = "";
    private transient HttpServletRequest httpServletRequest;

    public SetupView(HttpServletRequest httpServletRequest) {
        this.httpServletRequest = httpServletRequest;
    }

    @PostConstruct
    private void init() {
        if (!httpServletRequest.isSecure()) {
            add(ErrorComponentUtils.getErrorDiv(SetupLayout.INSECURE_CONNECTION_MESSAGE));
        }

        final var hWelcome = new H1("Welcome to SAPL Server CE Setup Wizard");

        final var hDesc = new H2("Description");
        final var pDesc = new Paragraph(
                "The Setup Wizard is a component designed to streamline the setup and initial configuration process of the SAPL Server CE. It serves as a user-friendly interface that guides administrators through the necessary steps to properly set up and configure the SAPL Server CE for their specific environment.");

        final var hKeyFeatures = new H2("Key Features");
        final var pKeyFeatures = new Paragraph();
        pKeyFeatures.getStyle().setWhiteSpace(Style.WhiteSpace.PRE_LINE);
        pKeyFeatures.setText(pKeyFeatures.getText()
                + "1. Configure the DBMS connection, either with an existing H2 or MariaDB database or a newly created H2 database.\n");
        pKeyFeatures.setText(pKeyFeatures.getText()
                + "2. Set up the username and password for the administrator user for the SAPL Server CE.\n");

        pKeyFeatures.setText(
                pKeyFeatures.getText() + "3. Configure the HTTP and RSocket endpoints of the SAPL Server CE.\n");
        pKeyFeatures.setText(pKeyFeatures.getText()
                + "4. Configure the basic authentication settings for accessing the API of SAPL Server CE.\n");
        pKeyFeatures.setText(
                pKeyFeatures.getText() + "5. Configure the logging settings of SAPL, SAPL Server CE and Spring.\n");

        final var hUsage = new H2("Usage");
        final var pUsage = new Paragraph();
        pUsage.getStyle().setWhiteSpace(Style.WhiteSpace.PRE_LINE);
        pUsage.setText(pUsage.getText()
                + "- Run the SAPL Server CE with the production profile as described in the readme.\n");
        pUsage.setText(pUsage.getText()
                + "- If the application cannot find a URL for the connection to the database and the name for the admin user in the configuration, the Setup Wizard will be started and shown in the browser. The default ports are 8080 and 8443.\n");
        pUsage.setText(pUsage.getText() + "- Now you can set up all the parameters according to your environment.\n");
        pUsage.setText(pUsage.getText()
                + "- After you have saved all settings, use the \"Restart SAPL Server CE\" button to restart the application.\n");
        pUsage.setText(pUsage.getText() + "- Once restarted, the new configuration parameters will take effect.\n");

        final var hLimitations = new H2("Limitations");
        final var pLimitations = new Paragraph();
        pLimitations.getStyle().setWhiteSpace(Style.WhiteSpace.PRE_LINE);
        pLimitations.setText(pLimitations.getText()
                + "- If you use the spring-dev-tools by activating them in the pom.xml, the restart functionality works correctly only in the production profile. If you use spring-dev-tool with a non-production profile, you have to restart the application yourself.\n");
        pLimitations.setText(pLimitations.getText()
                + "- The Setup Wizard is designed to work with .yml-files. .properties-files are not supported.\n");

        final var hGoodToKnow = new H2("Good to know");
        final var pGoodToKnow = new Paragraph();
        pGoodToKnow.getStyle().setWhiteSpace(Style.WhiteSpace.PRE_LINE);
        pGoodToKnow.setText(pGoodToKnow.getText()
                + "- The configuration properties are stored in the Spring application.yml files.\n");
        pGoodToKnow.setText(pGoodToKnow.getText()
                + "- It can work with multiple application-*.yml files, for example, when using different profiles or importing a second application-*.yml file into the main one.\n");
        pGoodToKnow.setText(pGoodToKnow.getText()
                + "- The Setup Wizard attempts to overwrite existing properties in the file with the highest priority.\n");
        pGoodToKnow.setText(pGoodToKnow.getText()
                + "- If a property is not available, the Setup Wizard will add it to the file with the highest priority.\n");
        pGoodToKnow.setText(pGoodToKnow.getText()
                + "- If no application.yml file outside the classpath is found, the Setup Wizard will create one and all necessary folders located in <working-dir>/config/\n");

        final var hNotUseIt = new H2("If you don't want to use it");
        final var pNotUseIt = new Paragraph();
        final var aDocu     = new Anchor("https://github.com/heutelbeck/sapl-server");
        pNotUseIt.getStyle().setWhiteSpace(Style.WhiteSpace.PRE_LINE);
        pNotUseIt.setText(pNotUseIt.getText()
                + "If you don't want to use it, just have a look at the documentation, set up the application.yml files as you prefer and start the SAPL Server CE\n");
        aDocu.setText("Have a look at the documentation");

        add(hWelcome, hDesc, pDesc, hKeyFeatures, pKeyFeatures, hUsage, pUsage, hLimitations, pLimitations, hGoodToKnow,
                pGoodToKnow, hNotUseIt, pNotUseIt, aDocu);
    }
}
