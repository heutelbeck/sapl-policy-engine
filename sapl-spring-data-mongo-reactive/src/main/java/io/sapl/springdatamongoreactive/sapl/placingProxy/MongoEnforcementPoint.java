package io.sapl.springdatamongoreactive.sapl.placingProxy;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryProxyPostProcessor;
import org.springframework.stereotype.Service;

/**
 * This service is used to provide an instance of type
 * {@link RepositoryProxyPostProcessor} to then use the customize method of
 * {@link org.springframework.data.repository.core.support.RepositoryFactoryCustomizer}
 * which injects the EnforcementPoint.
 */
@Service
public class MongoEnforcementPoint implements RepositoryProxyPostProcessor {

    private final MongoProxyInterceptor<?> mongoProxyInterceptor;

    MongoEnforcementPoint(MongoProxyInterceptor<?> mongoProxyInterceptor) {
        this.mongoProxyInterceptor = mongoProxyInterceptor;
    }

    /**
     * This method of the RepositoryProxyPostProcessors interface allows to
     * manipulate a ProxyFactory class via the postProcess method.
     *
     * @param factory               the corresponding {@link ProxyFactory}.
     * @param repositoryInformation the related {@link RepositoryInformation}.
     */
    @Override
    public void postProcess(ProxyFactory factory,
            @SuppressWarnings("NullableProblems") RepositoryInformation repositoryInformation) {
        factory.addAdvice(mongoProxyInterceptor);
    }

}
