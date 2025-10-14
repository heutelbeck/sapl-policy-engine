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

import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.test.coverage.api.CoverageAPIFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.Path;

@Named
@Singleton
public class CoverageAPIHelper {

    public CoverageTargets readHits(Path baseDir) throws IOException {
        final var reader = CoverageAPIFactory.constructCoverageHitReader(baseDir);
        return new CoverageTargets(reader.readPolicySetHits(), reader.readPolicyHits(),
                reader.readPolicyConditionHits());
    }

}
