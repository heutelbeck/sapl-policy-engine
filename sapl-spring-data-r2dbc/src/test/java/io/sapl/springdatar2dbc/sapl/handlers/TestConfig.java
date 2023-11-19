package io.sapl.springdatar2dbc.sapl.handlers;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;

@TestConfiguration
public class TestConfig {

    @Bean
    public BeanFactoryResolver beanFactoryResolver(BeanFactory beanFactory) {
        return new BeanFactoryResolver(beanFactory);
    }

    @Bean
    public StandardEvaluationContext evaluationContext(BeanFactoryResolver beanFactoryResolver) {
        final StandardEvaluationContext evaluationContext = new StandardEvaluationContext();
        evaluationContext.setBeanResolver(beanFactoryResolver);
        return evaluationContext;
    }
}
