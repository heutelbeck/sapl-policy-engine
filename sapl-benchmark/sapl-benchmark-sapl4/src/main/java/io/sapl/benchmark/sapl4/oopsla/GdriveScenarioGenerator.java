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
 * Google Drive file-sharing scenario. Entity generation is a line-by-line
 * translation of gdrive_entity_generator.py build_entities() from Cedar's
 * OOPSLA 2024 benchmarks.
 * <p>
 * Cedar source: generators/gdrive_entity_generator.py<br>
 * Cedar policies: benches/gdrive/cedar/policies.cedar (5 policies)<br>
 * Cedar call: apps.rs gdrive() with (num_entities, num_entities, num_entities,
 * num_entities, 0.05)
 *
 * @see <a href="https://arxiv.org/pdf/2403.04651">Cedar OOPSLA 2024 paper</a>
 */
public final class GdriveScenarioGenerator {

    // Cedar gdrive: 5 actions (gdrive_entity_generator.py action_entities())
    private static final String[] ACTIONS = { "read", "write", "share", "changeOwner", "createDocument" };

    // Cedar View entity naming: View::"User user_X", View::"Group group_X"
    private static final String VIEW_USER_PREFIX  = "View::User user_";
    private static final String VIEW_GROUP_PREFIX = "View::Group group_";

    // Cedar Policy 0 (policies.cedar):
    // permit (principal, action == Action::"read", resource)
    // when { resource in principal.documentsAndFoldersWithViewAccess };
    //
    // Cedar Policy 1 (policies.cedar):
    // permit (principal, action == Action::"read", resource)
    // when { resource.isPublic };
    //
    // Cedar Policy 2 (policies.cedar):
    // permit (
    // principal,
    // action in [Action::"read", Action::"write", Action::"share"],
    // resource
    // )
    // when { resource in principal.ownedDocuments || resource in
    // principal.ownedFolders };
    //
    // Cedar Policy 3 (policies.cedar):
    // permit (principal, action == Action::"changeOwner", resource)
    // when { principal.ownedDocuments.contains(resource) };
    //
    // Cedar Policy 4 (policies.cedar):
    // permit (principal, action == Action::"createDocument", resource)
    // when { principal.ownedFolders.contains(resource) };

    static final List<String> POLICIES = List.of("""
            // Policy 0: View access grants read permission.
            // Cedar: resource in principal.documentsAndFoldersWithViewAccess
            // The View entity is an ancestor of docs/folders in the entity hierarchy.
            // closed[(resource)] contains the resource's ancestors; we check if the
            // user's View entity is among them.
            policy "view-access-read"
            permit
                action == "read";
                var closed = graph.transitiveClosureSet(entityGraph);
                closed[(resource)][(users[(subject)].documentsAndFoldersWithViewAccess)] != undefined;
            """, """
            // Policy 1: Public documents can be read by anyone.
            // Cedar: resource.isPublic
            policy "public-read"
            permit
                action == "read";
                docs[(resource)].isPublic == true;
            """, """
            // Policy 2: Owners can read, write, and share their docs/folders.
            // Cedar: resource in principal.ownedDocuments || resource in principal.ownedFolders
            // ownedDocuments/ownedFolders are Set attributes (not hierarchy). SAPL "in" on arrays.
            policy "owner-read-write-share"
            permit
                action in ["read", "write", "share"];
                var user = users[(subject)];
                resource in user.ownedDocuments || resource in user.ownedFolders;
            """, """
            // Policy 3: Owners can change document ownership.
            // Cedar: principal.ownedDocuments.contains(resource)
            policy "owner-change-owner"
            permit
                action == "changeOwner";
                resource in users[(subject)].ownedDocuments;
            """, """
            // Policy 4: Folder owners can create documents.
            // Cedar: principal.ownedFolders.contains(resource)
            policy "folder-owner-create-document"
            permit
                action == "createDocument";
                resource in users[(subject)].ownedFolders;
            """);

    private GdriveScenarioGenerator() {
    }

