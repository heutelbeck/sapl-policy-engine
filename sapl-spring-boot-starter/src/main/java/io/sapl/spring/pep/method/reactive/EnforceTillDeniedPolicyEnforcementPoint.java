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
package io.sapl.spring.pep.method.reactive;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;

import io.sapl.spring.method.metadata.EnforceTillDenied;
import io.sapl.spring.method.metadata.SaplAttributeRegistry;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Scaffolded reactive PEP for {@link EnforceTillDenied}.
 * </p>
 * Currently a pass-through: when the annotation is present on the invoked
 * method, the PEP logs a WARN advertising that no enforcement is applied and
 * returns the protected method's {@code Publisher} unchanged. The continuous
 * decision stream, the per-decision plan re-planning and the Plan-driven
 * lifecycle signal firing (subscription/cancel/complete/terminate) will be
 * implemented separately. The scaffold exists so the rest of the wiring
 * (advisor registration, attribute lookup, integration with the new PEP
 * configuration) can be exercised end-to-end while the streaming logic is
 * built.
 *
 * @since 4.1.0
 */
@Slf4j
@RequiredArgsConstructor
public final class EnforceTillDeniedPolicyEnforcementPoint implements MethodInterceptor {

    private static final String WARN_SCAFFOLD_NOT_ENFORCING = "@EnforceTillDenied scaffold on {}: no enforcement applied (streaming PEP implementation pending in 4.1.0).";

    private final ObjectProvider<SaplAttributeRegistry> attributeRegistryProvider;

    @Override
    public Object invoke(@NonNull MethodInvocation methodInvocation) throws Throwable {
        val attribute = attributeRegistryProvider.getObject().getSaplAttributeForAnnotationType(methodInvocation,
                EnforceTillDenied.class);
        if (attribute.isEmpty()) {
            return methodInvocation.proceed();
        }
        log.warn(WARN_SCAFFOLD_NOT_ENFORCING, methodInvocation.getMethod());
        return methodInvocation.proceed();
    }
}
