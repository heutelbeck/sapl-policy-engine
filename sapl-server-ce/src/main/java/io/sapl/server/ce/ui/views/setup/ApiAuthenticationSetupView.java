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

import java.io.IOException;

import org.springframework.context.annotation.Conditional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.theme.lumo.LumoUtility;

import io.sapl.server.ce.model.setup.ApplicationConfigService;
import io.sapl.server.ce.model.setup.condition.SetupNotFinishedCondition;
import io.sapl.server.ce.ui.utils.ConfirmUtils;
import io.sapl.server.ce.ui.utils.ErrorComponentUtils;
import io.sapl.server.ce.ui.views.SetupLayout;
import jakarta.servlet.http.HttpServletRequest;

@AnonymousAllowed
@PageTitle("API Authentication Setup")
@Route(value = ApiAuthenticationSetupView.ROUTE, layout = SetupLayout.class)
@Conditional(SetupNotFinishedCondition.class)
public class ApiAuthenticationSetupView extends VerticalLayout {

    private static final long serialVersionUID = -8630199389314611354L;

    public static final String ROUTE = "/setup/apiauthentication";

    private transient ApplicationConfigService applicationConfigService;

    private final Checkbox     allowBasicAuth        = new Checkbox("Basic Auth");
    private final Checkbox     allowApiKeyAuth       = new Checkbox("API Key Auth");
    private final Checkbox     allowApiKeyCaching    = new Checkbox("API Key Caching");
    private final IntegerField apiKeyCacheExpires    = new IntegerField("Cache expires (seconds)");
    private final IntegerField apiKeyCacheMaxSize    = new IntegerField("Max Size");
    private final Checkbox     allowOAuth2Auth       = new Checkbox("OAuth2");
    private final TextField    oAuth2RessourceServer = new TextField("OAuth2 Ressource Server URI");
    private Span               oAuth2RessourceServerInputValidText;

    private final Button saveConfig = new Button("Save API Authentication Settings");

    public ApiAuthenticationSetupView(ApplicationConfigService applicationConfigService,
            HttpServletRequest httpServletRequest) {
        this.applicationConfigService = applicationConfigService;

        if (!httpServletRequest.isSecure()) {
            add(ErrorComponentUtils.getErrorDiv(SetupLayout.INSECURE_CONNECTION_MESSAGE));
        }
        add(getLayout());
        setVisibility();
    }

