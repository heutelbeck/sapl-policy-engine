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
package io.sapl.spring.method.metadata;

import org.springframework.security.authorization.AuthorizationDecision;

import io.sapl.api.SaplVersion;
import lombok.Getter;

@Getter
public class SaplAuthorizationDecision extends AuthorizationDecision {

    private static final long serialVersionUID = SaplVersion.VERISION_UID;

    transient SaplAttribute attribute;

    public SaplAuthorizationDecision(boolean granted, SaplAttribute attribute) {
        super(granted);
        this.attribute = attribute;
    }

    @Override
    public String toString() {
        return "SaplAuthorizationDecision(granted=" + this.isGranted() + ", annotation=" + attribute + ")";
    }

}
