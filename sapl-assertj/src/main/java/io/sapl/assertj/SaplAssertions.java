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
package io.sapl.assertj;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import jakarta.validation.constraints.NotNull;
import lombok.experimental.UtilityClass;

/**
 * Assertions for SAPL Val and AuthorizationDecision.
 *
 * @author Mohammed Aljer
 * @author Dominic Heutelbeck
 */
@UtilityClass
public class SaplAssertions {

    /**
     * Assert AuthorizationDecision properties with possibility to chain assertions
     * like this:
     *
     * <pre>{@code
     * assertThatAuthorizationDecision(someDecision).isPermit().hasResource().isObject().containsKey("key");
     * }</pre>
     *
     * @param actual a Val to be examined.
     * @return new assertion for chaining.
     */
    @NotNull
    public static AuthorizationDecisionAssert assertThatAuthorizationDecision(AuthorizationDecision actual) {
        return new AuthorizationDecisionAssert(actual);
    }

    /**
     * Assert Val properties with possibility to chain assertions like this:
     *
     * <pre>{@code
     * assertThatVal(Val.ofJson("{\"key\" : \"value\"}").hasValue()
     *                                                  .isObject()
     *                                                  .containsKey("key");
     * }</pre>
     *
     * @param actual an AuthorizationDecision to be examined.
     * @return new assertion for chaining.
     */
    @NotNull
    public static ValAssert assertThatVal(Val actual) {
        return new ValAssert(actual);
    }

}
