package io.sapl.spring.subscriptions;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.authorization.AuthorizationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.spring.method.metadata.SaplAttribute;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * This class contains the logic for SpEL expression evaluation and retrieving
 * request information from the application context or method invocation.
 */
@Service
@RequiredArgsConstructor
public class AuthorizationSubscriptionBuilderService {
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("key", "anonymous",
			AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

	private final MethodSecurityExpressionHandler expressionHandler;
	protected final ObjectFactory<ObjectMapper> objectMapperFactory;
	private ObjectMapper mapper;

	public AuthorizationSubscription constructAuthorizationSubscriptionWithReturnObject(Authentication authentication,
			MethodInvocation methodInvocation, SaplAttribute attribute, Object returnObject) {
		var evaluationCtx = expressionHandler.createEvaluationContext(authentication, methodInvocation);
		expressionHandler.setReturnObject(returnObject, evaluationCtx);
		return constructAuthorizationSubscription(authentication, methodInvocation, attribute, evaluationCtx);
	}

	public AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
			MethodInvocation methodInvocation, SaplAttribute attribute) {
		var evaluationCtx = expressionHandler.createEvaluationContext(authentication, methodInvocation);
		return constructAuthorizationSubscription(authentication, methodInvocation, attribute, evaluationCtx);
	}

	public Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(MethodInvocation methodInvocation,
			SaplAttribute attribute) {
		return Mono.deferContextual(contextView -> {
			return constructAuthorizationSubscriptionFromContextView(methodInvocation, attribute, contextView,
					Optional.empty());
		});
	}

	public Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(
			Mono<Authentication> authentication, @NonNull AuthorizationContext context) {
		lazyLoadDependencies();
		var request = context.getExchange().getRequest();
		return authentication.defaultIfEmpty(ANONYMOUS)
				.map(authn -> AuthorizationSubscription.of(authn, request, request, mapper));
	}

	public Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(MethodInvocation methodInvocation,
			SaplAttribute attribute, Object returnedObject) {
		return Mono.deferContextual(contextView -> {
			return constructAuthorizationSubscriptionFromContextView(methodInvocation, attribute, contextView,
					Optional.ofNullable(returnedObject));
		});
	}

	private Mono<? extends AuthorizationSubscription> constructAuthorizationSubscriptionFromContextView(
			MethodInvocation methodInvocation, SaplAttribute attribute, ContextView contextView,
			Optional<Object> returnedObject) {
		Optional<ServerWebExchange> serverWebExchange = contextView.getOrEmpty(ServerWebExchange.class);
		Optional<ServerHttpRequest> serverHttpRequest = serverWebExchange.map(ServerWebExchange::getRequest);
		Optional<Mono<SecurityContext>> securityContext = contextView.getOrEmpty(SecurityContext.class);
		Mono<Authentication> authentication = securityContext
				.map(ctx -> ctx.map(SecurityContext::getAuthentication).defaultIfEmpty(ANONYMOUS))
				.orElseGet(() -> Mono.just(ANONYMOUS));
		return authentication.map(authn -> constructAuthorizationSubscription(authn, serverHttpRequest,
				methodInvocation, attribute, returnedObject));
	}

