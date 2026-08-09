/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.attributeapigui.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.theme.lumo.LumoUtility;
import jakarta.annotation.security.RolesAllowed;

@PageTitle("SAPL Attribute API")
@RolesAllowed("ADMIN")
public class MainLayout extends AppLayout {
    public static final String PAGE_TITLE = "SAPL Attribute API";

    public MainLayout() {
        // Creates the hamburger menu and the page title
        addToNavbar(new DrawerToggle(), new H1(PAGE_TITLE));

        // Build the navigation on the side
        var navigation = new SideNav();

        // Add the menu items
        for (var entry : MenuConfiguration.getMenuEntries()) {
            var item = entry.icon() != null ? new SideNavItem(entry.title(), entry.path(), new Icon(entry.icon()))
                    : new SideNavItem(entry.title(), entry.path());
            navigation.addItem(item);
        }

        var container = new VerticalLayout(navigation);
        container.addClassNames(LumoUtility.Padding.SMALL);
        // Navigation build finished

        addToDrawer(container);
    }
}
