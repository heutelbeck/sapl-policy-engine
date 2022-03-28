package io.sapl.vaadin;

import org.springframework.stereotype.Service;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;

import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.RequiredArgsConstructor;

/**
 * This class provides functions to create different builder objects:
 *   - Single builder (See overloaded "with()" for each different vaadin component, e.g. Button, TextField, etc.)
 *   - Multi builder for multi subscriptions (See {@link PepBuilderService#getMultiBuilder()})
 *   - Lifecycle before enter builder (See {@link PepBuilderService#getLifecycleBeforeEnterPepBuilder()})
 *   - Lifecycle before leave builder (See {@link PepBuilderService#getLifecycleBeforeLeavePepBuilder()})
 */
@Service
@RequiredArgsConstructor
public class PepBuilderService {

	private final PolicyDecisionPoint pdp;
	private final VaadinConstraintEnforcementService vaadinConstraintEnforcementService;

	/**
	 * This function creates and returns a Single-Builder for subscriptions with a vaadin component.
	 * @param component Component to be linked with the Single-Pep-Builder
	 * @return New {@link VaadinPep.VaadinSingleComponentPepBuilder}
	 */
	public VaadinPep.VaadinSingleComponentPepBuilder with(Component component){
		return new VaadinPep.VaadinSingleComponentPepBuilder(pdp, vaadinConstraintEnforcementService, component);
	}

	/**
	 * This function creates and returns a Single-Builder for subscriptions with a vaadin button.
	 * @param button Button to be linked with the Single-Pep-Builder
	 * @return New {@link VaadinPep.VaadinSingleButtonPepBuilder}
	 */
	public VaadinPep.VaadinSingleButtonPepBuilder with(Button button){
		return new VaadinPep.VaadinSingleButtonPepBuilder(pdp, vaadinConstraintEnforcementService, button);
	}

	/**
	 * This function creates and returns a Single-Builder for subscriptions with a vaadin text field.
	 * @param textField Text field to be linked with the Single-Pep-Builder
	 * @return New {@link VaadinPep.VaadinSingleTextFieldPepBuilder}
	 */
	public VaadinPep.VaadinSingleTextFieldPepBuilder with(TextField textField){
		return new VaadinPep.VaadinSingleTextFieldPepBuilder(pdp, vaadinConstraintEnforcementService, textField);
	}

	/**
	 * This function creates and returns a Single-Builder for subscriptions with a vaadin checkbox.
	 * @param checkbox Checkbox to be linked with the Single-Pep-Builder
	 * @return New {@link VaadinPep.VaadinSingleCheckboxPepBuilder}
	 */
	public VaadinPep.VaadinSingleCheckboxPepBuilder with(Checkbox checkbox){
		return new VaadinPep.VaadinSingleCheckboxPepBuilder(pdp, vaadinConstraintEnforcementService, checkbox);
	}

	/**
	 * This function creates and returns a Single-Builder for subscriptions with a vaadin span.
	 * @param span Span to be linked with the Single-Pep-Builder
	 * @return New {@link VaadinPep.VaadinSingleSpanPepBuilder}
	 */
	public VaadinPep.VaadinSingleSpanPepBuilder with(Span span){
		return new VaadinPep.VaadinSingleSpanPepBuilder(pdp, vaadinConstraintEnforcementService, span);
	}

	/**
	 * This function creates and returns a {@link MultiBuilder} object.
	 * @return New {@link MultiBuilder}
	 */
	public MultiBuilder getMultiBuilder(){
		return new MultiBuilder(pdp, vaadinConstraintEnforcementService);
	}

	/**
	 * This function creates and returns a builder for subscriptions with the before enter event.
	 * @return New {@link VaadinPep.LifecycleBeforeEnterPepBuilder}
	 */
	public VaadinPep.LifecycleBeforeEnterPepBuilder getLifecycleBeforeEnterPepBuilder(){
		return new VaadinPep.LifecycleBeforeEnterPepBuilder(pdp, vaadinConstraintEnforcementService);
	}

	/**
	 * This function creates and returns a builder for subscriptions with the before leave event.
	 * @return New {@link VaadinPep.LifecycleBeforeLeavePepBuilder}
	 */
	public VaadinPep.LifecycleBeforeLeavePepBuilder getLifecycleBeforeLeavePepBuilder(){
		return new VaadinPep.LifecycleBeforeLeavePepBuilder(pdp, vaadinConstraintEnforcementService);
	}
}
