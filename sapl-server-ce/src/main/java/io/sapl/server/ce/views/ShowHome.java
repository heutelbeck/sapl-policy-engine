package io.sapl.server.ce.views;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import lombok.NoArgsConstructor;

/**
 * View for the home page.
 */
@Tag("show-home")
@Route(value = ShowHome.ROUTE, layout = AppNavLayout.class)
@JsModule("./show-home.js")
@PageTitle("SAPL PDP-Server CE")
@NoArgsConstructor
public class ShowHome extends PolymerTemplate<ShowHome.ShowHomeModel> {
	public static final String ROUTE = "";
	/**
	 * This model binds properties between CreateSaplDocument and
	 * create-sapl-document
	 */
	public interface ShowHomeModel extends TemplateModel {
	}
}
