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
package io.sapl.springdatamongoreactive.sapl.proxy;

import static io.sapl.springdatacommon.sapl.utils.Utilities.convertReturnTypeIfNecessary;
import static io.sapl.springdatamongoreactive.sapl.utils.annotation.AnnotationUtilities.convertToEnforce;
import static io.sapl.springdatamongoreactive.sapl.utils.annotation.AnnotationUtilities.hasAnnotationEnforce;
import static io.sapl.springdatamongoreactive.sapl.utils.annotation.AnnotationUtilities.hasAnnotationQuery;
import static io.sapl.springdatamongoreactive.sapl.utils.annotation.AnnotationUtilities.hasAnnotationSaplProtected;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Service;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.springdatacommon.handlers.AuthorizationSubscriptionHandlerProvider;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatacommon.sapl.utils.Utilities;
import io.sapl.springdatamongoreactive.sapl.QueryManipulationEnforcementPointFactory;
import io.sapl.springdatamongoreactive.sapl.utils.annotation.EnforceMongoReactive;
import lombok.SneakyThrows;

/**
 * This service is the gathering point of all SaplEnforcementPoints for the
 * MongoDb database type. A {@link MethodInterceptor} is needed to manipulate
 * the database method query. At the beginning the corresponding
 * {@link AuthorizationSubscription} is searched for and built together if
 * necessary. Afterward between 4 scenarios is differentiated and acted
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
@Service
public class MongoProxyInterceptor<T> implements MethodInterceptor {
    private final AuthorizationSubscriptionHandlerProvider authSubHandler;
    private final QueryManipulationEnforcementData<T>      enforcementData;
    private final QueryManipulationEnforcementPointFactory factory;

    private static final String REACTIVE_MONGO_REPOSITORY_PATH = "org.springframework.data.mongodb.repository.ReactiveMongoRepository";
    private static final String REACTIVE_CRUD_REPOSITORY_PATH  = "org.springframework.data.repository.reactive.ReactiveCrudRepository";

    public MongoProxyInterceptor(AuthorizationSubscriptionHandlerProvider authSubHandler, BeanFactory beanFactory,
            PolicyDecisionPoint pdp, QueryManipulationEnforcementPointFactory factory) {
        this.authSubHandler  = authSubHandler;
        this.factory         = factory;
        this.enforcementData = new QueryManipulationEnforcementData<>(null, beanFactory, null, pdp, null);
    }

    @SneakyThrows // // Throwable by proceed() method, ClassNotFoundException
    public Object invoke(MethodInvocation methodInvocation) {

        var repositoryMethod = methodInvocation.getMethod();
        var repository       = methodInvocation.getMethod().getDeclaringClass();

        if (hasAnnotationSaplProtected(repository) || hasAnnotationSaplProtected(repositoryMethod)
                || hasAnnotationEnforce(repositoryMethod)) {

            var returnClassOfMethod  = Objects.requireNonNull(repositoryMethod).getReturnType();
            var enforceMongoReactive = AnnotationUtils.findAnnotation(repositoryMethod, EnforceMongoReactive.class);
            var enforceAnnotation    = convertToEnforce(enforceMongoReactive);
            var authSub              = this.authSubHandler.getAuthSub(repository, methodInvocation, enforceAnnotation);

            if (authSub == null) {
                throw new IllegalStateException(
                        "The Sapl implementation for the manipulation of the database queries was recognised, but no AuthorizationSubscription was found.");
            }

            var domainType = extractDomainType(repository);

            enforcementData.setMethodInvocation(methodInvocation);
            enforcementData.setDomainType(domainType);
            enforcementData.setAuthSub(authSub);

            /*
             * Introduce {@link MongoAnnotationQueryManipulationEnforcementPoint} if method
             * has {@link Query} annotation. In this enforcement point, the query from the
             * annotation is only extended with the conditions that were specified in the
             * policy's obligation.
             */
            if (hasAnnotationQuery(repositoryMethod)) {
                var annotationQueryEnforcementPoint = factory
                        .createMongoAnnotationQueryManipulationEnforcementPoint(enforcementData);

                return convertReturnTypeIfNecessary(annotationQueryEnforcementPoint.enforce(), returnClassOfMethod);
            }

            /*
             * Introduce {@link MongoMethodNameQueryManipulationEnforcementPoint} if the
             * query can be derived from the name of the repository method. Spring Data JPA
             * enables the use of query methods. The query is built from the name of the
             * method. The domain class and its attributes are inspected, which is passed to
             * the repository as a generic. Spring Data JPA uses the {@link
             * org.springframework.data.repository.query.parser.PartTree} class, among
             * others, to translate the query methods. This EnforcementPoint makes use of
             * these classes from Spring Data JPA to rebuild the queries. Example of a query
             * method with the domain object attribute 'firstname':
             * findAllByFirstname(String firstname)
             */
            if (Utilities.isMethodNameValid(repositoryMethod.getName())) {
                var methodNameQueryEnforcementPoint = factory
                        .createMongoMethodNameQueryManipulationEnforcementPoint(enforcementData);

                return convertReturnTypeIfNecessary(methodNameQueryEnforcementPoint.enforce(), returnClassOfMethod);
            }

            /*
             * Introduce {@link ProceededDataFilterEnforcementPoint} if none of the previous
             * cases apply. The corresponding database method is executed and the received
             * data is intercepted. The sapl conditions from the {@link PolicyDecisionPoint}
             * are converted to filters and applied to the received data. The filtered data
             * is then returned.
             */
            var filterEnforcementPoint = factory.createProceededDataFilterEnforcementPoint(enforcementData);
            return convertReturnTypeIfNecessary(filterEnforcementPoint.enforce(), returnClassOfMethod);
        }

        /*
         * If no filtering of the data is desired, the call to the method is merely
         * forwarded.
         */
        return methodInvocation.proceed();
    }

    @SuppressWarnings("unchecked") // casting domain type from Class<?> to Class<T>
    private Class<T> extractDomainType(Class<?> repository) throws ClassNotFoundException {
        var repositoryTypes = repository.getGenericInterfaces();

        for (Type interfaceType : repositoryTypes) {
            if ((interfaceType.getTypeName().contains(REACTIVE_MONGO_REPOSITORY_PATH)
                    || interfaceType.getTypeName().contains(REACTIVE_CRUD_REPOSITORY_PATH))) {
                var parameterizedType = (ParameterizedType) interfaceType;
                return (Class<T>) parameterizedType.getActualTypeArguments()[0];
            }
        }

        throw new ClassNotFoundException("The " + ReactiveMongoRepository.class + " or " + ReactiveCrudRepository.class
                + " could not be found as an extension of the " + repository);
    }

}
