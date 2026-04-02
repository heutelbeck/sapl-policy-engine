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

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.benchmark.sapl4.Scenario;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * GitHub repository-access scenario. Entity generation is a line-by-line
 * translation of github_entity_generator.py build_entities() from Cedar's
 * OOPSLA 2024 benchmarks.
 * <p>
 * Cedar source: generators/github_entity_generator.py<br>
 * Cedar policies: benches/github/cedar/policies.cedar (8 policies)<br>
 * Cedar call: apps.rs github() with (num_entities, num_entities, num_entities,
 * num_entities, 0.05)
 *
 * @see <a href="https://arxiv.org/pdf/2403.04651">Cedar OOPSLA 2024 paper</a>
 */
@UtilityClass
public class GithubScenarioGenerator {

    // Cedar github: 5 actions with hierarchy read < triage < write < maintain <
    // admin
    // (github_entity_generator.py action_entities(), lines 17-23)
    private static final String[] ACTIONS = { "read", "triage", "write", "maintain", "admin" };

    // Cedar: 5 repo permission levels (random_role_str(), uniform 20% each, lines
    // 72-83)
    private static final String[] REPO_ROLES = { "readers", "triagers", "writers", "maintainers", "admins" };
    // Cedar: 3 org permission levels (random_org_role_str(), uniform 33% each,
    // lines 85-92)
    private static final String[] ORG_ROLES = { "readers", "writers", "admins" };

    // Cedar permission entity type prefixes
    // Cedar: euid("RepoPermission", name+"_readers") ->
    // RepoPermission::"repo_0_readers"
    private static final String REPO_PERM_PREFIX = "RepoPermission::repo_";
    // Cedar: euid("OrgPermission", name+"_readers") ->
    // OrgPermission::"org_0_readers"
    private static final String ORG_PERM_PREFIX = "OrgPermission::org_";

    // Cedar repo-level policies (policies.cedar):
    // permit (principal, action == Action::"read", resource)
    // when { principal in resource.readers };
    //
    // permit (principal, action in Action::"triage", resource)
    // when { principal in resource.triagers };
    //
    // permit (principal, action in Action::"write", resource)
    // when { principal in resource.writers };
    //
    // permit (principal, action in Action::"maintain", resource)
    // when { principal in resource.maintainers };
    //
    // permit (principal, action in Action::"admin", resource)
    // when { principal in resource.admins };
    //
    // Cedar org-level policies (policies.cedar):
    // permit (principal, action == Action::"read", resource)
    // when { principal in resource.owner.readers };
    //
    // permit (principal, action in Action::"write", resource)
    // when { principal in resource.owner.writers };
    //
    // permit (principal, action in Action::"admin", resource)
    // when { principal in resource.owner.admins };
    //
    // Note: Cedar's action hierarchy (read < triage < write < maintain < admin)
    // means "action in Action::write" matches read, triage, AND write.
    // SAPL has no action hierarchy, so we use explicit action lists.

    static final List<String> POLICIES = List.of("""
            // Policy 0: Users with repo read permission can read.
            // Cedar: principal in resource.readers
            policy "repo-read"
            permit
                action == "read";
                var repo = repos[(resource)];
                repo != undefined;
                var closed = graph.transitiveClosureSet(entityGraph);
                closed[(subject)][(repo.readers)] != undefined;
            """, """
            // Policy 1: Users with repo triage permission can triage and read.
            // Cedar: principal in resource.triagers (action hierarchy includes read)
            policy "repo-triage"
            permit
                action in ["triage", "read"];
                var repo = repos[(resource)];
                repo != undefined;
                var closed = graph.transitiveClosureSet(entityGraph);
                closed[(subject)][(repo.triagers)] != undefined;
            """, """
            // Policy 2: Users with repo write permission can write, triage, and read.
            // Cedar: principal in resource.writers
            policy "repo-write"
            permit
                action in ["write", "triage", "read"];
                var repo = repos[(resource)];
                repo != undefined;
                var closed = graph.transitiveClosureSet(entityGraph);
                closed[(subject)][(repo.writers)] != undefined;
            """, """
            // Policy 3: Users with repo maintain permission.
            // Cedar: principal in resource.maintainers
            policy "repo-maintain"
            permit
                action in ["maintain", "write", "triage", "read"];
                var repo = repos[(resource)];
                repo != undefined;
                var closed = graph.transitiveClosureSet(entityGraph);
                closed[(subject)][(repo.maintainers)] != undefined;
            """, """
            // Policy 4: Users with repo admin permission.
            // Cedar: principal in resource.admins
            policy "repo-admin"
            permit
                action in ["admin", "maintain", "write", "triage", "read"];
                var repo = repos[(resource)];
                repo != undefined;
                var closed = graph.transitiveClosureSet(entityGraph);
                closed[(subject)][(repo.admins)] != undefined;
            """, """
            // Policy 5: Users with org read permission can read repos owned by that org.
            // Cedar: principal in resource.owner.readers
            policy "org-read"
            permit
                action == "read";
                var repo = repos[(resource)];
                repo != undefined;
                var closed = graph.transitiveClosureSet(entityGraph);
                closed[(subject)][(orgs[(repo.owner)].readers)] != undefined;
            """, """
            // Policy 6: Users with org write permission.
            // Cedar: principal in resource.owner.writers
            policy "org-write"
            permit
                action in ["write", "triage", "read"];
                var repo = repos[(resource)];
                repo != undefined;
                var closed = graph.transitiveClosureSet(entityGraph);
                closed[(subject)][(orgs[(repo.owner)].writers)] != undefined;
            """, """
            // Policy 7: Users with org admin permission.
            // Cedar: principal in resource.owner.admins
            policy "org-admin"
            permit
                action in ["admin", "maintain", "write", "triage", "read"];
                var repo = repos[(resource)];
                repo != undefined;
                var closed = graph.transitiveClosureSet(entityGraph);
                closed[(subject)][(orgs[(repo.owner)].admins)] != undefined;
            """);

