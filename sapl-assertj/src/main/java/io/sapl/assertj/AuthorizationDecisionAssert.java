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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import jakarta.validation.constraints.NotNull;
import net.javacrumbs.jsonunit.assertj.JsonAssert.ConfigurableJsonAssert;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import net.javacrumbs.jsonunit.assertj.JsonListAssert;
import org.assertj.core.api.AbstractAssert;

import java.util.Objects;

/**
 * Assertions for SAPL AuthorizationDecision.
 *
 * @author Mohammed Aljer
 * @author Dominic Heutelbeck
 */
public class AuthorizationDecisionAssert extends AbstractAssert<AuthorizationDecisionAssert, AuthorizationDecision> {

    AuthorizationDecisionAssert(AuthorizationDecision actual) {
        super(actual, AuthorizationDecisionAssert.class);
    }

    public @NotNull AuthorizationDecisionAssert hasDecision(Decision expectedDecision) {
        isNotNull();
        if (!Objects.equals(expectedDecision, actual.getDecision())) {
            failWithMessage("Expected AuthorizationDecision to have decision <%s> but was <%s>.", expectedDecision,
                    actual.getDecision());
        }
        return this;
    }

    public @NotNull AuthorizationDecisionAssert isPermit() {
        return hasDecision(Decision.PERMIT);
    }

    public @NotNull AuthorizationDecisionAssert isDeny() {
        return hasDecision(Decision.DENY);
    }

    public @NotNull AuthorizationDecisionAssert isNotApplicable() {
        return hasDecision(Decision.NOT_APPLICABLE);
    }

    public @NotNull AuthorizationDecisionAssert isIndeterminate() {
        return hasDecision(Decision.INDETERMINATE);
    }

    public @NotNull JsonListAssert hasObligations() {
        isNotNull();
        final var optionalObligations = actual.getObligations();
        if (optionalObligations.isEmpty()) {
            failWithMessage("Expected AuthorizationDecision to have obligations but it had none.");
        }
        return JsonAssertions.assertThatJson(optionalObligations.get()).isArray().isNotEmpty();
    }

    public @NotNull AuthorizationDecisionAssert hasNoObligations() {
        isNotNull();
        final var optionalObligations = actual.getObligations();
        if (optionalObligations.isPresent()) {
            failWithMessage("Expected AuthorizationDecision to have no obligations but they were <%s>.",
                    optionalObligations.get());
        }
        return this;
    }

    public @NotNull JsonListAssert hasAdvice() {
        isNotNull();
        final var optionalAdvice = actual.getAdvice();
        if (optionalAdvice.isEmpty()) {
            failWithMessage("Expected AuthorizationDecision to have advice but it had none.");
        }
        return JsonAssertions.assertThatJson(optionalAdvice.get()).isArray().isNotEmpty();
    }

    public @NotNull AuthorizationDecisionAssert hasNoAdvice() {
        isNotNull();
        final var optionalAdvice = actual.getAdvice();
        if (optionalAdvice.isPresent()) {
            failWithMessage("Expected AuthorizationDecision to have no advice but they were <%s>.",
                    optionalAdvice.get());
        }
        return this;
    }

    public @NotNull ConfigurableJsonAssert hasResource() {
        isNotNull();
        final var optionalResource = actual.getResource();
        if (optionalResource.isEmpty()) {
            failWithMessage("Expected AuthorizationDecision to have a resource but it had none.");
        }
        return JsonAssertions.assertThatJson(optionalResource.get());
    }

    public @NotNull AuthorizationDecisionAssert hasNoResource() {
        isNotNull();
        final var optionalResource = actual.getResource();
        if (optionalResource.isPresent()) {
            failWithMessage("Expected AuthorizationDecision to have no resource but was <%s>.", optionalResource.get());
        }
        return this;
    }
}
