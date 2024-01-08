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
package io.sapl.springdatamongoreactive.sapl.queries.enforcement;

import java.util.Objects;
import java.util.function.Function;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.springdatacommon.handlers.DataManipulationHandler;
import io.sapl.springdatacommon.handlers.LoggingConstraintHandlerProvider;
import io.sapl.springdatacommon.handlers.QueryManipulationObligationProvider;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementPoint;
import io.sapl.springdatacommon.sapl.queries.enforcement.QueryAnnotationParameterResolver;
import io.sapl.springdatacommon.sapl.utils.ConstraintHandlerUtils;
import lombok.SneakyThrows;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This class is responsible for the implementation of the
 * MongoAnnotationQueryManipulationEnforcementPoint for all methods in the
 * repository that have a @Query annotation.
 *
 * @param <T> is the domain type.
 */
public class MongoAnnotationQueryManipulationEnforcementPoint<T> implements QueryManipulationEnforcementPoint<T> {
    private final LoggingConstraintHandlerProvider    loggingConstraintHandlerProvider    = new LoggingConstraintHandlerProvider();
    private final QueryManipulationObligationProvider queryManipulationObligationProvider = new QueryManipulationObligationProvider();
    private final DataManipulationHandler<T>          dataManipulationHandler;
    private final ReactiveMongoTemplate               reactiveMongoTemplate;

    private final QueryManipulationEnforcementData<T> enforcementData;
    private final BasicQuery                          basicQuery;
    private final String                              mongoQueryManipulation = "mongoQueryManipulation";

    public MongoAnnotationQueryManipulationEnforcementPoint(QueryManipulationEnforcementData<T> enforcementData) {
        this.enforcementData         = new QueryManipulationEnforcementData<>(enforcementData.getMethodInvocation(),
                enforcementData.getBeanFactory(), enforcementData.getDomainType(), enforcementData.getPdp(),
                enforcementData.getAuthSub());
        this.dataManipulationHandler = new DataManipulationHandler<>(this.enforcementData.getDomainType(), false);
        this.reactiveMongoTemplate   = this.enforcementData.getBeanFactory().getBean(ReactiveMongoTemplate.class);

        var queryAnnotation = QueryAnnotationParameterResolver.resolveBoundedMethodParametersAndAnnotationParameters(
                enforcementData.getMethodInvocation().getMethod(), enforcementData.getMethodInvocation().getArguments(),
                false);
        basicQuery = new BasicQuery(queryAnnotation);
    }

    /**
     * The PDP {@link io.sapl.api.pdp.PolicyDecisionPoint} is called with the
     * appropriate {@link AuthorizationSubscription} and then the {@link Decision}
     * of the PDP is forwarded.
     *
     * @return database objects that may have been filtered and/or transformed.
     */
    public Flux<T> enforce() {
        return Mono.defer(() -> enforcementData.getPdp().decide(enforcementData.getAuthSub()).next())
                .flatMapMany(enforceDecision());
    }

    /**
     * The decision is checked for permitting and throws an
     * {@link AccessDeniedException} accordingly. Otherwise, the {@link Decision}'s
     * obligation is applied to the objects in the database.
     *
     * @return database objects that may have been filtered and/or transformed.
     */
    public Function<AuthorizationDecision, Flux<T>> enforceDecision() {
        return decision -> {
            var advice           = ConstraintHandlerUtils.getAdvices(decision);
            var decisionIsPermit = Decision.PERMIT == decision.getDecision();

            loggingConstraintHandlerProvider.getHandler(advice).run();

            if (decisionIsPermit) {
                var obligations = ConstraintHandlerUtils.getObligations(decision);
                var data        = retrieveData(obligations, basicQuery);

                return dataManipulationHandler.manipulate(obligations).apply(data);
            } else {
                return Flux.error(new AccessDeniedException("Access Denied by PDP"));
            }
        };
    }

    /**
     * If desired, the query is manipulated and then the database is called with it.
     * Otherwise, receives the data from the database by original method and
     * forwards it.
     *
     * @param obligations     are the obligations from the {@link Decision}.
     * @param annotationQuery is the original value from the
     *                        {@link org.springframework.data.mongodb.repository.Query}
     *                        annotation.
     * @return objects from the database that were queried with the manipulated
     *         query.
     */
    @SneakyThrows
    @SuppressWarnings("unchecked")
    private Flux<T> retrieveData(JsonNode obligations, BasicQuery annotationQuery) {
        if (queryManipulationObligationProvider.isResponsible(obligations, mongoQueryManipulation)) {
            var mongoQueryManipulationObligation = queryManipulationObligationProvider.getObligation(obligations,
                    mongoQueryManipulation);
            var conditions                       = queryManipulationObligationProvider
                    .getConditions(mongoQueryManipulationObligation);
            var query                            = enforceQueryManipulation(annotationQuery, conditions);

            return reactiveMongoTemplate.find(query, enforcementData.getDomainType());
        } else {

            if (enforcementData.getMethodInvocation().getMethod().getReturnType().equals(Mono.class)) {
                return Flux.from((Mono<T>) Objects.requireNonNull(enforcementData.getMethodInvocation().proceed()));
            }

            return (Flux<T>) enforcementData.getMethodInvocation().proceed();
        }
    }

    /**
     * Manipulates the original query by appending the conditions from the decision
     * and calling the database with the manipulated query.
     * <p>
     * Note: In MongoDB, conditions that filter by the same field of the
     * DomainObject are overridden. In our case: If the original repository method
     * filters by name ({'name': 'Lloyd'}) and the sapl condition also contains a
     * filter with the name field, the original filter will be overwritten with the
     * value of the sapl condition.
     *
     * @param annotationQuery is the original value from the
     *                        {@link org.springframework.data.mongodb.repository.Query}
     *                        annotation.
     * @param conditions      are the query conditions from the {@link Decision}.
     * @return the manipulated query.
     */
    private BasicQuery enforceQueryManipulation(BasicQuery annotationQuery, JsonNode conditions) {
        for (JsonNode condition : conditions) {
            var conditionAsBasicQuery = new BasicQuery(condition.asText());
            conditionAsBasicQuery.getQueryObject()
                    .forEach((key, value) -> annotationQuery.getQueryObject().append(key, value));
        }

        return annotationQuery;
    }
}
