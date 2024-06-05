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
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.auth.AccessAnnotationChecker;
import com.vaadin.flow.theme.lumo.LumoUtility;

import io.sapl.api.SaplVersion;
import io.sapl.server.ce.model.setup.condition.SetupFinishedCondition;
import io.sapl.server.ce.security.AuthenticatedUser;
import io.sapl.server.ce.ui.views.clientcredentials.ClientCredentialsView;
import io.sapl.server.ce.ui.views.digitalpolicies.DigitalPoliciesView;
import io.sapl.server.ce.ui.views.digitalpolicies.PublishedPoliciesView;
import io.sapl.server.ce.ui.views.librariesdocumentation.LibrariesDocumentationView;
import io.sapl.server.ce.ui.views.pdpconfig.PDPConfigView;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * The main view is a top-level placeholder for other views.
 */
@RequiredArgsConstructor
@Conditional(SetupFinishedCondition.class)
public class MainLayout extends AppLayout {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    private H2 viewTitle;

    private final AuthenticatedUser       authenticatedUser;
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
        var appName = new H1("SAPL Server CE");
        appName.addClassNames(LumoUtility.FontSize.XLARGE, LumoUtility.Margin.NONE);
        logoLayout.add(logo, appName);

        var header   = new Header(logoLayout);
        var scroller = new Scroller(createNavigation());

        addToDrawer(header, scroller, createFooter());
    }

    private SideNav createNavigation() {
        var nav = new SideNav();
        addItem(nav, "Digital Policies", DigitalPoliciesView.class, LineAwesomeIcon.FILE_SOLID);
        addItem(nav, "Published Policies", PublishedPoliciesView.class, LineAwesomeIcon.FILE_ALT);
        addItem(nav, "PDP Config", PDPConfigView.class, LineAwesomeIcon.COG_SOLID);
        addItem(nav, "Libraries Documentation", LibrariesDocumentationView.class, LineAwesomeIcon.BOOK_SOLID);
        addItem(nav, "Client Credentials", ClientCredentialsView.class, LineAwesomeIcon.KEY_SOLID);
        return nav;
    }

    private void addItem(SideNav nav, String label, Class<? extends Component> view, LineAwesomeIcon icon) {
        if (accessChecker.hasAccess(view)) {
            nav.addItem(new SideNavItem(label, view, icon.create()));
        }
    }

    private Footer createFooter() {
        var layout    = new Footer();
        var maybeUser = authenticatedUser.get();
        if (maybeUser.isPresent()) {
            var user = maybeUser.get();

            var avatar = new Avatar(user.getUsername());
            avatar.setThemeName("xsmall");
            avatar.getElement().setAttribute("tabindex", "-1");

            var userMenu = new MenuBar();
            userMenu.setThemeName("tertiary-inline contrast");

            var userName = userMenu.addItem("");
            var div      = new Div();
            div.add(avatar);
            div.add(user.getUsername());
            div.add(new Icon("lumo", "dropdown"));
            div.getElement().getStyle().set("display", "flex");
            div.getElement().getStyle().set("align-items", "center");
            div.getElement().getStyle().set("gap", "var(--lumo-space-s)");
            userName.add(div);
            userName.getSubMenu().addItem("Sign out", e -> authenticatedUser.logout());

            layout.add(userMenu);
        } else {
            var loginLink = new Anchor("login", "Sign in");
            layout.add(loginLink);
        }

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