    /**
     * Generates the GDrive scenario.
     * <p>
     * Construction order (matching Cedar exactly):
     * <ol>
     * <li><b>Users</b> (lines 70-85): group membership (p=0.05 per
     * group), ownedDocuments (p=0.05 per doc), ownedFolders (p=0.05 per
     * folder). Creates User + View("User X") entities.</li>
     * <li><b>Groups</b> (lines 86-88): View("Group X") entity whose
     * parents are View("User Y") for each user Y in the group. No
     * additional RNG calls.</li>
     * <li><b>Documents</b> (lines 89-101): isPublic (50% via
     * randint(1,2)). Parents: folders (p=0.05), View("User") (p=0.05),
     * View("Group") (p=0.05).</li>
     * <li><b>Folders</b> (lines 102-114): Parents: folders j&lt;i
     * (p=0.05, DAG), View("User") (p=0.05), View("Group")
     * (p=0.05).</li>
     * </ol>
     *
     * @param n number of users, groups, documents, and folders (all equal)
     * @param seed RNG seed for graph and request generation
     * @return scenario with 5 policies and 500 subscriptions
     */
    public static Scenario generate(int n, long seed) {
        var rng = new Random(seed);

        var entityGraph = ObjectValue.builder();
        var users       = ObjectValue.builder();
        var docs        = ObjectValue.builder();

        var groupMembership = new ArrayList<List<Integer>>(n);
        for (int g = 0; g < n; g++) {
            groupMembership.add(new ArrayList<>());
        }

        // Step 1: Users. Cedar: lines 70-85.
        // for i in range(num_users):
        // parents = [] (Group parents)
        // for j in range(num_groups):
        // if random.random() < p: parents.append(Group); group_membership[j].append(i)
        // owned_docs = [Doc_j for j if random.random() < p]
        // owned_folders = [Folder_j for j if random.random() < p]
        // user_entities(name, parents, owned_docs, owned_folders) creates:
        // User entity: attrs={viewAccess: View("User X"), ownedDocs, ownedFolders},
        // parents=Group parents
        // View("User X") entity: attrs={}, parents=[]
        for (int i = 0; i < n; i++) {
            var userParents = new ArrayList<Value>();
            for (int j = 0; j < n; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    userParents.add(Value.of(OopslaConstants.PREFIX_GROUP + j));
                    groupMembership.get(j).add(i);
                }
            }
            entityGraph.put(OopslaConstants.PREFIX_USER + i, Value.ofArray(userParents.toArray(Value[]::new)));

            var ownedDocs    = collectEdges(n, OopslaConstants.PREFIX_DOC, rng);
            var ownedFolders = collectEdges(n, OopslaConstants.PREFIX_FOLDER, rng);
            var viewEntity   = VIEW_USER_PREFIX + i;
            users.put(OopslaConstants.PREFIX_USER + i, Value.ofObject(Map.of("documentsAndFoldersWithViewAccess",
                    Value.of(viewEntity), "ownedDocuments", ownedDocs, "ownedFolders", ownedFolders)));

            // Cedar: View("User X") entity has no parents
            entityGraph.put(viewEntity, Value.ofArray());
        }

        // Step 2: Groups. Cedar: lines 86-88.
        // group_entities(name, [user names in group]) creates:
        // Group entity: attrs={}, parents=[]
        // View("Group X"): attrs={}, parents=[View("User Y") for Y in membership]
        // No additional random calls.
        for (int g = 0; g < n; g++) {
            entityGraph.put(OopslaConstants.PREFIX_GROUP + g, Value.ofArray());

            var viewParents = new ArrayList<Value>();
            for (var userId : groupMembership.get(g)) {
                viewParents.add(Value.of(VIEW_USER_PREFIX + userId));
            }
            entityGraph.put(VIEW_GROUP_PREFIX + g, Value.ofArray(viewParents.toArray(Value[]::new)));
        }

