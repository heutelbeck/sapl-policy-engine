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
 * Integration tests for GDrive OOPSLA benchmark policies with hand-crafted
 * entity graph.
 * <p>
 * Entity graph:
 * <ul>
 * <li>User::alice has View::User alice, owns Document::report</li>
 * <li>User::bob has View::User bob, in Group::team</li>
 * <li>User::eve has View::User eve (no group, no ownership)</li>
 * <li>View::Group team has parents [View::User bob] (bob is member)</li>
 * <li>Document::report has parents [View::User alice, Folder::shared]
 * (alice has direct view, doc is in shared folder)</li>
 * <li>Document::public_doc isPublic=true, no view parents</li>
 * <li>Document::secret isPublic=false, parents [View::Group team]
 * (group view access)</li>
 * <li>Folder::shared has parents [View::Group team] (group can view
 * folder)</li>
 * </ul>
 */
class GdriveScenarioTests {

    private static final Value ENTITY_GRAPH = Value.ofJson("""
            {
                "User::alice":         [],
                "User::bob":           ["Group::team"],
                "User::eve":           [],
                "View::User alice":    [],
                "View::User bob":      [],
                "View::User eve":      [],
                "Group::team":         [],
                "View::Group team":    ["View::User bob"],
                "Document::report":    ["View::User alice", "Folder::shared"],
                "Document::public_doc": [],
                "Document::secret":    ["View::Group team"],
                "Folder::shared":      ["View::Group team"]
            }
            """);

    private static final Value USERS = Value.ofJson("""
            {
                "User::alice": {
                    "documentsAndFoldersWithViewAccess": "View::User alice",
                    "ownedDocuments": ["Document::report"],
                    "ownedFolders":   ["Folder::shared"]
                },
                "User::bob": {
                    "documentsAndFoldersWithViewAccess": "View::User bob",
                    "ownedDocuments": [],
                    "ownedFolders":   []
                },
                "User::eve": {
                    "documentsAndFoldersWithViewAccess": "View::User eve",
                    "ownedDocuments": [],
                    "ownedFolders":   []
                }
            }
            """);

    private static final Value DOCS = Value.ofJson("""
            {
                "Document::report":     { "isPublic": false },
                "Document::public_doc": { "isPublic": true },
                "Document::secret":     { "isPublic": false }
            }
            """);

    private SaplTestFixture fixture() {
        var fixture = SaplTestFixture.createIntegrationTest();
        for (var policy : GdriveScenarioGenerator.POLICIES) {
            fixture.withPolicy(policy);
        }
        return fixture.withFunctionLibrary(GraphFunctionLibrary.class).withCombiningAlgorithm(OopslaConstants.ALGORITHM)
                .givenVariable("entityGraph", ENTITY_GRAPH).givenVariable("users", USERS).givenVariable("docs", DOCS);
    }

    @Nested
    @DisplayName("Policy 0: View access read")
    class ViewAccessReadTests {

        @Test
        @DisplayName("user with direct view access can read document")
        void whenUserHasDirectViewAccess_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "read", "Document::report")).expectPermit()
                    .verify();
        }

        @Test
        @DisplayName("user with group view access can read document")
        void whenUserHasGroupViewAccess_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "read", "Document::secret")).expectPermit()
                    .verify();
        }

        @Test
        @DisplayName("user can read folder contents through group view on folder")
        void whenUserHasGroupViewOnFolder_thenCanReadDocInFolder() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "read", "Document::report")).expectPermit()
                    .verify();
        }

        @Test
        @DisplayName("user without view access cannot read")
        void whenUserHasNoViewAccess_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::eve", "read", "Document::report")).expectDeny()
                    .verify();
        }
    }

    @Nested
    @DisplayName("Policy 1: Public read")
    class PublicReadTests {

        @Test
        @DisplayName("anyone can read public document")
        void whenDocIsPublic_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::eve", "read", "Document::public_doc"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("non-public document denied for user without access")
        void whenDocIsNotPublicAndNoAccess_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::eve", "read", "Document::secret")).expectDeny()
                    .verify();
        }
    }

    @Nested
    @DisplayName("Policy 2: Owner read/write/share")
    class OwnerReadWriteShareTests {

        @Test
        @DisplayName("owner can write to owned document")
        void whenOwnerWrites_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "write", "Document::report"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("owner can share owned document")
        void whenOwnerShares_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "share", "Document::report"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("non-owner cannot write")
        void whenNonOwnerWrites_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "write", "Document::report")).expectDeny()
                    .verify();
        }

        @Test
        @DisplayName("owner can write to owned folder")
        void whenOwnerWritesToFolder_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "write", "Folder::shared")).expectPermit()
                    .verify();
        }
    }

    @Nested
    @DisplayName("Policy 3: Owner change owner")
    class OwnerChangeOwnerTests {

        @Test
        @DisplayName("owner can change owner of owned document")
        void whenOwnerChangesOwner_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "changeOwner", "Document::report"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("non-owner cannot change owner")
        void whenNonOwnerChangesOwner_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "changeOwner", "Document::report"))
                    .expectDeny().verify();
        }

        @Test
        @DisplayName("cannot change owner of folder (ownedDocuments only)")
        void whenOwnerChangesOwnerOfFolder_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "changeOwner", "Folder::shared"))
                    .expectDeny().verify();
        }
    }

    @Nested
    @DisplayName("Policy 4: Folder owner create document")
    class FolderOwnerCreateDocTests {

        @Test
        @DisplayName("folder owner can create document")
        void whenFolderOwnerCreatesDoc_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "createDocument", "Folder::shared"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("non-folder-owner cannot create document")
        void whenNonFolderOwnerCreatesDoc_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "createDocument", "Folder::shared"))
                    .expectDeny().verify();
        }
    }
}
