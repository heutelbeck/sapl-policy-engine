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
package io.sapl.benchmark.sapl4;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates benchmark scenarios in 1:1 alignment with the Cedar OOPSLA 2024
 * paper's evaluation (Section 5.2). Policies, entity graph structure, edge
 * probability (0.05), and subscription generation match Cedar's published
 * generators at github.com/cedar-policy/cedar-examples/oopsla2024-benchmarks.
 * <p>
 * Entity graphs are passed in the subscription environment field. Policies use
 * graph.reachable() for hierarchy traversal, matching Cedar's built-in
 * {@code principal in X} entity resolution.
 * <p>
 * 200 random entity graphs x 500 random requests = 100,000 subscriptions per
 * scenario, matching Cedar's methodology.
 *
 * @see <a href="https://arxiv.org/pdf/2403.04651">Cedar OOPSLA 2024 paper</a>
 * @see <a href=
 * "https://github.com/cedar-policy/cedar-examples/tree/main/oopsla2024-benchmarks">Cedar
 * benchmark source</a>
 */
final class OopslaScenarioGenerator {

    private static final CombiningAlgorithm ALGORITHM = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    private static final int    DATASTORES_PER_SCENARIO = 200;
    private static final int    REQUESTS_PER_DATASTORE  = 500;
    private static final double EDGE_PROBABILITY        = 0.05;

    private OopslaScenarioGenerator() {
    }

    /**
     * Google Drive-like file sharing scenario. 5 policies matching Cedar's
     * gdrive/policies.cedar exactly. Entity graph: Users in Groups, Documents
     * in Folders, View access chains.
     *
     * @param entityCount number of users, groups, documents, and folders
     * @return a benchmark scenario with 5 policies and 100,000 subscriptions
     */
    static Scenario gdrive(int entityCount) {
        var policies = List.of("""
                policy "view-access-read"
                permit
                    action == "read";
                    resource.id in graph.reachable(environment.viewAccess, subject.viewEntity);
                """, """
                policy "public-read"
                permit
                    action == "read";
                    resource.isPublic == true;
                """, """
                policy "owner-read-write-share"
                permit
                    action in ["read", "write", "share"];
                    resource.id in subject.ownedDocuments || resource.id in subject.ownedFolders;
                """, """
                policy "owner-change-owner"
                permit
                    action == "changeOwner";
                    resource.id in subject.ownedDocuments;
                """, """
                policy "folder-owner-create-document"
                permit
                    action == "createDocument";
                    resource.id in subject.ownedFolders;
                """);

        var subscriptions = generateGdriveSubscriptions(entityCount);
        return new Scenario("gdrive-" + entityCount, () -> policies, Value.EMPTY_OBJECT, ALGORITHM, subscriptions,
                AuthorizationDecision.DENY);
    }

    /**
     * GitHub-like repository access scenario. 8 policies matching Cedar's
     * github/policies.cedar exactly. 5 per-repo permission levels
     * (read/triage/write/maintain/admin) + 3 org-level inheritance.
     *
     * @param entityCount number of users, teams, repos, and orgs
     * @return a benchmark scenario with 8 policies and 100,000 subscriptions
     */
    static Scenario github(int entityCount) {
        var policies = List.of("""
                policy "repo-read"
                permit
                    action == "read";
                    subject.id in graph.reachable(environment.repoReaders, resource.id);
                """, """
                policy "repo-triage"
                permit
                    action in ["triage", "read"];
                    subject.id in graph.reachable(environment.repoTriagers, resource.id);
                """, """
                policy "repo-write"
                permit
                    action in ["write", "triage", "read"];
                    subject.id in graph.reachable(environment.repoWriters, resource.id);
                """, """
                policy "repo-maintain"
                permit
                    action in ["maintain", "write", "triage", "read"];
                    subject.id in graph.reachable(environment.repoMaintainers, resource.id);
                """, """
                policy "repo-admin"
                permit
                    action in ["admin", "maintain", "write", "triage", "read"];
                    subject.id in graph.reachable(environment.repoAdmins, resource.id);
                """, """
                policy "org-read"
                permit
                    action == "read";
                    subject.id in graph.reachable(environment.orgReaders, resource.owner);
                """, """
                policy "org-write"
                permit
                    action in ["write", "triage", "read"];
                    subject.id in graph.reachable(environment.orgWriters, resource.owner);
                """, """
                policy "org-admin"
                permit
                    action in ["admin", "maintain", "write", "triage", "read"];
                    subject.id in graph.reachable(environment.orgAdmins, resource.owner);
                """);

        var subscriptions = generateGithubSubscriptions(entityCount);
        return new Scenario("github-" + entityCount, () -> policies, Value.EMPTY_OBJECT, ALGORITHM, subscriptions,
                AuthorizationDecision.DENY);
    }