    /**
     * Generates the GitHub scenario.
     * <p>
     * Construction order (matching Cedar exactly):
     * <ol>
     * <li><b>Orgs</b> (lines 106-112): for each org pair, p=0.05 chance
     * of OrgPermission parent (random role via randint(1,3)). Creates Org
     * + 3 OrgPermission entities (readers, writers, admins).</li>
     * <li><b>Repos</b> (lines 113-116): random owner org via
     * randint(0,n-1). Creates Repo + 5 RepoPermission entities.</li>
     * <li><b>Teams</b> (lines 117-126): DAG hierarchy (j&lt;i, p=0.05).
     * For each repo, p=0.05 RepoPermission parent (random role via
     * random_role_str(), lines 72-83, uniform over 5 roles).</li>
     * <li><b>Users</b> (lines 127-141): For each repo, p=0.05
     * RepoPermission parent (random role). For each team, p=0.05 Team
     * parent. For each org, p=0.05 Org parent AND independent p=0.05
     * OrgPermission parent (random role via random_org_role_str(), lines
     * 85-92, uniform over 3 roles).</li>
     * </ol>
     *
     * @param n number of users, teams, repos, and orgs (all equal)
     * @param seed RNG seed for graph and request generation
     * @return scenario with 8 policies and 500 subscriptions
     */
    public static Scenario generate(int n, long seed) {
        val rng = new Random(seed);

        val entityGraph = ObjectValue.builder();
        val orgs        = ObjectValue.builder();
        val repos       = ObjectValue.builder();

        // Step 1: Orgs. Cedar: lines 106-112.
        // for i in range(num_orgs):
        // parents = []
        // for j in range(num_orgs):
        // if random.random() < p:
        // parents.append(OrgPermission("org_"+j+random_org_role_str()))
        // org_entities(name, parents) creates:
        // Org entity: attrs={readers, writers, admins: OrgPermission refs}
        // 3 OrgPermission entities (no attrs, no parents)
        for (int o = 0; o < n; o++) {
            val orgParents = new ArrayList<Value>();
            for (int j = 0; j < n; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    orgParents.add(Value.of(ORG_PERM_PREFIX + j + "_" + randomOrgRole(rng)));
                }
            }
            entityGraph.put(OopslaConstants.PREFIX_ORG + o, Value.ofArray(orgParents.toArray(Value[]::new)));

            // 3 OrgPermission entities: no parents
            entityGraph.put(ORG_PERM_PREFIX + o + "_readers", Value.ofArray());
            entityGraph.put(ORG_PERM_PREFIX + o + "_writers", Value.ofArray());
            entityGraph.put(ORG_PERM_PREFIX + o + "_admins", Value.ofArray());

            orgs.put(OopslaConstants.PREFIX_ORG + o,
                    Value.ofObject(Map.of("readers", Value.of(ORG_PERM_PREFIX + o + "_readers"), "writers",
                            Value.of(ORG_PERM_PREFIX + o + "_writers"), "admins",
                            Value.of(ORG_PERM_PREFIX + o + "_admins"))));
        }

