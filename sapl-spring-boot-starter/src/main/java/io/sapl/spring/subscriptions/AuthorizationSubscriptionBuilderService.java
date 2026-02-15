/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.subscriptions;

import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.spring.method.metadata.QueryEnforce;
import io.sapl.spring.method.metadata.SaplAttribute;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.DefaultAuthorizationManagerFactory;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.Optional;

import static io.sapl.api.model.ValueJsonMarshaller.fromJsonNode;
import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isProtected;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static java.lang.reflect.Modifier.isSynchronized;

/**
 * Unified service for building {@link AuthorizationSubscription} instances from
 * method invocations and security annotations.
 * <p>
 * Supports both Servlet (blocking) and WebFlux (reactive) contexts, as well as
 * method-level security ({@link SaplAttribute}) and data repository security
 * ({@link QueryEnforce}) annotations.
 */
@Slf4j
public class AuthorizationSubscriptionBuilderService {

    private static final String ERROR_EXPRESSION_EVALUATION_FAILED  = "Failed to evaluate expression '";
    private static final String ERROR_QUERY_ENFORCE_ANNOTATION_NULL = "QueryEnforce annotation must not be null";
    private static final String ERROR_SECRETS_MUST_BE_OBJECT        = "Secrets expression must evaluate to an object, but got: ";

    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("key", "anonymous",
            AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();

    public static final String AUTHENTICATION    = "authentication";
    public static final String METHOD_INVOCATION = "methodInvocation";

    private final ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider;
    private final ObjectProvider<ObjectMapper>                    mapperProvider;
    private final ObjectProvider<GrantedAuthorityDefaults>        defaultsProvider;
    private final ApplicationContext                              applicationContext;
    private final SubscriptionSecretsInjector                     secretsInjector;

    private MethodSecurityExpressionHandler expressionHandler;
    private ObjectMapper                    mapper;

    /**
     * Constructor for reactive method security context with resolved beans.
     *
     * @param expressionHandler the method security expression handler
     * @param mapper the object mapper for JSON serialization
     */
    public AuthorizationSubscriptionBuilderService(MethodSecurityExpressionHandler expressionHandler,
            ObjectMapper mapper) {
        this(expressionHandler, mapper, null);
    }

    /**
     * Constructor for reactive method security context with resolved beans
     * and optional secrets injector.
     *
     * @param expressionHandler the method security expression handler
     * @param mapper the object mapper for JSON serialization
     * @param secretsInjector optional injector for subscription secrets
     */
    public AuthorizationSubscriptionBuilderService(MethodSecurityExpressionHandler expressionHandler,
            ObjectMapper mapper,
            @Nullable SubscriptionSecretsInjector secretsInjector) {
        this.expressionHandler         = expressionHandler;
        this.mapper                    = mapper;
        this.expressionHandlerProvider = null;
        this.mapperProvider            = null;
        this.defaultsProvider          = null;
        this.applicationContext        = null;
        this.secretsInjector           = secretsInjector;
    }

    /**
     * Constructor for lazy bean resolution via ObjectProviders.
     *
     * @param expressionHandlerProvider provider for the expression handler
     * @param mapperProvider provider for the object mapper
     * @param defaultsProvider provider for granted authority defaults
     * @param applicationContext the application context
     */
    public AuthorizationSubscriptionBuilderService(
            ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider,
            ObjectProvider<ObjectMapper> mapperProvider,
            ObjectProvider<GrantedAuthorityDefaults> defaultsProvider,
            ApplicationContext applicationContext) {
        this(expressionHandlerProvider, mapperProvider, defaultsProvider, applicationContext, null);
    }

    /**
     * Constructor for lazy bean resolution via ObjectProviders with optional
     * secrets injector.
     *
     * @param expressionHandlerProvider provider for the expression handler
     * @param mapperProvider provider for the object mapper
     * @param defaultsProvider provider for granted authority defaults
     * @param applicationContext the application context
     * @param secretsInjector optional injector for subscription secrets
     */
    public AuthorizationSubscriptionBuilderService(
            ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider,
            ObjectProvider<ObjectMapper> mapperProvider,
            ObjectProvider<GrantedAuthorityDefaults> defaultsProvider,
            ApplicationContext applicationContext,
            @Nullable SubscriptionSecretsInjector secretsInjector) {
        this.expressionHandlerProvider = expressionHandlerProvider;
        this.mapperProvider            = mapperProvider;
        this.defaultsProvider          = defaultsProvider;
        this.applicationContext        = applicationContext;
        this.secretsInjector           = secretsInjector;
    }

    /**
     * Constructs an authorization subscription for Servlet/blocking context.
     *
     * @param authentication the current authentication
     * @param methodInvocation the method invocation
     * @param attribute the SAPL attribute containing expressions
     * @return the constructed authorization subscription
     */
    public AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
            MethodInvocation methodInvocation, SaplAttribute attribute) {
        val evaluationCtx = expressionHandler().createEvaluationContext(authentication, methodInvocation);
        evaluationCtx.setVariable(AUTHENTICATION, authentication);
        evaluationCtx.setVariable(METHOD_INVOCATION, methodInvocation);
        return constructServletAuthorizationSubscription(authentication, methodInvocation, attribute, evaluationCtx);
    }

