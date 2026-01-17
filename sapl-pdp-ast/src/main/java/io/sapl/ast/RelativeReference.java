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
package io.sapl.ast;

import io.sapl.api.model.SourceLocation;
import lombok.NonNull;

/**
 * Relative reference: {@code @} (value) or {@code #} (location).
 * <p>
 * Relative references are pure and not subscription-dependent - they refer to
 * the current context in filters/conditions, not the authorization
 * subscription.
 *
 * @param type the relative reference type
 * @param location voterMetadata location
 */
public record RelativeReference(@NonNull RelativeType type, @NonNull SourceLocation location) implements Expression {}
