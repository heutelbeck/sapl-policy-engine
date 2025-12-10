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
package io.sapl.server.ce.ui.views.login;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.sapl.api.SaplVersion;
import io.sapl.server.ce.model.oauth2.OAuth2Provider;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.security.AuthenticatedUser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;

import java.io.Serial;

@Route("oauth2")
@PageTitle("OAuth2 Login")
@AnonymousAllowed
@RequiredArgsConstructor
@Conditional(SetupFinishedCondition.class)
public class OAuth2 extends VerticalLayout implements BeforeEnterObserver {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private final AuthenticatedUser authenticatedUser;

    @PostConstruct
    void init() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        H1   title       = new H1("SAPL Server CE");
        Span description = new Span("Login with OAuth2 account");

        // Add a button to the that redirects to the Keycloak endpoint
        Button loginButton = new Button("Login mit Keycloak", click -> getUI()
                .ifPresent(ui -> ui.getPage().setLocation("/oauth2/authorization/" + OAuth2Provider.KEYCLOAK)));

        // Set the same theme to the button as for the LoginView
        loginButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add(title, description, loginButton);

        getStyle().set("padding", "200px");
        setAlignItems(FlexComponent.Alignment.CENTER);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (authenticatedUser.get().isPresent()) {
            // Already logged in. Forward the user to the main page after login
            event.forwardTo("/");
        }
    }
}
