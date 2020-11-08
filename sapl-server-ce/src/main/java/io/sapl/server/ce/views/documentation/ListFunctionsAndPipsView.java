package io.sapl.server.ce.views.documentation;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PostConstruct;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.data.provider.DataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.interpreter.functions.LibraryDocumentation;
import io.sapl.interpreter.pip.PolicyInformationPointDocumentation;
import io.sapl.server.ce.views.MainView;
import io.sapl.spring.pdp.embedded.FunctionLibrariesDocumentation;
import io.sapl.spring.pdp.embedded.PolicyInformationPointsDocumentation;
import lombok.RequiredArgsConstructor;

/**
 * A Designer generated component for the list-functions-and-pips-view template.
 *
 * Designer will add and remove fields with @Id mappings but does not overwrite
 * or otherwise change this file.
 */
@Tag("list-functions-and-pips-view")
@Route(value = ListFunctionsAndPipsView.ROUTE, layout = MainView.class)
@JsModule("./list-functions-and-pips-view.js")
@PageTitle("Functions & Attributes")
@RequiredArgsConstructor
public class ListFunctionsAndPipsView extends PolymerTemplate<ListFunctionsAndPipsView.ListFunctionsAndPipsViewModel> {
	public static final String ROUTE = "functions";

	private final FunctionLibrariesDocumentation functionLibrariesDocumentation;
	private final PolicyInformationPointsDocumentation policyInformationPointsDocumentation;

	@Id(value = "functionLibsGrid")
	private Grid<LibraryDocumentation> functionLibsGrid;

	@Id(value = "showCurrentFunctionLibLayout")
	private VerticalLayout showCurrentFunctionLibLayout;

	@Id(value = "descriptionOfCurrentFunctionLibDiv")
	private Div descriptionOfCurrentFunctionLibDiv;

	@Id(value = "functionsOfCurrentFunctionLibGrid")
	private Grid<Entry<String, String>> functionsOfCurrentFunctionLibGrid;

	@Id(value = "pipsGrid")
	private Grid<PolicyInformationPointDocumentation> pipsGrid;

	@Id(value = "showCurrentPipLayout")
	private VerticalLayout showCurrentPipLayout;

	@Id(value = "descriptionOfCurrentPipDiv")
	private Div descriptionOfCurrentPipDiv;

	@Id(value = "functionsOfCurrentPipGrid")
	private Grid<Entry<String, String>> functionsOfCurrentPipGrid;

	@PostConstruct
	private void postConstruct() {
		initUi();
	}

	private void initUi() {
		initUiForFunctions();
		initUiForPips();
	}

	private void initUiForFunctions() {
		showCurrentFunctionLibLayout.setVisible(false);

		Collection<LibraryDocumentation> availableFunctionLibs = functionLibrariesDocumentation.getDocumentation();
		CallbackDataProvider<LibraryDocumentation, Void> dataProviderForCurrentFunctionLibGrid = DataProvider
				.fromCallbacks(query -> {
					int offset = query.getOffset();
					int limit = query.getLimit();

					return availableFunctionLibs.stream().skip(offset).limit(limit);
				}, query -> availableFunctionLibs.size());

		functionLibsGrid.addColumn(LibraryDocumentation::getName).setHeader("Name");
		functionLibsGrid.addSelectionListener(selection -> {
			Optional<LibraryDocumentation> optionalSelectedFunctionLib = selection.getFirstSelectedItem();
			optionalSelectedFunctionLib.ifPresentOrElse((LibraryDocumentation selectedFunctionLib) -> {
				showCurrentFunctionLibLayout.setVisible(true);

				descriptionOfCurrentFunctionLibDiv.setText(selectedFunctionLib.getDescription());

				Map<String, String> documentation = selectedFunctionLib.getDocumentation();
				Set<Entry<String, String>> documentationAsEntrySet = documentation.entrySet();
				CallbackDataProvider<Entry<String, String>, Void> dataProviderForFunctionsOfCurrentFunctionLibGrid = DataProvider
						.fromCallbacks(query -> {
							int offset = query.getOffset();
							int limit = query.getLimit();

							return documentationAsEntrySet.stream().skip(offset).limit(limit);
						}, query -> documentationAsEntrySet.size());

				functionsOfCurrentFunctionLibGrid.removeAllColumns();
				functionsOfCurrentFunctionLibGrid.addColumn(Entry<String, String>::getKey).setHeader("Function");
				functionsOfCurrentFunctionLibGrid.addColumn(Entry<String, String>::getValue).setHeader("Documentation")
						.setFlexGrow(5);
				functionsOfCurrentFunctionLibGrid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);
				functionsOfCurrentFunctionLibGrid.setDataProvider(dataProviderForFunctionsOfCurrentFunctionLibGrid);
			}, () -> {
				showCurrentFunctionLibLayout.setVisible(false);
			});
		});
		functionLibsGrid.setDataProvider(dataProviderForCurrentFunctionLibGrid);
	}

	private void initUiForPips() {
		showCurrentPipLayout.setVisible(false);

		Collection<PolicyInformationPointDocumentation> availablePips = policyInformationPointsDocumentation
				.getDocumentation();
		CallbackDataProvider<PolicyInformationPointDocumentation, Void> dataProviderForCurrentPipGrid = DataProvider
				.fromCallbacks(query -> {
					int offset = query.getOffset();
					int limit = query.getLimit();

					return availablePips.stream().skip(offset).limit(limit);
				}, query -> availablePips.size());

		pipsGrid.addColumn(PolicyInformationPointDocumentation::getName).setHeader("Name");
		pipsGrid.addSelectionListener(selection -> {
			Optional<PolicyInformationPointDocumentation> optionalSelectedPip = selection.getFirstSelectedItem();
			optionalSelectedPip.ifPresentOrElse((PolicyInformationPointDocumentation selectedPip) -> {
				showCurrentPipLayout.setVisible(true);

				descriptionOfCurrentPipDiv.setText(selectedPip.getDescription());

				Map<String, String> documentation = selectedPip.getDocumentation();
				Set<Entry<String, String>> documentationAsEntrySet = documentation.entrySet();
				CallbackDataProvider<Entry<String, String>, Void> dataProviderForFunctionsOfCurrentPipGrid = DataProvider
						.fromCallbacks(query -> {
							int offset = query.getOffset();
							int limit = query.getLimit();

							return documentationAsEntrySet.stream().skip(offset).limit(limit);
						}, query -> documentationAsEntrySet.size());

				functionsOfCurrentPipGrid.removeAllColumns();
				functionsOfCurrentPipGrid.addColumn(Entry<String, String>::getKey).setHeader("Function");
				functionsOfCurrentPipGrid.addColumn(Entry<String, String>::getValue).setHeader("Documentation")
						.setFlexGrow(5);
				functionsOfCurrentPipGrid.addThemeVariants(GridVariant.LUMO_WRAP_CELL_CONTENT);
				functionsOfCurrentPipGrid.setDataProvider(dataProviderForFunctionsOfCurrentPipGrid);
			}, () -> {
				showCurrentPipLayout.setVisible(false);
			});
		});
		pipsGrid.setDataProvider(dataProviderForCurrentPipGrid);
	}

	/**
	 * This model binds properties between ListFunctionsAndPipsView and
	 * list-functions-and-pips-view
	 */
	public interface ListFunctionsAndPipsViewModel extends TemplateModel {
	}
}