    /**
     * TinyTodo-like task list scenario. 4 policies matching Cedar's
     * tinytodo/tinytodo.cedar exactly.
     *
     * @param entityCount number of users, teams, and lists
     * @return a benchmark scenario with 4 policies and 100,000 subscriptions
     */
    static Scenario tinytodo(int entityCount) {
        var policies = List.of("""
                policy "create-and-get-lists"
                permit
                    action in ["CreateList", "GetLists"];
                    resource.type == "Application";
                """, """
                policy "owner-full-access"
                permit
                    resource has "owner";
                    subject.id == resource.owner;
                """, """
                policy "reader-or-editor-get-list"
                permit
                    action == "GetList";
                    var effectiveTeams = graph.reachable(environment.teamHierarchy, subject.teams);
                    resource.readers in effectiveTeams || resource.editors in effectiveTeams;
                """, """
                policy "editor-modify"
                permit
                    action in ["UpdateList", "CreateTask", "UpdateTask", "DeleteTask"];
                    var effectiveTeams = graph.reachable(environment.teamHierarchy, subject.teams);
                    resource.editors in effectiveTeams;
                """);

        var subscriptions = generateTinytodoSubscriptions(entityCount);
        return new Scenario("tinytodo-" + entityCount, () -> policies, Value.EMPTY_OBJECT, ALGORITHM, subscriptions,
                AuthorizationDecision.DENY);
    }

    // ---- gdrive ----

    private static List<AuthorizationSubscription> generateGdriveSubscriptions(int n) {
        var subscriptions = new ArrayList<AuthorizationSubscription>(DATASTORES_PER_SCENARIO * REQUESTS_PER_DATASTORE);
        var actions       = new String[] { "read", "write", "share", "changeOwner", "createDocument" };

        for (int ds = 0; ds < DATASTORES_PER_SCENARIO; ds++) {
            var rng = new Random(42L + ds);
            var env = buildGdriveEnvironment(n, rng);

            for (int req = 0; req < REQUESTS_PER_DATASTORE; req++) {
                var userId = "user_" + rng.nextInt(n);
                var action = actions[rng.nextInt(actions.length)];
                var isDoc  = rng.nextBoolean();
                var resId  = isDoc ? "doc_" + rng.nextInt(n) : "folder_" + rng.nextInt(n);

                var ownedDocs    = new ArrayList<Value>();
                var ownedFolders = new ArrayList<Value>();
                for (int j = 0; j < n; j++) {
                    if (rng.nextDouble() < EDGE_PROBABILITY) {
                        ownedDocs.add(Value.of("doc_" + j));
                    }
                    if (rng.nextDouble() < EDGE_PROBABILITY) {
                        ownedFolders.add(Value.of("folder_" + j));
                    }
                }

                var subject  = Value
                        .ofObject(Map.of("id", Value.of(userId), "viewEntity", Value.of("view_user_" + userId),
                                "ownedDocuments", Value.ofArray(ownedDocs.toArray(Value[]::new)), "ownedFolders",
                                Value.ofArray(ownedFolders.toArray(Value[]::new))));
                var resource = Value.ofObject(Map.of("id", Value.of(resId), "isPublic", Value.of(rng.nextInt(2) == 0)));

                subscriptions.add(AuthorizationSubscription.of(subject, Value.of(action), resource, env));
            }
        }
        return subscriptions;
    }

