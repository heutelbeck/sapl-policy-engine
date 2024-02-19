package io.sapl.spring.data.r2dbc.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.spring.data.r2dbc.enforcement.R2dbcPolicyEnforcementPoint;
import io.sapl.spring.data.r2dbc.proxy.R2dbcBeanPostProcessor;
import io.sapl.spring.data.r2dbc.proxy.R2dbcRepositoryFactoryCustomizer;
import io.sapl.spring.data.r2dbc.proxy.R2dbcRepositoryProxyPostProcessor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@AutoConfiguration
public class SaplR2dbcAutoConfiguration {
    public SaplR2dbcAutoConfiguration() {
        log.info("# Setting up SAPL R2DBC policy enforcement points...");
    }

    @Bean
    R2dbcPolicyEnforcementPoint r2dbcPolicyEnforcementPoint(PolicyDecisionPoint policyDecisionPoint) {
        log.info("# Instantiate R2dbcPolicyEnforcementPoint...");
        return new R2dbcPolicyEnforcementPoint(policyDecisionPoint);
    }

    @Bean
    R2dbcRepositoryProxyPostProcessor r2dbcRepositoryProxyPostProcessor(
            R2dbcPolicyEnforcementPoint r2dbcPolicyEnforcementPoint) {
        log.info("# Instantiate R2dbcRepositoryProxyPostProcessor...");
        return new R2dbcRepositoryProxyPostProcessor(r2dbcPolicyEnforcementPoint);
    }

    @Bean
    R2dbcRepositoryFactoryCustomizer saplR2dbcRepositoryFactoryCustomizer(
            R2dbcRepositoryProxyPostProcessor r2dbcRepositoryProxyPostProcessor) {
        log.info("# Instantiate SaplR2dbcRepositoryFactoryCustomizer...");
        return new R2dbcRepositoryFactoryCustomizer(r2dbcRepositoryProxyPostProcessor);
    }

    @Bean
    static R2dbcBeanPostProcessor r2dbcBeanPostProcessor(
            ObjectProvider<R2dbcRepositoryFactoryCustomizer> r2dbcRepositoryFactoryCustomizerProvider) {
        log.info("# Instantiate R2dbcBeanPostProcessor...");
        return new R2dbcBeanPostProcessor(r2dbcRepositoryFactoryCustomizerProvider);
    }
}
