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
package io.sapl.spring.config;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authorization.DefaultAuthorizationManagerFactory;
import org.springframework.security.config.core.GrantedAuthorityDefaults;

import lombok.experimental.UtilityClass;
import lombok.val;

/**
 * Shared resolution of the {@link MethodSecurityExpressionHandler} bean used
 * by both blocking and reactive method-security configurations. Honours an
 * application-supplied handler when present, otherwise falls back to a
 * {@link DefaultMethodSecurityExpressionHandler} configured with the
 * application's {@link GrantedAuthorityDefaults} role prefix.
 */
@UtilityClass
class MethodSecurityExpressionHandlers {

    static MethodSecurityExpressionHandler resolve(
            ObjectProvider<MethodSecurityExpressionHandler> expressionHandlerProvider,
            ObjectProvider<GrantedAuthorityDefaults> defaultsProvider, ApplicationContext context) {
        return expressionHandlerProvider.getIfAvailable(() -> defaultExpressionHandler(defaultsProvider, context));
    }

    private static MethodSecurityExpressionHandler defaultExpressionHandler(
            ObjectProvider<GrantedAuthorityDefaults> defaultsProvider, ApplicationContext context) {
        val handler = new DefaultMethodSecurityExpressionHandler();
        defaultsProvider.ifAvailable(d -> {
            val authFactory = new DefaultAuthorizationManagerFactory<MethodInvocation>();
            val rolePrefix  = d.getRolePrefix();
            authFactory.setRolePrefix(rolePrefix != null ? rolePrefix : "");
            handler.setAuthorizationManagerFactory(authFactory);
        });
        handler.setApplicationContext(context);
        return handler;
    }
}