    /**
     * Constructs an authorization subscription for Servlet/blocking context with
     * return object.
     *
     * @param authentication the current authentication
     * @param methodInvocation the method invocation
     * @param attribute the SAPL attribute containing expressions
     * @param returnObject the return object from the method
     * @return the constructed authorization subscription
     */
    public AuthorizationSubscription constructAuthorizationSubscriptionWithReturnObject(Authentication authentication,
            MethodInvocation methodInvocation, SaplAttribute attribute, Object returnObject) {
        val evaluationCtx = expressionHandler().createEvaluationContext(authentication, methodInvocation);
        expressionHandler().setReturnObject(returnObject, evaluationCtx);
        evaluationCtx.setVariable(AUTHENTICATION, authentication);
        evaluationCtx.setVariable(METHOD_INVOCATION, methodInvocation);
        return constructServletAuthorizationSubscription(authentication, methodInvocation, attribute, evaluationCtx);
    }

    /**
     * Constructs a reactive authorization subscription for WebFlux context.
     *
     * @param methodInvocation the method invocation
     * @param attribute the SAPL attribute containing expressions
     * @return a Mono emitting the constructed authorization subscription
     */
    public Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(MethodInvocation methodInvocation,
            SaplAttribute attribute) {
        return Mono.deferContextual(contextView -> constructAuthorizationSubscriptionFromContextView(methodInvocation,
                attribute, contextView, null));
    }

    /**
     * Constructs a reactive authorization subscription for WebFlux context with
     * return object.
     *
     * @param methodInvocation the method invocation
     * @param attribute the SAPL attribute containing expressions
     * @param returnedObject the return object from the method
     * @return a Mono emitting the constructed authorization subscription
     */
    public Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(MethodInvocation methodInvocation,
            SaplAttribute attribute, Object returnedObject) {
        return Mono.deferContextual(contextView -> constructAuthorizationSubscriptionFromContextView(methodInvocation,
                attribute, contextView, returnedObject));
    }

    /**
     * Builds a reactive {@link AuthorizationSubscription} from a
     * {@link QueryEnforce} annotation.
     *
     * @param methodInvocation the method invocation
     * @param queryEnforce the QueryEnforce annotation
     * @param domainType the domain type for the query
     * @return a Mono emitting the constructed authorization subscription
     */
    public Mono<AuthorizationSubscription> reactiveConstructAuthorizationSubscription(MethodInvocation methodInvocation,
            QueryEnforce queryEnforce, Class<?> domainType) {
        if (queryEnforce == null) {
            return Mono.error(new IllegalArgumentException(ERROR_QUERY_ENFORCE_ANNOTATION_NULL));
        }

        return ReactiveSecurityContextHolder.getContext().flatMap(ctx -> Mono.justOrEmpty(ctx.getAuthentication()))
                .switchIfEmpty(Mono.fromCallable(SecurityContextHolder::getContext)
                        .flatMap(ctx -> Mono.justOrEmpty(ctx.getAuthentication())))
                .defaultIfEmpty(ANONYMOUS).map(authentication -> {
                    val evaluationCtx = createQueryEnforceEvaluationContext(authentication, methodInvocation);
                    return constructAuthorizationSubscription(authentication, evaluationCtx,
                            parseExpressionIfNotEmpty(queryEnforce.subject()),
                            parseExpressionIfNotEmpty(queryEnforce.action()),
                            parseExpressionIfNotEmpty(queryEnforce.resource()),
                            parseExpressionIfNotEmpty(queryEnforce.environment()),
                            parseExpressionIfNotEmpty(queryEnforce.secrets()), methodInvocation, null, domainType);
                });
    }

