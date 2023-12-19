package io.sapl.springdatamongoreactive.sapl.placingProxy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactoryBean;
import org.springframework.stereotype.Service;

/**
 * This service implements the {@link BeanPostProcessor} interface and is thus
 * able to manipulate beans before the application is ready to start via the
 * postProcessBeforeInitialization method.
 */
@Service
public class MongoPostProcessor implements BeanPostProcessor {

    private final MongoCustomizer mongoCustomizer;

    public MongoPostProcessor(MongoCustomizer mongoCustomizer) {
        this.mongoCustomizer = mongoCustomizer;
    }

    /**
     * Here the bean is filtered, which belongs to class of
     * {@link ReactiveMongoRepositoryFactoryBean}. Spring creates a
     * {@link ReactiveMongoRepositoryFactoryBean} from a reactive mongo repository,
     * which is intercepted here and serves as a starting point to inject the
     * EnforcementPoint.
     *
     * @param bean     the new bean instance.
     * @param beanName the name of the bean.
     * @return the original bean or the manipulated bean, should it be of type
     *         {@link ReactiveMongoRepositoryFactoryBean}.
     */
    @Override
    public Object postProcessBeforeInitialization(Object bean, @SuppressWarnings("NullableProblems") String beanName)
            throws BeansException {
        if (bean.getClass().equals(ReactiveMongoRepositoryFactoryBean.class)) {
            addMongoCustomizer(bean);
        }
        return bean;
    }

    private void addMongoCustomizer(Object bean) {
        ((ReactiveMongoRepositoryFactoryBean<?, ?, ?>) bean).addRepositoryFactoryCustomizer(mongoCustomizer);
    }

}