    private static Value buildGdriveEnvironment(int n, Random rng) {
        var viewAccess = ObjectValue.builder();
        for (int u = 0; u < n; u++) {
            var reachable = new ArrayList<Value>();
            for (int d = 0; d < n; d++) {
                if (rng.nextDouble() < EDGE_PROBABILITY) {
                    reachable.add(Value.of("doc_" + d));
                }
                if (rng.nextDouble() < EDGE_PROBABILITY) {
                    reachable.add(Value.of("folder_" + d));
                }
            }
            viewAccess.put("view_user_user_" + u, Value.ofArray(reachable.toArray(Value[]::new)));
        }
        for (int g = 0; g < n; g++) {
            var reachable = new ArrayList<Value>();
            for (int d = 0; d < n; d++) {
                if (rng.nextDouble() < EDGE_PROBABILITY) {
                    reachable.add(Value.of("doc_" + d));
                }
                if (rng.nextDouble() < EDGE_PROBABILITY) {
                    reachable.add(Value.of("folder_" + d));
                }
            }
            viewAccess.put("view_group_group_" + g, Value.ofArray(reachable.toArray(Value[]::new)));
            var groupMembers = new ArrayList<Value>();
            for (int u = 0; u < n; u++) {
                if (rng.nextDouble() < EDGE_PROBABILITY) {
                    groupMembers.add(Value.of("view_user_user_" + u));
                }
            }
            viewAccess.put("view_group_group_" + g,
                    Value.ofArray(concatArrays(reachable, groupMembers).toArray(Value[]::new)));
        }
        return Value.ofObject(Map.of("viewAccess", viewAccess.build()));
    }

    // ---- github ----

    private static List<AuthorizationSubscription> generateGithubSubscriptions(int n) {
        var subscriptions = new ArrayList<AuthorizationSubscription>(DATASTORES_PER_SCENARIO * REQUESTS_PER_DATASTORE);
        var actions       = new String[] { "read", "triage", "write", "maintain", "admin" };
        var orgCount      = Math.max(1, n);

        for (int ds = 0; ds < DATASTORES_PER_SCENARIO; ds++) {
            var rng = new Random(42L + ds);
            var env = buildGithubEnvironment(n, orgCount, rng);

            for (int req = 0; req < REQUESTS_PER_DATASTORE; req++) {
                var userId = "user_" + rng.nextInt(n);
                var action = actions[rng.nextInt(actions.length)];
                var repoId = "repo_" + rng.nextInt(n);
                var owner  = "org_" + rng.nextInt(orgCount);

                var subject  = Value.ofObject(Map.of("id", Value.of(userId)));
                var resource = Value.ofObject(Map.of("id", Value.of(repoId), "owner", Value.of(owner)));

                subscriptions.add(AuthorizationSubscription.of(subject, Value.of(action), resource, env));
            }
        }
        return subscriptions;
    }

