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

package io.sapl.server.ce.ui.views;

import org.springframework.context.annotation.Conditional;
import org.vaadin.lineawesome.LineAwesomeIcon;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Image;
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
import io.sapl.server.ce.ui.views.setup.AdminUserSetupView;
import io.sapl.server.ce.ui.views.setup.ApiAuthenticationSetupView;
import io.sapl.server.ce.ui.views.setup.DbmsSetupView;
import io.sapl.server.ce.ui.views.setup.FinishSetupView;
import io.sapl.server.ce.ui.views.setup.HttpEndpointSetupView;
import io.sapl.server.ce.ui.views.setup.LoggingSetupView;
import io.sapl.server.ce.ui.views.setup.RSocketEndpointSetupView;
import io.sapl.server.ce.ui.views.setup.SetupView;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

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
        var toggle = new DrawerToggle();
        toggle.getElement().setAttribute("aria-label", "Menu toggle");

        viewTitle = new H2();
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        addToNavbar(true, toggle, viewTitle);
    }

    private void addDrawerContent() {

        var logoLayout = new HorizontalLayout();
        logoLayout.setId("logo");
        logoLayout.setAlignItems(FlexComponent.Alignment.CENTER);
        logoLayout.setPadding(true);
        var logo = new Image("images/SAPL-Logo.png", "SAPL Logo");
        logo.setHeight("50px");
        var appName = new H1("SAPL Server CE Setup");
        appName.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.Margin.NONE);
        logoLayout.add(logo, appName);

        var header   = new Header(logoLayout);
        var scroller = new Scroller(createNavigation());

        addToDrawer(header, scroller, createFooter());
    }

    private SideNav createNavigation() {
        var nav = new SideNav();
        addItem(nav, "Welcome", SetupView.class, LineAwesomeIcon.FILE_SOLID);
        addItem(nav, "DBMS Setup", DbmsSetupView.class, LineAwesomeIcon.DATABASE_SOLID);
        addItem(nav, "Admin User Setup", AdminUserSetupView.class, LineAwesomeIcon.USER_SOLID);
        addItem(nav, "HTTP Endpoint Setup", HttpEndpointSetupView.class, LineAwesomeIcon.SERVER_SOLID);
        addItem(nav, "RSocket Endpoint Setup", RSocketEndpointSetupView.class, LineAwesomeIcon.SERVER_SOLID);
        addItem(nav, "API Authentication Setup", ApiAuthenticationSetupView.class, LineAwesomeIcon.NETWORK_WIRED_SOLID);
        addItem(nav, "Logging Setup", LoggingSetupView.class, LineAwesomeIcon.FILE_ALT_SOLID);
        addItem(nav, "Finish Setup", FinishSetupView.class, LineAwesomeIcon.CHECK_SQUARE_SOLID);
        return nav;
    }

    private void addItem(SideNav nav, String label, Class<? extends Component> view, LineAwesomeIcon icon) {
        if (accessChecker.hasAccess(view)) {
            nav.addItem(new SideNavItem(label, view, icon.create()));
        }
    }

    private Footer createFooter() {
        var layout = new Footer();

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
        var title = getContent().getClass().getAnnotation(PageTitle.class);
        return title == null ? "" : title.value();
    }

}
