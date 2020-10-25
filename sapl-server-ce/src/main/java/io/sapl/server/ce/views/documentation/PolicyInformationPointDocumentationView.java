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
import io.sapl.server.ce.service.documentation.PipService;
import io.sapl.server.ce.service.documentation.PolicyInformationPoint;
import io.sapl.server.ce.views.AppNavLayout;
import lombok.NonNull;

/**
 * View for showing a specific PIP.
 */
@Tag("show-single-pip")
@JsModule("./show-single-pip.js")
@Route(value = PolicyInformationPointDocumentationView.ROUTE, layout = AppNavLayout.class)
@PageTitle("View functions of PIP")
public class PolicyInformationPointDocumentationView extends PolymerTemplate<PolicyInformationPointDocumentationView.ShowSinglePipModel>
		implements HasUrlParameter<String> {
	public static final String ROUTE = "documentation/pips";

	private final PipService pipService;

	/**
	 * The name of the PIP to view.
	 */
	private String pipName;

	@Id(value = "nameTextArea")
	private TextArea nameTextArea;

	@Id(value = "descriptionTextArea")
	private TextArea descriptionTextArea;

	@Id(value = "pipGrid")
	private Grid<Function> functionGrid;

    /**
     * Creates a new ShowSinglePip.
     */
	public PolicyInformationPointDocumentationView(@NonNull PipService pipService) {
		this.pipService = pipService;

		this.initUI();
    }

	@Override
	public void setParameter(BeforeEvent event, String parameter) {
		this.pipName = parameter;

		this.reloadPolicyInformationPoint();
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

			return this.pipService.getFunctionsOfPIP(this.pipName).stream().skip(offset).limit(limit);
		}, query -> this.pipService.getByName(this.pipName).getAmountOfFunctions());
		this.functionGrid.setDataProvider(dataProvider);

		this.functionGrid.setHeightByRows(true);
	}

	private void reloadPolicyInformationPoint() {
		PolicyInformationPoint policyInformationPoint = this.pipService.getByName(this.pipName);

		this.nameTextArea.setValue(policyInformationPoint.getName());
		this.descriptionTextArea.setValue(policyInformationPoint.getDescription());
	}

	/**
	 * This model binds properties between ShowSinglePip and show-single-pip
	 */
    public interface ShowSinglePipModel extends TemplateModel {
    }
}
