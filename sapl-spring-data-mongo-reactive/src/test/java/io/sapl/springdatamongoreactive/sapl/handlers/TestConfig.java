package io.sapl.springdatamongoreactive.sapl.handlers;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;

@TestConfiguration
public class TestConfig {

    @Bean
    public BeanResolver beanFactoryResolver(BeanFactory beanFactory) {
        return new BeanFactoryResolver(beanFactory);
    }

    @Bean
    public StandardEvaluationContext evaluationContext(BeanResolver beanResolver) {
        final StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
        evaluationContext.setBeanResolver(beanResolver);
        return evaluationContext;
    }
}
