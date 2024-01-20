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
package io.sapl.pdp.config;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data structure holding the configured algorithm to be used to combine SAPL
 * documents and configured system variables to be available in all policies.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyDecisionPointConfiguration {

    private PolicyDocumentCombiningAlgorithm algorithm = PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES;

    private Map<String, JsonNode> variables = new HashMap<>();

}
