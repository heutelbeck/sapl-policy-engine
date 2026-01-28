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
package io.sapl.spring.data.r2dbc.enforcement;

import com.fasterxml.jackson.databind.node.ArrayNode;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.constraints.ConstraintEnforcementService;
import io.sapl.spring.data.services.ConstraintQueryEnforcementService;
import io.sapl.spring.data.r2dbc.queries.QueryCreation;
import io.sapl.spring.data.r2dbc.queries.QueryManipulationExecutor;
import lombok.AllArgsConstructor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.function.Function;

/**
 * This class is responsible for the implementation of the
 * R2dbcAnnotationQueryManipulationEnforcementPoint for all methods in the
 * repository that have a @Query annotation.
 *
 * @param <T> is the domain type.
 */
@AllArgsConstructor
public class R2dbcAnnotationQueryManipulationEnforcementPoint<T> {

    private static final String ERROR_ACCESS_DENIED_BY_PDP = "Access Denied by PDP";

    private final ObjectProvider<PolicyDecisionPoint>               pdpProvider;
    private final ObjectProvider<QueryManipulationExecutor>         queryManipulationExecutorProvider;
    private final ObjectProvider<ConstraintQueryEnforcementService> constraintQueryEnforcementServiceProvider;
    private ConstraintEnforcementService                            constraintEnforcementService;

    /**
     * The PDP {@link io.sapl.api.pdp.PolicyDecisionPoint} is called with the
     * appropriate {@link AuthorizationSubscription} and then the {@link Decision}
     * of the PDP is forwarded.
     *
     * @return database objects that may have been filtered and/or transformed.
     */
    public Flux<T> enforce(AuthorizationSubscription authorizationSubscription, Class<T> domainType,
            MethodInvocation invocation) {
        return Mono.defer(() -> pdpProvider.getObject().decide(authorizationSubscription).next())
                .flatMapMany(enforceDecision(domainType, invocation));
    }

    /**
     * The decision is checked for permitting and throws an
     * {@link AccessDeniedException} accordingly. Otherwise, the {@link Decision}'s
     * obligation is applied to the objects in the database.
     *
     * @return database objects that may have been filtered and/or transformed.
     */
    private Function<AuthorizationDecision, Flux<T>> enforceDecision(Class<T> domainType, MethodInvocation invocation) {

        final var baseQuery = QueryCreation.createBaselineQuery(invocation);

        return decision -> {

            Flux<T> resourceAccessPoint;

            var decisionIsPermit = Decision.PERMIT == decision.decision();

            if (!decisionIsPermit) {
                resourceAccessPoint = Flux.error(new AccessDeniedException(ERROR_ACCESS_DENIED_BY_PDP));
            } else {
                final var queryManipulationHandler = constraintQueryEnforcementServiceProvider.getObject()
                        .queryManipulationForR2dbc(decision);

                var jsonObligations = queryManipulationHandler.getQueryManipulationObligations();
                var conditions      = queryManipulationHandler.getConditions();
                var selections      = queryManipulationHandler.getSelections();
                var transformations = queryManipulationHandler.getTransformations();
                var alias           = queryManipulationHandler.getAlias();

                var obligations             = Arrays.stream(jsonObligations).map(ValueJsonMarshaller::fromJsonNode)
                        .toArray(Value[]::new);
                var constraintHandlerBundle = constraintEnforcementService.reactiveTypeBundleFor(decision, domainType,
                        obligations);

                constraintHandlerBundle.handleMethodInvocationHandlers(invocation);
                resourceAccessPoint = retrieveData(conditions, selections, transformations, alias, baseQuery,
                        domainType);

                resourceAccessPoint = constraintEnforcementService.replaceIfResourcePresent(resourceAccessPoint,
                        decision.resource(), domainType);

                return resourceAccessPoint;
            }

            return Flux.error(new AccessDeniedException(ERROR_ACCESS_DENIED_BY_PDP));
        };
    }

    /**
     * Receives the data from the database, if desired, and forwards it. If desired,
     * the query is manipulated and then the database is called with it.
     *
     * @param obligations are the obligations from the {@link Decision}.
     * @param query is the original value from the
     * {@link org.springframework.data.r2dbc.repository.Query} annotation.
     * @return objects from the database that were queried with the manipulated
     * query.
     */
    private Flux<T> retrieveData(ArrayNode conditions, ArrayNode selections, ArrayNode transformations, String alias,
            String basicQuery, Class<T> domainType) {

        final var manipulatedCondition = QueryCreation.manipulateQuery(basicQuery, conditions, selections,
                transformations, alias, domainType);

        return queryManipulationExecutorProvider.getObject().execute(manipulatedCondition, domainType);
    }

}
