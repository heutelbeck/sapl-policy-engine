/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.interpreter.context;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import reactor.util.context.Context;

class AuthorizationContextTests {

    @Test
    void when_setReservedVariableName_then_throw() {
        var ctx = Context.empty();
        assertThatThrownBy(() -> AuthorizationContext.setVariable(ctx, "subject", Val.NULL))
                .hasMessage(String.format(AuthorizationContext.CANNOT_OVERWRITE_REQUEST_VARIABLE_S_ERROR, "subject"));
        assertThatThrownBy(() -> AuthorizationContext.setVariable(ctx, "action", Val.NULL))
                .hasMessage(String.format(AuthorizationContext.CANNOT_OVERWRITE_REQUEST_VARIABLE_S_ERROR, "action"));
        assertThatThrownBy(() -> AuthorizationContext.setVariable(ctx, "resource", Val.NULL))
                .hasMessage(String.format(AuthorizationContext.CANNOT_OVERWRITE_REQUEST_VARIABLE_S_ERROR, "resource"));
        assertThatThrownBy(() -> AuthorizationContext.setVariable(ctx, "environment", Val.NULL)).hasMessage(
                String.format(AuthorizationContext.CANNOT_OVERWRITE_REQUEST_VARIABLE_S_ERROR, "environment"));
    }
}
