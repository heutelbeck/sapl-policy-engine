package io.sapl.server.ce.views.documentation;

import javax.annotation.PostConstruct;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.server.ce.service.documentation.FunctionLibrary;
import io.sapl.server.ce.service.documentation.FunctionLibraryService;
import io.sapl.server.ce.views.AppNavLayout;
import lombok.RequiredArgsConstructor;

/**
 * View for showing loaded function libraries.
 */
@RequiredArgsConstructor
@Tag("show-function-libraries")
@PageTitle("Function Libraries")
@JsModule("./show-function-libraries.js")
@Route(value = FunctionLibrariesDocumentationView.ROUTE, layout = AppNavLayout.class)
public class FunctionLibrariesDocumentationView
		extends PolymerTemplate<FunctionLibrariesDocumentationView.ShowFunctionLibrariesModel> {
	public static final String ROUTE = "documentation/function-libraries";

	private final FunctionLibraryService functionLibraryService;

	@Id(value = "libraryGrid")
	private Grid<FunctionLibrary> libraryGrid;

	@PostConstruct
	public void initUI() {
		initLibraryGrid();
	}

	private void initLibraryGrid() {
		// add columns
		this.libraryGrid.addColumn(FunctionLibrary::getName).setHeader("Name");
		this.libraryGrid.addColumn(FunctionLibrary::getDescription).setHeader("Description");
		this.libraryGrid.addColumn(FunctionLibrary::getAmountOfFunctions).setHeader("Amount of Functions");
		this.libraryGrid.addComponentColumn(functionLibrary -> {
			Button viewButton = new Button("View", VaadinIcon.SEARCH.create());
			viewButton.addClickListener(clickEvent -> {
				String uriToNavigateTo = String.format("%s/%s", FunctionLibraryDocumentationView.ROUTE,
						functionLibrary.getName());
				viewButton.getUI().ifPresent(ui -> ui.navigate(uriToNavigateTo));
			});

			HorizontalLayout componentsForEntry = new HorizontalLayout();
			componentsForEntry.add(viewButton);

			return componentsForEntry;
		});

		// set data provider
		CallbackDataProvider<FunctionLibrary, Void> dataProvider = DataProvider.fromCallbacks(query -> {
			int offset = query.getOffset();
			int limit = query.getLimit();

			return this.functionLibraryService.getAll().stream().skip(offset).limit(limit);
		}, query -> (int) this.functionLibraryService.getAmount());
		this.libraryGrid.setDataProvider(dataProvider);

		this.libraryGrid.setHeightByRows(true);
	}

	/**
	 * This model binds properties between ShowFunctionLibraries and
	 * show-function-libraries
	 */
	public interface ShowFunctionLibrariesModel extends TemplateModel {
		// Add setters and getters for template properties here.
	}
}
