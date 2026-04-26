/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.spring.pep.data.r2dbc;

import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.r2dbc.core.DatabaseClient;

import io.sapl.spring.pep.constraints.Signal.SqlShimSignal;
import io.sapl.spring.pep.data.ShimSignalContributor;
import lombok.val;

/**
 * Wraps every {@link DatabaseClient} bean in a CGLIB proxy that
 * (a) intercepts {@code sql(...)} via
 * {@link DatabaseClientShimMethodInterceptor}
 * so the produced query reflects any active {@link SqlShimSignal} obligation
 * before it leaves the JVM, and (b) introduces the
 * {@link ShimSignalContributor}
 * interface so the proxy is picked up by the PEP's contributor lookup as the
 * source of truth for which shim signals are actually fired.
 * <p>
 * Wrapping at the {@code DatabaseClient} level (rather than at the
 * {@code R2dbcEntityTemplate} level) is the single interception point that
 * covers every R2DBC dispatch path: the entity template, the derived-query
 * code path via {@code PartTreeR2dbcQuery}, {@code @Query}-annotated
 * repository methods, and direct user calls to {@code databaseClient.sql(...)}.
 */
public class DatabaseClientShimBeanPostProcessor implements BeanPostProcessor {

    private static final ShimSignalContributor CONTRIBUTOR = () -> Set.of(SqlShimSignal.TYPE);

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (!(bean instanceof DatabaseClient client)) {
            return bean;
        }
        if (bean instanceof ShimSignalContributor) {
            return bean;
        }
        val proxyFactory = new ProxyFactory(client);
        proxyFactory.setProxyTargetClass(false);
        proxyFactory.addInterface(ShimSignalContributor.class);
        proxyFactory.addAdvice(new DelegatingIntroductionInterceptor(CONTRIBUTOR));
        proxyFactory.addAdvice(new DatabaseClientShimMethodInterceptor());
        return proxyFactory.getProxy();
    }
}
