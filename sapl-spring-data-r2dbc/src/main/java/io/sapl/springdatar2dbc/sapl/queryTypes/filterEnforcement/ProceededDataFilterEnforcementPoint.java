package io.sapl.springdatar2dbc.sapl.queryTypes.filterEnforcement;

import io.sapl.springdatar2dbc.sapl.QueryManipulationEnforcementData;
import io.sapl.springdatar2dbc.sapl.QueryManipulationEnforcementPoint;
import io.sapl.springdatar2dbc.sapl.handlers.DataManipulationHandler;
import io.sapl.springdatar2dbc.sapl.handlers.LoggingConstraintHandlerProvider;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

import static io.sapl.springdatar2dbc.sapl.utils.ConstraintHandlerUtils.getAdvices;
import static io.sapl.springdatar2dbc.sapl.utils.ConstraintHandlerUtils.getObligations;

/**
 * This class is responsible for the implementation of the
 * MongoQueryManipulation for all methods in the repository that have no @Query
 * annotation and the query can't be derived from the name of the repository
 * method.
 *
 * @param <T> is the domain type.
 */
public class ProceededDataFilterEnforcementPoint<T> implements QueryManipulationEnforcementPoint<T> {
    private final QueryManipulationEnforcementData<T> enforcementData;
    private final DataManipulationHandler<T>          dataManipulationHandler;

    private final LoggingConstraintHandlerProvider loggingConstraintHandlerProvider = new LoggingConstraintHandlerProvider();

    public ProceededDataFilterEnforcementPoint(QueryManipulationEnforcementData<T> enforcementData) {
        this.enforcementData         = new QueryManipulationEnforcementData<>(enforcementData.getMethodInvocation(),
                enforcementData.getBeanFactory(), enforcementData.getDomainType(), enforcementData.getPdp(),
                enforcementData.getAuthSub());
        this.dataManipulationHandler = new DataManipulationHandler<>(enforcementData.getDomainType());
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
    @SuppressWarnings("unchecked")
    public Function<AuthorizationDecision, Flux<T>> enforceDecision() {
        return (decision) -> {
            var decisionIsPermit = Decision.PERMIT == decision.getDecision();
            var advice           = getAdvices(decision);

            loggingConstraintHandlerProvider.getHandler(advice).run();

            if (!decisionIsPermit) {
                return Flux.error(new AccessDeniedException("Access Denied by PDP"));
            } else {
                var obligations = getObligations(decision);

                Flux<T> data;
                try {
                    data = (Flux<T>) enforcementData.getMethodInvocation().proceed();
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }

                return dataManipulationHandler.manipulate(obligations).apply(data);
            }
        };
    }
}
