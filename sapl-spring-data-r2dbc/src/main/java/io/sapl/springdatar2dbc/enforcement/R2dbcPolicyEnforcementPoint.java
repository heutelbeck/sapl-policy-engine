/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatar2dbc.enforcement;

import static io.sapl.springdatacommon.utils.Utilities.convertReturnTypeIfNecessary;
import static io.sapl.springdatacommon.utils.AnnotationUtilities.hasAnnotationQueryR2dbc;
import static io.sapl.springdatacommon.utils.AnnotationUtilities.hasAnnotationQueryEnforce;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.r2dbc.repository.Query;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.springdatacommon.services.QueryEnforceAuthorizationSubscriptionService;
import io.sapl.springdatacommon.services.RepositoryInformationCollectorService;
import io.sapl.springdatacommon.utils.Utilities;
import io.sapl.spring.method.metadata.QueryEnforce;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

/**
 * This service is the gathering point of all SaplEnforcementPoints for the
 * MongoDb database type. A {@link MethodInterceptor} is needed to manipulate
 * the database method query. At the beginning the corresponding
 * {@link AuthorizationSubscription} is searched for and built together if
 * necessary. Afterwards between 4 scenarios is differentiated and acted
 * accordingly.
 * <p>
 * Scenario 1: The method of the repository, which is to be executed, has a
 * {@link Query} annotation.
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
public class R2dbcPolicyEnforcementPoint<T> implements MethodInterceptor {

    private final ObjectProvider<R2dbcAnnotationQueryManipulationEnforcementPoint<T>> r2dbcAnnotationQueryManipulationEnforcementPointProvider;
    private final ObjectProvider<R2dbcMethodNameQueryManipulationEnforcementPoint<T>> r2dbcMethodNameQueryManipulationEnforcementPointProvider;
    private final ObjectProvider<QueryEnforceAuthorizationSubscriptionService>        queryEnforceAnnotationService;
    private final RepositoryInformationCollectorService                               repositoryInformationCollectorService;

    @SneakyThrows // proceeding invocation
    public Object invoke(MethodInvocation invocation) {

        final var repositoryMethod = invocation.getMethod();

        if (hasAnnotationQueryEnforce(repositoryMethod)) {

            log.debug("# R2dbcPolicyEnforcementPoint intercept: {}",
                    invocation.getMethod().getDeclaringClass().getSimpleName() + invocation.getMethod().getName());

            final var repository            = invocation.getMethod().getDeclaringClass();
            final var repositoryInformation = repositoryInformationCollectorService
                    .getRepositoryByName(repository.getName());

            if (repositoryInformation.isCustomMethod(repositoryMethod)) {
                throw new IllegalStateException(
                        "The QueryEnforce annotation cannot be applied to custom repository methods. ");
            }

            @SuppressWarnings("unchecked") // over repositoryInformation we can be save about domainType
            final var domainType = (Class<T>) repositoryInformation.getDomainType();

            final var returnClassOfMethod = Objects.requireNonNull(repositoryMethod).getReturnType();
            final var queryEnforce        = AnnotationUtils.findAnnotation(repositoryMethod, QueryEnforce.class);
            final var authSub             = queryEnforceAnnotationService.getObject()
                    .getAuthorizationSubscription(invocation, queryEnforce);

            if (authSub == null) {
                throw new IllegalStateException(
                        "The Sapl implementation for the manipulation of the database queries was recognised, but no AuthorizationSubscription was found.");
            }

            /*
             * Introduce RelationalAtQueryImplementation if method has @Query-Annotation.
             */
            if (hasAnnotationQueryR2dbc(repositoryMethod)) {

                final var resultData = r2dbcAnnotationQueryManipulationEnforcementPointProvider.getObject()
                        .enforce(authSub, domainType, invocation);

                return convertReturnTypeIfNecessary(resultData, returnClassOfMethod);
            }

            /*
             * Introduce MethodBasedImplementation if the query can be derived from the name
             * of the method.
             */
            if (Utilities.isSpringDataDefaultMethod(invocation.getMethod().getName())
                    || Utilities.isMethodNameValid(invocation.getMethod().getName())) {

                final var resultdata = r2dbcMethodNameQueryManipulationEnforcementPointProvider.getObject()
                        .enforce(authSub, domainType, invocation);
                return convertReturnTypeIfNecessary(resultdata, returnClassOfMethod);
            }
        }

        /*
         * If no manipulation of the query is desired, the call to the method is merely
         * forwarded.
         */
        return invocation.proceed();
    }

}
