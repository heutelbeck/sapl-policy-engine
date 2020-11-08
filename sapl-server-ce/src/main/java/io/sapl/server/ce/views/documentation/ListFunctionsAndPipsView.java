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
import io.sapl.server.ce.views.MainView;
import io.sapl.spring.pdp.embedded.FunctionLibrariesDocumentation;
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
	public static final String ROUTE = "foo";

	private final FunctionLibrariesDocumentation functionLibrariesDocumentation;

	@Id(value = "functionLibsGrid")
	private Grid<LibraryDocumentation> functionLibsGrid;

	@Id(value = "showCurrentFunctionLibLayout")
	private VerticalLayout showCurrentFunctionLibLayout;

	@Id(value = "descriptionOfCurrentFunctionLibDiv")
	private Div descriptionOfCurrentFunctionLibDiv;

	@Id(value = "functionsOfCurrentFunctionLibGrid")
	private Grid<Entry<String, String>> functionsOfCurrentFunctionLibGrid;

	@PostConstruct
	private void postConstruct() {
		initUi();
	}

	private void initUi() {
		initUiForFunctions();
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
				functionsOfCurrentFunctionLibGrid.addColumn(Entry<String, String>::getKey).setHeader("Function")
						.setAutoWidth(true);
				functionsOfCurrentFunctionLibGrid.addColumn(Entry<String, String>::getValue).setHeader("Documentation")
						.setAutoWidth(true);
				functionsOfCurrentFunctionLibGrid.setDataProvider(dataProviderForFunctionsOfCurrentFunctionLibGrid);
			}, () -> {
				showCurrentFunctionLibLayout.setVisible(false);
			});
		});
		functionLibsGrid.setDataProvider(dataProviderForCurrentFunctionLibGrid);
	}

	/**
	 * This model binds properties between ListFunctionsAndPipsView and
	 * list-functions-and-pips-view
	 */
	public interface ListFunctionsAndPipsViewModel extends TemplateModel {
	}
}
