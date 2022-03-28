package io.sapl.vaadin;

import static io.sapl.vaadin.base.SecurityHelper.getUserRoles;
import static io.sapl.vaadin.base.SecurityHelper.getUsername;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasEnabled;
import com.vaadin.flow.component.HasText;
import com.vaadin.flow.component.HasValueAndElement;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterListener;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveListener;
import com.vaadin.flow.server.VaadinServletRequest;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.RunnableConstraintHandlerProvider;
import io.sapl.vaadin.constraint.VaadinFunctionConstraintHandlerProvider;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import reactor.core.Disposable;

/**
 * This class contains the subscription relevant data and provides functions to
 * start a subscription.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class VaadinPep {

	private final PolicyDecisionPoint pdp;
	private final EnforceConstraintsOfDecision enforceConstraintsOfDecision;
	private final List<Consumer<AuthorizationDecision>> decisionListenerList = new ArrayList<>();
	private final List<BiConsumer<AuthorizationDecision, BeforeEvent>> decisionEventListenerList = new ArrayList<>();
	private final List<VaadinFunctionConstraintHandlerProvider> localVaadinFunctionProvider = new ArrayList<>();
	private final List<ConsumerConstraintHandlerProvider<UI>> localConsumerProviders = new ArrayList<>();
	private final List<RunnableConstraintHandlerProvider> localRunnableProviders = new ArrayList<>();
	private Disposable disposable;
	private Object subject;
	private Object action;
	private Object resource;
	private Object environment;

	/**
	 * This function shall execute all decision listener consumers
	 *
	 * @param authzDecision Authorization decision passed to the consumers
	 */
	protected void handleDecision(AuthorizationDecision authzDecision) {
		for (Consumer<AuthorizationDecision> listener : decisionListenerList) {
			listener.accept(authzDecision);
		}
	}

	/**
	 * This function shall execute all decision event listener bi consumers
	 *
	 * @param authzDecision Authorization decision passed as first argument to the
	 *                      bi consumers
	 * @param event         Event passed as the second argument to the bi consumers
	 */
	private void handleEventDecision(AuthorizationDecision authzDecision, BeforeEvent event) {
		for (BiConsumer<AuthorizationDecision, BeforeEvent> listener : decisionEventListenerList) {
			listener.accept(authzDecision, event);
		}
	}

	/**
	 * This function sets the subject of the subscription
	 *
	 * @param subject Subject to set
	 */
	protected void subject(Object subject) {
		this.subject = subject;
	}

	/**
	 * This function returns the authorization subscription of the current subject,
	 * action and resource.
	 *
	 * @return Authorization subscription of subject, action and resource.
	 */
	protected AuthorizationSubscription getAuthorizationSubscription() {
		return AuthorizationSubscription.of(subject, action, resource, environment);
	}

	/**
	 * This function shall stop the current subscription.
	 */
	protected void stopSubscription() {
		if (disposable != null && !disposable.isDisposed()) {
			disposable.dispose();
		}
	}

	/**
	 * This function shall start the subscription.
	 *
	 * @param ui Current UI used for constraint handling
	 */
	private void enforce(UI ui) {
		stopSubscription();
		disposable = pdp.decide(getAuthorizationSubscription()).flatMap(
				authzDecision -> enforceConstraintsOfDecision.enforceConstraintsOfDecision(authzDecision, ui, this))
				.subscribe(this::handleDecision);
	}

	/**
	 * This function shall start the subscription and block until a decision has
	 * arrived.
	 *
	 * @param ui    Current UI used for constraint handling
	 * @param event Current event passed to the event bi consumers
	 */
	private void enforceOnce(UI ui, BeforeEvent event) {
		AuthorizationDecision decision = pdp.decide(getAuthorizationSubscription()).flatMap(
				authzDecision -> enforceConstraintsOfDecision.enforceConstraintsOfDecision(authzDecision, ui, this))
				.blockFirst();
		handleEventDecision(decision, event);
	}

	protected List<VaadinFunctionConstraintHandlerProvider> getLocalVaadinFunctionProvider() {
		return localVaadinFunctionProvider;
	}

	protected List<ConsumerConstraintHandlerProvider<UI>> getLocalConsumerProviders() {
		return localConsumerProviders;
	}

	protected List<RunnableConstraintHandlerProvider> getLocalRunnableProviders() {
		return localRunnableProviders;
	}

	// **** Interfaces ****

	/**
	 * This interface provides basic functions which all components support.
	 *
	 * @param <T> VaadinSingle*PepBuilder or VaadinMulti*PepBuilder (e.g.
	 *            VaadinMultiButtonBuilder)
	 * @param <C> Subclass of component (e.g. Button). Note: Should be consistent
	 *            with T
	 */
	public interface VaadinPepBuilderBase<T, C extends Component> {
		/**
		 * This function shall add the bi consumer to the internal decision listener
		 * list.
		 *
		 * @param biConsumer BiConsumer that is called when a decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		T onDecisionDo(BiConsumer<AuthorizationDecision, C> biConsumer);

		/**
		 * This function shall add the consumer to the internal decision listener list.
		 *
		 * @param consumer Consumer that is called when a decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onDecisionDo(Consumer<AuthorizationDecision> consumer) {
			return onDecisionDo((authzDecision, component) -> consumer.accept(authzDecision));
		}

		/**
		 * This function shall add the bi consumer to the internal decision listener
		 * list.
		 *
		 * @param biConsumer BiConsumer that is called when a PERMIT decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		T onPermitDo(BiConsumer<AuthorizationDecision, C> biConsumer);

		/**
		 * This function shall add the consumer to the internal decision listener list.
		 *
		 * @param consumer Consumer of UI that is called when a PERMIT decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onPermitDo(Consumer<C> consumer) {
			return onPermitDo((authzDecision, component) -> consumer.accept(component));
		}

		/**
		 * This function shall add the runnable to the internal decision listener list.
		 *
		 * @param runnable Runnable that is called when a PERMIT decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onPermitDo(Runnable runnable) {
			return onPermitDo((authzDecision, component) -> runnable.run());
		}

		/**
		 * This function shall add the bi consumer to the internal decision listener
		 * list.
		 *
		 * @param biConsumer BiConsumer that is called when a DENY decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		T onDenyDo(BiConsumer<AuthorizationDecision, C> biConsumer);

		/**
		 * This function shall add the consumer to the internal decision listener list.
		 *
		 * @param consumer Consumer of UI that is called when a DENY decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onDenyDo(Consumer<C> consumer) {
			return onDenyDo((authzDecision, component) -> consumer.accept(component));
		}

		/**
		 * This function shall add the runnable to the internal decision listener list.
		 *
		 * @param runnable Runnable that is called when a DENY decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onDenyDo(Runnable runnable) {
			return onDenyDo((authzDecision, component) -> runnable.run());
		}

		/**
		 * This function shall add a callback to the internal decision listener list.
		 * The callback shall set the visibility of the component depending on the
		 * decision. If a PERMIT decision occurs the component shall be set visible, if
		 * a DENY decision occurs the component shall be set invisible.
		 *
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onDecisionVisibleOrHidden() {
			return onDecisionDo((AuthorizationDecision authzDecision, C component) -> {
				final boolean accessDenied = authzDecision.getDecision() == Decision.DENY;
				component.getUI().ifPresent(ui -> ui.access(() -> component.setVisible(!accessDenied)));
			});
		}

		/**
		 * Add a callback to the internal decision listener list. The callback sends the
		 * defined Notification if a DENY decision occurs.
		 *
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onDenyNotify(String message, NotificationVariant variant) {
			return onDenyDo((authzDecision, component) -> component.getUI().ifPresent(ui -> ui.access(() -> {
				Notification notification = Notification.show(message);
				notification.addThemeVariants(variant);
			})));
		}

		/**
		 * Add a callback to the internal decision listener list. The callback sends the
		 * defined Notification if a DENY decision occurs.
		 *
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onDenyNotify(String message) {
			return onDenyNotify(message, NotificationVariant.LUMO_ERROR);
		}

		/**
		 * Add a callback to the internal decision listener list. The callback sends the
		 * defined Notification if a PERMIT decision occurs.
		 *
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onPermitNotify(String message, NotificationVariant variant) {
			return onPermitDo((authzDecision, component) -> component.getUI().ifPresent(ui -> ui.access(() -> {
				Notification notification = Notification.show(message);
				notification.addThemeVariants(variant);
			})));
		}

		/**
		 * Add a callback to the internal decision listener list. The callback sends the
		 * defined Notification if a PERMIT decision occurs.
		 *
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onPermitNotify(String message) {
			return onPermitNotify(message, NotificationVariant.LUMO_SUCCESS);
		}

		/**
		 * This function shall return the current object (=this). Note: This function is
		 * used to prevent the unchecked typecast warning from most IDEs.
		 *
		 * @return Current object (=this)
		 */
		default T self() {
			@SuppressWarnings("unchecked")
			T self = (T) this;
			return self;
		}
	}

	/**
	 * This interface provides functions for components that implement the
	 * HasEnabled interface.
	 *
	 * @param <T> VaadinSingle*PepBuilder or VaadinMulti*PepBuilder (e.g.
	 *            VaadinMultiButtonBuilder)
	 * @param <C> Subclass of component (e.g. Button). Note: Should be consistent
	 *            with T
	 */
	public interface EnforceHasEnabled<T, C extends Component> extends VaadinPepBuilderBase<T, C> {
		/**
		 * This function shall add a callback to the internal decision listener list.
		 * The callback shall set the components enabled property depending on the
		 * decision. If a PERMIT decision occurs the component shall be enabled, if a
		 * DENY decision occurs the component shall be disabled.
		 *
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onDecisionEnableOrDisable() {
			return onDecisionDo((AuthorizationDecision authzDecision, C component) -> {
				final boolean accessGranted = authzDecision.getDecision() == Decision.PERMIT;
				component.getUI().ifPresent(ui -> ui.access(() -> ((HasEnabled) component).setEnabled(accessGranted)));
			});
		}
	}

	/**
	 * This interface provides functions for components that implement the HasText
	 * interface.
	 *
	 * @param <T> VaadinSingle*PepBuilder or VaadinMulti*PepBuilder (e.g.
	 *            VaadinMultiButtonBuilder)
	 * @param <C> Subclass of component (e.g. Button). Note: Should be consistent
	 *            with T
	 */
	public interface EnforceHasText<T, C extends Component & HasText> extends VaadinPepBuilderBase<T, C> {
		/**
		 * This function shall add a callback to the internal decision listener list.
		 * The callback shall set the components text property depending on the
		 * decision. If a PERMIT decision occurs the components text shall be set to the
		 * passed permit text, if a DENY decision occurs the components text shall be
		 * set to the passed deny text.
		 *
		 * @param permitText Text that will be used as the components text, if a PERMIT
		 *                   decision occurs.
		 * @param denyText   Text that will be used as the components text, if a DENY
		 *                   decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onDecisionSetText(String permitText, String denyText) {
			return onDecisionDo((AuthorizationDecision authzDecision, C component) -> {
				final boolean isPermit = authzDecision.getDecision() == Decision.PERMIT;
				if (isPermit) {
					component.getUI().ifPresent(ui -> ui.access(() -> component.setText(permitText)));
				} else {
					component.getUI().ifPresent(ui -> ui.access(() -> component.setText(denyText)));
				}
			});
		}

		/**
		 * This function shall add a callback to the internal decision listener list.
		 * The callback shall set the components text property, if a PERMIT decision
		 * occurs.
		 *
		 * @param permitText Text that will be used as the components text, if a PERMIT
		 *                   decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onPermitSetText(String permitText) {
			return onPermitDo((AuthorizationDecision authzDecision, C component)
					-> component.getUI().ifPresent(ui -> ui.access(() -> component.setText(permitText))));
		}

		/**
		 * This function shall add a callback to the internal decision listener list.
		 * The callback shall set the components text property, if a DENY decision
		 * occurs.
		 *
		 * @param denyText Text that will be used as the components text, if a DENY
		 *                 decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onDenySetText(String denyText) {
			return onDenyDo((AuthorizationDecision authzDecision, C component)
					-> component.getUI().ifPresent(ui -> ui.access(() -> component.setText(denyText))));
		}
	}

	/**
	 * This interface provides functions for components that implement the HasText
	 * interface.
	 *
	 * @param <T> VaadinSingle*PepBuilder or VaadinMulti*PepBuilder (e.g.
	 *            VaadinMultiButtonBuilder)
	 * @param <C> Subclass of component (e.g. Button). Note: Should be consistent
	 *            with T
	 */
	public interface EnforceHasValueAndElement<T, C extends Component & HasValueAndElement<?, ?>>
			extends VaadinPepBuilderBase<T, C> {

		/**
		 * This function shall add a callback to the internal decision listener list.
		 * The callback shall set the component to readonly if a DENY decision occurs.
		 *
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		default T onDecisionReadOnlyOrReadWrite() {
			return onDecisionDo((AuthorizationDecision authzDecision, C component) -> {
				final boolean isPermit = authzDecision.getDecision() == Decision.PERMIT;
				component.setReadOnly(!isPermit);
			});
		}
	}

	// **** Builder ****

	/**
	 * This class is the basic builder class used for the specific component
	 * builders.
	 *
	 * @param <T> VaadinSingle*PepBuilder or VaadinMulti*PepBuilder (e.g.
	 *            VaadinMultiButtonBuilder)
	 * @param <C> Subclass of component (e.g. Button). Note: Should be consistent
	 *            with T
	 */
	public abstract static class VaadinPepBuilder<T, C extends Component> implements VaadinPepBuilderBase<T, C> {
		protected final VaadinPep vaadinPep;
		protected final C component;
		protected boolean denyRuleIsPresent = false;

		protected VaadinPepBuilder(PolicyDecisionPoint pdp, EnforceConstraintsOfDecision enforceConstraintsOfDecision,
				C component) {
			vaadinPep = new VaadinPep(pdp, enforceConstraintsOfDecision);
			this.component = component;
		}

		/**
		 * The function shall set the action of the authorization subscription.
		 *
		 * @param action Action to be set
		 * @return Current object (=this)
		 */
		public T action(Object action) {
			vaadinPep.action = action;
			return self();
		}

		/**
		 * The function shall set the resource of the authorization subscription.
		 *
		 * @param resource Resource to be set
		 * @return Current object (=this)
		 */
		public T resource(Object resource) {
			vaadinPep.resource = resource;
			return self();
		}

		/**
		 * The function shall set the environment of the authorization subscription.
		 *
		 * @param environment Environment to be set
		 * @return Current object (=this)
		 */
		public T environment(Object environment) {
			vaadinPep.environment = environment;
			return self();
		}

		/**
		 * This function shall add the bi consumer to the internal decision listener
		 * list.
		 *
		 * @param biConsumer BiConsumer that is called when a decision occurs.
		 * @return Current object (=this)
		 */
		public T onDecisionDo(BiConsumer<AuthorizationDecision, C> biConsumer) {
			vaadinPep.decisionListenerList.add((authzDecision) -> biConsumer.accept(authzDecision, component));
			denyRuleIsPresent = true;
			return self();
		}

		/**
		 * This function shall add the bi consumer to the internal decision listener
		 * list.
		 *
		 * @param biConsumer BiConsumer that is called when a PERMIT decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		public T onPermitDo(BiConsumer<AuthorizationDecision, C> biConsumer) {
			vaadinPep.decisionListenerList.add((authzDecision) -> {
				if (authzDecision.getDecision() == Decision.PERMIT) {
					biConsumer.accept(authzDecision, component);
				}
			});
			return self();
		}

		/**
		 * This function shall add the bi consumer to the internal decision listener
		 * list.
		 *
		 * @param biConsumer BiConsumer that is called when a DENY decision occurs.
		 * @return VaadinSingle*PepBuilder or VaadinMulti*PepBuilder
		 */
		public T onDenyDo(BiConsumer<AuthorizationDecision, C> biConsumer) {
			vaadinPep.decisionListenerList.add((authzDecision) -> {
				if (authzDecision.getDecision() == Decision.DENY) {
					biConsumer.accept(authzDecision, component);
				}
			});
			denyRuleIsPresent = true;
			return self();
		}

		/**
		 * Adds the function constraint handler provider to the local vaadin function
		 * provider list
		 * 
		 * @param provider Provider to add
		 * @return Current object (=this)
		 */
		public T addVaadinFunctionConstraintHandlerProvider(VaadinFunctionConstraintHandlerProvider provider) {
			vaadinPep.localVaadinFunctionProvider.add(provider);
			return self();
		}

		/**
		 * Adds the consumer constraint handler provider to the local consumer provider
		 * list
		 * 
		 * @param provider Provider to add
		 * @return Current object (=this)
		 */
		public T addConsumerConstraintHandlerProvider(ConsumerConstraintHandlerProvider<UI> provider) {
			vaadinPep.localConsumerProviders.add(provider);
			return self();
		}

		/**
		 * Adds the runnable constraint handler provider to the local runnable provider
		 * list
		 * 
		 * @param provider Provider to add
		 * @return Current object (=this)
		 */
		public T addRunnableConstraintHandlerProvider(RunnableConstraintHandlerProvider provider) {
			vaadinPep.localRunnableProviders.add(provider);
			return self();
		}

		/**
		 * Adds a constraint handler provider to the local consumer provider list
		 *
		 * @param isResponsible predicate of JsonNode to determine the constraint the handler is responsible for
		 * @param getHandler function that returns a Consumer handler for a specific constraint
		 * @return Current object (=this)
		 */
		public T addConstraintHandler(Predicate<JsonNode> isResponsible, Function<JsonNode, Consumer<UI>> getHandler) {
			vaadinPep.localConsumerProviders.add(new ConsumerConstraintHandlerProvider<>() {
				@Override
				public Class<UI> getSupportedType() {
					return UI.class;
				}

				@Override
				public boolean isResponsible(JsonNode constraint) {
					return isResponsible.test(constraint);
				}

				@Override
				public Consumer<UI> getHandler(JsonNode constraint) {
					return getHandler.apply(constraint);
				}
			});
			return self();
		}
	}

	// **** Single / Multi builder ****

	/**
	 * This class is the basic single builder class for the specific component
	 * single builders.
	 *
	 * @param <T> VaadinSingle*PepBuilder (e.g. VaadinSingleButtonBuilder)
	 * @param <C> Subclass of component (e.g. Button). Note: Should be consistent
	 *            with T
	 */
	public abstract static class VaadinSinglePepBuilder<T, C extends Component> extends VaadinPepBuilder<T, C> {
		protected boolean isBuild = false;

		protected VaadinSinglePepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision, C component) {
			super(pdp, enforceConstraintsOfDecision, component);
		}

		/**
		 * The function shall set the subject of the authorization subscription.
		 *
		 * @param subject Subject to be set
		 * @return Current object (=this)
		 */
		public T subject(Object subject) {
			vaadinPep.subject = subject;
			return self();
		}

		/**
		 * This function shall start the single subscription. If build() has been called
		 * previously, an AccessDeniedException shall be thrown.
		 *
		 * @return The original vaadin component
		 */
		public C build() {
			if (!isBuild) {
				// ensure that at least one handler is present for DENY decisions
				if (!denyRuleIsPresent) {
					throw new AccessDeniedException("You need to define at least one handler for DENY decisions.");
				}
				component.addDetachListener((__) -> vaadinPep.stopSubscription());
				// start subscription now or later
				if (component.isAttached()) {
					Optional<UI> optionalUI = component.getUI();
					if (optionalUI.isPresent()) {
						vaadinPep.enforce(optionalUI.get());
					} else {
						throw new AccessDeniedException("Unable to start subscription, UI is not present");
					}
				} else {
					component.addAttachListener((e) -> {
						Optional<UI> optionalUI = component.getUI();
						if (optionalUI.isPresent()) {
							vaadinPep.enforce(optionalUI.get());
						} else {
							throw new AccessDeniedException("Unable to start subscription, UI is not present");
						}
					});
				}
				isBuild = true;
				return component;
			} else {
				throw new AccessDeniedException("Builder has already been build. The builder can only be used once.");
			}
		}

	}

	/**
	 * This class is the basic multi builder class for the specific component multi
	 * builders.
	 *
	 * @param <T> VaadinMulti*PepBuilder (e.g. VaadinMultiButtonBuilder)
	 * @param <C> Subclass of component (e.g. Button). Note: Should be consistent
	 *            with T
	 */
	public abstract static class VaadinMultiPepBuilder<T, C extends Component> extends VaadinPepBuilder<T, C> {
		private final MultiBuilder multiBuilder;
		private boolean isBuild = false;

		protected VaadinMultiPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision, MultiBuilder multiBuilder, C component) {
			super(pdp, enforceConstraintsOfDecision, component);
			this.multiBuilder = multiBuilder;
		}

		/**
		 * This function shall register the current subscription and return the multi
		 * builder instance for further multi subscription configuration. If build() has
		 * been called previously, an AccessDeniedException is thrown.
		 *
		 * @return Current multi builder object
		 */
		public MultiBuilder and() {
			if (!isBuild) {
				// ensure that at least one handler is present for DENY decisions
				if (!denyRuleIsPresent) {
					throw new AccessDeniedException("You need to define at least one handler for DENY decisions.");
				}
				multiBuilder.registerPep(vaadinPep);
				isBuild = true;
				return multiBuilder;
			} else {
				throw new AccessDeniedException("Builder has already been build. The builder can only be used once.");
			}
		}

		/**
		 * This function shall build the multi subscription. If build() has been called
		 * previously, an AccessDeniedException is thrown.
		 */
		public void build() {
			and().build(component);
		}

		// **** Component specific methods ****

		/**
		 * This function shall register the previous configured component and start
		 * configuring a new subscription for the component.
		 *
		 * @param component Vaadin component to be linked with the subscription
		 * @return New multi component pep builder
		 */
		public VaadinMultiComponentPepBuilder and(Component component) {
			return and().with(component);
		}

		/**
		 * This function shall register the previous configured component and start
		 * configuring a new subscription for the button.
		 *
		 * @param button Vaadin button to be linked with the subscription
		 * @return New multi button pep builder
		 */
		public VaadinMultiButtonPepBuilder and(Button button) {
			return and().with(button);
		}

		/**
		 * This function shall register the previous configured component and start
		 * configuring a new subscription for the text field.
		 *
		 * @param textfield Vaadin text field to be linked with the subscription
		 * @return New multi text field pep builder
		 */
		public VaadinMultiTextFieldPepBuilder and(TextField textfield) {
			return and().with(textfield);
		}

		/**
		 * This function shall register the previous configured component and start
		 * configuring a new subscription for the checkbox.
		 *
		 * @param checkbox Vaadin checkbox to be linked with the subscription
		 * @return New multi checkbox pep builder
		 */
		public VaadinMultiCheckboxPepBuilder and(Checkbox checkbox) {
			return and().with(checkbox);
		}

		/**
		 * This function shall register the previous configured component and start
		 * configuring a new subscription for the span.
		 *
		 * @param span Vaadin span to be linked with the subscription
		 * @return New multi span pep builder
		 */
		public VaadinMultiSpanPepBuilder and(Span span) {
			return and().with(span);
		}
	}

	// **** Component builder ****

	/**
	 * This class is a builder class for single subscriptions with Vaadin components
	 */
	public static class VaadinSingleComponentPepBuilder
			extends VaadinSinglePepBuilder<VaadinSingleComponentPepBuilder, Component>
			implements EnforceHasEnabled<VaadinSingleComponentPepBuilder, Component> {
		protected VaadinSingleComponentPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision, Component component) {
			super(pdp, enforceConstraintsOfDecision, component);
		}
	}

	/**
	 * This class is a builder class for multi subscriptions with Vaadin components
	 */
	public static class VaadinMultiComponentPepBuilder
			extends VaadinMultiPepBuilder<VaadinMultiComponentPepBuilder, Component>
			implements EnforceHasEnabled<VaadinMultiComponentPepBuilder, Component> {
		protected VaadinMultiComponentPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision, MultiBuilder multiBuilder,
				Component component) {
			super(pdp, enforceConstraintsOfDecision, multiBuilder, component);
		}
	}

	// **** Button builder ****

	/**
	 * This class is a builder class for single subscriptions with Vaadin buttons
	 */
	public static class VaadinSingleButtonPepBuilder
			extends VaadinSinglePepBuilder<VaadinSingleButtonPepBuilder, Button>
			implements EnforceHasEnabled<VaadinSingleButtonPepBuilder, Button>,
			EnforceHasText<VaadinSingleButtonPepBuilder, Button> {
		protected VaadinSingleButtonPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision, Button button) {
			super(pdp, enforceConstraintsOfDecision, button);
		}
	}

	/**
	 * This class is a builder class for multi subscriptions with Vaadin buttons
	 */
	public static class VaadinMultiButtonPepBuilder extends VaadinMultiPepBuilder<VaadinMultiButtonPepBuilder, Button>
			implements EnforceHasEnabled<VaadinMultiButtonPepBuilder, Button>,
			EnforceHasText<VaadinMultiButtonPepBuilder, Button> {
		protected VaadinMultiButtonPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision, MultiBuilder multiBuilder, Button button) {
			super(pdp, enforceConstraintsOfDecision, multiBuilder, button);
		}
	}

	// **** TextField builder ****

	/**
	 * This class is a builder class for single subscriptions with Vaadin text
	 * fields
	 */
	public static class VaadinSingleTextFieldPepBuilder
			extends VaadinSinglePepBuilder<VaadinSingleTextFieldPepBuilder, TextField>
			implements EnforceHasEnabled<VaadinSingleTextFieldPepBuilder, TextField>,
			EnforceHasValueAndElement<VaadinSingleTextFieldPepBuilder, TextField> {
		protected VaadinSingleTextFieldPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision, TextField textField) {
			super(pdp, enforceConstraintsOfDecision, textField);
		}
	}

	/**
	 * This class is a builder class for multi subscriptions with Vaadin text fields
	 */
	public static class VaadinMultiTextFieldPepBuilder
			extends VaadinMultiPepBuilder<VaadinMultiTextFieldPepBuilder, TextField>
			implements EnforceHasEnabled<VaadinMultiTextFieldPepBuilder, TextField>,
			EnforceHasValueAndElement<VaadinMultiTextFieldPepBuilder, TextField> {
		protected VaadinMultiTextFieldPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision, MultiBuilder multiBuilder,
				TextField textField) {
			super(pdp, enforceConstraintsOfDecision, multiBuilder, textField);
		}
	}

	// **** Checkbox builder ****

	/**
	 * This class is a builder class for single subscriptions with Vaadin checkboxes
	 */
	public static class VaadinSingleCheckboxPepBuilder
			extends VaadinSinglePepBuilder<VaadinSingleCheckboxPepBuilder, Checkbox>
			implements EnforceHasEnabled<VaadinSingleCheckboxPepBuilder, Checkbox> {
		protected VaadinSingleCheckboxPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision, Checkbox checkbox) {
			super(pdp, enforceConstraintsOfDecision, checkbox);
		}
	}

	/**
	 * This class is a builder class for multi subscriptions with Vaadin checkboxes
	 */
	public static class VaadinMultiCheckboxPepBuilder
			extends VaadinMultiPepBuilder<VaadinMultiCheckboxPepBuilder, Checkbox>
			implements EnforceHasEnabled<VaadinMultiCheckboxPepBuilder, Checkbox> {
		protected VaadinMultiCheckboxPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision, MultiBuilder multiBuilder,
				Checkbox checkbox) {
			super(pdp, enforceConstraintsOfDecision, multiBuilder, checkbox);
		}
	}

	// **** Span builder ****

	/**
	 * This class is a builder class for single subscriptions with Vaadin spans
	 */
	public static class VaadinSingleSpanPepBuilder extends VaadinSinglePepBuilder<VaadinSingleSpanPepBuilder, Span>
			implements EnforceHasEnabled<VaadinSingleSpanPepBuilder, Span>,
			EnforceHasText<VaadinSingleSpanPepBuilder, Span> {
		protected VaadinSingleSpanPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision, Span span) {
			super(pdp, enforceConstraintsOfDecision, span);
		}
	}

	/**
	 * This class is a builder class for multi subscriptions with Vaadin spans
	 */
	public static class VaadinMultiSpanPepBuilder extends VaadinMultiPepBuilder<VaadinMultiSpanPepBuilder, Span>
			implements EnforceHasEnabled<VaadinMultiSpanPepBuilder, Span>,
			EnforceHasText<VaadinMultiSpanPepBuilder, Span> {
		protected VaadinMultiSpanPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision, MultiBuilder multiBuilder, Span span) {
			super(pdp, enforceConstraintsOfDecision, multiBuilder, span);
		}
	}

	// **** Lifecycle builder ****
	/**
	 * The builder class for the pep which handles events of the navigation
	 * lifecycle. It is the parent class for {@link LifecycleBeforeEnterPepBuilder}
	 * and {@link LifecycleBeforeLeavePepBuilder}.
	 *
	 * @param <L> {@link LifecycleBeforeEnterPepBuilder} or
	 *            {@link LifecycleBeforeLeavePepBuilder}
	 */
	public static class LifecycleEventHandlerPepBuilder<L> {
		final VaadinPep vaadinPep;

		/**
		 * The constructor sets the subject of the subscription.
		 */
		public LifecycleEventHandlerPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision) {
			vaadinPep = new VaadinPep(pdp, enforceConstraintsOfDecision);
			setDefaultSubject();
		}

		/**
		 * This method returns the instance of this LifecycleEventHandlerPepBuilderV2.
		 * 
		 * @return The current instance of this LifecycleEventHandlerPepBuilderV2.
		 */
		private L self() {
			@SuppressWarnings("unchecked")
			L self = (L) this;
			return self;
		}

		/**
		 * This method automatically fills the vaadinPep-subject with information like
		 * the username and the user roles
		 */
		private void setDefaultSubject() {
			JsonNodeFactory JSON = JsonNodeFactory.instance;
			var subject = JSON.objectNode();
			subject.put("username", getUsername());
			var rolesNode = JSON.arrayNode();
			for (String role : getUserRoles()) {
				rolesNode.add(role);
			}
			subject.set("roles", rolesNode);
			vaadinPep.subject = subject;
		}

		/**
		 * Setter for the subject of the subscription.
		 *
		 * @param subject subject of the subscription
		 * @return Returns an instance of {@link LifecycleBeforeEnterPepBuilder} or
		 *         {@link LifecycleBeforeLeavePepBuilder}
		 */
		public L subject(Object subject) {
			vaadinPep.subject = subject;
			return self();
		}

		/**
		 * Setter for the action of the subscription.
		 *
		 * @param action subject of the subscription
		 * @return Returns an instance of {@link LifecycleBeforeEnterPepBuilder} or
		 *         {@link LifecycleBeforeLeavePepBuilder}
		 */
		public L action(Object action) {
			vaadinPep.action = action;
			return self();
		}

		/**
		 * Setter for the resource of the subscription.
		 *
		 * @param resource resource of the subscription
		 * @return Returns an instance of {@link LifecycleBeforeEnterPepBuilder} or
		 *         {@link LifecycleBeforeLeavePepBuilder}
		 */
		public L resource(Object resource) {
			vaadinPep.resource = resource;
			return self();
		}

		/**
		 * Setter for the environment of the subscription.
		 *
		 * @param environment subject of the subscription
		 * @return Returns an instance of {@link LifecycleBeforeEnterPepBuilder} or
		 *         {@link LifecycleBeforeLeavePepBuilder}
		 */
		public L environment(Object environment) {
			vaadinPep.environment = environment;
			return self();
		}

		/**
		 * Calls the onDenyDo method with a {@link BiConsumer} that reroutes the user to
		 * a specified target. The routing keeps the original URL in the browser’s
		 * address bar and doesn't change it to a new URL based on the new target.
		 *
		 * @param navigationTarget The target the user is rerouted to.
		 * @return The current instance of this LifecycleEventHandlerPepBuilderV2.
		 */
		public L onDenyRerouteTo(String navigationTarget) {
			onDenyDo((decision, event) -> event.rerouteTo(navigationTarget));
			return self();
		}

		/**
		 * Calls the onDenyDo method with a {@link BiConsumer} that reroutes the user to
		 * a specified target. The forwarding navigates to the target and updates the
		 * browser URL.
		 *
		 * @param navigationTarget The target the user is redirected to.
		 * @return The current instance of this LifecycleEventHandlerPepBuilderV2.
		 */
		public L onDenyRedirectTo(String navigationTarget) {
			onDenyDo((decision, event) -> event.forwardTo(navigationTarget));
			return self();
		}

		/**
		 * Calls the onDenyDo method with a {@link BiConsumer} that logs out the current
		 * user. If the onDenyLogout method is to be used for lifecycle handling, an
		 * onDenyRerouteTo/onDenyRedirectTo may have to be called first, since Vaadin
		 * caches the last page visited that triggers again a direct logout when logging
		 * in.
		 *
		 * @return The current instance of this LifecycleEventHandlerPepBuilderV2.
		 */
		public L onDenyLogout() {
			onDenyDo((decision, event) -> logout());
			return self();
		}

		/**
		 * This method handles the logout process.
		 */
		private void logout() {
			SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();
			logoutHandler.logout(VaadinServletRequest.getCurrent().getHttpServletRequest(), null, null);
		}

		/**
		 * Calls the onDenyDo method with a {@link BiConsumer} that notifies the user
		 * with the specified message.
		 *
		 * @param message The message the user should see as a notification.
		 * @return The current instance of this LifecycleEventHandlerPepBuilderV2.
		 */
		public L onDenyNotify(String message) {
			onDenyDo((decision, event) -> event.getUI().access(() -> {
				Notification notification = Notification.show(message);
				notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
			}));
			return self();
		}

		/**
		 * Calls the method onDenyNotify with the message "You are not authorized!"
		 *
		 * @return The current instance of this LifecycleEventHandlerPepBuilderV2.
		 */
		public L onDenyNotify() {
			return onDenyNotify("You are not authorized!");
		}

		public L onPermitDo(BiConsumer<AuthorizationDecision, BeforeEvent> action) {
			this.onDecisionDo((decision, event) -> {
				if (decision.getDecision() == Decision.PERMIT) {
					action.accept(decision, event);
				}
			});
			return self();
		}

		/**
		 * This method lets you set a custom {@link BiConsumer} which is executed when
		 * the decision is 'DENY'.
		 *
		 * @param action A custom {@link BiConsumer} to be executed
		 */
		public L onDenyDo(BiConsumer<AuthorizationDecision, BeforeEvent> action) {
			this.onDecisionDo((decision, event) -> {
				if (decision.getDecision() != Decision.PERMIT) {
					action.accept(decision, event);
				}
			});
			return self();
		}

		/**
		 * Adds a {@link BiConsumer} to the decisionEventListenerList.
		 *
		 * @param action A custom {@link BiConsumer} to be executed. This BiConsumer
		 *               must be able to handle the decisions 'DENY' and 'PERMIT'.
		 */
		public L onDecisionDo(BiConsumer<AuthorizationDecision, BeforeEvent> action) {
			vaadinPep.decisionEventListenerList.add(action);
			return self();
		}

		/**
		 * Sets the resource for the subscription to the current navigation target.
		 *
		 * @param event The {@link BeforeEnterEvent} of the current navigation
		 *              lifecycle.
		 */
		protected void setResourceByNavigationTargetIfNotDefined(BeforeEvent event) {
			if (vaadinPep.resource == null) {
				JsonNodeFactory JSON = JsonNodeFactory.instance;
				var resource = JSON.objectNode();
				resource.put("target", event.getNavigationTarget().getName());
				vaadinPep.resource = resource;
			}
		}
	}

	/**
	 * The builder class for the pep which handles {@link BeforeEnterEvent} of the
	 * navigation lifecycle.
	 */
	public static class LifecycleBeforeEnterPepBuilder
			extends LifecycleEventHandlerPepBuilder<LifecycleBeforeEnterPepBuilder> {

		protected boolean isBuild = false;

		/**
		 * This constructor method sets the action of the subscription.
		 */
		public LifecycleBeforeEnterPepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision) {
			super(pdp, enforceConstraintsOfDecision);
			action("enter");
		}

		/**
		 * The method receives an {@link BeforeEnterEvent} and starts a subscription if there is at least one handler.
		 * 
		 * @param event {@link BeforeEnterEvent} from the UI
		 */
		private void beforeEnter(BeforeEnterEvent event) {
			if (!vaadinPep.decisionEventListenerList.isEmpty()) {
				setResourceByNavigationTargetIfNotDefined(event);
				vaadinPep.enforceOnce(event.getUI(), event);
			}
		}

		/**
		 * The method checks if there has been a previous build()-call. If build() has
		 * not been called previously, the {@link BeforeEnterListener} is returned. If
		 * build() has been called previously, an {@link AccessDeniedException} shall be
		 * thrown.
		 * 
		 * @return {@link BeforeEnterListener}
		 */
		public BeforeEnterListener build() {
			if (!isBuild) {
				isBuild = true;
				return this::beforeEnter;
			} else {
				throw new AccessDeniedException("Builder has already been build. The builder can only be used once.");
			}
		}
	}

	/**
	 * The builder class for the pep which handles {@link BeforeLeaveEvent} of the
	 * navigation lifecycle.
	 */
	public static class LifecycleBeforeLeavePepBuilder
			extends LifecycleEventHandlerPepBuilder<LifecycleBeforeLeavePepBuilder> {

		protected boolean isBuild = false;

		/**
		 * This constructor method sets the action of the subscription.
		 */
		protected LifecycleBeforeLeavePepBuilder(PolicyDecisionPoint pdp,
				EnforceConstraintsOfDecision enforceConstraintsOfDecision) {
			super(pdp, enforceConstraintsOfDecision);
			action("leave");
		}

		/**
		 * The method receives an {@link BeforeLeaveEvent} and starts a subscription if
		 * there is at least one handler.
		 * 
		 * @param event {@link BeforeLeaveEvent} from the UI
		 */
		private void beforeLeave(BeforeLeaveEvent event) {
			if (!vaadinPep.decisionEventListenerList.isEmpty()) {
				setResourceByNavigationTargetIfNotDefined(event);
				vaadinPep.enforceOnce(event.getUI(), event);
			}
		}

		/**
		 * The method checks if there has been a previous build()-call. If build() has
		 * not been called previously, the {@link BeforeLeaveListener} is returned. If
		 * build() has been called previously, an {@link AccessDeniedException} shall be
		 * thrown.
		 *
		 * @return {@link BeforeLeaveListener}
		 */
		public BeforeLeaveListener build() {
			if (!isBuild) {
				isBuild = true;
				return this::beforeLeave;
			} else {
				throw new AccessDeniedException("Builder has already been build. The builder can only be used once.");
			}
		}
	}
}
