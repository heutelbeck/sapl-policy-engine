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
 * Array slicing: {@code base[1:3]}, {@code base[::2]}, {@code base[1:]}
 *
 * @param base the expression to slice
 * @param from start index, or null for beginning
 * @param to end index (exclusive), or null for end
 * @param step step value, or null for 1
 * @param location voterMetadata location
 */
public record SliceStep(
        @NonNull Expression base,
        Integer from,
        Integer to,
        Integer step,
        @NonNull SourceLocation location) implements Step {}
