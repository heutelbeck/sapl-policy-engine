/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatamongoreactive.sapl.querytypes.methodnameenforcement;

import java.util.Objects;
import java.util.function.Function;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.security.access.AccessDeniedException;
import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.springdatamongoreactive.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatamongoreactive.sapl.QueryManipulationEnforcementPoint;
import io.sapl.springdatamongoreactive.sapl.handlers.DataManipulationHandler;
import io.sapl.springdatamongoreactive.sapl.handlers.LoggingConstraintHandlerProvider;
import io.sapl.springdatamongoreactive.sapl.handlers.MongoQueryManipulationObligationProvider;
import io.sapl.springdatamongoreactive.sapl.utils.ConstraintHandlerUtils;
import lombok.SneakyThrows;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This class is responsible for the implementation of the
 * MongoMethodNameQueryManipulationEnforcementPoint for all methods in the
 * repository that query can be derived from the method name.
 *
 * @param <T> is the domain type.
 */
public class MongoMethodNameQueryManipulationEnforcementPoint<T> implements QueryManipulationEnforcementPoint<T> {
    private final MongoQueryManipulationObligationProvider mongoQueryManipulationObligationProvider = new MongoQueryManipulationObligationProvider();
    private final LoggingConstraintHandlerProvider         loggingConstraintHandlerProvider         = new LoggingConstraintHandlerProvider();
    private final SaplPartTreeCriteriaCreator<T>           saplPartTreeCriteriaCreator;
    private final ReactiveMongoTemplate                    reactiveMongoTemplate;
    private final DataManipulationHandler<T>               dataManipulationHandler;
    private final QueryManipulationEnforcementData<T>      enforcementData;

    public MongoMethodNameQueryManipulationEnforcementPoint(QueryManipulationEnforcementData<T> enforcementData) {
        this.enforcementData             = new QueryManipulationEnforcementData<T>(
                enforcementData.getMethodInvocation(), enforcementData.getBeanFactory(),
                enforcementData.getDomainType(), enforcementData.getPdp(), enforcementData.getAuthSub());
        this.reactiveMongoTemplate       = enforcementData.getBeanFactory().getBean(ReactiveMongoTemplate.class);
        this.dataManipulationHandler     = new DataManipulationHandler<T>(enforcementData.getDomainType());
        this.saplPartTreeCriteriaCreator = new SaplPartTreeCriteriaCreator<T>(reactiveMongoTemplate,
                enforcementData.getMethodInvocation(), enforcementData.getDomainType());
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
                var data        = retrieveData(obligations);

                return dataManipulationHandler.manipulate(obligations).apply(data);
            } else {
                return Flux.error(new AccessDeniedException("Access Denied by PDP"));
            }
        };
    }

    /**
     * Manipulates the original query and calls the database with it.
     *
     * @param conditions are the query conditions of the {@link Decision}
     * @return the queried data from the database.
     */
    private Flux<T> executeMongoQueryManipulation(JsonNode conditions) {
        var query = saplPartTreeCriteriaCreator.createManipulatedQuery(conditions);

        return reactiveMongoTemplate.find(query, enforcementData.getDomainType());
    }

    /**
     * Receives the data from the database, if desired, and forwards it. If desired,
     * the query is manipulated and then the database is called with it.
     *
     * @param obligations are the obligations from the {@link Decision}.
     * @return objects from the database that were queried with the manipulated
     *         query.
     */
    @SneakyThrows
    @SuppressWarnings("unchecked")
    private Flux<T> retrieveData(JsonNode obligations) {
        if (mongoQueryManipulationObligationProvider.isResponsible(obligations)) {
            var mongoQueryManipulationObligation = mongoQueryManipulationObligationProvider.getObligation(obligations);
            var conditions                       = mongoQueryManipulationObligationProvider
                    .getConditions(mongoQueryManipulationObligation);

            return executeMongoQueryManipulation(conditions);
        } else {

            if (enforcementData.getMethodInvocation().getMethod().getReturnType().equals(Mono.class)) {
                return Flux.from((Mono<T>) Objects.requireNonNull(enforcementData.getMethodInvocation().proceed()));
            }

            return (Flux<T>) enforcementData.getMethodInvocation().proceed();
        }
    }
}
