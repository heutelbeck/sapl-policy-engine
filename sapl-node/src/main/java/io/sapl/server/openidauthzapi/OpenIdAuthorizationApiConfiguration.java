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
package io.sapl.server.openidauthzapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.val;

/**
 * Wires the request-size limit filter for the OpenID Authorization API
 * endpoint. The default of 16 KiB is generous for typical subscriptions
 * (subject + action + resource + small context) and bounds the impact of
 * oversized payloads on the Jackson deserializer.
 */
@Configuration(proxyBeanMethods = false)
class OpenIdAuthorizationApiConfiguration {

    @Bean
    FilterRegistrationBean<OpenIdRequestSizeLimitFilter> openIdRequestSizeLimitFilter(
            @Value("${io.sapl.server.openid-authz-api.max-request-body-bytes:16384}") long maxRequestBodyBytes) {
        val registration = new FilterRegistrationBean<>(new OpenIdRequestSizeLimitFilter(maxRequestBodyBytes));
        registration.addUrlPatterns("/access/v1/*");
        registration.setName("openIdRequestSizeLimitFilter");
        return registration;
    }
}