    private static Value buildGithubEnvironment(int n, int orgCount, Random rng) {
        var roles      = new String[] { "readers", "triagers", "writers", "maintainers", "admins" };
        var envBuilder = ObjectValue.builder();

        for (var role : roles) {
            var repoRole = ObjectValue.builder();
            for (int r = 0; r < n; r++) {
                var members = new ArrayList<Value>();
                for (int u = 0; u < n; u++) {
                    if (rng.nextDouble() < EDGE_PROBABILITY) {
                        members.add(Value.of("user_" + u));
                    }
                }
                for (int t = 0; t < n; t++) {
                    if (rng.nextDouble() < EDGE_PROBABILITY) {
                        members.add(Value.of("team_" + t));
                    }
                }
                repoRole.put("repo_" + r, Value.ofArray(members.toArray(Value[]::new)));
            }
            envBuilder.put("repo" + capitalize(role), repoRole.build());
        }

        var orgRoles = new String[] { "readers", "writers", "admins" };
        for (var role : orgRoles) {
            var orgRole = ObjectValue.builder();
            for (int o = 0; o < orgCount; o++) {
                var members = new ArrayList<Value>();
                for (int u = 0; u < n; u++) {
                    if (rng.nextDouble() < EDGE_PROBABILITY) {
                        members.add(Value.of("user_" + u));
                    }
                }
                orgRole.put("org_" + o, Value.ofArray(members.toArray(Value[]::new)));
            }
            envBuilder.put("org" + capitalize(role), orgRole.build());
        }

        var teamHierarchy = ObjectValue.builder();
        for (int t = 0; t < n; t++) {
            var children = new ArrayList<Value>();
            for (int c = 0; c < t; c++) {
                if (rng.nextDouble() < EDGE_PROBABILITY) {
                    children.add(Value.of("team_" + c));
                }
            }
            teamHierarchy.put("team_" + t, Value.ofArray(children.toArray(Value[]::new)));
        }
        envBuilder.put("teamHierarchy", teamHierarchy.build());

        return envBuilder.build();
    }

    // ---- tinytodo ----

    private static List<AuthorizationSubscription> generateTinytodoSubscriptions(int n) {
        var subscriptions = new ArrayList<AuthorizationSubscription>(DATASTORES_PER_SCENARIO * REQUESTS_PER_DATASTORE);
        var actions       = new String[] { "CreateList", "GetLists", "GetList", "UpdateList", "CreateTask",
                "UpdateTask", "DeleteTask", "EditShares" };

        for (int ds = 0; ds < DATASTORES_PER_SCENARIO; ds++) {
            var rng = new Random(42L + ds);
            var env = buildTinytodoEnvironment(n, rng);

            for (int req = 0; req < REQUESTS_PER_DATASTORE; req++) {
                var userId = "user_" + rng.nextInt(n);
                var teamId = "team_" + rng.nextInt(n);
                var action = actions[rng.nextInt(actions.length)];

                Value subject;
                Value resource;

                if (action.equals("CreateList") || action.equals("GetLists")) {
                    subject  = Value.ofObject(Map.of("id", Value.of(userId), "teams", Value.ofArray(Value.of(teamId))));
                    resource = Value.ofObject(Map.of("type", Value.of("Application")));
                } else {
                    var listId  = "list_" + rng.nextInt(n);
                    var ownerId = "user_" + rng.nextInt(n);
                    var readers = "team_" + rng.nextInt(n);
                    var editors = "team_" + rng.nextInt(n);
                    subject  = Value.ofObject(Map.of("id", Value.of(userId), "teams", Value.ofArray(Value.of(teamId))));
                    resource = Value.ofObject(Map.of("id", Value.of(listId), "owner", Value.of(ownerId), "readers",
                            Value.of(readers), "editors", Value.of(editors)));
                }

                subscriptions.add(AuthorizationSubscription.of(subject, Value.of(action), resource, env));
            }
        }
        return subscriptions;
    }

    private static Value buildTinytodoEnvironment(int n, Random rng) {
        var teamHierarchy = ObjectValue.builder();
        for (int t = 0; t < n; t++) {
            var children = new ArrayList<Value>();
            for (int c = 0; c < t; c++) {
                if (rng.nextDouble() < EDGE_PROBABILITY) {
                    children.add(Value.of("team_" + c));
                }
            }
            teamHierarchy.put("team_" + t, Value.ofArray(children.toArray(Value[]::new)));
        }
        return Value.ofObject(Map.of("teamHierarchy", teamHierarchy.build()));
    }

    // ---- helpers ----

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private static List<Value> concatArrays(List<Value> a, List<Value> b) {
        var result = new ArrayList<Value>(a.size() + b.size());
        result.addAll(a);
        result.addAll(b);
        return result;
    }

}
