package io.sapl.server.ce.views.documentation;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.server.ce.service.documentation.Function;
import io.sapl.server.ce.service.documentation.FunctionLibrary;
import io.sapl.server.ce.service.documentation.FunctionLibraryService;
import io.sapl.server.ce.views.AppNavLayout;

/**
 * View for showing a specific function library.
 */
@Tag("show-single-function-library")
@Route(value = FunctionLibraryDocumentationView.ROUTE, layout = AppNavLayout.class)
@JsModule("./show-single-function-library.js")
@PageTitle("View function library")
public class FunctionLibraryDocumentationView extends PolymerTemplate<FunctionLibraryDocumentationView.ShowSingleFunctionLibraryModel>
		implements HasUrlParameter<String> {
	public static final String ROUTE = "documentation/function-libraries";

	private final FunctionLibraryService functionLibraryService;

	/**
	 * The name of the function library to view.
	 */
	private String functionLibraryName;

	@Id(value = "nameTextArea")
	private TextArea nameTextArea;

	@Id(value = "descriptionTextArea")
	private TextArea descriptionTextArea;

	@Id(value = "functionGrid")
	private Grid<Function> functionGrid;

	/**
	 * Creates a new ShowSingleFunctionLibrary.
	 */
	public FunctionLibraryDocumentationView(FunctionLibraryService functionLibraryService) {
		this.functionLibraryService = functionLibraryService;

		this.initUI();
	}

	@Override
	public void setParameter(BeforeEvent event, String parameter) {
		this.functionLibraryName = parameter;

		this.reloadFunctionLibrary();
	}

	private void reloadFunctionLibrary() {
		FunctionLibrary functionLibrary = this.functionLibraryService.getByName(this.functionLibraryName);

		this.nameTextArea.setValue(functionLibrary.getName());
		this.descriptionTextArea.setValue(functionLibrary.getDescription());
	}

	private void initUI() {
		this.initFunctionGrid();
	}

	private void initFunctionGrid() {
		// add columns
		this.functionGrid.addColumn(Function::getName).setHeader("Name").setAutoWidth(true);
		this.functionGrid.addColumn(Function::getDocumentation).setHeader("Documentation").setAutoWidth(true);

		// set data provider
		CallbackDataProvider<Function, Void> dataProvider = DataProvider.fromCallbacks(query -> {
			int offset = query.getOffset();
			int limit = query.getLimit();

			return this.functionLibraryService.getFunctionsOfLibrary(this.functionLibraryName).stream().skip(offset)
					.limit(limit);
		}, query -> this.functionLibraryService.getByName(this.functionLibraryName).getAmountOfFunctions());
		this.functionGrid.setDataProvider(dataProvider);

		this.functionGrid.setHeightByRows(true);
	}

	/**
	 * This model binds properties between ShowSingleFunctionLibrary and
	 * show-single-function-library
	 */
	public interface ShowSingleFunctionLibraryModel extends TemplateModel {
	}
}
