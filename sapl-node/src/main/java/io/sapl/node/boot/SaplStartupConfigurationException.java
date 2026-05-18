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
package io.sapl.node.boot;

import java.io.Serial;

import io.sapl.api.SaplVersion;
import lombok.Getter;

/**
 * Carries an operator facing description and an actionable next step. The
 * companion {@link SaplStartupConfigurationFailureAnalyzer} unwraps both into
 * the Spring Boot startup failure report so misconfiguration produces a short
 * clean message instead of a Spring stack trace.
 */
@Getter
public class SaplStartupConfigurationException extends IllegalStateException {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private final String action;

    public SaplStartupConfigurationException(String description, String action) {
        super(description);
        this.action = action;
    }

}
