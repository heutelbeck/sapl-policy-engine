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
package io.sapl.validation;

import io.sapl.api.interpreter.Val;

@FunctionalInterface
public interface Validator {

    public static Validator NOOP = v -> {};

    void validate(Val v) throws ValidationException;

    default Validator or(Validator thatValidator) {
        return v -> {
            var    thisInvalid = false;
            String thisError   = "";
            try {
                validate(v);
            } catch (ValidationException e) {
                thisInvalid = true;
                thisError   = e.getMessage();
            }
            var    thatInvalid = false;
            String thatError   = "";
            try {
                thatValidator.validate(v);
            } catch (ValidationException e) {
                thatInvalid = true;
                thatError   = e.getMessage();
            }
            if (thisInvalid && thatInvalid) {
                throw new ValidationException(thisError + ' ' + thatError);
            }
        };
    }
}
