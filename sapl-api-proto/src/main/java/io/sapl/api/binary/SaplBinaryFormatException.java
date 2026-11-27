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
package io.sapl.api.binary;

import java.io.IOException;
import java.io.Serial;

import io.sapl.api.SaplVersion;
import lombok.Getter;

/**
 * Why a payload is not the wire object it should be in the SAPL binary format,
 * or why a wire object has no form in it.
 */
@Getter
public final class SaplBinaryFormatException extends IOException {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    private static final String ERROR_BLANK_SUBSCRIPTION_ID     = "A subscription id must not be empty or blank.";
    private static final String ERROR_DUPLICATE_SUBSCRIPTION_ID = "A subscription id is used twice.";
    private static final String ERROR_INVALID_NUMBER            = "A number is malformed or out of range.";
    private static final String ERROR_MALFORMED                 = "The payload is not a SAPL binary payload of the expected kind.";
    private static final String ERROR_SECRETS_NOT_AN_OBJECT     = "The secrets of a subscription must be an object.";
    private static final String ERROR_TOO_DEEP                  = "Arrays and objects are nested too deeply.";
    private static final String ERROR_TOO_MANY_SUBSCRIPTIONS    = "The multi-subscription has too many subscriptions.";
    private static final String ERROR_UNPAIRED_SURROGATE        = "A text holds an unpaired surrogate, which UTF-8 cannot carry.";

    /**
     * The reasons a payload is refused, or a wire object has no form.
     */
    public enum Reason {
        /**
         * The payload is cut off, followed by more bytes, has no lead byte of
         * version 1, an unknown tag, a length or count beyond the payload, a
         * varint beyond 64 bits, or text that is not UTF-8.
         */
        MALFORMED(ERROR_MALFORMED),
        /** A number is beyond the limits of the value model. */
        INVALID_NUMBER(ERROR_INVALID_NUMBER),
        /** Arrays and objects are nested more than 500 deep. */
        TOO_DEEP(ERROR_TOO_DEEP),
        /** The secrets of a subscription are not an object. */
        SECRETS_NOT_AN_OBJECT(ERROR_SECRETS_NOT_AN_OBJECT),
        /** A multi-subscription holds more subscriptions than allowed. */
        TOO_MANY_SUBSCRIPTIONS(ERROR_TOO_MANY_SUBSCRIPTIONS),
        /** An id of a multi-subscription is empty or blank. */
        BLANK_SUBSCRIPTION_ID(ERROR_BLANK_SUBSCRIPTION_ID),
        /** An id of a multi-subscription or a multi-decision is used twice. */
        DUPLICATE_SUBSCRIPTION_ID(ERROR_DUPLICATE_SUBSCRIPTION_ID),
        /** A text to write holds an unpaired surrogate. */
        UNPAIRED_SURROGATE(ERROR_UNPAIRED_SURROGATE);

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    /** Why the payload is refused or the wire object has no form. */
    private final Reason reason;

    /**
     * @param reason why the payload is refused or the wire object has no form
     */
    public SaplBinaryFormatException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }
}