        // Step 2: Repos. Cedar: lines 113-116.
        // for i in range(num_repos):
        // owner = "org_"+str(random.randint(0, num_orgs-1))
        // repo_entities(name, owner) creates:
        // Repo entity: attrs={readers,triagers,writers,maintainers,admins, owner}
        // 5 RepoPermission entities (no attrs, no parents)
        for (int r = 0; r < n; r++) {
            val owner = OopslaConstants.PREFIX_ORG + rng.nextInt(n);

            entityGraph.put(OopslaConstants.PREFIX_REPO + r, Value.ofArray());

            // 5 RepoPermission entities: no parents
            for (val role : REPO_ROLES) {
                entityGraph.put(REPO_PERM_PREFIX + r + "_" + role, Value.ofArray());
            }

            repos.put(OopslaConstants.PREFIX_REPO + r,
                    Value.ofObject(Map.of("readers", Value.of(REPO_PERM_PREFIX + r + "_readers"), "triagers",
                            Value.of(REPO_PERM_PREFIX + r + "_triagers"), "writers",
                            Value.of(REPO_PERM_PREFIX + r + "_writers"), "maintainers",
                            Value.of(REPO_PERM_PREFIX + r + "_maintainers"), "admins",
                            Value.of(REPO_PERM_PREFIX + r + "_admins"), "owner", Value.of(owner))));
        }

        // Step 3: Teams. Cedar: lines 117-126.
        // for i in range(num_teams):
        // for j in range(i): if random.random() < p: parents.append(Team_j) # DAG
        // for j in range(num_repos): if random.random() < p:
        // parents.append(RepoPermission("repo_"+j+random_role_str()))
        for (int t = 0; t < n; t++) {
            val teamParents = new ArrayList<Value>();
            for (int j = 0; j < t; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    teamParents.add(Value.of(OopslaConstants.PREFIX_TEAM + j));
                }
            }
            for (int j = 0; j < n; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    teamParents.add(Value.of(REPO_PERM_PREFIX + j + "_" + randomRepoRole(rng)));
                }
            }
            entityGraph.put(OopslaConstants.PREFIX_TEAM + t, Value.ofArray(teamParents.toArray(Value[]::new)));
        }

        // Step 4: Users. Cedar: lines 127-141.
        // for i in range(num_users):
        // for j in range(num_repos): if random.random() < p:
        // parents.append(RepoPermission("repo_"+j+random_role_str()))
        // for j in range(num_teams): if random.random() < p: parents.append(Team_j)
        // for j in range(num_orgs):
        // if random.random() < p: parents.append(Org_j)
        // if random.random() < p:
        // parents.append(OrgPermission("org_"+j+random_org_role_str()))
        for (int u = 0; u < n; u++) {
            val userParents = new ArrayList<Value>();
            for (int j = 0; j < n; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    userParents.add(Value.of(REPO_PERM_PREFIX + j + "_" + randomRepoRole(rng)));
                }
            }
            for (int j = 0; j < n; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    userParents.add(Value.of(OopslaConstants.PREFIX_TEAM + j));
                }
            }
            for (int j = 0; j < n; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    userParents.add(Value.of(OopslaConstants.PREFIX_ORG + j));
                }
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    userParents.add(Value.of(ORG_PERM_PREFIX + j + "_" + randomOrgRole(rng)));
                }
            }
            entityGraph.put(OopslaConstants.PREFIX_USER + u, Value.ofArray(userParents.toArray(Value[]::new)));
        }

        val variables = ObjectValue.builder().put("entityGraph", entityGraph.build()).put("orgs", orgs.build())
                .put("repos", repos.build()).build();

        val requestRng    = new Random(seed + OopslaConstants.REQUEST_RNG_SEED_OFFSET);
        val subscriptions = new ArrayList<AuthorizationSubscription>(OopslaConstants.REQUESTS_PER_GRAPH);
        // First subscription: guaranteed DENY (nonexistent repo)
        subscriptions.add(AuthorizationSubscription.of(OopslaConstants.PREFIX_USER + "0", "read", "nonexistent"));
        for (int i = 1; i < OopslaConstants.REQUESTS_PER_GRAPH; i++) {
            subscriptions.add(buildRequest(n, requestRng));
        }

        return new Scenario("github-" + n, () -> POLICIES, variables, OopslaConstants.ALGORITHM, subscriptions, null);
    }

    private static AuthorizationSubscription buildRequest(int n, Random rng) {
        val subject  = OopslaConstants.PREFIX_USER + rng.nextInt(n);
        val action   = ACTIONS[rng.nextInt(ACTIONS.length)];
        val resource = OopslaConstants.PREFIX_REPO + rng.nextInt(n);
        return AuthorizationSubscription.of(subject, action, resource);
    }

    /**
     * Uniform over 5 repo roles. Matches Cedar's random_role_str() (lines
     * 72-83).
     */
    private static String randomRepoRole(Random rng) {
        return REPO_ROLES[rng.nextInt(REPO_ROLES.length)];
    }

    /**
     * Uniform over 3 org roles. Matches Cedar's random_org_role_str() (lines
     * 85-92).
     */
    private static String randomOrgRole(Random rng) {
        return ORG_ROLES[rng.nextInt(ORG_ROLES.length)];
    }

}