    private MethodSecurityExpressionHandler expressionHandler() {
        if (expressionHandler == null && expressionHandlerProvider != null) {
            expressionHandler = expressionHandlerProvider
                    .getIfAvailable(() -> defaultExpressionHandler(defaultsProvider, applicationContext));
        }
        return expressionHandler;
    }

    private static MethodSecurityExpressionHandler defaultExpressionHandler(
            ObjectProvider<GrantedAuthorityDefaults> defaultsProvider, ApplicationContext context) {
        val handler = new DefaultMethodSecurityExpressionHandler();
        if (defaultsProvider != null) {
            defaultsProvider.ifAvailable(d -> {
                val authFactory = new DefaultAuthorizationManagerFactory<MethodInvocation>();
                val rolePrefix  = d.getRolePrefix();
                authFactory.setRolePrefix(rolePrefix != null ? rolePrefix : "");
                handler.setAuthorizationManagerFactory(authFactory);
            });
        }
        handler.setApplicationContext(context);
        return handler;
    }

    private ObjectMapper mapper() {
        if (mapper == null && mapperProvider != null) {
            mapper = mapperProvider.getIfAvailable(ObjectMapper::new);
        }
        return mapper;
    }

    private AuthorizationSubscription constructServletAuthorizationSubscription(Authentication authentication,
            MethodInvocation methodInvocation, SaplAttribute attribute, EvaluationContext evaluationCtx) {
        val httpRequest = retrieveServletRequest();
        return constructAuthorizationSubscription(authentication, evaluationCtx, attribute.subjectExpression(),
                attribute.actionExpression(), attribute.resourceExpression(), attribute.environmentExpression(),
                attribute.secretsExpression(), methodInvocation, httpRequest, Object.class);
    }

    private static @Nullable Object retrieveServletRequest() {
        val requestAttributes = RequestContextHolder.getRequestAttributes();
        return requestAttributes != null ? ((ServletRequestAttributes) requestAttributes).getRequest() : null;
    }

    private Mono<AuthorizationSubscription> constructAuthorizationSubscriptionFromContextView(
            MethodInvocation methodInvocation, SaplAttribute attribute, ContextView contextView,
            @Nullable Object returnedObject) {
        Optional<ServerWebExchange> serverWebExchange = contextView.getOrEmpty(ServerWebExchange.class);
        log.debug("Building authorization subscription for method {}: ServerWebExchange present = {}",
                methodInvocation.getMethod().getName(), serverWebExchange.isPresent());
        Object                          serverHttpRequest = serverWebExchange.map(ServerWebExchange::getRequest)
                .orElse(null);
        Optional<Mono<SecurityContext>> securityContext   = contextView.getOrEmpty(SecurityContext.class);
        Mono<Authentication>            authentication    = securityContext
                .map(ctx -> ctx.flatMap(sc -> Mono.justOrEmpty(sc.getAuthentication())).defaultIfEmpty(ANONYMOUS))
                .orElseGet(() -> Mono.just(ANONYMOUS));

        return authentication.map(auth -> {
            val evaluationCtx = expressionHandler().createEvaluationContext(auth, methodInvocation);
            evaluationCtx.setVariable(AUTHENTICATION, auth);
            evaluationCtx.setVariable(METHOD_INVOCATION, methodInvocation);
            if (returnedObject != null) {
                expressionHandler().setReturnObject(returnedObject, evaluationCtx);
            }
            return constructAuthorizationSubscription(auth, evaluationCtx, attribute.subjectExpression(),
                    attribute.actionExpression(), attribute.resourceExpression(), attribute.environmentExpression(),
                    attribute.secretsExpression(), methodInvocation, serverHttpRequest, Object.class);
        });
    }

