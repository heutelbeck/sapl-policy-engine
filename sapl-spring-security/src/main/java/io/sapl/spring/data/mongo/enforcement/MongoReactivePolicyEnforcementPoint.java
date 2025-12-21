/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.spring.data.mongo.enforcement;

import io.sapl.spring.method.metadata.QueryEnforce;
import io.sapl.spring.data.services.QueryEnforceAuthorizationSubscriptionService;
import io.sapl.spring.data.services.RepositoryInformationCollectorService;
import io.sapl.spring.data.utils.Utilities;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;

import static io.sapl.spring.data.utils.AnnotationUtilities.hasAnnotationQueryEnforce;
import static io.sapl.spring.data.utils.AnnotationUtilities.hasAnnotationQueryReactiveMongo;
import static io.sapl.spring.data.utils.Utilities.convertReturnTypeIfNecessary;

/**
 * This service is the gathering point of all SaplEnforcementPoints for the
 * MongoDb database type. A {@link MethodInterceptor} is needed to manipulate
 * the database method query. At the beginning the corresponding
 * {@link io.sapl.api.pdp.AuthorizationSubscription} is searched for and built
 * together if necessary. Afterward between 4 scenarios is differentiated and
 * acted accordingly.
 * <p>
 * Scenario 1: The method of the repository, which is to be executed, has a
 * {@link org.springframework.data.relational.core.query.Query} annotation.
 * <p>
 * 2nd scenario: The method of the repository, which should be executed, is a
 * Spring Data JPA query method.
 * <p>
 * 3rd scenario: Neither of the above scenarios is the case, but an
 * AuthorizationSubscription has been found and thus manipulation of the data is
 * desired.
 * <p>
 * 4th scenario: None of the above scenarios are true and the execution of the
 * database method is forwarded.
 */
@Slf4j
@RequiredArgsConstructor
public class MongoReactivePolicyEnforcementPoint<T> implements MethodInterceptor {

    private final ObjectProvider<MongoReactiveAnnotationQueryManipulationEnforcementPoint<T>> mongoReactiveAnnotationQueryManipulationEnforcementPoint;
    private final ObjectProvider<MongoReactiveMethodNameQueryManipulationEnforcementPoint<T>> mongoReactiveMethodNameQueryManipulationEnforcementPoint;
    private final ObjectProvider<QueryEnforceAuthorizationSubscriptionService>                queryEnforceAnnotationService;
    private final RepositoryInformationCollectorService                                       repositoryInformationCollectorService;

    @SuppressWarnings("unchecked")
    public Object invoke(@NonNull MethodInvocation invocation) throws Throwable {

        final var repositoryMethod = invocation.getMethod();

        if (!hasAnnotationQueryEnforce(repositoryMethod)) {
            // If no manipulation of the query is desired, the call to the method is merely
            // forwarded.
            return invocation.proceed();
        }

        log.debug("# MongoReactivePolicyEnforcementPoint intercept: {}",
                invocation.getMethod().getDeclaringClass().getSimpleName() + invocation.getMethod().getName());

        final var repository            = invocation.getMethod().getDeclaringClass();
        final var repositoryInformation = repositoryInformationCollectorService
                .getRepositoryByName(repository.getName());

        if (repositoryInformation.isCustomMethod(repositoryMethod)) {
            throw new IllegalStateException(
                    "The QueryEnforce annotation cannot be applied to custom repository methods. ");
        }

        final var domainType          = (Class<T>) repositoryInformation.getDomainType();
        final var returnClassOfMethod = Objects.requireNonNull(repositoryMethod).getReturnType();
        final var queryEnforce        = AnnotationUtils.findAnnotation(repositoryMethod, QueryEnforce.class);

        // Use ReactiveSecurityContextHolder for proper reactive security context
        // Fall back to SecurityContextHolder for blocking contexts and test scenarios
        Mono<Authentication> authMono = ReactiveSecurityContextHolder.getContext().map(ctx -> ctx.getAuthentication())
                .switchIfEmpty(Mono.fromCallable(() -> SecurityContextHolder.getContext().getAuthentication()));

        Flux<T> resultFlux = authMono.flatMapMany(authentication -> {
            var authSub = queryEnforceAnnotationService.getObject().getAuthorizationSubscription(invocation,
                    queryEnforce, domainType, authentication);

            if (authSub == null) {
                return Flux.error(new IllegalStateException(
                        "The Sapl implementation for the manipulation of the database queries was recognised, but no AuthorizationSubscription was found."));
            }

            // Introduce MongoReactiveAtQueryImplementation if method has
            // @Query-Annotation.
            if (hasAnnotationQueryReactiveMongo(repositoryMethod)) {
                return mongoReactiveAnnotationQueryManipulationEnforcementPoint.getObject().enforce(authSub, domainType,
                        invocation);
            }

            // Introduce MethodBasedImplementation if the query can be derived from the
            // name of the method.
            if (Utilities.isSpringDataDefaultMethod(invocation.getMethod().getName())
                    || Utilities.isMethodNameValid(invocation.getMethod().getName())) {
                return mongoReactiveMethodNameQueryManipulationEnforcementPoint.getObject().enforce(authSub, domainType,
                        invocation);
            }

            // Fallback - proceed without enforcement
            try {
                Object result = invocation.proceed();
                if (result instanceof Flux) {
                    return (Flux<T>) result;
                } else if (result instanceof Mono) {
                    return ((Mono<T>) result).flux();
                }
                return Flux.just((T) result);
            } catch (Throwable e) {
                return Flux.error(e);
            }
        });

        return convertReturnTypeIfNecessary(resultFlux, returnClassOfMethod);
    }

}
