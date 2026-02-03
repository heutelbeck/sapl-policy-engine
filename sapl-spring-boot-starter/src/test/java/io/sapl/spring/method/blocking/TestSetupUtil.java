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

import io.sapl.spring.subscriptions.AuthorizationSubscriptionBuilderService;
import lombok.experimental.UtilityClass;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@UtilityClass
public class TestSetupUtil {

    public static ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @SuppressWarnings("unchecked")
    public static AuthorizationSubscriptionBuilderService subscriptionBuilderService() {
        final var mapper                        = objectMapper();
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