    private EvaluationContext createQueryEnforceEvaluationContext(Authentication authentication,
            MethodInvocation methodInvocation) {
        val evaluationCtx = new StandardEvaluationContext(methodInvocation);
        if (applicationContext != null) {
            evaluationCtx.setBeanResolver(new BeanFactoryResolver(applicationContext));
        }
        evaluationCtx.setVariable(AUTHENTICATION, authentication);
        evaluationCtx.setVariable(METHOD_INVOCATION, methodInvocation);

        val params = methodInvocation.getMethod().getParameters();
        val args   = methodInvocation.getArguments();
        for (int i = 0; i < params.length; i++) {
            evaluationCtx.setVariable(params[i].getName(), args[i]);
        }

        return evaluationCtx;
    }

    private AuthorizationSubscription constructAuthorizationSubscription(Authentication authentication,
            EvaluationContext evaluationCtx, Expression subjectExpr, Expression actionExpr, Expression resourceExpr,
            Expression environmentExpr, Expression secretsExpr, MethodInvocation methodInvocation,
            @Nullable Object httpRequest, Class<?> domainType) {

        val subject     = retrieveSubject(authentication, subjectExpr, evaluationCtx);
        val action      = retrieveAction(methodInvocation, actionExpr, evaluationCtx, httpRequest);
        val resource    = retrieveResource(methodInvocation, resourceExpr, evaluationCtx, httpRequest, domainType);
        val environment = retrieveEnvironment(environmentExpr, evaluationCtx);
        val secrets     = retrieveSecrets(authentication, secretsExpr, evaluationCtx);

        return new AuthorizationSubscription(fromJsonNode(subject), action, resource,
                environment != null ? fromJsonNode(mapper().valueToTree(environment)) : Value.UNDEFINED, secrets);
    }

    /**
     * Retrieves the subject for the authorization subscription. When no explicit
     * subject expression is provided, serializes the authentication object and
     * strips sensitive fields:
     * <ul>
     * <li>{@code credentials} - removed from the root authentication object</li>
     * <li>{@code token.tokenValue} - raw encoded token removed from token
     * object</li>
     * <li>{@code principal.password} - password removed from principal</li>
     * <li>{@code principal.tokenValue} - raw encoded token removed from
     * principal</li>
     * </ul>
     */
    private JsonNode retrieveSubject(Authentication authentication, Expression subjectExpr, EvaluationContext ctx) {
        if (subjectExpr != null) {
            return evaluateToJson(subjectExpr, ctx);
        }

        ObjectNode subject = mapper().valueToTree(authentication);
        subject.remove("credentials");
        stripTokenValue(subject.get("token"));
        val principal = subject.get("principal");
        if (principal instanceof ObjectNode objectPrincipal) {
            objectPrincipal.remove("password");
            objectPrincipal.remove("tokenValue");
        }

        return subject;
    }

    private Value retrieveAction(MethodInvocation mi, Expression actionExpr, EvaluationContext ctx,
            @Nullable Object requestObject) {
        if (actionExpr != null) {
            return fromJsonNode(evaluateToJson(actionExpr, ctx));
        }

        val actionBuilder = ObjectValue.builder();

        if (requestObject != null) {
            actionBuilder.put("http", fromJsonNode(mapper().valueToTree(requestObject)));
        }

        val javaBuilder = ObjectValue.builder().putAll(toValue(mi));

        val arguments = mi.getArguments();
        if (arguments.length > 0) {
            val argsBuilder = ArrayValue.builder();
            for (val arg : arguments) {
                try {
                    argsBuilder.add(fromJsonNode(mapper().valueToTree(arg)));
                } catch (IllegalArgumentException e) {
                    // drop if not mappable to JSON
                }
            }
            val argsArray = argsBuilder.build();
            if (!argsArray.isEmpty()) {
                javaBuilder.put("arguments", argsArray);
            }
        }

        actionBuilder.put("java", javaBuilder.build());
        return actionBuilder.build();
    }

