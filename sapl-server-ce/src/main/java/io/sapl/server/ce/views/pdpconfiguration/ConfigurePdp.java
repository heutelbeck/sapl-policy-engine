package io.sapl.server.ce.views.pdpconfiguration;

import java.util.stream.Stream;

import javax.annotation.PostConstruct;

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
import io.sapl.server.ce.views.utils.confirm.ConfirmUtils;
import lombok.RequiredArgsConstructor;

/**
 * A Designer generated component for the configure-pdp template.
 *
 * Designer will add and remove fields with @Id mappings but does not overwrite
 * or otherwise change this file.
 */
@Tag("configure-pdp")
@Route(value = ConfigurePdp.ROUTE, layout = AppNavLayout.class)
@JsModule("./configure-pdp.js")
@PageTitle("PDP Configuration")
@RequiredArgsConstructor
public class ConfigurePdp extends PolymerTemplate<ConfigurePdp.ConfigurePdpModel> {
	public static final String ROUTE = "pdp-configuration";

	private final CombiningAlgorithmService combiningAlgorithmService;

	@Id(value = "comboBoxCombAlgo")
	private ComboBox<String> comboBoxCombAlgo;

	private boolean isIgnoringNextCombiningAlgorithmComboBoxChange;

	@PostConstruct
	public void postConstruct() {
		this.initUi();
	}

	private void initUi() {
		this.initUiForCombiningAlgorithm();
	}

	private void initUiForCombiningAlgorithm() {
		PolicyDocumentCombiningAlgorithm[] availableCombiningAlgorithms = this.combiningAlgorithmService.getAvailable();
		String[] availableCombiningAlgorithmsAsStrings = Stream.of(availableCombiningAlgorithms)
				.map(algorithmType -> algorithmType.toString()).toArray(String[]::new);
		this.comboBoxCombAlgo.setItems(availableCombiningAlgorithmsAsStrings);

		PolicyDocumentCombiningAlgorithm selectedCombiningAlgorithm = this.combiningAlgorithmService.getSelected();
		this.comboBoxCombAlgo.setValue(selectedCombiningAlgorithm.toString());

		this.comboBoxCombAlgo.addValueChangeListener(changedEvent -> {
			if (isIgnoringNextCombiningAlgorithmComboBoxChange) {
				isIgnoringNextCombiningAlgorithmComboBoxChange = false;
				return;
			}

			String newCombiningAlgorithmAsString = changedEvent.getValue();
			PolicyDocumentCombiningAlgorithm newCombiningAlgorithm = PolicyDocumentCombiningAlgorithm
					.valueOf(newCombiningAlgorithmAsString);

			ConfirmUtils.letConfirm(
					"The combining algorithm describes how to come to the final decision while evaluating all published policies.\n\nPlease consider the consequences and confirm the action.",
					() -> {
						combiningAlgorithmService.setSelected(newCombiningAlgorithm);
					}, () -> {
						isIgnoringNextCombiningAlgorithmComboBoxChange = true;

						String oldCombiningAlgorithmAsString = changedEvent.getOldValue();
						comboBoxCombAlgo.setValue(oldCombiningAlgorithmAsString);
					});
		});
	}

	/**
	 * This model binds properties between ConfigurePdp and configure-pdp
	 */
	public interface ConfigurePdpModel extends TemplateModel {
	}
}
