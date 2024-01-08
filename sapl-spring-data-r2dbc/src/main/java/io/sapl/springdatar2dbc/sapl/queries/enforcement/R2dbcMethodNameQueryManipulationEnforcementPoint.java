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
package io.sapl.springdatar2dbc.sapl.queries.enforcement;

import static io.sapl.springdatacommon.sapl.utils.ConstraintHandlerUtils.getAdvices;
import static io.sapl.springdatacommon.sapl.utils.ConstraintHandlerUtils.getObligations;

import java.util.Objects;
import java.util.function.Function;

import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.springdatacommon.handlers.DataManipulationHandler;
import io.sapl.springdatacommon.handlers.LoggingConstraintHandlerProvider;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatacommon.handlers.QueryManipulationObligationProvider;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementPoint;
import io.sapl.springdatar2dbc.sapl.QueryManipulationExecutor;
import lombok.SneakyThrows;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * This class is responsible for the implementation of the
 * R2dbcMethodNameQueryManipulationEnforcementPoint for all methods in the
 * repository that query can be derived from the method name.
 *
 * @param <T> is the domain type.
 */
public class R2dbcMethodNameQueryManipulationEnforcementPoint<T> implements QueryManipulationEnforcementPoint<T> {
    private final QueryManipulationObligationProvider QueryManipulationObligationProvider = new QueryManipulationObligationProvider();
    private final LoggingConstraintHandlerProvider    loggingConstraintHandlerProvider    = new LoggingConstraintHandlerProvider();
    private final DataManipulationHandler<T>          dataManipulationHandler;
    private final QueryManipulationExecutor           queryManipulationExecutor;

    private final QueryManipulationEnforcementData<T> enforcementData;
    private final String                              r2dbcQueryManipulationType = "r2dbcQueryManipulation";

    public R2dbcMethodNameQueryManipulationEnforcementPoint(QueryManipulationEnforcementData<T> enforcementData) {
        this.enforcementData           = enforcementData;
        this.dataManipulationHandler   = new DataManipulationHandler<>(enforcementData.getDomainType(), true);
        this.queryManipulationExecutor = new QueryManipulationExecutor(enforcementData.getBeanFactory());
    }

    /**
     * The PDP {@link io.sapl.api.pdp.PolicyDecisionPoint} is called with the
     * appropriate {@link AuthorizationSubscription} and then the {@link Decision}
     * of the PDP is forwarded.
     *
     * @return database objects that may have been filtered and/or transformed.
     */
    @Override
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
            var decisionIsPermit = Decision.PERMIT == decision.getDecision();
            var advice           = getAdvices(decision);

            loggingConstraintHandlerProvider.getHandler(advice).run();

            if (decisionIsPermit) {
                var obligations = getObligations(decision);
                var data        = retrieveData(obligations);

                return dataManipulationHandler.manipulate(obligations).apply(data);
            } else {
                return Flux.error(new AccessDeniedException("Access Denied by PDP"));
            }
        };
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
        if (QueryManipulationObligationProvider.isResponsible(obligations, r2dbcQueryManipulationType)) {
            return enforceQueryManipulation(obligations);
        } else {

            if (enforcementData.getMethodInvocation().getMethod().getReturnType().equals(Mono.class)) {
                return Flux.from((Mono<T>) Objects.requireNonNull(enforcementData.getMethodInvocation().proceed()));
            }

            return (Flux<T>) enforcementData.getMethodInvocation().proceed();
        }
    }

    /**
     * Calling the database with the manipulated query.
     *
     * @param obligations are the obligations from the {@link Decision}.
     * @return objects from the database that were queried with the manipulated
     *         query.
     */
    private Flux<T> enforceQueryManipulation(JsonNode obligations) {
        var manipulatedCondition = createSqlQuery(obligations);

        return queryManipulationExecutor.execute(manipulatedCondition, enforcementData.getDomainType())
                .map(dataManipulationHandler.toDomainObject());
    }

    /**
     * The method fetches the matching obligation and extracts the condition from
     * it. This condition is appended to the end of the sql query. The base query is
     * converted from the method name.
     *
     * @param obligations are the obligations from the {@link Decision}.
     * @return created sql query.
     */
    private String createSqlQuery(JsonNode obligations) {
        var r2dbcQueryManipulationObligation = QueryManipulationObligationProvider.getObligation(obligations,
                r2dbcQueryManipulationType);
        var condition                        = QueryManipulationObligationProvider
                .getConditions(r2dbcQueryManipulationObligation);
        var sqlConditionFromDecision         = addMissingConjunction(condition.asText());
        var baseQuery                        = PartTreeToSqlQueryStringConverter.createSqlBaseQuery(enforcementData);

        return baseQuery + sqlConditionFromDecision;
    }

    /**
     * If the condition from the obligation does not have a conjunction, an "AND"
     * conjunction is automatically assumed and appended to the base query.
     *
     * @param sqlConditionFromDecision represents the condition
     * @return the condition with conjunction or not
     */
    private String addMissingConjunction(String sqlConditionFromDecision) {
        var conditionStartsWithConjunction = sqlConditionFromDecision.toLowerCase().trim().startsWith("and ")
                || sqlConditionFromDecision.toLowerCase().trim().startsWith("or ");

        if (conditionStartsWithConjunction) {
            return " " + sqlConditionFromDecision;
        } else {
            return " AND " + sqlConditionFromDecision;
        }
    }

}
