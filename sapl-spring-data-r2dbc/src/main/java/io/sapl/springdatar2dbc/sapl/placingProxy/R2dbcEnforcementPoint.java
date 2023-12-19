package io.sapl.springdatar2dbc.sapl.placingProxy;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import org.springframework.stereotype.Service;

@Service
public class R2dbcEnforcementPoint implements RepositoryProxyPostProcessor {

    private final R2dbcProxyInterceptor<?> r2dbcProxyInterceptor;

    R2dbcEnforcementPoint(R2dbcProxyInterceptor<?> r2dbcProxyInterceptor) {
        this.r2dbcProxyInterceptor = r2dbcProxyInterceptor;
    }

    @Override
    public void postProcess(ProxyFactory factory, RepositoryInformation repositoryInformation) {
        factory.addAdvice(r2dbcProxyInterceptor);
    }
}
