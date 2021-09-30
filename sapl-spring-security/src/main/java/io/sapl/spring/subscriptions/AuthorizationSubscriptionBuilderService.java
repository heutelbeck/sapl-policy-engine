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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ServerWebExchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.spring.method.attributes.EnforcementAttribute;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * This class contains the logic for SpEL expression evaluation and retrieving
 * request information from the application context or method invocation.
 */
@Slf4j
@RequiredArgsConstructor
public class AuthorizationSubscriptionBuilderService {
	private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
	private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("key", "anonymous",
			AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

	private final MethodSecurityExpressionHandler expressionHandler;
	protected final ObjectFactory<ObjectMapper> objectMapperFactory;
	private ObjectMapper mapper;

	public AuthorizationSubscription constructAuthorizationSubscriptionWithReturnObject(Authentication authentication,
			MethodInvocation methodInvocation, EnforcementAttribute attribute, Object returnObject) {
		var evaluationCtx = expressionHandler.createEvaluationContext(authentication, methodInvocation);
		expressionHandler.setReturnObject(returnObject, evaluationCtx);
		return constructAuthorizationSubscription(authentication, methodInvocation, attribute, evaluationCtx);
	}

	public AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
			MethodInvocation methodInvocation, EnforcementAttribute attribute) {
		var evaluationCtx = expressionHandler.createEvaluationContext(authentication, methodInvocation);
		return constructAuthorizationSubscription(authentication, methodInvocation, attribute, evaluationCtx);
	}

	public Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(MethodInvocation methodInvocation,
			EnforcementAttribute attribute) {
		return Mono.deferContextual(contextView -> {
			return constructAuthorizationSubscriptionFromContextView(methodInvocation, attribute, contextView);
		});
	}

	private Mono<? extends AuthorizationSubscription> constructAuthorizationSubscriptionFromContextView(
			MethodInvocation methodInvocation, EnforcementAttribute attribute, ContextView contextView) {
		contextView.stream().forEach(entry -> log.error("key: '{}' value: '{}'", entry.getKey(), entry.getValue()));

		Optional<ServerWebExchange> serverWebExchange = contextView.getOrEmpty(ServerWebExchange.class);
		Optional<ServerHttpRequest> serverHttpRequest = serverWebExchange.map(ServerWebExchange::getRequest);
		log.info("request        : {}", serverHttpRequest);
		Optional<Mono<SecurityContext>> securityContext = contextView.getOrEmpty(SecurityContext.class);
		log.info("securitycontext: {}", securityContext);
		Mono<Authentication> authentication = securityContext
				.map(ctx -> ctx.map(SecurityContext::getAuthentication).defaultIfEmpty(ANONYMOUS))
				.orElse(Mono.just(ANONYMOUS));
		log.info("authn: {}", authentication);
		return authentication.map(
				authn -> constructAuthorizationSubscription(authn, serverHttpRequest, methodInvocation, attribute));
	}

	private AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
			Optional<ServerHttpRequest> serverHttpRequest, MethodInvocation methodInvocation,
			EnforcementAttribute attribute) {
		lazyLoadDependencies();

		var evaluationCtx = expressionHandler.createEvaluationContext(authentication, methodInvocation);
		var subject = retrieveSubject(authentication, attribute, evaluationCtx);
		var action = retrieveAction(methodInvocation, attribute, evaluationCtx, serverHttpRequest);
		var resource = retrieveResource(methodInvocation, attribute, evaluationCtx);
		var environment = retrieveEnvironment(attribute, evaluationCtx);
		return new AuthorizationSubscription(mapper.valueToTree(subject), mapper.valueToTree(action),
				mapper.valueToTree(resource), mapper.valueToTree(environment));
	}

	private AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
			MethodInvocation methodInvocation, EnforcementAttribute attribute, EvaluationContext evaluationCtx) {
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

	private Object retrieveSubject(Authentication authentication, EnforcementAttribute attr, EvaluationContext ctx) {
		if (attr.getSubjectExpression() == null) {
			var subject = mapper.valueToTree(authentication);
			// sanitize the authentication depending on the application context, the
			// authentication may still contain credentials information, which should not be
			// send over the wire to the PDP
			if (subject instanceof ObjectNode) {
				ObjectNode object = (ObjectNode) subject;
				object.remove("credentials");
				if (object.has("principal")) {
					var principal = object.get("principal");
					if (principal instanceof ObjectNode) {
						((ObjectNode) principal).remove("password");
					}
				}
			}
			return subject;
		}
		return evaluateToJson(attr.getSubjectExpression(), ctx);
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

	private Object retrieveAction(MethodInvocation mi, EnforcementAttribute attr, EvaluationContext ctx,
			Optional<?> requestObject) {
		if (attr.getActionExpression() == null)
			return retrieveAction(mi, requestObject);
		return evaluateToJson(attr.getActionExpression(), ctx);
	}

	private Object retrieveAction(MethodInvocation mi, Optional<?> requestObject) {
		var actionNode = mapper.createObjectNode();
		requestObject.ifPresent(request -> actionNode.set("http", mapper.valueToTree(request)));
		actionNode.set("java", mapper.valueToTree(mi));

		var array = JSON.arrayNode();
		for (Object o : mi.getArguments()) {
			try {
				var json = mapper.valueToTree(o);
				array.add(json);
			} catch (IllegalArgumentException e) {
				array.add(JSON.nullNode());
			}
		}
		actionNode.set("arguments", array);
		return actionNode;
	}

	private Object retrieveResource(MethodInvocation mi, EnforcementAttribute attr, EvaluationContext ctx) {
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

	private Object retrieveEnvironment(EnforcementAttribute attr, EvaluationContext ctx) {
		if (attr.getEnvironmentExpression() == null)
			return null;
		return evaluateToJson(attr.getEnvironmentExpression(), ctx);
	}

}
