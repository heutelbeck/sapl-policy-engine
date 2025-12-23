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
package io.sapl.spring.method.blocking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aopalliance.intercept.MethodInvocation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import io.sapl.spring.serialization.HttpServletRequestSerializer;
import io.sapl.spring.serialization.MethodInvocationSerializer;
import io.sapl.spring.serialization.ServerHttpRequestSerializer;
import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TestSetupUtil {

    public static ObjectMapper objectMapperWithSerializers() {
        final var module = new SimpleModule();
        module.addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
        module.addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
        module.addSerializer(ServerHttpRequest.class, new ServerHttpRequestSerializer());
        final var mapper = new ObjectMapper();
        mapper.registerModule(module);
        return mapper;
    }

    @SuppressWarnings("unchecked")
    public static AuthorizationSubscriptionBuilderService subscriptionBuilderService() {
        final var mapper                        = objectMapperWithSerializers();
        final var mockExpressionHandlerProvider = mock(ObjectProvider.class);
        when(mockExpressionHandlerProvider.getIfAvailable(any()))
                .thenReturn(new DefaultMethodSecurityExpressionHandler());
        final var mockMapperProvider = mock(ObjectProvider.class);
        when(mockMapperProvider.getIfAvailable(any())).thenReturn(mapper);
        final var mockDefaultsProvider = mock(ObjectProvider.class);
        final var mockContext          = mock(ApplicationContext.class);
        return new AuthorizationSubscriptionBuilderService(mockExpressionHandlerProvider, mockMapperProvider,
                mockDefaultsProvider, mockContext);
    }
}
