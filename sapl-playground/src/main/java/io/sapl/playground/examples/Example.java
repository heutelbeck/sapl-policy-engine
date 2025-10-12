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
package io.sapl.playground.examples;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;

import java.util.List;

/**
 * Represents a complete playground example with policies, subscription, and
 * variables.
 *
 * @param slug unique identifier for URL fragments
 * @param displayName human-readable name shown in UI
 * @param description explanation of what the example demonstrates
 * @param policies list of SAPL policy documents
 * @param combiningAlgorithm algorithm to use when multiple policies apply
 * @param subscription authorization subscription JSON
 * @param variables variables JSON
 */
public record Example(
        String slug,
        String displayName,
        String description,
        List<String> policies,
        PolicyDocumentCombiningAlgorithm combiningAlgorithm,
        String subscription,
        String variables) {}
