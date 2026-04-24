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
package io.sapl.spring.pep.method.blocking;

import java.util.List;

import lombok.experimental.UtilityClass;
import lombok.val;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Subject lookup for the blocking AOP PEPs. Reads the current
 * {@link Authentication} from the thread-bound {@link SecurityContextHolder}
 * and synthesises a canonical {@link AnonymousAuthenticationToken} when the
 * context holds none. Never throws; the PDP always receives a meaningful
 * subject so policies can explicitly decide on the anonymous case rather than
 * the PEP failing the request opaquely.
 */
@UtilityClass
public class BlockingAuthentication {

    private static final Authentication ANONYMOUS = new AnonymousAuthenticationToken("sapl-anonymous", "anonymousUser",
            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

    public static Authentication current() {
        val authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication : ANONYMOUS;
    }
}
