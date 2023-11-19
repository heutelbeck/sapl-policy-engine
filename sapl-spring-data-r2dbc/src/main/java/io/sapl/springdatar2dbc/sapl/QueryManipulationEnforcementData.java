package io.sapl.springdatar2dbc.sapl;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.BeanFactory;

@Getter
@Setter
@AllArgsConstructor
public class QueryManipulationEnforcementData<T> {

    private MethodInvocation          methodInvocation;
    private BeanFactory               beanFactory;
    private Class<T>                  domainType;
    private PolicyDecisionPoint       pdp;
    private AuthorizationSubscription authSub;
}
