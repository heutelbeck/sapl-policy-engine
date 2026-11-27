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
package io.sapl.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.semver4j.Semver;

import lombok.val;

@DisplayName("Node build information identifies the packaged Node artifact")
class SaplNodeBuildInfoTests {

    @Test
    @DisplayName("The Node version is read from its own Maven filtered artifact metadata")
    void whenVersionIsRequestedThenPackagedNodeVersionIsReported() throws IOException {
        val properties = new Properties();
        try (val resource = SaplNodeBuildInfo.class.getResourceAsStream("/META-INF/sapl-node-version.properties")) {
            assertThat(resource).isNotNull();
            properties.load(resource);
        }

        assertThat(properties).containsEntry("saplArtifactId", "sapl-node").containsEntry("saplGroupId", "io.sapl");
        assertThat(SaplNodeBuildInfo.version()).isEqualTo(properties.getProperty("saplVersion")).doesNotContain("${")
                .isNotEqualTo("unknown").satisfies(value -> assertThat(Semver.isValid(value)).isTrue());
    }
}
