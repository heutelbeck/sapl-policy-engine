/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.dsl.interpreter;

import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.CombiningAlgorithmEnum;

class PDPCombiningAlgorithmInterpreter {
    PolicyDocumentCombiningAlgorithm interpretPdpCombiningAlgorithm(final CombiningAlgorithmEnum combiningAlgorithm) {
        if (combiningAlgorithm == null) {
            throw new SaplTestException("CombiningAlgorithm is null");
        }

        return switch (combiningAlgorithm) {
        case DENY_OVERRIDES -> PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES;
        case PERMIT_OVERRIDES -> PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES;
        case ONLY_ONE_APPLICABLE -> PolicyDocumentCombiningAlgorithm.ONLY_ONE_APPLICABLE;
        case DENY_UNLESS_PERMIT -> PolicyDocumentCombiningAlgorithm.DENY_UNLESS_PERMIT;
        case PERMIT_UNLESS_DENY -> PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY;
        };
    }
}
