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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.sapl.api.SaplVersion;
import io.sapl.server.SaplServerCeApplication;
import io.sapl.server.ce.model.setup.ApplicationConfigService;
import io.sapl.server.ce.model.setup.condition.SetupNotFinishedCondition;
import io.sapl.server.ce.ui.utils.ErrorComponentUtils;
import io.sapl.server.ce.ui.views.SetupLayout;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Conditional;

@AnonymousAllowed
@PageTitle("Finish Setup")
@Route(value = FinishSetupView.ROUTE, layout = SetupLayout.class)
@Conditional(SetupNotFinishedCondition.class)
public class FinishSetupView extends VerticalLayout {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    public static final String  ROUTE                  = "/setup/finish";
    private static final String THEME_BADGEERRORPILL   = "badge error pill";
    private static final String THEME_BADGESUCCESSPILL = "badge success pill";
    private static final String PADDING_XS             = "var(--lumo-space-xs";

    private transient ApplicationConfigService applicationConfigService;
    private transient HttpServletRequest       httpServletRequest;

    public FinishSetupView(ApplicationConfigService applicationConfigService, HttpServletRequest httpServletRequest) {
        this.applicationConfigService = applicationConfigService;
        this.httpServletRequest       = httpServletRequest;
    }

    @PostConstruct
    private void init() {
        if (!httpServletRequest.isSecure()) {
            add(ErrorComponentUtils.getErrorDiv(SetupLayout.INSECURE_CONNECTION_MESSAGE));
        }
        add(getLayout());
    }

