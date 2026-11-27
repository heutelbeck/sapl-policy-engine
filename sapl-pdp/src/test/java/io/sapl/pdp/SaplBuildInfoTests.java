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
package io.sapl.pdp;

import static io.sapl.pdp.SaplBuildInfo.minimumVersionRequirement;
import static io.sapl.pdp.SaplBuildInfo.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.semver4j.Semver;

import lombok.val;

@DisplayName("Engine build information identifies the packaged PDP artifact")
class SaplBuildInfoTests {

    @Test
    @DisplayName("The packaged engine satisfies its generated minimum version requirement")
    void whenMinimumRequirementIsRequestedThenPackagedEngineSatisfiesIt() {
        assertThat(minimumVersionRequirement()).isEqualTo(minimumVersionRequirement(version()))
                .satisfies(requirement -> assertThat(new Semver(version()).satisfies(requirement)).isTrue());
    }

    @ParameterizedTest(name = "Engine {0} requires {1}")
    @DisplayName("Generated requirements include prereleases of the installed base version and ignore build metadata")
    @CsvSource(delimiter = '|', value = { "4.2.0 | >=4.2.0", "4.2.0-SNAPSHOT | >=4.2.0-0", "4.3.0-SNAPSHOT | >=4.3.0-0",
            "4.2.0-rc.1 | >=4.2.0-0", "4.2.0+build.7 | >=4.2.0", "4.2.0-SNAPSHOT+build.7 | >=4.2.0-0",
            "0.2.0 | >=0.2.0" })
    void whenBuildVersionIsKnownThenMinimumRequirementMatchesItsReleaseKind(String engineVersion, String expected) {
        assertThat(minimumVersionRequirement(engineVersion)).isEqualTo(expected)
                .satisfies(requirement -> assertThat(new Semver(engineVersion).satisfies(requirement)).isTrue());
    }

    @NullAndEmptySource
    @ParameterizedTest(name = "Invalid build version: {0}")
    @ValueSource(strings = { " ", "unknown", "${project.version}", "4.02.0", "broken" })
    @DisplayName("Missing or invalid build versions cannot silently disable generated engine requirements")
    void whenBuildVersionIsInvalidThenMinimumRequirementFailsClearly(String engineVersion) {
        assertThatThrownBy(() -> minimumVersionRequirement(engineVersion)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sapl-pdp").hasMessageContaining("semantic version");
    }

    @ParameterizedTest(name = "Installed engine: {0}")
    @ValueSource(strings = { "4.2.0", "4.2.0-SNAPSHOT" })
    @DisplayName("Generated minimum requirements retain the explicit opt in for later prerelease versions")
    void whenLaterPrereleaseIsComparedThenCurrentDefaultDoesNotAcceptIt(String engineVersion) {
        assertThat(new Semver("4.3.0-SNAPSHOT").satisfies(minimumVersionRequirement(engineVersion))).isFalse();
    }

    @Test
    @DisplayName("The engine version is the Maven filtered version of sapl-pdp")
    void whenVersionIsRequestedThenPackagedPdpVersionIsReported() throws IOException {
        val properties = new Properties();
        try (val resource = SaplBuildInfo.class.getResourceAsStream("/saplversion.properties")) {
            assertThat(resource).isNotNull();
            properties.load(resource);
        }

        assertThat(properties).containsEntry("saplArtifactId", "sapl-pdp").containsEntry("saplGroupId", "io.sapl");
        assertThat(SaplBuildInfo.version()).isEqualTo(properties.getProperty("saplVersion")).doesNotContain("${")
                .isNotEqualTo("unknown").satisfies(value -> assertThat(Semver.isValid(value)).isTrue());
    }
}
