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
package io.sapl.spring.serialization;

import java.io.Serial;

import org.aopalliance.intercept.MethodInvocation;

import io.sapl.api.SaplVersion;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson module that registers serializers for servlet-based Spring
 * applications.
 */
public class SaplServletJacksonModule extends SimpleModule {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Creates a new servlet Jackson module with serializers for HttpServletRequest
     * and MethodInvocation.
     */
    public SaplServletJacksonModule() {
        super("SaplServletJacksonModule");
        addSerializer(HttpServletRequest.class, new HttpServletRequestSerializer());
        addSerializer(MethodInvocation.class, new MethodInvocationSerializer());
    }

}
