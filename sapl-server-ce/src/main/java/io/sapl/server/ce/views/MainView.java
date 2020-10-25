package io.sapl.server.ce.views;

import java.util.HashMap;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.page.BodySize;
import com.vaadin.flow.router.RouterLayout;
import com.vaadin.flow.server.InitialPageSettings;
import com.vaadin.flow.server.PageConfigurator;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;

import lombok.NonNull;

/**
 * The main view of the application with the common ui of the remaining views.
 */
@BodySize
@Theme(value = Lumo.class, variant = Lumo.DARK)
public class MainView extends Div implements RouterLayout, PageConfigurator {
	public MainView() {
		getElement().getStyle().set("height", "100%");
	}

	@Override
	public void configurePage(@NonNull InitialPageSettings settings) {
		HashMap<String, String> attributes = new HashMap<>();
		attributes.put("rel", "shortcut icon");
		attributes.put("type", "image/png");
		settings.addLink("icons/favicon.png", attributes);
	}
}
