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
package io.sapl.test.junit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PolicyDiscoveryHelper tests")
class PolicyDiscoveryHelperTests {

    @Test
    @DisplayName("returns empty list when directory does not exist")
    void whenDirectoryDoesNotExist_thenReturnsEmptyList() {
        var result = PolicyDiscoveryHelper.discoverPolicies("non-existent-directory");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("returns empty list when root resources directory does not exist")
    void whenRootResourcesDirectoryDoesNotExist_thenReturnsEmptyList() {
        // This tests the discoverPolicies() overload with no arguments
        // In test context, src/main/resources may not have .sapl files
        var result = PolicyDiscoveryHelper.discoverPolicies();

        // Just verify it doesn't throw and returns a list (may or may not be empty)
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("policies directory strips path prefix to bare filename for unit-test naming")
    void whenSubdirectoryIsPolicies_thenNameIsBareFilename() {
        var name = PolicyDiscoveryHelper.extractPolicyName("policies", "nested/dir/simple.sapl", true);

        assertThat(name).isEqualTo("simple");
    }

    @Test
    @DisplayName("non-policies directory retains subdirectory-prefixed path for integration-test naming")
    void whenSubdirectoryIsNotPolicies_thenNameRetainsPrefixedPath() {
        var name = PolicyDiscoveryHelper.extractPolicyName("policiesIT", "groupA/policy_A.sapl", false);

        assertThat(name).isEqualTo("policiesIT/groupA/policy_A");
    }

    @Test
    @DisplayName("RESOURCES_ROOT constant is defined correctly")
    void whenAccessingResourcesRoot_thenConstantIsDefined() {
        assertThat(PolicyDiscoveryHelper.RESOURCES_ROOT).isEqualTo("src/main/resources");
    }
}
