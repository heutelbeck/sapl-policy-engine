package io.sapl.server.ce.views.pdpconfiguration;

import java.util.stream.Stream;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.templatemodel.TemplateModel;

import io.sapl.api.pdp.PolicyDocumentCombiningAlgorithm;
import io.sapl.server.ce.service.pdpconfiguration.CombiningAlgorithmService;
import io.sapl.server.ce.views.AppNavLayout;

/**
 * A Designer generated component for the configure-combining-algorithm template.
 *
 * Designer will add and remove fields with @Id mappings but
 * does not overwrite or otherwise change this file.
 */
@Tag("configure-combining-algorithm")
@Route(value = CombiningAlgorithmView.ROUTE, layout = AppNavLayout.class)
@JsModule("./configure-combining-algorithm.js")
@PageTitle("Combining Algorithm")
public class CombiningAlgorithmView extends PolymerTemplate<CombiningAlgorithmView.ConfigureCombiningAlgorithmModel> {
	public static final String ROUTE = "pdp/combining-algorithm";

	private final CombiningAlgorithmService combiningAlgorithmService;

	@Id("selectionComboBox")
	private ComboBox<String> selectionComboBox;

	public CombiningAlgorithmView(CombiningAlgorithmService combiningAlgorithmService) {
		this.combiningAlgorithmService = combiningAlgorithmService;

		this.initUI();
    }


	private void initUI() {
		PolicyDocumentCombiningAlgorithm[] availableCombiningAlgorithms = this.combiningAlgorithmService.getAvailable();
		String[] availableCombiningAlgorithmsAsStrings = Stream.of(availableCombiningAlgorithms)
				.map(algorithmType -> algorithmType.toString()).toArray(String[]::new);
		this.selectionComboBox.setItems(availableCombiningAlgorithmsAsStrings);

		PolicyDocumentCombiningAlgorithm selectedCombiningAlgorithm = this.combiningAlgorithmService.getSelected();
		this.selectionComboBox.setValue(selectedCombiningAlgorithm.toString());
		
		this.selectionComboBox.addValueChangeListener(changedEvent -> {
			String newCombiningAlgorithmAsString = changedEvent.getValue();
			PolicyDocumentCombiningAlgorithm newCombiningAlgorithm = PolicyDocumentCombiningAlgorithm
					.valueOf(newCombiningAlgorithmAsString);

			this.combiningAlgorithmService.setSelected(newCombiningAlgorithm);
		});
	}

	public interface ConfigureCombiningAlgorithmModel extends TemplateModel {
    }
}
