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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * TinyTodo task-list scenario. Entity generation is a line-by-line translation
 * of tinytodo_entity_generator.py build_entities() from Cedar's OOPSLA 2024
 * benchmarks (cedar-examples/oopsla2024-benchmarks/generators/).
 * <p>
 * Cedar source: generators/tinytodo_entity_generator.py<br>
 * Cedar policies: benches/tinytodo/cedar/tinytodo.cedar (4 active, 2
 * commented)<br>
 * Cedar call: apps.rs tinytodo() with (num_entities, num_entities,
 * num_entities, 0.05)
 * <p>
 * Entity hierarchy uses Cedar's parent-edge direction (child -> [parents...]),
 * compatible with {@code graph.transitiveClosure()} /
 * {@code graph.transitiveClosureSet()}.
 * <p>
 * <b>Policies are placeholders</b> pending SAPL 4 semantic review.
 *
 * @see <a href="https://arxiv.org/pdf/2403.04651">Cedar OOPSLA 2024 paper</a>
 */
public final class TinytodoScenarioGenerator {

    // Cedar: Application::"TinyTodo" is the singleton app container entity
    private static final String APPLICATION = "Application::TinyTodo";

    // Cedar tinytodo: 9 actions (tinytodo_entity_generator.py action_entities())
    private static final String[] ACTIONS = { "CreateList", "GetList", "UpdateList", "DeleteList", "GetLists",
            "CreateTask", "UpdateTask", "DeleteTask", "EditShares" };

    static final List<String> POLICIES = List.of("""
            // Policy 0: Any user can create a new list or see their own lists.
            // These are app-level actions (CreateList, GetLists) against the Application resource.
            //
            // Cedar Policy 0 (tinytodo.cedar):
            // permit (
            // principal,
            // action in [Action::"CreateList", Action::"GetLists"],
            // resource == Application::"TinyTodo"
            // );

            policy "create-and-get-lists"
            permit
                action in ["CreateList", "GetLists"];
                resource == "Application::TinyTodo";
            """,
            """
                    // Policy 1: A list's owner can do anything to their list (ABAC - attribute check: resource.owner == principal).
                    //
                    // Cedar Policy 1 (tinytodo.cedar):
                    // permit (principal, action, resource)
                    // when { resource has owner && resource.owner == principal };

                    policy "Policy 1: owner-full-access"
                    permit
                        subject == lists[(resource)].owner;
                    """,
            """
                    // Policy 2: A user can view a list (GetList) if they're a member of the list's readers OR editors team.
                    // This is ReBAC - it checks principal in resource.readers, which traverses the team hierarchy transitively.
                    //
                    // Cedar Policy 2 (tinytodo.cedar):
                    // permit (
                    // principal,
                    // action == Action::"GetList",
                    // resource
                    // )
                    // when { principal in resource.readers || principal in resource.editors };

                    policy "reader-or-editor-get-list"
                    permit
                        action == "GetList";
                        // The following operation is folded at compile-time once and can be shared between policies
                        // via SAPL's data deduplication.
                        var closed = graph.transitiveClosureSet(entityGraph);
                        var list = lists[(resource)];
                        closed[(subject)][(list.readers)] != undefined || closed[(subject)][(list.editors)] != undefined;

                        // Readable alternative using transitiveClosure (arrays) + in:
                        // var closed = graph.transitiveClosure(entityGraph);
                        // lists[(resource)].readers in closed[(subject)]
                        // This is semantically easier to read but O(n) per lookup.
                        // The transitiveClosureSet variant above is O(1) per lookup.
                    """,
            """
                    // Policy 3: A user can modify a list (UpdateList, CreateTask, UpdateTask, DeleteTask)
                    // if they're a member of the list's editors team.
                    //
                    // Cedar Policy 3 (tinytodo.cedar):
                    // permit (
                    // principal,
                    // action in
                    // [Action::"UpdateList",
                    // Action::"CreateTask",
                    // Action::"UpdateTask",
                    // Action::"DeleteTask"],
                    // resource
                    // )
                    // when { principal in resource.editors };

                    policy "editor-modify"
                    permit
                        action in ["UpdateList", "CreateTask", "UpdateTask", "DeleteTask"];
                        var closed = graph.transitiveClosureSet(entityGraph);
                        closed[(subject)][(lists[(resource)].editors)] != undefined;
                    """);

    // Cedar Policies 4-5 are commented out in the original Cedar benchmark.

    private TinytodoScenarioGenerator() {
    }

