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
package io.sapl.pdp.configuration.realm;

import java.io.Serial;

import io.sapl.api.SaplVersion;

import lombok.experimental.StandardException;

/**
 * Thrown when a realm index cannot be produced or when a received index is
 * malformed, unsigned/foreign-signed where a signature is required, for the
 * wrong realm, or stale (a rollback). A consumer treats this as a no-op and
 * keeps its current configuration.
 */
@StandardException
public class RealmIndexException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;
}
