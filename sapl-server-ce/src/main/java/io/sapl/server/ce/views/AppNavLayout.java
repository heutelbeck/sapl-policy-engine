package io.sapl.server.ce.views;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasComponents;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.dependency.CssImport;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabVariant;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.ParentLayout;
import com.vaadin.flow.router.RouterLink;

import io.sapl.server.ce.views.documentation.FunctionLibrariesDocumentationView;
import io.sapl.server.ce.views.documentation.PolicyInformationPointsDocumentationView;
import io.sapl.server.ce.views.pdpconfiguration.ConfigurePdp;
import io.sapl.server.ce.views.sapldocument.SaplDocumentsView;

/**
 * Basic layout with navigation bar (navbar).
 */
@CssImport("./styles.css")
@ParentLayout(MainView.class)
public class AppNavLayout extends AppLayout implements AfterNavigationObserver {
	private final Image headerImage;
	private final Tabs menu;

	public AppNavLayout() {
		this.setDrawerOpened(true);
		this.setPrimarySection(Section.DRAWER);

		this.headerImage = new Image("logo-header.png", "SAPL PDP-Server Community Edition (CE)");
		this.headerImage.addClickListener(clickEvent -> {
			this.headerImage.getUI().get().navigate("");
		});
		this.headerImage.addClassName("center-image");
		this.menu = AppNavLayout.createMenuTabs();

		this.addToDrawer(this.headerImage, this.menu);

		// force specific height to trigger redrawing navbar
		this.headerImage.setHeight("75px");
	}

	private static Tabs createMenuTabs() {
		final Tabs tabs = new Tabs();
		tabs.setOrientation(Tabs.Orientation.VERTICAL);
		tabs.add(getAvailableTabs());
		return tabs;
	}

	private static Tab[] getAvailableTabs() {
		final List<Tab> tabs = new ArrayList<>();
		tabs.add(createTab("Home", ShowHome.class));
		tabs.add(createTab("SAPL Documents", SaplDocumentsView.class));
		tabs.add(createTab("PDP Configuration", ConfigurePdp.class));
		tabs.add(createTab("Policy Information Points", PolicyInformationPointsDocumentationView.class));
		tabs.add(createTab("Function Libraries", FunctionLibrariesDocumentationView.class));
		return tabs.toArray(new Tab[tabs.size()]);
	}

	private static Tab createTab(String title, Class<? extends Component> viewClass) {
		return createTab(populateLink(new RouterLink(null, viewClass), title));
	}

	private static Tab createTab(Component content) {
		final Tab tab = new Tab();
		tab.addThemeVariants(TabVariant.LUMO_ICON_ON_TOP);
		tab.add(content);
		return tab;
	}

	private static <T extends HasComponents> T populateLink(T a, String title) {
		a.add(title);
		return a;
	}

	@Override
	public void afterNavigation(AfterNavigationEvent event) {
		// Select the matching navigation tab on page load
		String location = event.getLocation().getFirstSegment();
		menu.getChildren().forEach(component -> {
			if (component instanceof Tab) {
				Tab tab = (Tab) component;
				tab.getChildren().findFirst().ifPresent(component1 -> {
					if (component1 instanceof RouterLink) {
						RouterLink link = (RouterLink) component1;
						if (link.getHref().equals(location)) {
							menu.setSelectedTab(tab);
						}
					}
				});
			}
		});
	}
}
