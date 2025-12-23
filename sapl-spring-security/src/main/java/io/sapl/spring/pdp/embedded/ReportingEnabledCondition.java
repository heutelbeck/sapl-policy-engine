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
package io.sapl.spring.pdp.embedded;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Condition that matches when at least one reporting property is enabled.
 * <p>
 * This condition is true if any of the following properties is set to
 * {@code true}:
 * <ul>
 * <li>{@code io.sapl.pdp.embedded.print-trace}</li>
 * <li>{@code io.sapl.pdp.embedded.print-json-report}</li>
 * <li>{@code io.sapl.pdp.embedded.print-text-report}</li>
 * </ul>
 */
class ReportingEnabledCondition implements Condition {

    private static final String PREFIX = "io.sapl.pdp.embedded.";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var environment = context.getEnvironment();
        return "true".equalsIgnoreCase(environment.getProperty(PREFIX + "print-trace"))
                || "true".equalsIgnoreCase(environment.getProperty(PREFIX + "print-json-report"))
                || "true".equalsIgnoreCase(environment.getProperty(PREFIX + "print-text-report"));
    }

}
