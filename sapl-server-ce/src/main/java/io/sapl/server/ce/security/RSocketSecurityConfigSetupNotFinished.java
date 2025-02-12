/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.rsocket.EnableRSocketSecurity;
import org.springframework.security.config.annotation.rsocket.RSocketSecurity;
import org.springframework.security.rsocket.core.PayloadSocketAcceptorInterceptor;

import io.sapl.server.ce.model.setup.condition.SetupNotFinishedCondition;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableRSocketSecurity
@RequiredArgsConstructor
@EnableReactiveMethodSecurity
@Conditional(SetupNotFinishedCondition.class)
public class RSocketSecurityConfigSetupNotFinished {
    private static void customize(RSocketSecurity.AuthorizePayloadsSpec spec) {
        spec.anyRequest().denyAll();
    }

    /**
     * The PayloadSocketAcceptorInterceptor Bean (rsocketPayloadAuthorization)
     * configures the Security Filter Chain for Rsocket Payloads. Deny everything.
     */
    @Bean
    PayloadSocketAcceptorInterceptor rsocketPayloadAuthorization(RSocketSecurity security) {
        security = security.authorizePayload(RSocketSecurityConfigSetupNotFinished::customize);
        return security.build();

    }
}
