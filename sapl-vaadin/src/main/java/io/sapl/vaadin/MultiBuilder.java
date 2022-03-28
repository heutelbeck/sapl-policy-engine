package io.sapl.vaadin;

import java.util.ArrayList;
import java.util.Optional;

import org.springframework.security.access.AccessDeniedException;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.textfield.TextField;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.MultiAuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.vaadin.base.SecurityHelper;
import lombok.RequiredArgsConstructor;
import reactor.core.Disposable;


/**
 * This is a builder to class to create a multi subscription with multiple components.
 * An instance of this class may be acquired via the method {@link PepBuilderService#getMultiBuilder() PepBuilderService.getMultiBuilder()}.
 *
 * A subscription subject can be set with the function {@link #subject(Object)}.
 * The "with()" method is an overloaded function which allows to add a Vaadin component to the subscription.
 * Calling the "with()" function will return a specific multi-pep-builder object (In case of a button for example {@link VaadinPep.VaadinMultiButtonPepBuilder}).
 * This multi-pep-builder can be used to set action and resource of the subscription.
 * To add another component, the function "and()" of the multi-pep-builder may be used.
 * When the configuring is done, call "build()" to start the multi subscription.
 * Note: The same builder can not be used to build twice.
 */
@RequiredArgsConstructor
public class MultiBuilder {

	private final PolicyDecisionPoint pdp;
	private final VaadinConstraintEnforcementService vaadinConstraintEnforcementService;
	private Disposable disposable;
	private boolean isBuild = false;
	private Object subject = SecurityHelper.getSubject();
	private UI ui;
	private final ArrayList<VaadinPep> vaadinPepArrayList = new ArrayList<>();

	/**
	 * This function shall add the parameter vaadinPep to the list of subscriptions.
	 * @param vaadinPep	Subscription container to be added to the multi subscription.
	 * @return Index of the added vaadinPep in the list (list size - 1).
	 */
	protected int registerPep(VaadinPep vaadinPep){
		int index = vaadinPepArrayList.size();
		vaadinPepArrayList.add(vaadinPep);
		return index;
	}

	/**
	 * This function shall create a {@link VaadinPep.VaadinMultiComponentPepBuilder} with the given component.
	 * @param component Component to be linked with the Multi-Pep-Builder
	 * @return VaadinMultiComponentPepBuilder
	 */
	public VaadinPep.VaadinMultiComponentPepBuilder with(Component component) {
		return new VaadinPep.VaadinMultiComponentPepBuilder(pdp,
				vaadinConstraintEnforcementService,
				this,
				component);
	}

	/**
	 * This function shall create a {@link VaadinPep.VaadinMultiButtonPepBuilder} with the given button.
	 * @param button Button to be linked with the Multi-Pep-Builder
	 * @return VaadinMultiButtonPepBuilder
	 */
	public VaadinPep.VaadinMultiButtonPepBuilder with(Button button) {
		return new VaadinPep.VaadinMultiButtonPepBuilder(pdp,
				vaadinConstraintEnforcementService,
				this,
				button);
	}

	/**
	 * This function shall create a {@link VaadinPep.VaadinMultiTextFieldPepBuilder} with the given text field.
	 * @param textField Text field to be linked with the Multi-Pep-Builder
	 * @return VaadinMultiTextFieldPepBuilder
	 */
	public VaadinPep.VaadinMultiTextFieldPepBuilder with(TextField textField) {
		return new VaadinPep.VaadinMultiTextFieldPepBuilder(pdp,
				vaadinConstraintEnforcementService,
				this,
				textField);
	}

	/**
	 * This function shall create a {@link VaadinPep.VaadinMultiCheckboxPepBuilder} with the given checkbox.
	 * @param checkbox Checkbox to be linked with the Multi-Pep-Builder
	 * @return VaadinMultiCheckboxPepBuilder
	 */
	public VaadinPep.VaadinMultiCheckboxPepBuilder with(Checkbox checkbox) {
		return new VaadinPep.VaadinMultiCheckboxPepBuilder(pdp,
				vaadinConstraintEnforcementService,
				this,
				checkbox);
	}