    private Component getLayout() {
        Button restart = new Button("Restart Server CE");

        restart.addClickListener(
                e -> SaplServerCeApplication.restart(applicationConfigService.getHttpEndpoint().getUri()));
        restart.setEnabled(applicationConfigService.getDbmsConfig().isSaved()
                && applicationConfigService.getAdminUserConfig().isSaved()
                && applicationConfigService.getHttpEndpoint().isSaved()
                && applicationConfigService.getRsocketEndpoint().isSaved()
                && applicationConfigService.getApiAuthenticationConfig().isSaved()
                && applicationConfigService.getLoggingConfig().isSaved());

        final var adminStateView = new Div();
        Icon      adminStateIcon;
        if (applicationConfigService.getAdminUserConfig().isSaved()) {
            adminStateIcon = VaadinIcon.CHECK_CIRCLE.create();
            adminStateIcon.getElement().getThemeList().add(THEME_BADGESUCCESSPILL);
            adminStateIcon.getStyle().setPadding(PADDING_XS);
        } else {
            adminStateIcon = VaadinIcon.CLOSE.create();
            adminStateIcon.getElement().getThemeList().add(THEME_BADGEERRORPILL);
            adminStateIcon.getStyle().setPadding(PADDING_XS);
        }
        adminStateView.add(new Text("Admin user setup finished "), adminStateIcon);

        final var dbmsStateView = new Div();
        Icon      dbmsStateIcon;
        if (applicationConfigService.getDbmsConfig().isSaved()) {
            dbmsStateIcon = VaadinIcon.CHECK_CIRCLE.create();
            dbmsStateIcon.getElement().getThemeList().add(THEME_BADGESUCCESSPILL);
            dbmsStateIcon.getStyle().setPadding(PADDING_XS);
        } else {
            dbmsStateIcon = VaadinIcon.CLOSE.create();
            dbmsStateIcon.getElement().getThemeList().add(THEME_BADGEERRORPILL);
            dbmsStateIcon.getStyle().setPadding(PADDING_XS);
        }
        dbmsStateView.add(new Text("Database setup finished "), dbmsStateIcon);

        final var httpStateView = new Div();
        Icon      httpStateIcon;
        if (applicationConfigService.getHttpEndpoint().isSaved()) {
            httpStateIcon = VaadinIcon.CHECK_CIRCLE.create();
            httpStateIcon.getElement().getThemeList().add(THEME_BADGESUCCESSPILL);
            httpStateIcon.getStyle().setPadding(PADDING_XS);
        } else {
            httpStateIcon = VaadinIcon.CLOSE.create();
            httpStateIcon.getElement().getThemeList().add(THEME_BADGEERRORPILL);
            httpStateIcon.getStyle().setPadding(PADDING_XS);
        }
        httpStateView.add(new Text("HTTP endpoint setup finished "), httpStateIcon);

        final var rsocketStateView = new Div();
        Icon      rsocketStateIcon;
        if (applicationConfigService.getRsocketEndpoint().isSaved()) {
            rsocketStateIcon = VaadinIcon.CHECK_CIRCLE.create();
            rsocketStateIcon.getElement().getThemeList().add(THEME_BADGESUCCESSPILL);
            rsocketStateIcon.getStyle().setPadding(PADDING_XS);
        } else {
            rsocketStateIcon = VaadinIcon.CLOSE.create();
            rsocketStateIcon.getElement().getThemeList().add(THEME_BADGEERRORPILL);
            rsocketStateIcon.getStyle().setPadding(PADDING_XS);
        }
        rsocketStateView.add(new Text("RSocket endpoint setup finished "), rsocketStateIcon);

        final var apiAuthenticationView = new Div();
        Icon      apiAuthenticationIconState;
        if (applicationConfigService.getApiAuthenticationConfig().isSaved()) {
            apiAuthenticationIconState = VaadinIcon.CHECK_CIRCLE.create();
            apiAuthenticationIconState.getElement().getThemeList().add(THEME_BADGESUCCESSPILL);
            apiAuthenticationIconState.getStyle().setPadding(PADDING_XS);
        } else {
            apiAuthenticationIconState = VaadinIcon.CLOSE.create();
            apiAuthenticationIconState.getElement().getThemeList().add(THEME_BADGEERRORPILL);
            apiAuthenticationIconState.getStyle().setPadding(PADDING_XS);
        }
        apiAuthenticationView.add(new Text("API Authentication setup finished "), apiAuthenticationIconState);

        final var loggingView = new Div();
        Icon      loggingIconState;
        if (applicationConfigService.getLoggingConfig().isSaved()) {
            loggingIconState = VaadinIcon.CHECK_CIRCLE.create();
            loggingIconState.getElement().getThemeList().add(THEME_BADGESUCCESSPILL);
            loggingIconState.getStyle().setPadding(PADDING_XS);
        } else {
            loggingIconState = VaadinIcon.CLOSE.create();
            loggingIconState.getElement().getThemeList().add(THEME_BADGEERRORPILL);
            loggingIconState.getStyle().setPadding(PADDING_XS);
        }
        loggingView.add(new Text("Logging setup finished "), loggingIconState);

        VerticalLayout stateLayout = new VerticalLayout();
        stateLayout.setSpacing(false);
        stateLayout.getThemeList().add("spacing-l");
        stateLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        stateLayout.add(dbmsStateView);
        stateLayout.add(adminStateView);
        stateLayout.add(httpStateView);
        stateLayout.add(getTlsDisabledWarning("Http", !applicationConfigService.getHttpEndpoint().getSslEnabled()));
        stateLayout.add(rsocketStateView);
        stateLayout
                .add(getTlsDisabledWarning("RSocket", !applicationConfigService.getRsocketEndpoint().getSslEnabled()));
        stateLayout.add(apiAuthenticationView);
        stateLayout.add(loggingView);

        final var hInfo = new H2(
                "The following settings must be adjusted and saved before the application can be restarted and used.");

        FormLayout adminUserLayout = new FormLayout(hInfo, stateLayout, restart);
        adminUserLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1, FormLayout.ResponsiveStep.LabelsPosition.TOP),
                new FormLayout.ResponsiveStep("490px", 2, FormLayout.ResponsiveStep.LabelsPosition.TOP));
        adminUserLayout.setColspan(restart, 2);
        adminUserLayout.setColspan(hInfo, 2);
        adminUserLayout.setColspan(stateLayout, 2);

        return adminUserLayout;
    }

    private Div getTlsDisabledWarning(String protocol, boolean visible) {
        final var warning = "Warning: You have not selected any TLS-protocol for  " + protocol
                + ". Please do not use this configuration in production.\n"
                + "This option may open the server to malicious probing and exfiltration attempts through "
                + "the authorization endpoints, potentially resulting in unauthorized access to your "
                + "organization's data, depending on your policies.";
        final var div     = ErrorComponentUtils.getErrorDiv(warning);
        div.setVisible(visible);
        return div;
    }

}
