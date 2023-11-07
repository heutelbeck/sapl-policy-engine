/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.views;

import java.net.URI;
import java.net.URISyntaxException;
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
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.InitialPageSettings;
import com.vaadin.flow.server.PageConfigurator;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServletRequest;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

import antlr.StringUtils;
import io.sapl.server.ce.views.client.ListClientCredentials;
import io.sapl.server.ce.views.documentation.ListFunctionsAndPipsView;
import io.sapl.server.ce.views.pdpconfiguration.ConfigurePdp;
import io.sapl.server.ce.views.publishedpolicies.PublishedPoliciesView;
import io.sapl.server.ce.views.sapldocument.SaplDocumentsView;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

/**
 * The main view is a top-level placeholder for other views.
 */
@Push
@Slf4j
@SuppressWarnings("deprecation")
@Theme(value = Lumo.class, variant = Lumo.DARK)
@JsModule("./styles/shared-styles.js")
@CssImport("./styles/views/main/main-view.css")
public class MainView extends AppLayout implements RouterLayout, PageConfigurator {
	private static final List<MenuItem> menuItems = initMenuItems();

	private final Tabs menu;
	private final Map<Integer, RouterLink> routerLinksPerTabIndex = Maps.newTreeMap();

	private H1 viewTitle;

	public MainView() {
		setPrimarySection(Section.DRAWER);
		addToNavbar(true, createHeaderContent());
		menu = createMenu();
		addToDrawer(createDrawerContent(menu));
	}

	@Override
	public void configurePage(InitialPageSettings settings) {
		settings.addLink("shortcut icon", "icons/favicon.ico");
	}

	private static List<MenuItem> initMenuItems() {
		return Lists.newArrayList(
				new MenuItem("Digital Policies", SaplDocumentsView.class, SaplDocumentsView.ROUTE, VaadinIcon.FILE),
				new MenuItem("Published Policies", PublishedPoliciesView.class, PublishedPoliciesView.ROUTE, VaadinIcon.FILE_TEXT),
				new MenuItem("PDP Configuration", ConfigurePdp.class, ConfigurePdp.ROUTE, VaadinIcon.COG),
				new MenuItem("Functions & Attributes", ListFunctionsAndPipsView.class, ListFunctionsAndPipsView.ROUTE,
						VaadinIcon.BOOK),
				new MenuItem("Client Credentials", ListClientCredentials.class, ListClientCredentials.ROUTE,
						VaadinIcon.KEY));
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

		String resolvedSaplLogoPath = VaadinServletService.getCurrent()
				.resolveResource("images/SAPL-Logo.png",
						VaadinSession.getCurrent().getBrowser());
		logoLayout.add(new Image(resolvedSaplLogoPath, "SAPL Server CE logo"));

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

		selectTabBasedOnPathOfCurrentRequest(tabs);

		return tabs;
	}

	private void selectTabBasedOnPathOfCurrentRequest(@NonNull Tabs tabs) {
		VaadinServletRequest req = (VaadinServletRequest) VaadinService.getCurrentRequest();
		StringBuffer uriAsString = req.getRequestURL();
		URI uri;
		try {
			uri = new URI(uriAsString.toString());
		} catch (URISyntaxException ex) {
			log.warn("cannot parse URI from String: {}", uriAsString);
			return;
		}

		String path = uri.getPath();

		// remove leading slash for compatibility with Vaadin route syntax
		final String adjustedPath = StringUtils.stripFront(path, '/');

		//@formatter:off
		menuItems.stream()
			.filter(menuItem -> menuItem.getRoute().equals(adjustedPath))
			.findFirst()
			.ifPresent(
				menuItem -> {
					int index = menuItems.indexOf(menuItem);
					tabs.setSelectedIndex(index);
				});
		//@formatter:on
	}

	private Component[] createMenuItems() {
		//@formatter:off
		return menuItems.stream()
				.map(menuItem -> {
					return Pair.of(new RouterLink(menuItem.getText(), menuItem.getRoutingClazz()), menuItem.getIcon());
				})
				.map(this::createTab)
				.toArray(Tab[]::new);
		//@formatter:on
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
				return routeData.getTemplate();
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

	@Value
	private static class MenuItem {
		private String text;
		private Class<? extends Component> routingClazz;
		private String route;
		private VaadinIcon icon;
	}
}
