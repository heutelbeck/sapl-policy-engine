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
package io.sapl.extension.jwt;

enum DispatchMode {

    /**
     * Dispatcher returns the true Base64Url-encoded key, matching the kid
     */
    TRUE,
    /**
     * Dispatcher returns the true Base64-encoded key, matching the kid
     */
    BASIC,
    /**
     * Dispatcher returns a wrong Base64Url-encoded key, not matching the kid
     */
    WRONG,
    /**
     * Dispatcher returns the key with Base64(URL) encoding errors
     */
    INVALID,
    /**
     * Dispatcher returns bogus data, not resembling an encoded key
     */
    BOGUS,
    /**
     * Dispatcher always returns 404 - unknown
     */
    UNKNOWN

}
