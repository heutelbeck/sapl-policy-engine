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
    private static final long   BASE_SEED               = 42L;

    private static final String PREFIX_DOC    = "doc_";
    private static final String PREFIX_FOLDER = "folder_";
    private static final String PREFIX_LIST   = "list_";
    private static final String PREFIX_ORG    = "org_";
    private static final String PREFIX_REPO   = "repo_";
    private static final String PREFIX_TEAM   = "team_";
    private static final String PREFIX_USER   = "user_";

    private static final String[] GDRIVE_ACTIONS   = { "read", "write", "share", "changeOwner", "createDocument" };
    private static final String[] GITHUB_ACTIONS   = { "read", "triage", "write", "maintain", "admin" };
    private static final String[] TINYTODO_ACTIONS = { "CreateList", "GetLists", "GetList", "UpdateList", "CreateTask",
            "UpdateTask", "DeleteTask", "EditShares" };

    private static final String[] GITHUB_REPO_ROLES = { "readers", "triagers", "writers", "maintainers", "admins" };
    private static final String[] GITHUB_ORG_ROLES  = { "readers", "writers", "admins" };

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

    private static List<AuthorizationSubscription> generateGdriveSubscriptions(int n) {
        var subscriptions = new ArrayList<AuthorizationSubscription>(DATASTORES_PER_SCENARIO * REQUESTS_PER_DATASTORE);
        for (int ds = 0; ds < DATASTORES_PER_SCENARIO; ds++) {
            var rng = new Random(BASE_SEED + ds);
            var env = buildGdriveEnvironment(n, rng);
            for (int req = 0; req < REQUESTS_PER_DATASTORE; req++) {
                subscriptions.add(buildGdriveRequest(n, rng, env));
            }
        }
        return subscriptions;
    }

    private static AuthorizationSubscription buildGdriveRequest(int n, Random rng, Value env) {
        var userId = PREFIX_USER + rng.nextInt(n);
        var action = GDRIVE_ACTIONS[rng.nextInt(GDRIVE_ACTIONS.length)];
        var resId  = rng.nextBoolean() ? PREFIX_DOC + rng.nextInt(n) : PREFIX_FOLDER + rng.nextInt(n);

        var ownedDocs    = collectEdges(rng, n, PREFIX_DOC);
        var ownedFolders = collectEdges(rng, n, PREFIX_FOLDER);

        var subject  = Value.ofObject(Map.of("id", Value.of(userId), "viewEntity", Value.of("view_" + userId),
                "ownedDocuments", ownedDocs, "ownedFolders", ownedFolders));
        var resource = Value.ofObject(Map.of("id", Value.of(resId), "isPublic", Value.of(rng.nextInt(2) == 0)));

        return AuthorizationSubscription.of(subject, Value.of(action), resource, env);
    }

    private static Value buildGdriveEnvironment(int n, Random rng) {
        var viewAccess = ObjectValue.builder();
        for (int u = 0; u < n; u++) {
            viewAccess.put("view_" + PREFIX_USER + u, buildDocFolderEdges(n, rng));
        }
        for (int g = 0; g < n; g++) {
            var reachable    = buildDocFolderEdgeList(n, rng);
            var groupMembers = collectEdgeList(rng, n, "view_" + PREFIX_USER);
            reachable.addAll(groupMembers);
            viewAccess.put("view_group_group_" + g, Value.ofArray(reachable.toArray(Value[]::new)));
        }
        return Value.ofObject(Map.of("viewAccess", viewAccess.build()));
    }

    private static List<AuthorizationSubscription> generateGithubSubscriptions(int n) {
        var subscriptions = new ArrayList<AuthorizationSubscription>(DATASTORES_PER_SCENARIO * REQUESTS_PER_DATASTORE);
        var orgCount      = Math.max(1, n);
        for (int ds = 0; ds < DATASTORES_PER_SCENARIO; ds++) {
            var rng = new Random(BASE_SEED + ds);
            var env = buildGithubEnvironment(n, orgCount, rng);
            for (int req = 0; req < REQUESTS_PER_DATASTORE; req++) {
                var userId = PREFIX_USER + rng.nextInt(n);
                var action = GITHUB_ACTIONS[rng.nextInt(GITHUB_ACTIONS.length)];
                var repoId = PREFIX_REPO + rng.nextInt(n);
                var owner  = PREFIX_ORG + rng.nextInt(orgCount);

                var subject  = Value.ofObject(Map.of("id", Value.of(userId)));
                var resource = Value.ofObject(Map.of("id", Value.of(repoId), "owner", Value.of(owner)));
                subscriptions.add(AuthorizationSubscription.of(subject, Value.of(action), resource, env));
            }
        }
        return subscriptions;
    }

    private static Value buildGithubEnvironment(int n, int orgCount, Random rng) {
        var envBuilder = ObjectValue.builder();
        for (var role : GITHUB_REPO_ROLES) {
            envBuilder.put("repo" + capitalize(role),
                    buildRoleMembership(n, n, rng, PREFIX_USER, PREFIX_TEAM, PREFIX_REPO));
        }
        for (var role : GITHUB_ORG_ROLES) {
            envBuilder.put("org" + capitalize(role),
                    buildRoleMembership(n, orgCount, rng, PREFIX_USER, null, PREFIX_ORG));
        }
        envBuilder.put("teamHierarchy", buildHierarchy(n, rng, PREFIX_TEAM));
        return envBuilder.build();
    }

    private static ObjectValue buildRoleMembership(int memberCount, int entityCount, Random rng, String memberPrefix,
            String teamPrefix, String entityPrefix) {
        var role = ObjectValue.builder();
        for (int e = 0; e < entityCount; e++) {
            var members = collectEdgeList(rng, memberCount, memberPrefix);
            if (teamPrefix != null) {
                members.addAll(collectEdgeList(rng, memberCount, teamPrefix));
            }
            role.put(entityPrefix + e, Value.ofArray(members.toArray(Value[]::new)));
        }
        return role.build();
    }

    private static List<AuthorizationSubscription> generateTinytodoSubscriptions(int n) {
        var subscriptions = new ArrayList<AuthorizationSubscription>(DATASTORES_PER_SCENARIO * REQUESTS_PER_DATASTORE);
        for (int ds = 0; ds < DATASTORES_PER_SCENARIO; ds++) {
            var rng = new Random(BASE_SEED + ds);
            var env = buildTinytodoEnvironment(n, rng);
            for (int req = 0; req < REQUESTS_PER_DATASTORE; req++) {
                subscriptions.add(buildTinytodoRequest(n, rng, env));
            }
        }
        return subscriptions;
    }

    private static AuthorizationSubscription buildTinytodoRequest(int n, Random rng, Value env) {
        var userId  = PREFIX_USER + rng.nextInt(n);
        var teamId  = PREFIX_TEAM + rng.nextInt(n);
        var action  = TINYTODO_ACTIONS[rng.nextInt(TINYTODO_ACTIONS.length)];
        var subject = Value.ofObject(Map.of("id", Value.of(userId), "teams", Value.ofArray(Value.of(teamId))));

        Value resource;
        if ("CreateList".equals(action) || "GetLists".equals(action)) {
            resource = Value.ofObject(Map.of("type", Value.of("Application")));
        } else {
            resource = Value.ofObject(Map.of("id", Value.of(PREFIX_LIST + rng.nextInt(n)), "owner",
                    Value.of(PREFIX_USER + rng.nextInt(n)), "readers", Value.of(PREFIX_TEAM + rng.nextInt(n)),
                    "editors", Value.of(PREFIX_TEAM + rng.nextInt(n))));
        }

        return AuthorizationSubscription.of(subject, Value.of(action), resource, env);
    }

    private static Value buildTinytodoEnvironment(int n, Random rng) {
        return Value.ofObject(Map.of("teamHierarchy", buildHierarchy(n, rng, PREFIX_TEAM)));
    }

    private static ObjectValue buildHierarchy(int n, Random rng, String prefix) {
        var hierarchy = ObjectValue.builder();
        for (int t = 0; t < n; t++) {
            var children = new ArrayList<Value>();
            for (int c = 0; c < t; c++) {
                if (rng.nextDouble() < EDGE_PROBABILITY) {
                    children.add(Value.of(prefix + c));
                }
            }
            hierarchy.put(prefix + t, Value.ofArray(children.toArray(Value[]::new)));
        }
        return hierarchy.build();
    }

    private static Value collectEdges(Random rng, int n, String prefix) {
        var edges = collectEdgeList(rng, n, prefix);
        return Value.ofArray(edges.toArray(Value[]::new));
    }

    private static ArrayList<Value> collectEdgeList(Random rng, int n, String prefix) {
        var edges = new ArrayList<Value>();
        for (int i = 0; i < n; i++) {
            if (rng.nextDouble() < EDGE_PROBABILITY) {
                edges.add(Value.of(prefix + i));
            }
        }
        return edges;
    }

    private static Value buildDocFolderEdges(int n, Random rng) {
        var edges = buildDocFolderEdgeList(n, rng);
        return Value.ofArray(edges.toArray(Value[]::new));
    }

    private static ArrayList<Value> buildDocFolderEdgeList(int n, Random rng) {
        var edges = new ArrayList<Value>();
        for (int d = 0; d < n; d++) {
            if (rng.nextDouble() < EDGE_PROBABILITY) {
                edges.add(Value.of(PREFIX_DOC + d));
            }
            if (rng.nextDouble() < EDGE_PROBABILITY) {
                edges.add(Value.of(PREFIX_FOLDER + d));
            }
        }
        return edges;
    }

    private static String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

}