    /**
     * Generates the TinyTodo scenario.
     * <p>
     * Construction order (matching Cedar exactly):
     * <ol>
     * <li><b>Users</b> (lines 73-79): for each team (0..n-1), parent edge
     * with p=0.05. Each user also parents to Application.</li>
     * <li><b>Teams</b> (lines 82-86): for each team j&lt;i, parent edge
     * with p=0.05 (DAG). Each team also parents to Application.</li>
     * <li><b>Lists</b> (lines 87-92): owner = random user, readers =
     * random team, editors = random team. Tasks generated per list (lines
     * 37-38, 49): count = randint(0,50), each task consumes 3 RNG calls
     * (name, id, state).</li>
     * </ol>
     *
     * @param n number of users, teams, and lists (all equal, matching Cedar)
     * @param seed RNG seed for graph and request generation
     * @return scenario with 4 policies and 500 subscriptions
     */
    public static Scenario generate(int n, long seed) {
        var rng = new Random(seed);

        var entityGraph = ObjectValue.builder();
        var lists       = ObjectValue.builder();

        // Application entity: no parents.
        // Cedar: app_entity() -> entity(app_euid(), attrs={}, parents=[])
        entityGraph.put(APPLICATION, Value.ofArray());

        // Step 1: Users. Cedar: lines 73-79.
        // for i in range(num_users):
        // for j in range(num_teams):
        // if random.random() < chance_user_in_team: parents.append(team_j)
        // user_entity(name, parents) adds [app_euid()] to parents
        for (int i = 0; i < n; i++) {
            var parents = new ArrayList<Value>();
            for (int j = 0; j < n; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    parents.add(Value.of(OopslaConstants.PREFIX_TEAM + j));
                }
            }
            parents.add(Value.of(APPLICATION));
            entityGraph.put(OopslaConstants.PREFIX_USER + i, Value.ofArray(parents.toArray(Value[]::new)));
        }

        // Step 2: Teams. Cedar: lines 82-86.
        // for i in range(num_teams):
        // for j in range(i): # DAG constraint
        // if random.random() < chance_team_in_team: parents.append(team_j)
        // team_entity(name, parents) adds [app_euid()] to parents
        for (int i = 0; i < n; i++) {
            var parents = new ArrayList<Value>();
            for (int j = 0; j < i; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    parents.add(Value.of(OopslaConstants.PREFIX_TEAM + j));
                }
            }
            parents.add(Value.of(APPLICATION));
            entityGraph.put(OopslaConstants.PREFIX_TEAM + i, Value.ofArray(parents.toArray(Value[]::new)));
        }

        // Step 3: Lists. Cedar: lines 87-92.
        // for i in range(num_lists):
        // owner = euid("User", "user_"+str(random.randint(0, num_users-1)))
        // readers = euid("Team", "team_"+str(random.randint(0, num_teams-1)))
        // editors = euid("Team", "team_"+str(random.randint(0, num_teams-1)))
        // list_entity(name, owner, readers, editors)
        // -> tasks = [task() for _ in range(random.randint(0, 50))]
        // -> task(): name=randint(1,1e6), id=randint(1,1e6), state=randint(1,2)
        for (int i = 0; i < n; i++) {
            var owner   = OopslaConstants.PREFIX_USER + rng.nextInt(n);
            var readers = OopslaConstants.PREFIX_TEAM + rng.nextInt(n);
            var editors = OopslaConstants.PREFIX_TEAM + rng.nextInt(n);

            // Cedar: list_entity parents = [app_euid()]
            entityGraph.put(OopslaConstants.PREFIX_LIST + i, Value.ofArray(Value.of(APPLICATION)));

            // Cedar: tasks = [task() for _ in range(random.randint(0, 50))]
            var taskCount = rng.nextInt(51);
            var tasks     = new Value[taskCount];
            for (int t = 0; t < taskCount; t++) {
                var taskName  = "task_" + (rng.nextInt(1_000_000) + 1);
                var taskId    = rng.nextInt(1_000_000) + 1;
                var taskState = rng.nextInt(2) + 1 == 1 ? "Checked" : "Unchecked";
                tasks[t] = Value.ofObject(
                        Map.of("name", Value.of(taskName), "id", Value.of(taskId), "state", Value.of(taskState)));
            }

            lists.put(OopslaConstants.PREFIX_LIST + i,
                    Value.ofObject(Map.of("name", Value.of(OopslaConstants.PREFIX_LIST + i), "owner", Value.of(owner),
                            "readers", Value.of(readers), "editors", Value.of(editors), "tasks",
                            Value.ofArray(tasks))));
        }

        var variables = ObjectValue.builder().put("entityGraph", entityGraph.build()).put("lists", lists.build())
                .build();

        var requestRng    = new Random(seed + 1_000_000L);
        var subscriptions = new ArrayList<AuthorizationSubscription>(OopslaConstants.REQUESTS_PER_GRAPH);
        // First subscription: guaranteed DENY (DeleteList is not in any policy)
        subscriptions.add(AuthorizationSubscription.of(OopslaConstants.PREFIX_USER + "0", "DeleteList",
                OopslaConstants.PREFIX_LIST + "0"));
        for (int i = 1; i < OopslaConstants.REQUESTS_PER_GRAPH; i++) {
            subscriptions.add(buildRequest(n, requestRng));
        }

        return new Scenario("tinytodo-" + n, () -> POLICIES, variables, OopslaConstants.ALGORITHM, subscriptions, null);
    }

    private static AuthorizationSubscription buildRequest(int n, Random rng) {
        var subject = OopslaConstants.PREFIX_USER + rng.nextInt(n);
        var action  = ACTIONS[rng.nextInt(ACTIONS.length)];

        String resource;
        if ("CreateList".equals(action) || "GetLists".equals(action)) {
            resource = APPLICATION;
        } else {
            resource = OopslaConstants.PREFIX_LIST + rng.nextInt(n);
        }
        return AuthorizationSubscription.of(subject, action, resource);
    }

}