    private Value retrieveResource(MethodInvocation mi, Expression resourceExpr, EvaluationContext ctx,
            @Nullable Object httpRequest, Class<?> domainType) {
        if (resourceExpr != null) {
            return fromJsonNode(evaluateToJson(resourceExpr, ctx));
        }

        val resourceBuilder = ObjectValue.builder();

        if (httpRequest != null) {
            resourceBuilder.put("http", fromJsonNode(mapper().valueToTree(httpRequest)));
        }

        resourceBuilder.put("java", toValue(mi));

        if (domainType != null && domainType != Object.class) {
            resourceBuilder.put("entityType", Value.of(domainType.getName()));
        }

        return resourceBuilder.build();
    }

    private Object retrieveEnvironment(Expression environmentExpr, EvaluationContext ctx) {
        if (environmentExpr == null) {
            return null;
        }
        return evaluateToJson(environmentExpr, ctx);
    }

    private static void stripTokenValue(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            objectNode.remove("tokenValue");
        }
    }

    private ObjectValue retrieveSecrets(Authentication authentication, Expression secretsExpr, EvaluationContext ctx) {
        if (secretsExpr != null) {
            val result = evaluateToJson(secretsExpr, ctx);
            val value  = fromJsonNode(result);
            if (value instanceof ObjectValue ov) {
                return ov;
            }
            throw new IllegalArgumentException(ERROR_SECRETS_MUST_BE_OBJECT + value.getClass().getSimpleName());
        }
        if (secretsInjector != null) {
            return secretsInjector.injectSecrets(authentication);
        }
        return Value.EMPTY_OBJECT;
    }

    private JsonNode evaluateToJson(Expression expr, EvaluationContext ctx) {
        try {
            return mapper().valueToTree(expr.getValue(ctx));
        } catch (EvaluationException e) {
            throw new IllegalArgumentException(ERROR_EXPRESSION_EVALUATION_FAILED + expr.getExpressionString() + "'",
                    e);
        }
    }

    private Expression parseExpressionIfNotEmpty(String expressionString) {
        if (expressionString == null || expressionString.isEmpty()) {
            return null;
        }
        return PARSER.parseExpression(expressionString);
    }

    private ObjectValue toValue(MethodInvocation invocation) {
        val method  = invocation.getMethod();
        val builder = ObjectValue.builder().put("name", Value.of(method.getName()))
                .put("declaringTypeName", Value.of(method.getDeclaringClass().getTypeName()))
                .put("modifiers", modifiersToValue(method.getModifiers()));

        val target      = invocation.getThis();
        val targetClass = target != null ? target.getClass() : method.getDeclaringClass();
        builder.put("instanceof", classHierarchyToValue(targetClass));

        return builder.build();
    }

    private ArrayValue modifiersToValue(int modifiers) {
        val builder = ArrayValue.builder();
        if (isFinal(modifiers)) {
            builder.add(Value.of("final"));
        }
        if (isPrivate(modifiers)) {
            builder.add(Value.of("private"));
        }
        if (isProtected(modifiers)) {
            builder.add(Value.of("protected"));
        }
        if (isPublic(modifiers)) {
            builder.add(Value.of("public"));
        }
        if (isStatic(modifiers)) {
            builder.add(Value.of("static"));
        }
        if (isSynchronized(modifiers)) {
            builder.add(Value.of("synchronized"));
        }
        return builder.build();
    }

    private ArrayValue classHierarchyToValue(Class<?> clazz) {
        val builder = ArrayValue.builder();
        var current = clazz;
        while (current != null) {
            builder.add(classToValue(current));
            for (val iface : current.getInterfaces()) {
                addInterfaceHierarchy(builder, iface);
            }
            current = current.getSuperclass();
        }
        return builder.build();
    }

    private void addInterfaceHierarchy(ArrayValue.Builder builder, Class<?> iface) {
        builder.add(classToValue(iface));
        for (val parentIface : iface.getInterfaces()) {
            addInterfaceHierarchy(builder, parentIface);
        }
    }

    private ObjectValue classToValue(Class<?> clazz) {
        return ObjectValue.builder().put("name", Value.of(clazz.getName()))
                .put("simpleName", Value.of(clazz.getSimpleName())).build();
    }

}
