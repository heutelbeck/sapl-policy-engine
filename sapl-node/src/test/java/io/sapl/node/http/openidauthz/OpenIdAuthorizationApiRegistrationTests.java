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
package io.sapl.node.http.openidauthz;

import static org.assertj.core.api.Assertions.assertThat;

import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.pdp.BlockingPolicyDecisionPoint;
import io.sapl.reactive.api.tenant.BlockingTenantResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

@DisplayName("OpenID Authorization API registration")
@ExtendWith(MockitoExtension.class)
class OpenIdAuthorizationApiRegistrationTests {

    private static final String NODE_ENABLE_PROPERTY = "io.sapl.node.openid-authz-api.enabled=false";

    @Mock
    private BlockingPolicyDecisionPoint pdp;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("registers the OpenID Authorization API by default")
    void whenNoOpenIdApiPropertyThenControllerIsRegistered() {
        contextRunner().run(context -> assertThat(context).hasSingleBean(OpenIdAuthorizationApiController.class));
    }

    @Test
    @DisplayName("does not register the OpenID Authorization API when disabled")
    void whenNodeOpenIdApiDisabledThenControllerIsNotRegistered() {
        contextRunner().withPropertyValues(NODE_ENABLE_PROPERTY)
                .run(context -> assertThat(context).doesNotHaveBean(OpenIdAuthorizationApiController.class));
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner().withUserConfiguration(OpenIdAuthorizationApiController.class)
                .withBean(BlockingPolicyDecisionPoint.class, () -> pdp)
                .withBean(BlockingTenantResolver.class, () -> () -> StreamingPolicyDecisionPoint.DEFAULT_PDP_ID)
                .withBean(ObjectMapper.class, () -> objectMapper);
    }
}
