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
package io.sapl.compiler;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.traced.AttributeRecord;
import io.sapl.api.pdp.traced.ConditionHit;

import java.util.List;

/**
 * Result of policy body compilation with coverage tracking information.
 *
 * @param value the evaluation result
 * @param contributingAttributes attributes that contributed to the result
 * @param hits condition evaluation hits with indices and results
 * @param numberOfConditions total number of conditions in the policy body
 */
public record TracedPolicyBodyResultAndCoverage(
        Value value,
        List<AttributeRecord> contributingAttributes,
        List<ConditionHit> hits,
        long numberOfConditions) {}