        // Step 3: Documents. Cedar: lines 89-101.
        // for i in range(num_docs):
        // isPublic = random.randint(1, 2) == 1
        // parents = []
        // for j in range(num_folders): if random.random() < p: parents.append(Folder_j)
        // for j in range(num_users): if random.random() < p: parents.append(View("User
        // "+str(j)))
        // for j in range(num_groups): if random.random() < p:
        // parents.append(View("Group group_"+str(j)))
        for (int i = 0; i < n; i++) {
            var isPublic = rng.nextInt(2) + 1 == 1;

            var docParents = new ArrayList<Value>();
            for (int j = 0; j < n; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    docParents.add(Value.of(OopslaConstants.PREFIX_FOLDER + j));
                }
            }
            for (int j = 0; j < n; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    docParents.add(Value.of(VIEW_USER_PREFIX + j));
                }
            }
            for (int j = 0; j < n; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    docParents.add(Value.of(VIEW_GROUP_PREFIX + j));
                }
            }
            entityGraph.put(OopslaConstants.PREFIX_DOC + i, Value.ofArray(docParents.toArray(Value[]::new)));
            docs.put(OopslaConstants.PREFIX_DOC + i, Value.ofObject(Map.of("isPublic", Value.of(isPublic))));
        }

        // Step 4: Folders. Cedar: lines 102-114.
        // for i in range(num_folders):
        // for j in range(i): if random.random() < p: parents.append(Folder_j) # DAG
        // for j in range(num_users): if random.random() < p: parents.append(View("User
        // "+str(j)))
        // for j in range(num_groups): if random.random() < p:
        // parents.append(View("Group group_"+str(j)))
        for (int i = 0; i < n; i++) {
            var folderParents = new ArrayList<Value>();
            for (int j = 0; j < i; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    folderParents.add(Value.of(OopslaConstants.PREFIX_FOLDER + j));
                }
            }
            for (int j = 0; j < n; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    folderParents.add(Value.of(VIEW_USER_PREFIX + j));
                }
            }
            for (int j = 0; j < n; j++) {
                if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                    folderParents.add(Value.of(VIEW_GROUP_PREFIX + j));
                }
            }
            entityGraph.put(OopslaConstants.PREFIX_FOLDER + i, Value.ofArray(folderParents.toArray(Value[]::new)));
        }

        var variables = ObjectValue.builder().put("entityGraph", entityGraph.build()).put("users", users.build())
                .put("docs", docs.build()).build();

        var requestRng    = new Random(seed + 1_000_000L);
        var subscriptions = new ArrayList<AuthorizationSubscription>(OopslaConstants.REQUESTS_PER_GRAPH);
        // First subscription: guaranteed DENY (nonexistent resource, not public, not
        // owned)
        subscriptions
                .add(AuthorizationSubscription.of(OopslaConstants.PREFIX_USER + "0", "changeOwner", "nonexistent"));
        for (int i = 1; i < OopslaConstants.REQUESTS_PER_GRAPH; i++) {
            subscriptions.add(buildRequest(n, requestRng));
        }

        return new Scenario("gdrive-" + n, () -> POLICIES, variables, OopslaConstants.ALGORITHM, subscriptions, null);
    }

    private static AuthorizationSubscription buildRequest(int n, Random rng) {
        var subject  = OopslaConstants.PREFIX_USER + rng.nextInt(n);
        var action   = ACTIONS[rng.nextInt(ACTIONS.length)];
        var isDoc    = rng.nextBoolean();
        var idx      = rng.nextInt(n);
        var resource = isDoc ? OopslaConstants.PREFIX_DOC + idx : OopslaConstants.PREFIX_FOLDER + idx;
        return AuthorizationSubscription.of(subject, action, resource);
    }

    private static Value collectEdges(int n, String prefix, Random rng) {
        var edges = new ArrayList<Value>();
        for (int i = 0; i < n; i++) {
            if (rng.nextDouble() < OopslaConstants.EDGE_PROBABILITY) {
                edges.add(Value.of(prefix + i));
            }
        }
        return Value.ofArray(edges.toArray(Value[]::new));
    }

}