    private Component getLayout() {
        saveConfig.setEnabled(applicationConfigService.getApiAuthenticationConfig().isValidConfig());
        saveConfig.addClickListener(e -> persistApiAuthenticationConfig());

        allowBasicAuth.setValue(applicationConfigService.getApiAuthenticationConfig().isBasicAuthEnabled());

        allowApiKeyAuth.setValue(applicationConfigService.getApiAuthenticationConfig().isApiKeyAuthEnabled());
        allowApiKeyAuth.getStyle().setAlignItems(Style.AlignItems.START);

        allowApiKeyCaching.setValue(applicationConfigService.getApiAuthenticationConfig().isApiKeyCachingEnabled());

        apiKeyCacheExpires.setValueChangeMode(ValueChangeMode.EAGER);
        apiKeyCacheExpires.setRequiredIndicatorVisible(true);
        apiKeyCacheExpires.setRequired(true);
        apiKeyCacheExpires.setMin(1);
        apiKeyCacheExpires.setHelperText("Larger than 0");
        apiKeyCacheExpires.setValue(applicationConfigService.getApiAuthenticationConfig().getApiKeyCachingExpires());

        apiKeyCacheMaxSize.setValueChangeMode(ValueChangeMode.EAGER);
        apiKeyCacheMaxSize.setRequiredIndicatorVisible(true);
        apiKeyCacheMaxSize.setRequired(true);
        apiKeyCacheMaxSize.setMin(1);
        apiKeyCacheMaxSize.setHelperText("Larger than 0");
        apiKeyCacheMaxSize.setValue(applicationConfigService.getApiAuthenticationConfig().getApiKeyCachingMaxSize());

        final var apiKeyLayout = new VerticalLayout();
        apiKeyLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        apiKeyLayout.setPadding(false);
        apiKeyLayout.add(allowApiKeyAuth, allowApiKeyCaching, apiKeyCacheExpires, apiKeyCacheMaxSize);

        allowOAuth2Auth.setValue(applicationConfigService.getApiAuthenticationConfig().isOAuth2AuthEnabled());

        oAuth2RessourceServer.setRequiredIndicatorVisible(true);
        oAuth2RessourceServer.setValueChangeMode(ValueChangeMode.EAGER);
        oAuth2RessourceServer
                .setValue(applicationConfigService.getApiAuthenticationConfig().getOAuth2RessourceServer());
        oAuth2RessourceServerInputValidText = new Span("Not a valid URL");
        oAuth2RessourceServerInputValidText.addClassNames(LumoUtility.TextColor.ERROR);
        oAuth2RessourceServer.setHelperComponent(oAuth2RessourceServerInputValidText);

        final var oAuth2Layout = new VerticalLayout();
        oAuth2Layout.setAlignItems(FlexComponent.Alignment.STRETCH);
        oAuth2Layout.setPadding(false);
        oAuth2Layout.add(allowOAuth2Auth, oAuth2RessourceServer);

        allowBasicAuth.addValueChangeListener(e -> updateApiAuthenticationConfig());
        allowApiKeyAuth.addValueChangeListener(e -> updateApiAuthenticationConfig());
        allowApiKeyCaching.addValueChangeListener(e -> updateApiAuthenticationConfig());
        apiKeyCacheExpires.addValueChangeListener(e -> updateApiAuthenticationConfig());
        apiKeyCacheMaxSize.addValueChangeListener(e -> updateApiAuthenticationConfig());
        allowOAuth2Auth.addValueChangeListener(e -> updateApiAuthenticationConfig());
        oAuth2RessourceServer.addValueChangeListener(e -> updateApiAuthenticationConfig());

        final var apiAuthenticationLayout = new FormLayout(allowBasicAuth, apiKeyLayout, oAuth2Layout, saveConfig);

        apiAuthenticationLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1, FormLayout.ResponsiveStep.LabelsPosition.TOP),
                new FormLayout.ResponsiveStep("490px", 2, FormLayout.ResponsiveStep.LabelsPosition.TOP));
        apiAuthenticationLayout.setColspan(allowBasicAuth, 2);
        apiAuthenticationLayout.setColspan(saveConfig, 2);

        return apiAuthenticationLayout;

    }

    private void setVisibility() {
        allowApiKeyCaching.setVisible(applicationConfigService.getApiAuthenticationConfig().isApiKeyAuthEnabled());
        apiKeyCacheExpires.setVisible(applicationConfigService.getApiAuthenticationConfig().isApiKeyAuthEnabled()
                && applicationConfigService.getApiAuthenticationConfig().isApiKeyCachingEnabled());
        apiKeyCacheMaxSize.setVisible(applicationConfigService.getApiAuthenticationConfig().isApiKeyAuthEnabled()
                && applicationConfigService.getApiAuthenticationConfig().isApiKeyCachingEnabled());
        oAuth2RessourceServer.setVisible(applicationConfigService.getApiAuthenticationConfig().isOAuth2AuthEnabled());
        oAuth2RessourceServerInputValidText
                .setVisible(!applicationConfigService.getApiAuthenticationConfig().isValidOAuth2RessourceServerUrl());
    }

    private void persistApiAuthenticationConfig() {
        try {
            applicationConfigService.persistApiAuthenticationConfig();
            ConfirmUtils.inform("saved", "API Authentication setup successfully saved");
        } catch (IOException ioe) {
            ConfirmUtils.inform("IO-Error",
                    "Error while writing application.yml-File. Please make sure that the file is not in use and can be written. Otherwise configure the application.yml-file manually. Error: "
                            + ioe.getLocalizedMessage());
        }
    }

    private void updateApiAuthenticationConfig() {
        applicationConfigService.getApiAuthenticationConfig().setBasicAuthEnabled(allowBasicAuth.getValue());
        applicationConfigService.getApiAuthenticationConfig().setApiKeyAuthEnabled(allowApiKeyAuth.getValue());
        applicationConfigService.getApiAuthenticationConfig().setApiKeyCachingEnabled(allowApiKeyCaching.getValue());
        if (apiKeyCacheExpires.getValue() == null) {
            applicationConfigService.getApiAuthenticationConfig().setApiKeyCachingExpires(0);
        } else {
            applicationConfigService.getApiAuthenticationConfig()
                    .setApiKeyCachingExpires(apiKeyCacheExpires.getValue());
        }
        if (apiKeyCacheMaxSize.getValue() == null) {
            applicationConfigService.getApiAuthenticationConfig().setApiKeyCachingMaxSize(0);
        } else {
            applicationConfigService.getApiAuthenticationConfig()
                    .setApiKeyCachingMaxSize(apiKeyCacheMaxSize.getValue());
        }
        applicationConfigService.getApiAuthenticationConfig().setOAuth2AuthEnabled(allowOAuth2Auth.getValue());
        applicationConfigService.getApiAuthenticationConfig()
                .setOAuth2RessourceServer(oAuth2RessourceServer.getValue());

        setVisibility();
        saveConfig.setEnabled(applicationConfigService.getApiAuthenticationConfig().isValidConfig());
    }

}
