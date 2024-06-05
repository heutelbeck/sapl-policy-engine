/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import io.sapl.api.SaplVersion;
import io.sapl.server.ce.model.setup.ApplicationConfigService;
import io.sapl.server.ce.model.setup.condition.SetupNotFinishedCondition;
import io.sapl.server.ce.ui.utils.ConfirmUtils;
import io.sapl.server.ce.ui.utils.ErrorComponentUtils;
import io.sapl.server.ce.ui.views.SetupLayout;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;

@AnonymousAllowed
@PageTitle("Admin User Setup")
@Route(value = AdminUserSetupView.ROUTE, layout = SetupLayout.class)
@Conditional(SetupNotFinishedCondition.class)
public class AdminUserSetupView extends VerticalLayout {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    public static final String ROUTE = "/setup/admin";

    private static final String SUCCESS_COLOR  = "var(--lumo-success-color)";
    private static final String MODERATE_COLOR = "#e7c200";
    private static final String ERROR_COLOR    = "var(--lumo-error-color)";

    private transient ApplicationConfigService applicationConfigService;
    private transient HttpServletRequest       httpServletRequest;

    private final TextField     username             = new TextField("Username");
    private final PasswordField password             = new PasswordField("Password");
    private final PasswordField passwordRepeat       = new PasswordField("Repeat Password");
    private final Button        pwdSaveConfig        = new Button("Save Admin-User Settings");
    private final Icon          pwdEqualCheckIcon    = VaadinIcon.CHECK.create();
    private final Span          passwordStrengthText = new Span();
    private final Span          passwordEqualText    = new Span();

    public AdminUserSetupView(@Autowired ApplicationConfigService applicationConfigService,
            @Autowired HttpServletRequest httpServletRequest) {
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
        pwdSaveConfig.setEnabled(applicationConfigService.getAdminUserConfig().isValidConfig());
        pwdSaveConfig.addClickListener(e -> {
            try {
                applicationConfigService.persistAdminUserConfig();
                ConfirmUtils.inform("saved", "Username and password successfully saved");
            } catch (IOException ioe) {
                ConfirmUtils.inform("IO-Error",
                        "Error while writing application.yml-File. Please make sure that the file is not in use and can be written. Otherwise configure the application.yml-file manually. Error: "
                                + ioe.getMessage());
            }
        });

        password.setValue(applicationConfigService.getAdminUserConfig().getPassword());
        passwordRepeat.setValue(applicationConfigService.getAdminUserConfig().getPasswordRepeat());
        username.setValue(applicationConfigService.getAdminUserConfig().getUsername());
        username.addValueChangeListener(e -> updateAdminUserConfig());
        username.setValueChangeMode(ValueChangeMode.EAGER);
        username.setRequiredIndicatorVisible(true);
        username.setRequired(true);

        FormLayout adminUserLayout = new FormLayout(username, pwdLayout(), pwdRepeatLayout(), pwdSaveConfig);
        adminUserLayout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1, FormLayout.ResponsiveStep.LabelsPosition.TOP),
                new FormLayout.ResponsiveStep("490px", 2, FormLayout.ResponsiveStep.LabelsPosition.TOP));
        adminUserLayout.setColspan(username, 2);
        adminUserLayout.setColspan(pwdSaveConfig, 2);

        return adminUserLayout;
    }

    private PasswordField pwdLayout() {
        Div passwordStrength = new Div();
        passwordStrength.add(new Text("Password strength: "), passwordStrengthText);
        password.setHelperComponent(passwordStrength);

        add(password);
        password.setValueChangeMode(ValueChangeMode.EAGER);
        password.addValueChangeListener(e -> updateAdminUserConfig());
        return password;
    }

    private PasswordField pwdRepeatLayout() {
        pwdEqualCheckIcon.setVisible(false);
        pwdEqualCheckIcon.getStyle().setColor(SUCCESS_COLOR);
        passwordRepeat.setSuffixComponent(pwdEqualCheckIcon);
        passwordRepeat.setValueChangeMode(ValueChangeMode.EAGER);
        passwordRepeat.addValueChangeListener(e -> updateAdminUserConfig());

        Div passwordEqual = new Div();
        passwordEqual.add(new Text("Passwords are "), passwordEqualText);
        passwordRepeat.setHelperComponent(passwordEqual);

        add(passwordRepeat);

        return passwordRepeat;
    }

    private void updatePwdStrengthText() {
        switch (applicationConfigService.getAdminUserConfig().getPasswordStrength()) {
        case STRONG:
            passwordStrengthText.setText("strong");
            passwordStrengthText.getStyle().setColor(SUCCESS_COLOR);
            break;
        case MODERATE:
            passwordStrengthText.setText("moderate");
            passwordStrengthText.getStyle().setColor(MODERATE_COLOR);
            break;
        default:
            passwordStrengthText.setText("weak");
            passwordStrengthText.getStyle().setColor(ERROR_COLOR);
        }
    }

    private void updatePwdEqualText() {
        if (password.getValue().equals(passwordRepeat.getValue())) {
            passwordEqualText.setText("equal");
            passwordEqualText.getStyle().setColor(SUCCESS_COLOR);
        } else if (password.getValue().isBlank() || passwordRepeat.getValue().isBlank()) {
            passwordEqualText.setText("not set");
            passwordEqualText.getStyle().setColor(MODERATE_COLOR);
        } else {
            passwordEqualText.setText("not equal");
            passwordEqualText.getStyle().setColor(ERROR_COLOR);
        }
    }

    private void updateAdminUserConfig() {
        applicationConfigService.getAdminUserConfig().setUsername(username.getValue());
        applicationConfigService.getAdminUserConfig().setPassword(password.getValue());
        applicationConfigService.getAdminUserConfig().setPasswordRepeat(passwordRepeat.getValue());

        updatePwdEqualText();
        updatePwdStrengthText();
        pwdEqualCheckIcon.setVisible(password.getValue().equals(passwordRepeat.getValue()));
        pwdSaveConfig.setEnabled(applicationConfigService.getAdminUserConfig().isValidConfig());

    }

}
