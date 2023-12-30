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
package io.sapl.springdatar2dbc.sapl.querytypes.annotationenforcement;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.springdatar2dbc.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatar2dbc.sapl.QueryManipulationEnforcementPoint;
import io.sapl.springdatar2dbc.sapl.QueryManipulationExecutor;
import io.sapl.springdatar2dbc.sapl.handlers.DataManipulationHandler;
import io.sapl.springdatar2dbc.sapl.handlers.R2dbcQueryManipulationObligationProvider;
import io.sapl.springdatar2dbc.sapl.handlers.LoggingConstraintHandlerProvider;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import lombok.SneakyThrows;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Objects;
import java.util.function.Function;

import static io.sapl.springdatar2dbc.sapl.utils.ConstraintHandlerUtils.*;

/**
 * This class is responsible for the implementation of the
 * R2dbcAnnotationQueryManipulationEnforcementPoint for all methods in the
 * repository that have a @Query annotation.
 *
 * @param <T> is the domain type.
 */
public class R2dbcAnnotationQueryManipulationEnforcementPoint<T> implements QueryManipulationEnforcementPoint<T> {
    private final R2dbcQueryManipulationObligationProvider r2dbcQueryManipulationObligationProvider = new R2dbcQueryManipulationObligationProvider();
    private final LoggingConstraintHandlerProvider         loggingConstraintHandlerProvider         = new LoggingConstraintHandlerProvider();
    private final DataManipulationHandler<T>               dataManipulationHandler;
    private final QueryManipulationExecutor                queryManipulationExecutor;
    private final QueryManipulationEnforcementData<T>      enforcementData;
    private final String                                   query;

    public R2dbcAnnotationQueryManipulationEnforcementPoint(QueryManipulationEnforcementData<T> enforcementData) {
        this.enforcementData           = enforcementData;
        this.dataManipulationHandler   = new DataManipulationHandler<T>(enforcementData.getDomainType());
        this.queryManipulationExecutor = new QueryManipulationExecutor(enforcementData.getBeanFactory());

        this.query = QueryAnnotationParameterResolver.resolveBoundedMethodParametersAndAnnotationParameters(
                enforcementData.getMethodInvocation().getMethod(),
                enforcementData.getMethodInvocation().getArguments());
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
                var data        = retrieveData(obligations, query);

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
     * @param query       is the original value from the
     *                    {@link org.springframework.data.r2dbc.repository.Query}
     *                    annotation.
     * @return objects from the database that were queried with the manipulated
     *         query.
     */
    @SneakyThrows
    @SuppressWarnings("unchecked")
    private Flux<T> retrieveData(JsonNode obligations, String query) {

        if (r2dbcQueryManipulationObligationProvider.isResponsible(obligations)) {
            var r2dbcQueryManipulationObligation = r2dbcQueryManipulationObligationProvider.getObligation(obligations);
            var condition                        = r2dbcQueryManipulationObligationProvider
                    .getCondition(r2dbcQueryManipulationObligation);
            var manipulatedCondition             = enforceQueryManipulation(query, condition);

            return queryManipulationExecutor.execute(manipulatedCondition, enforcementData.getDomainType())
                    .map(dataManipulationHandler.toDomainObject());
        } else {

            if (enforcementData.getMethodInvocation().getMethod().getReturnType().equals(Mono.class)) {
                return Flux.from((Mono<T>) Objects.requireNonNull(enforcementData.getMethodInvocation().proceed()));
            }

            return (Flux<T>) enforcementData.getMethodInvocation().proceed();
        }
    }

    /**
     * Enforces the conditions of the decision on the query and returning the
     * manipulated query.
     *
     * @param query     is the original value from the
     *                  {@link org.springframework.data.r2dbc.repository.Query}
     *                  annotation.
     * @param condition are the query conditions from the {@link Decision}.
     * @return the manipulated query.
     */
    private String enforceQueryManipulation(String query, JsonNode condition) {
        return manipulateQuery(query, condition);
    }

    /**
     * Manipulates the original query by appending the conditions from the decision,
     * if the the original query contains the keyword ' where '. Else just append
     * the conditions from the obligation.
     *
     * @param query     is the original value from the
     *                  {@link org.springframework.data.r2dbc.repository.Query}
     *                  annotation.
     * @param condition are the query conditions from the {@link Decision}.
     * @return the manipulated query.
     */
    private String manipulateQuery(String query, JsonNode condition) {
        var conditionObligationAsString = condition.asText();

        if (query.toLowerCase().contains(" where ")) {
            var indexWithoutWhere = query.toLowerCase().indexOf(" where ");
            var indexWithWhere    = indexWithoutWhere + 7;

            var originalConditions    = query.substring(indexWithWhere);
            var queryBeforeConditions = query.substring(0, indexWithWhere);

            return queryBeforeConditions + getConditionWithoutConjunction(conditionObligationAsString)
                    + getConjunction(conditionObligationAsString) + originalConditions;
        }

        return query + " WHERE " + getConditionWithoutConjunction(conditionObligationAsString);
    }

    /**
     * Returning conjunction of the condition.
     *
     * @param condition is the condition of the sql-query.
     * @return the conjunction.
     */
    private String getConjunction(String condition) {
        var adjustedCondition = condition.toLowerCase().trim();

        if (adjustedCondition.startsWith("or ")) {
            return " OR ";
        }

        return " AND ";
    }

    /**
     * When condition contains any conjunction, then this method removes the
     * conjunction from the condition and returns the condition only.
     *
     * @param condition is the condition of the sql-query.
     * @return the manipulated query.
     */
    private String getConditionWithoutConjunction(String condition) {
        var adjustedCondition = condition.toLowerCase().trim();

        if (adjustedCondition.startsWith("and ")) {
            return condition.substring(4);
        }

        if (adjustedCondition.startsWith("or ")) {
            return condition.substring(3);
        }

        return condition;
    }

}
