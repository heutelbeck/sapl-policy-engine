package io.sapl.server.ce.views.pdpconfiguration;

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

import io.sapl.server.ce.model.pdpconfiguration.Variable;
import io.sapl.server.ce.service.pdpconfiguration.VariablesService;
import io.sapl.server.ce.views.AppNavLayout;

/**
 * View to list variables.
 */
@Tag("list-variables")
@Route(value = VariablesView.ROUTE, layout = AppNavLayout.class)
@JsModule("./list-variables.js")
@PageTitle("Variables")
public class VariablesView extends PolymerTemplate<VariablesView.ListVariablesModel> {
	public static final String ROUTE = "configuration/variables";

	private final VariablesService variableService;

	@Id(value = "variablesGrid")
	private Grid<Variable> variablesGrid;

	@Id(value = "createButton")
	private Button createButton;

	public VariablesView(VariablesService variableService) {
		this.variableService = variableService;

		this.initUI();
    }

	private void initUI() {
		this.initVariablesGrid();

		this.createButton.addClickListener(clickEvent -> {
			this.createButton.getUI().ifPresent(ui -> ui.navigate(CreateVariableView.ROUTE));
		});
	}

	private void initVariablesGrid() {
		// add columns
		this.variablesGrid.addColumn(Variable::getName).setHeader("Name");
		this.variablesGrid.addColumn(Variable::getJsonValue).setHeader("JSON Value");
		this.variablesGrid.addComponentColumn(variable -> {
			Button editButton = new Button("Edit", VaadinIcon.EDIT.create());
			editButton.addClickListener(clickEvent -> {
				String uriToNavigateTo = String.format("%s/%d", EditVariableView.ROUTE, variable.getId());
				editButton.getUI().ifPresent(ui -> ui.navigate(uriToNavigateTo));
			});

			Button deleteButton = new Button("Delete", VaadinIcon.FILE_REMOVE.create());
			deleteButton.addClickListener(clickEvent -> {
				this.variableService.delete(variable.getId());

				// trigger refreshing variable grid
				this.variablesGrid.getDataProvider().refreshAll();
			});

			HorizontalLayout componentsForEntry = new HorizontalLayout();
			componentsForEntry.add(editButton);
			componentsForEntry.add(deleteButton);

			return componentsForEntry;
		});

		// set data provider
		CallbackDataProvider<Variable, Void> dataProvider = DataProvider.fromCallbacks(query -> {
			int offset = query.getOffset();
			int limit = query.getLimit();

			return this.variableService.getAll().stream().skip(offset).limit(limit);
		}, query -> (int) this.variableService.getAmount());
		this.variablesGrid.setDataProvider(dataProvider);

		this.variablesGrid.setHeightByRows(true);
	}

    public interface ListVariablesModel extends TemplateModel {
    }
}