	private AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
			Optional<ServerHttpRequest> serverHttpRequest, MethodInvocation methodInvocation, SaplAttribute attribute,
			Optional<Object> returnedObject) {
		lazyLoadDependencies();

		var evaluationCtx = expressionHandler.createEvaluationContext(authentication, methodInvocation);
		returnedObject.ifPresent(returnObject -> expressionHandler.setReturnObject(returnObject, evaluationCtx));

		var subject = retrieveSubject(authentication, attribute, evaluationCtx);
		var action = retrieveAction(methodInvocation, attribute, evaluationCtx, serverHttpRequest);
		var resource = retrieveResource(methodInvocation, attribute, evaluationCtx);
		var environment = retrieveEnvironment(attribute, evaluationCtx);
		return new AuthorizationSubscription(mapper.valueToTree(subject), mapper.valueToTree(action),
				mapper.valueToTree(resource), mapper.valueToTree(environment));
	}

	private AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
			MethodInvocation methodInvocation, SaplAttribute attribute, EvaluationContext evaluationCtx) {
		lazyLoadDependencies();

		var subject = retrieveSubject(authentication, attribute, evaluationCtx);
		var action = retrieveAction(methodInvocation, attribute, evaluationCtx, retrieveRequestObject());
		var resource = retrieveResource(methodInvocation, attribute, evaluationCtx);
		var environment = retrieveEnvironment(attribute, evaluationCtx);
		return new AuthorizationSubscription(mapper.valueToTree(subject), mapper.valueToTree(action),
				mapper.valueToTree(resource), mapper.valueToTree(environment));
	}

	private void lazyLoadDependencies() {
		if (mapper == null)
			mapper = objectMapperFactory.getObject();
	}

	private JsonNode retrieveSubject(Authentication authentication, SaplAttribute attr, EvaluationContext ctx) {
		if (attr.getSubjectExpression() != null)
			return evaluateToJson(attr.getSubjectExpression(), ctx);

		ObjectNode subject = mapper.valueToTree(authentication);

		// sanitize the authentication depending on the application context, the
		// authentication may still contain credentials information, which should not be
		// send over the wire to the PDP

		subject.remove("credentials");
		var principal = subject.get("principal");
		if (principal instanceof ObjectNode)
			((ObjectNode) principal).remove("password");

		return subject;
	}

	private JsonNode evaluateToJson(Expression expr, EvaluationContext ctx) {
		try {
			return mapper.valueToTree(expr.getValue(ctx));
		} catch (EvaluationException e) {
			throw new IllegalArgumentException("Failed to evaluate expression '" + expr.getExpressionString() + "'", e);
		}
	}

	private static JsonNode serializeTargetClassDescription(Class<?> clazz) {
		var result = JSON.objectNode();
		result.set("name", JSON.textNode(clazz.getName()));
		result.set("canonicalName", JSON.textNode(clazz.getCanonicalName()));
		result.set("typeName", JSON.textNode(clazz.getTypeName()));
		result.set("simpleName", JSON.textNode(clazz.getSimpleName()));
		return result;
	}

	private static Optional<HttpServletRequest> retrieveRequestObject() {
		var requestAttributes = RequestContextHolder.getRequestAttributes();
		var httpRequest = requestAttributes != null ? ((ServletRequestAttributes) requestAttributes).getRequest()
				: null;
		return Optional.ofNullable(httpRequest);
	}

	private Object retrieveAction(MethodInvocation mi, SaplAttribute attr, EvaluationContext ctx,
			Optional<?> requestObject) {
		if (attr.getActionExpression() == null)
			return retrieveAction(mi, requestObject);
		return evaluateToJson(attr.getActionExpression(), ctx);
	}

	private Object retrieveAction(MethodInvocation mi, Optional<?> requestObject) {
		var actionNode = mapper.createObjectNode();
		requestObject.ifPresent(request -> actionNode.set("http", mapper.valueToTree(request)));
		var java = (ObjectNode) mapper.valueToTree(mi);
		var arguments = mi.getArguments();
		if (arguments.length > 0) {
			var array = JSON.arrayNode();
			for (Object o : arguments) {
				try {
					array.add(mapper.valueToTree(o));
				} catch (IllegalArgumentException e) {
					// drop of not mappable to JSON
				}
			}
			if (array.size() > 0)
				java.set("arguments", array);
		}
		actionNode.set("java", java);
		return actionNode;
	}

	private Object retrieveResource(MethodInvocation mi, SaplAttribute attr, EvaluationContext ctx) {
		if (attr.getResourceExpression() == null)
			return retrieveResource(mi);
		return evaluateToJson(attr.getResourceExpression(), ctx);
	}

	private Object retrieveResource(MethodInvocation mi) {
		var resourceNode = mapper.createObjectNode();
		var httpServletRequest = retrieveRequestObject();
		// The action is in the context of a HTTP request. Adding it to the resource.
		httpServletRequest.ifPresent(servletRequest -> resourceNode.set("http", mapper.valueToTree(servletRequest)));
		var target = serializeTargetClassDescription(mi.getThis().getClass());
		resourceNode.set("targetClass", target);
		return resourceNode;
	}

	private Object retrieveEnvironment(SaplAttribute attr, EvaluationContext ctx) {
		if (attr.getEnvironmentExpression() == null)
			return null;
		return evaluateToJson(attr.getEnvironmentExpression(), ctx);
	}

}
