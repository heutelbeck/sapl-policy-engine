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
package io.sapl.node.auth;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import lombok.experimental.UtilityClass;

/**
 * Single source of truth for SAPL role and authority constants.
 * <p>
 * Spring Security's
 * {@link org.springframework.security.core.userdetails.User.UserBuilder#roles}
 * prefixes its argument with {@code ROLE_}, while
 * {@link SimpleGrantedAuthority} expects the already-prefixed form. Both
 * forms are exposed here so the prefix lives in exactly one place.
 */
@UtilityClass
public class SaplRoles {

    /**
     * Role name without the {@code ROLE_} prefix, suitable for {@code roles()}
     * builders.
     */
    public static final String PDP_CLIENT = "PDP_CLIENT";

    /**
     * Authority string with the {@code ROLE_} prefix, suitable for
     * {@link SimpleGrantedAuthority}.
     */
    public static final String ROLE_PDP_CLIENT = "ROLE_PDP_CLIENT";

    /** Pre-built singleton authority for PDP clients. */
    public static final SimpleGrantedAuthority PDP_CLIENT_AUTHORITY = new SimpleGrantedAuthority(ROLE_PDP_CLIENT);

    /** Pre-built singleton authority list for PDP clients. */
    public static final List<SimpleGrantedAuthority> PDP_CLIENT_AUTHORITIES = List.of(PDP_CLIENT_AUTHORITY);
}
