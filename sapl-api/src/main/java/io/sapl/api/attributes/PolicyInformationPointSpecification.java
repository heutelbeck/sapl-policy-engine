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
package io.sapl.api.attributes;

import lombok.NonNull;

import java.util.Set;

/**
 * Metadata describing a Policy Information Point (PIP) library.
 * <p>
 * A PIP specification encapsulates the name and all attribute finders provided
 * by a PIP. It is created during PIP
 * registration from @PolicyInformationPoint annotated classes or programmatic
 * registration.
 * <p>
 * The name serves as a namespace for all attributes in the PIP. For example, a
 * PIP named "time" would provide
 * attributes like "time.now", "time.dayOfWeek", etc.
 *
 * @param name
 * the PIP name (namespace for all its attributes)
 * @param attributeFinders
 * the set of attribute finder specifications provided by this PIP
 *
 * @see PolicyInformationPointImplementation
 * @see io.sapl.api.pip.PolicyInformationPoint
 */
public record PolicyInformationPointSpecification(
        @NonNull String name,
        @NonNull Set<AttributeFinderSpecification> attributeFinders) {}
