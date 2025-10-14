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
import jakarta.validation.constraints.NotNull;
import net.javacrumbs.jsonunit.assertj.JsonAssert.ConfigurableJsonAssert;
import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.StringAssert;

/**
 * Assertions for SAPL Val.
 *
 * @author Mohammed Aljer
 * @author Dominic Heutelbeck
 */
public class ValAssert extends AbstractAssert<ValAssert, Val> {

    ValAssert(Val actual) {
        super(actual, ValAssert.class);
    }

    public @NotNull StringAssert isError() {
        isNotNull();
        if (!actual.isError()) {
            failWithMessage("Expected Val to be an <ERROR[]> with any message but was <%s>.", actual);
        }
        return new StringAssert(actual.getMessage());
    }

    public @NotNull ValAssert isError(String expectederrorMessage) {
        isNotNull();
        if (!actual.isError() || !actual.getMessage().equals(expectederrorMessage)) {
            failWithMessage("Expected Val to be <ERROR[%s]> but was <%s>.", expectederrorMessage, actual);
        }
        return this;
    }

    public @NotNull ValAssert noError() {
        isNotNull();
        if (actual.isError()) {
            failWithMessage("Expected Val to not be an error but was <%s>.", actual);
        }
        return this;
    }

    public @NotNull ValAssert isUndefined() {
        isNotNull();
        if (!actual.isUndefined()) {
            failWithMessage("Expected Val to be <undefined> but was <%s>.", actual);
        }
        return this;
    }

    public @NotNull ValAssert isSecret() {
        isNotNull();
        if (!actual.isSecret()) {
            failWithMessage("Expected Val to be <SECRET> but was <%s>.", actual);
        }
        return this;
    }

    public @NotNull ConfigurableJsonAssert hasValue() {
        isNotNull();
        if (!actual.isDefined()) {
            failWithMessage("Excpected Val to be defined and have any value but was <%s>.", actual);
        }
        return JsonAssertions.assertThatJson(actual.get());
    }

    public @NotNull ConfigurableJsonAssert isTrue() {
        isNotNull();
        if (!actual.isBoolean() || !actual.getBoolean()) {
            failWithMessage("Expected Val to be <true> but was <%s>.", actual);
        }
        return JsonAssertions.assertThatJson(actual.get());
    }

    public @NotNull ConfigurableJsonAssert isFalse() {
        isNotNull();
        if (!actual.isBoolean() || actual.getBoolean()) {
            failWithMessage("Expected Val to be <false> but was <%s>.", actual);
        }
        return JsonAssertions.assertThatJson(actual.get());
    }

}
