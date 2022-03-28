package io.sapl.vaadin.annotation;

import static io.sapl.vaadin.base.VaadinAuthorizationSubscriptionBuilderService.serializeTargetClassDescription;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.BeforeLeaveEvent;

import io.sapl.vaadin.PepBuilderService;
import io.sapl.vaadin.VaadinPep;
import io.sapl.vaadin.annotation.annotations.OnDenyNavigate;
import io.sapl.vaadin.base.VaadinAuthorizationSubscriptionBuilderService;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VaadinNavigationPepService {

	private final PepBuilderService pepBuilderService;
	private final VaadinAuthorizationSubscriptionBuilderService authorizationSubscriptionBuilderService;

	public enum NavigationType {
		REDIRECT,
		REROUTE
	}

	public enum LifecycleType {
		LEAVE,
		ENTER,
		BOTH
	}

	public void beforeEnter(BeforeEnterEvent beforeEnterEvent) {
		OnDenyNavigate[] onDenyNavigateAnnotations = beforeEnterEvent.getNavigationTarget()
				.getAnnotationsByType(OnDenyNavigate.class);

		if (onDenyNavigateAnnotations.length == 1
				&& ( onDenyNavigateAnnotations[0].onLifecycleEvent() == LifecycleType.ENTER
				  || onDenyNavigateAnnotations[0].onLifecycleEvent() == LifecycleType.BOTH)) {

			var beforeEnterBuilder = pepBuilderService.getLifecycleBeforeEnterPepBuilder();
			addAnnotationInformationToBuilder(beforeEnterBuilder, onDenyNavigateAnnotations[0], beforeEnterEvent);
			beforeEnterBuilder
				.build()
				.beforeEnter(beforeEnterEvent);
		}
	}

	public void beforeLeave(BeforeLeaveEvent beforeLeaveEvent) {
		OnDenyNavigate[] onDenyNavigateAnnotations = beforeLeaveEvent.getNavigationTarget()
				.getAnnotationsByType(OnDenyNavigate.class);

		if (onDenyNavigateAnnotations.length == 1
				&& ( onDenyNavigateAnnotations[0].onLifecycleEvent() == LifecycleType.LEAVE
				  || onDenyNavigateAnnotations[0].onLifecycleEvent() == LifecycleType.BOTH)) {

			var beforeLeaveBuilder = pepBuilderService.getLifecycleBeforeLeavePepBuilder();
			addAnnotationInformationToBuilder(beforeLeaveBuilder, onDenyNavigateAnnotations[0], beforeLeaveEvent);
			beforeLeaveBuilder
					.build()
					.beforeLeave(beforeLeaveEvent);
		}
	}


	private void addAnnotationInformationToBuilder(VaadinPep.LifecycleEventHandlerPepBuilder<?> builder,
												   OnDenyNavigate onDenyNavigateAnnotation,
												   BeforeEvent beforeEvent) {

		// evaluate subject expression from annotation against authentication and add to builder
		String subjectExpression = onDenyNavigateAnnotation.subject();
		var authentication = SecurityContextHolder.getContext().getAuthentication();
		JsonNode subject = authorizationSubscriptionBuilderService.retrieveSubject(authentication, subjectExpression);
		builder.subject(subject);

		// add action
		String actionExpression = onDenyNavigateAnnotation.action();
		if ("".equals(actionExpression)) {
			builder.action("navigate_to");
		} else {
			var action = authorizationSubscriptionBuilderService.evaluateExpressionStringToJson(actionExpression, null);
			builder.action(action);
		}

		// add resource
		// evaluate resource expression from annotation against target class metadata and add to builder
		String resourceExpression = onDenyNavigateAnnotation.resource();
		var targetClassDescription = serializeTargetClassDescription(beforeEvent.getNavigationTarget());
		if ("".equals(resourceExpression)) {
			builder.resource(targetClassDescription);
		} else {
			var resource = authorizationSubscriptionBuilderService.evaluateExpressionStringToJson(
					resourceExpression,
					targetClassDescription
			);
			builder.resource(resource);
		}

		// add environment
		// only add environment if defined in the annotation
		String environmentExpression = onDenyNavigateAnnotation.environment();
		if (!"".equals(environmentExpression)) {
			builder.environment(
					authorizationSubscriptionBuilderService.evaluateExpressionStringToJson(environmentExpression, null)
			);
		}

		if (onDenyNavigateAnnotation.navigation() == NavigationType.REDIRECT) {
			builder.onDenyRedirectTo(onDenyNavigateAnnotation.value());

		}
		if (onDenyNavigateAnnotation.navigation() == NavigationType.REROUTE) {
			builder.onDenyRerouteTo(onDenyNavigateAnnotation.value());
		}
	}
}
