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
package io.sapl.server.ce.ui.views;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.sapl.api.SaplVersion;
import io.sapl.server.ce.model.setup.condition.SetupNotFinishedCondition;
import io.sapl.server.ce.ui.views.setup.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;

/**
 * The main view is a top-level placeholder for other views for the setup
 * wizard.
 */
@RequiredArgsConstructor
@Conditional(SetupNotFinishedCondition.class)
public class SetupLayout extends AppLayout {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    public static final String INSECURE_CONNECTION_MESSAGE = "Warning: This connection is not secure. \nProceeding means that someone could potentially intercept the entered parameters such as usernames and passwords. It is recommended to either configure a secure TLS connection for the application in the application.yml file or to run the wizard locally and later transfer the generated application.yml file to the target system via a secure connection.";

    private H2                            viewTitle;
    private final AccessAnnotationChecker accessChecker;

    @PostConstruct
    public void init() {

        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();

    }

    private void addHeaderContent() {
        final var toggle = new DrawerToggle();
        toggle.getElement().setAttribute("aria-label", "Menu toggle");

        viewTitle = new H2();
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        addToNavbar(true, toggle, viewTitle);
    }

    private void addDrawerContent() {

        final var logoLayout = new HorizontalLayout();
        logoLayout.setId("logo");
        logoLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        logoLayout.setPadding(true);
        final var logo = new Image("images/SAPL-Logo.png", "SAPL Logo");
        logo.setHeight("50px");
        final var appName = new H1("SAPL Server CE Setup");
        appName.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.Margin.NONE);
        logoLayout.add(logo, appName);

        final var header   = new Header(logoLayout);
        final var scroller = new Scroller(createNavigation());

        addToDrawer(header, scroller, createFooter());
    }

    private SideNav createNavigation() {
        final var nav = new SideNav();
        addItem(nav, "Welcome", SetupView.class, VaadinIcon.FILE);
        addItem(nav, "DBMS Setup", DbmsSetupView.class, VaadinIcon.DATABASE);
        addItem(nav, "Admin User Setup", AdminUserSetupView.class, VaadinIcon.USER);
        addItem(nav, "HTTP Endpoint Setup", HttpEndpointSetupView.class, VaadinIcon.SERVER);
        addItem(nav, "RSocket Endpoint Setup", RSocketEndpointSetupView.class, VaadinIcon.SERVER);
        addItem(nav, "API Authentication Setup", ApiAuthenticationSetupView.class, VaadinIcon.LOCK);
        addItem(nav, "Logging Setup", LoggingSetupView.class, VaadinIcon.TERMINAL);
        addItem(nav, "Finish Setup", FinishSetupView.class, VaadinIcon.CHECK_SQUARE);
        return nav;
    }

    private void addItem(SideNav nav, String label, Class<? extends Component> view, VaadinIcon icon) {
        if (accessChecker.hasAccess(view)) {
            nav.addItem(new SideNavItem(label, view, icon.create()));
        }
    }

    private Footer createFooter() {
        final var layout = new Footer();

        Anchor help = new Anchor("https://github.com/heutelbeck/sapl-server");
        help.getElement().setProperty("innerHTML", "You need help? <br />Have a look at the documentation");
        layout.add(help);

        return layout;
    }

    @Override
    protected void afterNavigation() {
        super.afterNavigation();
        viewTitle.setText(getCurrentPageTitle());
    }

    private String getCurrentPageTitle() {
        final var title = getContent().getClass().getAnnotation(PageTitle.class);
        return title == null ? "" : title.value();
    }

}
