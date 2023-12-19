package io.sapl.springdatamongoreactive.sapl.placingProxy;

import org.springframework.data.repository.core.support.RepositoryFactoryCustomizer;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import org.springframework.stereotype.Service;

/**
 * This service adds the EnforcementPoint to the corresponding
 * {@link RepositoryFactorySupport} as a {@link RepositoryProxyPostProcessor},
 */
@Service
public class MongoCustomizer implements RepositoryFactoryCustomizer {

    private final MongoEnforcementPoint mongoEnforcementPoint;

    public MongoCustomizer(MongoEnforcementPoint mongoEnforcementPoint) {
        this.mongoEnforcementPoint = mongoEnforcementPoint;
    }

    /**
     * This method allows access to the {@link RepositoryFactorySupport} class in
     * order to inject the EnforcementPoint.
     *
     * @param repositoryFactory is the {@link RepositoryFactorySupport} to be
     *                          customized.
     */
    @Override
    public void customize(RepositoryFactorySupport repositoryFactory) {
        repositoryFactory.addRepositoryProxyPostProcessor(mongoEnforcementPoint);
    }
}
