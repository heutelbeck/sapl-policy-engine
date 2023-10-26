/*
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

import java.util.List;

import io.sapl.test.coverage.api.model.PolicyConditionHit;
import io.sapl.test.coverage.api.model.PolicyHit;
import io.sapl.test.coverage.api.model.PolicySetHit;

/**
 * Interface for reading policy hits.
 */
public interface CoverageHitReader {

    /**
     * Internal method used by SAPL Coverage Reading to read all hits of
     * io.sapl.grammar.sapl.PolicySet's
     * 
     * @return List of {@link PolicySetHit}
     */
    List<PolicySetHit> readPolicySetHits();

    /**
     * Internal method used by SAPL Coverage Reading to read all hits of
     * io.sapl.grammar.sapl.Policy's
     * 
     * @return List of {@link PolicySetHit}
     */
    List<PolicyHit> readPolicyHits();

    /**
     * Internal method used by SAPL Coverage Reading to read all hits of
     * io.sapl.grammar.sapl.Condition's
     * 
     * @return List of {@link PolicySetHit}
     */
    List<PolicyConditionHit> readPolicyConditionHits();

    /**
     * Deletes all files used for coverage recording
     */
    void cleanCoverageHitFiles();

}
