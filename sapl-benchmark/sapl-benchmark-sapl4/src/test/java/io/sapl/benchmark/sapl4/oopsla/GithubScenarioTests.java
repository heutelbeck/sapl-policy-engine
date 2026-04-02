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
package io.sapl.benchmark.sapl4.oopsla;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.functions.libraries.GraphFunctionLibrary;
import io.sapl.test.SaplTestFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for GitHub OOPSLA benchmark policies with hand-crafted
 * entity graph.
 * <p>
 * Entity graph:
 * <ul>
 * <li>User::alice in [RepoPermission::repo_0_writers, Team::devs]
 * (direct repo write + team)</li>
 * <li>User::bob in [Team::devs] (team only)</li>
 * <li>User::eve in [Organization::acme] (org member only)</li>
 * <li>Team::devs in [RepoPermission::repo_0_readers] (team has repo read
 * perm)</li>
 * <li>Organization::acme in [OrgPermission::org_0_writers] (org has org
 * write perm)</li>
 * <li>Repository::repo_0 owned by Organization::acme</li>
 * </ul>
 * <p>
 * Expected access:
 * <ul>
 * <li>alice: write/triage/read on repo_0 (direct writers perm)</li>
 * <li>bob: read on repo_0 (via team_devs -> repo_0_readers)</li>
 * <li>eve: write/triage/read on repo_0 (via org_acme -> org_0_writers,
 * org-write policy)</li>
 * </ul>
 */
class GithubScenarioTests {

    private static final Value ENTITY_GRAPH = Value.ofJson("""
            {
                "User::alice":                      ["RepoPermission::repo_0_writers", "Team::devs"],
                "User::bob":                        ["Team::devs"],
                "User::eve":                        ["Organization::acme"],
                "Team::devs":                       ["RepoPermission::repo_0_readers"],
                "Organization::acme":               ["OrgPermission::org_0_writers"],
                "Repository::repo_0":               [],
                "RepoPermission::repo_0_readers":   [],
                "RepoPermission::repo_0_triagers":  [],
                "RepoPermission::repo_0_writers":   [],
                "RepoPermission::repo_0_maintainers": [],
                "RepoPermission::repo_0_admins":    [],
                "OrgPermission::org_0_readers":      [],
                "OrgPermission::org_0_writers":      [],
                "OrgPermission::org_0_admins":       []
            }
            """);

    private static final Value REPOS = Value.ofJson("""
            {
                "Repository::repo_0": {
                    "readers":     "RepoPermission::repo_0_readers",
                    "triagers":    "RepoPermission::repo_0_triagers",
                    "writers":     "RepoPermission::repo_0_writers",
                    "maintainers": "RepoPermission::repo_0_maintainers",
                    "admins":      "RepoPermission::repo_0_admins",
                    "owner":       "Organization::acme"
                }
            }
            """);

    private static final Value ORGS = Value.ofJson("""
            {
                "Organization::acme": {
                    "readers": "OrgPermission::org_0_readers",
                    "writers": "OrgPermission::org_0_writers",
                    "admins":  "OrgPermission::org_0_admins"
                }
            }
            """);

    private SaplTestFixture fixture() {
        var fixture = SaplTestFixture.createIntegrationTest();
        for (var policy : GithubScenarioGenerator.POLICIES) {
            fixture.withPolicy(policy);
        }
        return fixture.withFunctionLibrary(GraphFunctionLibrary.class).withCombiningAlgorithm(OopslaConstants.ALGORITHM)
                .givenVariable("entityGraph", ENTITY_GRAPH).givenVariable("repos", REPOS).givenVariable("orgs", ORGS);
    }

    @Nested
    @DisplayName("Repo-level policies: direct permission")
    class DirectRepoPermissionTests {

        @Test
        @DisplayName("user with repo writers perm can write")
        void whenUserHasWritersPerm_thenCanWrite() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "write", "Repository::repo_0"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("user with repo writers perm can read (action hierarchy)")
        void whenUserHasWritersPerm_thenCanRead() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "read", "Repository::repo_0"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("user with repo writers perm cannot admin")
        void whenUserHasWritersPerm_thenCannotAdmin() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "admin", "Repository::repo_0"))
                    .expectDeny().verify();
        }
    }

    @Nested
    @DisplayName("Repo-level policies: team-based permission")
    class TeamRepoPermissionTests {

        @Test
        @DisplayName("team member can read via team repo readers perm")
        void whenTeamHasReadersPerm_thenMemberCanRead() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "read", "Repository::repo_0")).expectPermit()
                    .verify();
        }

        @Test
        @DisplayName("team member cannot write via team repo readers perm")
        void whenTeamHasReadersPerm_thenMemberCannotWrite() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "write", "Repository::repo_0")).expectDeny()
                    .verify();
        }
    }

    @Nested
    @DisplayName("Org-level policies: org member inherits org permissions")
    class OrgPermissionTests {

        @Test
        @DisplayName("org member can write via org writers perm on org-owned repo")
        void whenOrgHasWritersPerm_thenMemberCanWrite() {
            fixture().whenDecide(AuthorizationSubscription.of("User::eve", "write", "Repository::repo_0"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("org member can read via org writers perm (action hierarchy)")
        void whenOrgHasWritersPerm_thenMemberCanRead() {
            fixture().whenDecide(AuthorizationSubscription.of("User::eve", "read", "Repository::repo_0")).expectPermit()
                    .verify();
        }

        @Test
        @DisplayName("org member cannot admin via org writers perm")
        void whenOrgHasWritersPerm_thenMemberCannotAdmin() {
            fixture().whenDecide(AuthorizationSubscription.of("User::eve", "admin", "Repository::repo_0")).expectDeny()
                    .verify();
        }
    }

    @Nested
    @DisplayName("Negative cases")
    class NegativeTests {

        @Test
        @DisplayName("user with no permissions is denied")
        void whenUserHasNoPerms_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::nobody", "read", "Repository::repo_0"))
                    .expectDeny().verify();
        }

        @Test
        @DisplayName("nonexistent repo is indeterminate (repos lookup fails, error maps to deny)")
        void whenRepoDoesNotExist_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "read", "nonexistent")).expectDeny()
                    .verify();
        }
    }
}