	/**
	 * This function shall create a {@link VaadinPep.VaadinMultiSpanPepBuilder} with the given span.
	 * @param span Span to be linked with the Multi-Pep-Builder
	 * @return VaadinMultiSpanPepBuilder
	 */
	public VaadinPep.VaadinMultiSpanPepBuilder with(Span span) {
		return new VaadinPep.VaadinMultiSpanPepBuilder(pdp,
				vaadinConstraintEnforcementService,
				this,
				span);
	}

	/**
	 * This function shall set the subject of the multi subscription.
	 * @param subject Subject to be set
	 * @return Current MultiBuilder instance (this)
	 */
	public MultiBuilder subject(Object subject){
		this.subject = subject;
		return this;
	}

	/**
	 * This function shall remove the subscription container at the given index.
	 * Note: This function cancels the current subscription and restarts it after the container has been removed.
	 * @param index Index of the VaadinPep to remove
	 */
	protected void unregisterPep(int index){
		stopMultiSubscription();
		if ( vaadinPepArrayList.size() > index ){
			vaadinPepArrayList.remove(index);
		}
		if (! vaadinPepArrayList.isEmpty() ){
			startMultiSubscription();
		}
	}

	/**
	 * This function shall stop the current multi subscription.
	 */
	private void stopMultiSubscription(){
		if ( disposable != null && ! disposable.isDisposed() ){
			disposable.dispose();
		}
	}

	/**
	 * This function shall get all authorization subscriptions from the container and add them to a new multi subscription.
	 * @return New MultiAuthorizationSubscription created out of the VaadinPep containers
	 */
	private MultiAuthorizationSubscription getMultiSubscription(){
		var multiAuthorizationSubscription = new MultiAuthorizationSubscription();
		for (int i = 0; i < vaadinPepArrayList.size(); i++){
			var pep = vaadinPepArrayList.get(i);
			pep.subject(subject);
			multiAuthorizationSubscription.addAuthorizationSubscription(String.valueOf(i), pep.getAuthorizationSubscription());
		}
		return multiAuthorizationSubscription;
	}

	/**
	 * This function shall start the multi subscription.
	 */
	private void startMultiSubscription(){
		disposable = pdp.decide(getMultiSubscription())
				.subscribe((identifiableDecision) -> {
					var id = identifiableDecision.getAuthorizationSubscriptionId();
					if (id != null) {
						AuthorizationDecision authzDecision = identifiableDecision.getAuthorizationDecision();
						VaadinPep vaadinPep = vaadinPepArrayList.get(Integer.parseInt(id));
						vaadinConstraintEnforcementService
								.enforceConstraintsOfDecision(authzDecision, ui, vaadinPep)
								.subscribe(vaadinPep::handleDecision);
					}
				});
	}

	/**
	 * This function shall execute the build and start the multi subscription.
	 * An AccessDeniedException shall be thrown, if build has already been called.
	 * @param component Component to get the ui from.
	 */
	protected void build(Component component) {
		if (!isBuild) {				
			component.addDetachListener((__) -> stopMultiSubscription());
			if (component.isAttached()) {
				Optional<UI> optionalUI = component.getUI();
				if ( optionalUI.isPresent() ){
					this.ui = optionalUI.get();
					startMultiSubscription();
				} else {
					throw new AccessDeniedException("Unable to start subscription, UI is not present");
				}
			} else {
				component.addAttachListener((e) -> {
					Optional<UI> optionalUI = component.getUI();
					if ( optionalUI.isPresent() ){
						this.ui = optionalUI.get();
						startMultiSubscription();
					} else {
						throw new AccessDeniedException("Unable to start subscription, UI is not present");
					}
				});
			}
			isBuild = true;
		} else {
			throw new AccessDeniedException("Builder has already been build. The builder can only be used once.");
		}
	}
}

