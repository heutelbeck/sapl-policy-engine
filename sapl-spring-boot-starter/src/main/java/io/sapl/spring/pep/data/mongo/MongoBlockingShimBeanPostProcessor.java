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
package io.sapl.spring.pep.data.mongo;

import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.data.mongodb.core.MongoTemplate;

import io.sapl.spring.pep.constraints.Signal.MongoDbQueryShimSignal;
import io.sapl.spring.pep.data.ShimSignalContributor;
import lombok.val;

/**
 * Blocking counterpart of {@link MongoShimBeanPostProcessor}. Wraps every
 * {@link MongoTemplate} bean in a CGLIB proxy that (a) narrows data-reaching
 * operations via {@link MongoBlockingShimMethodInterceptor}, and (b) introduces
 * the {@link ShimSignalContributor} interface so the proxy is the source of
 * truth the PEP uses to learn that {@link MongoDbQueryShimSignal} is actually
 * fired. Because the contributor rides on the proxy, the signal is advertised
 * to
 * the planner only when a blocking template is genuinely present and shimmed.
 */
public class MongoBlockingShimBeanPostProcessor implements BeanPostProcessor {

    private static final ShimSignalContributor CONTRIBUTOR = () -> Set.of(MongoDbQueryShimSignal.SIGNAL_TYPE);

    @Override
    public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) throws BeansException {
        if (!(bean instanceof MongoTemplate template)) {
            return bean;
        }
        val proxyFactory = new ProxyFactory(template);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addInterface(ShimSignalContributor.class);
        proxyFactory.addAdvice(new DelegatingIntroductionInterceptor(CONTRIBUTOR));
        proxyFactory.addAdvice(new MongoBlockingShimMethodInterceptor());
        return proxyFactory.getProxy();
    }
}
