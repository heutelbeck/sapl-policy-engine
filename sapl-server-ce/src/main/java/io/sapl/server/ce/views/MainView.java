/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.views;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.TabsVariant;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.router.RouteData;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

import io.sapl.server.ce.views.documentation.ListFunctionsAndPipsView;
import io.sapl.server.ce.views.pdpconfiguration.ConfigurePdp;
import io.sapl.server.ce.views.sapldocument.SaplDocumentsView;

/**
 * The main view is a top-level placeholder for other views.
 */
@Push
@JsModule("./styles/shared-styles.js")
@Theme(value = Lumo.class, variant = Lumo.DARK)
@CssImport("styles/views/main/main-view.css")
public class MainView extends AppLayout {

	private final Tabs menu;
	private final Map<Integer, RouterLink> routerLinksPerTabIndex = Maps.newTreeMap();

	private H1 viewTitle;

	public MainView() {
		setPrimarySection(Section.DRAWER);
		addToNavbar(true, createHeaderContent());
		menu = createMenu();
		addToDrawer(createDrawerContent(menu));
	}

	private Component createHeaderContent() {
		HorizontalLayout layout = new HorizontalLayout();
		layout.setId("header");
		layout.getThemeList().set("dark", true);
		layout.setWidthFull();
		layout.setSpacing(false);
		layout.setAlignItems(FlexComponent.Alignment.CENTER);
		layout.add(new DrawerToggle());
		viewTitle = new H1();
		layout.add(viewTitle);
		return layout;
	}

	private Component createDrawerContent(Tabs menu) {
		VerticalLayout layout = new VerticalLayout();
		layout.setSizeFull();
		layout.setPadding(false);
		layout.setSpacing(false);
		layout.getThemeList().set("spacing-s", true);
		layout.setAlignItems(FlexComponent.Alignment.STRETCH);
		HorizontalLayout logoLayout = new HorizontalLayout();
		logoLayout.setId("logo");
		logoLayout.setAlignItems(FlexComponent.Alignment.CENTER);
		logoLayout.add(new Image("images/logos/18.png", "SAPL Server CE logo"));
		logoLayout.add(new H1("SAPL Server CE"));
		layout.add(logoLayout, menu);
		return layout;
	}

	private Tabs createMenu() {
		final Tabs tabs = new Tabs();
		tabs.setOrientation(Tabs.Orientation.VERTICAL);
		tabs.addThemeVariants(TabsVariant.LUMO_MINIMAL);
		tabs.setId("tabs");
		tabs.add(createMenuItems());
		tabs.addSelectedChangeListener((selectedChangeEvent) -> {
			// always navigate to target independently from clicking on link, icon or
			// background
			RouterLink relevantRouterLink = routerLinksPerTabIndex.get(tabs.getSelectedIndex());
			UI.getCurrent().navigate(relevantRouterLink.getHref());
		});

		return tabs;
	}

	private Component[] createMenuItems() {
		//@formatter:off
		List<Pair<RouterLink, VaadinIcon>> linksWithIcons = Lists.newArrayList(
				Pair.of(new RouterLink("Home", ShowHome.class), VaadinIcon.HOME),
				Pair.of(new RouterLink("SAPL Documents", SaplDocumentsView.class), VaadinIcon.FILE),
				Pair.of(new RouterLink("PDP Configuration", ConfigurePdp.class), VaadinIcon.COG),
				Pair.of(new RouterLink("Functions & Attributes", ListFunctionsAndPipsView.class), VaadinIcon.BOOK),
				Pair.of(new RouterLink("Client Credentials", ListClientCredentials.class), VaadinIcon.SIGN_IN));
		//@formatter:on
		return linksWithIcons.stream().map(this::createTab).toArray(Tab[]::new);
	}

	private Tab createTab(Pair<RouterLink, VaadinIcon> linkWithIcon) {
		RouterLink routerLink = linkWithIcon.getLeft();
		VaadinIcon icon = linkWithIcon.getRight();

		HorizontalLayout layout = new HorizontalLayout();
		layout.add(icon.create());
		layout.add(routerLink);
		layout.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);

		routerLinksPerTabIndex.put(routerLinksPerTabIndex.size(), routerLink);

		final Tab tab = new Tab();
		tab.add(layout);
		return tab;
	}

	@Override
	protected void afterNavigation() {
		super.afterNavigation();
		updateChrome();
	}

	private void updateChrome() {
		getTabWithCurrentRoute().ifPresent(menu::setSelectedTab);
		viewTitle.setText(getCurrentPageTitle());
	}

	private Optional<Tab> getTabWithCurrentRoute() {
		String currentRoute = getCurrentRoute();
		return menu.getChildren().filter(tab -> hasLink(tab, currentRoute)).findFirst().map(Tab.class::cast);
	}

	private String getCurrentRoute() {
		// RouteConfiguration.forSessionScope().getUrl(getContent().getClass()) does not
		// work for views with URL parameter
		Class<?> clazz = getContent().getClass();

		RouteConfiguration routeConfiguration = RouteConfiguration.forSessionScope();
		for (RouteData routeData : routeConfiguration.getAvailableRoutes()) {
			if (routeData.getNavigationTarget().equals(clazz)) {
				return routeData.getUrl();
			}
		}

		throw new IllegalStateException(String.format("no route is available for %s", clazz));
	}

	private boolean hasLink(Component tab, String currentRoute) {
		return tab.getChildren().filter(RouterLink.class::isInstance).map(RouterLink.class::cast)
				.map(RouterLink::getHref).anyMatch(currentRoute::equals);
	}

	private String getCurrentPageTitle() {
		return getContent().getClass().getAnnotation(PageTitle.class).value();
	}
}
