package io.sapl.springdatar2dbc.sapl.placingProxy;

import org.springframework.data.repository.core.support.RepositoryFactoryCustomizer;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.stereotype.Service;

@Service
public class R2dbcCustomizer implements RepositoryFactoryCustomizer {

    private final R2dbcEnforcementPoint r2dbcEnforcementPoint;

    public R2dbcCustomizer(R2dbcEnforcementPoint r2dbcEnforcementPoint) {
        this.r2dbcEnforcementPoint = r2dbcEnforcementPoint;
    }

    @Override
    public void customize(RepositoryFactorySupport repositoryFactory) {
        repositoryFactory.addRepositoryProxyPostProcessor(r2dbcEnforcementPoint);
    }
}
