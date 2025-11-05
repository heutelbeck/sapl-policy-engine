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

import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.login.LoginOverlay;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.internal.RouteUtil;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import io.sapl.api.SaplVersion;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.security.AuthenticatedUser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;

@AnonymousAllowed
@PageTitle("Login")
@Route(value = "login")
@RequiredArgsConstructor
@Conditional(SetupFinishedCondition.class)
public class LoginView extends LoginOverlay implements BeforeEnterObserver {

    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private final AuthenticatedUser authenticatedUser;

    @PostConstruct
    void initialize() {
        setAction(RouteUtil.getRoutePath(VaadinService.getCurrent().getContext(), getClass()));

        final var i18n = LoginI18n.createDefault();
        i18n.setHeader(new LoginI18n.Header());
        i18n.getHeader().setTitle("SAPL Server CE");
        i18n.getHeader().setDescription("Login with administrator account.");
        i18n.setAdditionalInformation(null);
        setI18n(i18n);

        setForgotPasswordButtonVisible(false);
        setOpened(true);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (authenticatedUser.get().isPresent()) {
            // Already logged in
            setOpened(false);
            event.forwardTo("");
        }

        setError(event.getLocation().getQueryParameters().getParameters().containsKey("error"));
    }
}
