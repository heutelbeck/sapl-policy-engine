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
package io.sapl.mavenplugin.test.coverage.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.sapl.test.coverage.api.CoverageAPIFactory;
import io.sapl.test.coverage.api.model.PolicySetHit;

class CoverageAPIHelperTests {

    @Test
    void test(@TempDir Path tempDir) throws IOException {
        final var helper = new CoverageAPIHelper();
        final var writer = CoverageAPIFactory.constructCoverageHitRecorder(tempDir);

        final var hits1 = helper.readHits(tempDir);
        assertEquals(0, hits1.getPolicySets().size());

        writer.recordPolicySetHit(new PolicySetHit("testSet"));
        final var hits2 = helper.readHits(tempDir);
        assertEquals(1, hits2.getPolicySets().size());
    }

}
