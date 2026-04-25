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
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

import io.sapl.spring.pep.constraints.Signal.RelationalQueryShimSignal;
import io.sapl.spring.pep.data.ShimSignalContributor;
import lombok.val;

/**
 * Wraps every {@link R2dbcEntityTemplate} bean in a CGLIB proxy that
 * (a) intercepts the {@code Query}-bearing entry points via
 * {@link R2dbcShimMethodInterceptor} so they fire
 * {@link RelationalQueryShimSignal} against the active enforcement plan, and
 * (b) introduces the {@link ShimSignalContributor} interface so the proxy is
 * picked up by the PEP's contributor lookup as the source of truth for which
 * shim signals are actually fired.
 * </p>
 * Since the proxy is a CGLIB subclass of {@link R2dbcEntityTemplate},
 * injection sites typed as the concrete class continue to work, unlike the
 * earlier composition-based wrapper.
 */
public class R2dbcShimBeanPostProcessor implements BeanPostProcessor {

    private static final ShimSignalContributor CONTRIBUTOR = () -> Set.of(RelationalQueryShimSignal.TYPE);

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (!(bean instanceof R2dbcEntityTemplate template)) {
            return bean;
        }
        val proxyFactory = new ProxyFactory(template);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addInterface(ShimSignalContributor.class);
        proxyFactory.addAdvice(new DelegatingIntroductionInterceptor(CONTRIBUTOR));
        proxyFactory.addAdvice(new R2dbcShimMethodInterceptor());
        return proxyFactory.getProxy();
    }
}
