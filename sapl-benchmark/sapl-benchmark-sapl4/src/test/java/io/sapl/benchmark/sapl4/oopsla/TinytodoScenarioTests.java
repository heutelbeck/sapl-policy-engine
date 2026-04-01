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
 * Integration tests for TinyTodo OOPSLA benchmark policies with hand-crafted
 * entity graph and subscriptions.
 * <p>
 * Entity graph:
 * <ul>
 * <li>User::alice in [Team::editors, Application::TinyTodo]</li>
 * <li>User::bob in [Team::readers, Application::TinyTodo]</li>
 * <li>User::eve in [Application::TinyTodo] (no teams)</li>
 * <li>Team::readers in [Application::TinyTodo]</li>
 * <li>Team::editors in [Application::TinyTodo]</li>
 * <li>List::todo1: owner=User::alice, readers=Team::readers,
 * editors=Team::editors</li>
 * </ul>
 */
class TinytodoScenarioTests {

    private static final Value ENTITY_GRAPH = Value.ofJson("""
            {
                "Application::TinyTodo": [],
                "User::alice":           ["Team::editors", "Application::TinyTodo"],
                "User::bob":             ["Team::readers", "Application::TinyTodo"],
                "User::eve":             ["Application::TinyTodo"],
                "Team::readers":         ["Application::TinyTodo"],
                "Team::editors":         ["Application::TinyTodo"],
                "List::todo1":           ["Application::TinyTodo"]
            }
            """);

    private static final Value LISTS = Value.ofJson("""
            {
                "List::todo1": {
                    "name":    "List::todo1",
                    "owner":   "User::alice",
                    "readers": "Team::readers",
                    "editors": "Team::editors",
                    "tasks":   []
                }
            }
            """);

    private SaplTestFixture fixture() {
        var fixture = SaplTestFixture.createIntegrationTest();
        for (var policy : TinytodoScenarioGenerator.POLICIES) {
            fixture.withPolicy(policy);
        }
        return fixture.withFunctionLibrary(GraphFunctionLibrary.class).withCombiningAlgorithm(OopslaConstants.ALGORITHM)
                .givenVariable("entityGraph", ENTITY_GRAPH).givenVariable("lists", LISTS);
    }

    @Nested
    @DisplayName("Policy 0: CreateList and GetLists on Application")
    class CreateAndGetListsTests {

        @Test
        @DisplayName("any user can CreateList on Application")
        void whenAnyUserCreatesListOnApplication_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::eve", "CreateList", "Application::TinyTodo"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("any user can GetLists on Application")
        void whenAnyUserGetsListsOnApplication_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "GetLists", "Application::TinyTodo"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("CreateList on a List resource is permitted for owner (Policy 1)")
        void whenOwnerCreateListOnListResource_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "CreateList", "List::todo1"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("CreateList on a List resource is denied for non-owner")
        void whenNonOwnerCreateListOnListResource_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::eve", "CreateList", "List::todo1")).expectDeny()
                    .verify();
        }
    }

    @Nested
    @DisplayName("Policy 1: Owner full access")
    class OwnerFullAccessTests {

        @Test
        @DisplayName("owner can GetList on their list")
        void whenOwnerGetsOwnList_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "GetList", "List::todo1")).expectPermit()
                    .verify();
        }

        @Test
        @DisplayName("owner can DeleteTask on their list")
        void whenOwnerDeletesTaskOnOwnList_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "DeleteTask", "List::todo1"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("non-owner cannot use owner privilege")
        void whenNonOwnerTriesOwnerAction_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "EditShares", "List::todo1")).expectDeny()
                    .verify();
        }
    }

    @Nested
    @DisplayName("Policy 2: Reader or editor can GetList")
    class ReaderEditorGetListTests {

        @Test
        @DisplayName("reader can GetList")
        void whenReaderGetsGet_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "GetList", "List::todo1")).expectPermit()
                    .verify();
        }

        @Test
        @DisplayName("editor can GetList")
        void whenEditorGetsGet_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "GetList", "List::todo1")).expectPermit()
                    .verify();
        }

        @Test
        @DisplayName("user with no teams cannot GetList")
        void whenNoTeamUserGetsGet_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::eve", "GetList", "List::todo1")).expectDeny()
                    .verify();
        }
    }

    @Nested
    @DisplayName("Policy 3: Editor can modify")
    class EditorModifyTests {

        @Test
        @DisplayName("editor can UpdateList")
        void whenEditorUpdates_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "UpdateList", "List::todo1"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("editor can CreateTask")
        void whenEditorCreatesTask_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "CreateTask", "List::todo1"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("reader cannot UpdateList")
        void whenReaderUpdates_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "UpdateList", "List::todo1")).expectDeny()
                    .verify();
        }

        @Test
        @DisplayName("reader cannot CreateTask")
        void whenReaderCreatesTask_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "CreateTask", "List::todo1")).expectDeny()
                    .verify();
        }

        @Test
        @DisplayName("user with no teams cannot modify")
        void whenNoTeamUserModifies_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::eve", "UpdateList", "List::todo1")).expectDeny()
                    .verify();
        }
    }

    @Nested
    @DisplayName("Actions not in any policy")
    class UnmatchedActionTests {

        @Test
        @DisplayName("DeleteList is permitted for owner (Policy 1)")
        void whenOwnerDeletesList_thenPermit() {
            fixture().whenDecide(AuthorizationSubscription.of("User::alice", "DeleteList", "List::todo1"))
                    .expectPermit().verify();
        }

        @Test
        @DisplayName("DeleteList is denied for non-owner")
        void whenNonOwnerDeletesList_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "DeleteList", "List::todo1")).expectDeny()
                    .verify();
        }

        @Test
        @DisplayName("EditShares is denied for non-owner")
        void whenNonOwnerEditsShares_thenDeny() {
            fixture().whenDecide(AuthorizationSubscription.of("User::bob", "EditShares", "List::todo1")).expectDeny()
                    .verify();
        }
    }
}
