package io.sapl.server.ce.views.documentation;

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

import io.sapl.server.ce.service.documentation.PipService;
import io.sapl.server.ce.service.documentation.PolicyInformationPoint;
import io.sapl.server.ce.views.AppNavLayout;

/**
 * View for showing available policy information points (PIP).
 */
@Tag("show-pips")
@Route(value = PolicyInformationPointsDocumentationView.ROUTE, layout = AppNavLayout.class)
@JsModule("./show-pips.js")
@PageTitle("Available PIPs")
public class PolicyInformationPointsDocumentationView extends PolymerTemplate<PolicyInformationPointsDocumentationView.ShowPipsModel> {
	public static final String ROUTE = "documentation/pips";

	private final PipService pipService;

	@Id(value = "pipGrid")
	private Grid<PolicyInformationPoint> pipGrid;

    /**
     * Creates a new ShowPips.
     */
	public PolicyInformationPointsDocumentationView(PipService pipService) {
		this.pipService = pipService;

		this.initUI();
    }

	private void initUI() {
		this.initPipGrid();
	}

	private void initPipGrid() {
		// add columns
		this.pipGrid.addColumn(PolicyInformationPoint::getName).setHeader("Name").setAutoWidth(true);
		this.pipGrid.addColumn(PolicyInformationPoint::getDescription).setHeader("Description");
		this.pipGrid.addColumn(PolicyInformationPoint::getAmountOfFunctions).setHeader("Amount of Functions")
				.setAutoWidth(true);

		this.pipGrid.addComponentColumn(policyInformationPoint -> {
			Button viewButton = new Button("View", VaadinIcon.SEARCH.create());
			viewButton.addClickListener(clickEvent -> {
				String uriToNavigateTo = String.format("%s/%s", PolicyInformationPointDocumentationView.ROUTE,
						policyInformationPoint.getName());
				viewButton.getUI().ifPresent(ui -> ui.navigate(uriToNavigateTo));
			});

			HorizontalLayout componentsForEntry = new HorizontalLayout();
			componentsForEntry.add(viewButton);

			return componentsForEntry;
		});

		// set data provider
		CallbackDataProvider<PolicyInformationPoint, Void> dataProvider = DataProvider.fromCallbacks(query -> {
			int offset = query.getOffset();
			int limit = query.getLimit();

			return this.pipService.getAll().stream().skip(offset).limit(limit);
		}, query -> (int) this.pipService.getAmount());
		this.pipGrid.setDataProvider(dataProvider);

		this.pipGrid.setHeightByRows(true);
	}

	/**
	 * This model binds properties between ShowPips and show-pips
	 */
    public interface ShowPipsModel extends TemplateModel {
    }
}
