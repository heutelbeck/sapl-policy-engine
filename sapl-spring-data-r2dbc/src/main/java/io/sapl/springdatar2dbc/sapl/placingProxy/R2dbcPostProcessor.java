package io.sapl.springdatar2dbc.sapl.placingProxy;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactoryBean;
import org.springframework.stereotype.Service;

@Service
public class R2dbcPostProcessor implements BeanPostProcessor {

    private final R2dbcCustomizer r2dbcCustomizer;

    public R2dbcPostProcessor(R2dbcCustomizer r2dbcCustomizer) {
        this.r2dbcCustomizer = r2dbcCustomizer;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean.getClass().equals(R2dbcRepositoryFactoryBean.class)) {
            ((R2dbcRepositoryFactoryBean<?, ?, ?>) bean).addRepositoryFactoryCustomizer(r2dbcCustomizer);
        }
        return bean;
    }
}
