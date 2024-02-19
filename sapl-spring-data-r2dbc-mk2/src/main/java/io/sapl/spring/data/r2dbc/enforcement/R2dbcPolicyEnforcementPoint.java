package io.sapl.spring.data.r2dbc.enforcement;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class R2dbcPolicyEnforcementPoint implements MethodInterceptor {

    private final PolicyDecisionPoint policyDecisionPoint;

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        log.info("# R2dbcPolicyEnforcementPoint intercept: {}",
                invocation.getMethod().getDeclaringClass().getSimpleName() + invocation.getMethod().getName());
        return invocation.proceed();
    }

}
