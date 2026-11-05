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
package io.sapl.api;

import lombok.experimental.UtilityClass;

/**
 * Defines the serialization compatibility identifier shared by SAPL classes.
 * This identifier is maintained explicitly and does not identify an engine build.
 * Use {@code io.sapl.pdp.SaplBuildInfo.version()} from the PDP module for the
 * actual engine version, including prerelease identifiers.
 */
@UtilityClass
public class SaplVersion {
    public static final long VERSION_UID = 4_02_00L;
}
