/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.coverage.api;

import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

/**
 * Interface for recording policy hits.
 */
public interface CoverageHitRecorder {

    /**
     * Internal method used by SAPL Coverage Recording to record a hit of an
     * io.sapl.grammar.sapl.PolicySet
     * 
     * @param hit a policy hit
     */
    void recordPolicySetHit(PolicySetHit hit);

    /**
     * Internal method used by SAPL Coverage Recording to record a hit of an
     * io.sapl.grammar.sapl.Policy
     * 
     * @param hit a policy hit
     */
    void recordPolicyHit(PolicyHit hit);

    /**
     * Internal method used by SAPL Coverage Recording to record a hit of an
     * io.sapl.grammar.sapl.Condition
     * 
     * @param hit a policy condition hit
     */
    void recordPolicyConditionHit(PolicyConditionHit hit);

    /**
     * Deletes all files used for coverage recording
     */
    void cleanCoverageHitFiles();

    /**
     * Creates files in target/ dir used for coverage reporting
     */
    void createCoverageHitFiles();

}
