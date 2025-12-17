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
package io.sapl.mavenplugin.test.coverage.report;

import io.sapl.api.coverage.PolicyCoverageData;
import lombok.experimental.UtilityClass;

import java.util.Collection;
import java.util.List;

/**
 * Provides sample PolicyCoverageData for tests.
 */
@UtilityClass
public class SampleCoverageInformation {

    private static final String POLICY_SOURCE = """
            import test.upper as uppies

            set "testPolicies"
            deny-unless-permit

            policy "policy 1"
            permit
                action == "read"
            where
                subject.<test.upper> == "WILLI";
                var test = 1;
                time.dayOfWeekFrom(<time.now>) =~ "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY";
            """;

    /**
     * Returns sample coverage data for testing HTML report generation.
     */
    public static Collection<PolicyCoverageData> policies() {
        var policy = new PolicyCoverageData("policy_1.sapl", POLICY_SOURCE, "set");

        policy.recordTargetHit(true);

        policy.recordConditionHit(0, 10, true);
        policy.recordConditionHit(0, 10, false);

        policy.recordConditionHit(1, 12, true);

        return List.of(policy);
    }
}
