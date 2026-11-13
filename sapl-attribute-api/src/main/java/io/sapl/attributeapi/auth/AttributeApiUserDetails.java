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
package io.sapl.attributeapi.auth;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import io.sapl.api.SaplVersion;
import java.io.Serial;
import java.util.Collection;
import java.util.List;

/**
 * Used as principal for all auth modes.
 */
@Getter
@RequiredArgsConstructor
public class AttributeApiUserDetails implements UserDetails {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private final String username;
    private final String password;
    private final String pdpId;

    @Override
    @NullMarked
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Roles are not defined yet, but method is mandatory. Pdp id only.
        return List.of();
    }
}
