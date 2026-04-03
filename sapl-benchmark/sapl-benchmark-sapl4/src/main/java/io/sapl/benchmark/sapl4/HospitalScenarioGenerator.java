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
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.benchmark.sapl4.oopsla.OopslaConstants;
import lombok.experimental.UtilityClass;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Hospital authorization scenario generator for canonical index benchmarking.
 * <p>
 * Generates a realistic hospital access control domain combining:
 * <ul>
 * <li><b>ReBAC</b>: staff membership hierarchy (Staff -> Team -> Department)
 * via {@code graph.transitiveClosureSet}. Each department-scoped policy
 * verifies the subject has a path to the target department.</li>
 * <li><b>ABAC</b>: role checks ({@code subject.role}), resource type checks
 * ({@code resource.type}), and sensitivity/clearance levels.</li>
 * <li><b>IN-list policies</b>: multi-action permits (e.g.,
 * {@code action in ["read", "write", "create"]}) whose elements deliberately
 * overlap with explicit {@code action == "read"} checks in other roles.
 * When the compiler unrolls IN-lists, both forms produce identical
 * {@code action == "read"} predicates that the canonical index can share.</li>
 * <li><b>Deny policies</b>: per-department sensitivity deny based on
 * {@code resource.sensitivity > subject.clearance}.</li>
 * <li><b>Emergency overrides</b>: global policies with action IN-lists.</li>
 * </ul>
 * <p>
 * <b>Scaling:</b> 33 policies per department + 5 global = 33n + 5 total.
 * At n=300, the total is ~10,000 policies over a fixed vocabulary of 9 roles,
 * 12 actions, and 10 resource types.
 * <p>
 * <b>Predicate sharing:</b> With n=100 departments (3,305 policies):
 * {@code action == "read"} appears in 1,000+ policies (explicit and unrolled),
 * {@code resource.type == "PatientRecord"} in 700,
 * {@code subject.role == "attending"}
 * in 600. The canonical index exploits this to narrow 3,305 policies to ~33
 * candidates with a few predicate lookups.
 *
 * @see io.sapl.benchmark.sapl4.oopsla.GithubScenarioGenerator
 */
@UtilityClass
public class HospitalScenarioGenerator {

    private static final JsonMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    private static final String ACTION_APPROVE   = "approve";
    private static final String ACTION_AUDIT     = "audit";
    private static final String ACTION_BILL      = "bill";
    private static final String ACTION_CREATE    = "create";
    private static final String ACTION_DELETE    = "delete";
    private static final String ACTION_ORDER     = "order";
    private static final String ACTION_PRESCRIBE = "prescribe";
    private static final String ACTION_READ      = "read";
    private static final String ACTION_WRITE     = "write";

    private static final String RESOURCE_AUDIT_LOG      = "AuditLog";
    private static final String RESOURCE_BILLING_RECORD = "BillingRecord";
    private static final String RESOURCE_CARE_NOTE      = "CareNote";
    private static final String RESOURCE_IMAGING        = "Imaging";
    private static final String RESOURCE_LAB_RESULT     = "LabResult";
    private static final String RESOURCE_PATIENT_RECORD = "PatientRecord";
    private static final String RESOURCE_PRESCRIPTION   = "Prescription";
    private static final String RESOURCE_REFERRAL       = "Referral";
    private static final String RESOURCE_SCHEDULE       = "Schedule";

    private record Permission(String resourceType, String[] actions) {}

    private record RoleSpec(String role, int clearance, Permission[] permissions) {}

    private static final RoleSpec[] ROLE_SPECS = {
            // attending (clearance 4): 6 resource types, all multi-action IN-lists
            new RoleSpec("attending", 4, new Permission[] {
                    new Permission(RESOURCE_PATIENT_RECORD, new String[] { ACTION_READ, ACTION_WRITE, ACTION_CREATE }),
                    new Permission(RESOURCE_LAB_RESULT, new String[] { ACTION_READ, ACTION_ORDER }),
                    new Permission(RESOURCE_PRESCRIPTION, new String[] { ACTION_READ, ACTION_WRITE, ACTION_PRESCRIBE }),
                    new Permission(RESOURCE_IMAGING, new String[] { ACTION_READ, ACTION_ORDER }),
                    new Permission(RESOURCE_CARE_NOTE, new String[] { ACTION_READ, ACTION_WRITE, ACTION_CREATE }),
                    new Permission(RESOURCE_REFERRAL, new String[] { ACTION_READ, ACTION_WRITE, ACTION_CREATE }) }),

            // resident (clearance 3): mix of single-action == and multi-action IN
            new RoleSpec("resident", 3,
                    new Permission[] {
                            new Permission(RESOURCE_PATIENT_RECORD, new String[] { ACTION_READ, ACTION_WRITE }),
                            new Permission(RESOURCE_LAB_RESULT, new String[] { ACTION_READ }),
                            new Permission(RESOURCE_PRESCRIPTION, new String[] { ACTION_READ }),
                            new Permission(RESOURCE_IMAGING, new String[] { ACTION_READ, ACTION_ORDER }),
                            new Permission(RESOURCE_CARE_NOTE,
                                    new String[] { ACTION_READ, ACTION_WRITE, ACTION_CREATE }),
                            new Permission(RESOURCE_REFERRAL, new String[] { ACTION_READ, ACTION_CREATE }) }),

            // nurse (clearance 3): mix of single == and multi-action IN
            new RoleSpec("nurse", 3,
                    new Permission[] {
                            new Permission(RESOURCE_PATIENT_RECORD, new String[] { ACTION_READ, ACTION_WRITE }),
                            new Permission(RESOURCE_LAB_RESULT, new String[] { ACTION_READ }),
                            new Permission(RESOURCE_PRESCRIPTION, new String[] { ACTION_READ }),
                            new Permission(RESOURCE_CARE_NOTE,
                                    new String[] { ACTION_READ, ACTION_WRITE, ACTION_CREATE }),
                            new Permission(RESOURCE_SCHEDULE, new String[] { ACTION_READ, ACTION_WRITE }) }),

            // labTech (clearance 2): multi-action IN only
            new RoleSpec("labTech", 2, new Permission[] {
                    new Permission(RESOURCE_LAB_RESULT, new String[] { ACTION_READ, ACTION_WRITE, ACTION_CREATE }),
                    new Permission(RESOURCE_IMAGING, new String[] { ACTION_READ, ACTION_WRITE, ACTION_CREATE }) }),

            // pharmacist (clearance 3): one IN-list, one single ==
            new RoleSpec("pharmacist", 3,
                    new Permission[] {
                            new Permission(RESOURCE_PRESCRIPTION,
                                    new String[] { ACTION_READ, ACTION_WRITE, ACTION_APPROVE }),
                            new Permission(RESOURCE_PATIENT_RECORD, new String[] { ACTION_READ }) }),

            // admin (clearance 2): one 4-element IN, one 2-element IN, one single ==
            new RoleSpec("admin", 2,
                    new Permission[] {
                            new Permission(RESOURCE_SCHEDULE,
                                    new String[] { ACTION_READ, ACTION_WRITE, ACTION_CREATE, ACTION_DELETE }),
                            new Permission(RESOURCE_BILLING_RECORD, new String[] { ACTION_READ, ACTION_WRITE }),
                            new Permission(RESOURCE_AUDIT_LOG, new String[] { ACTION_READ }) }),

            // billing (clearance 2): one 4-element IN, one single ==
            new RoleSpec("billing", 2,
                    new Permission[] {
                            new Permission(RESOURCE_BILLING_RECORD,
                                    new String[] { ACTION_READ, ACTION_WRITE, ACTION_CREATE, ACTION_BILL }),
                            new Permission(RESOURCE_PATIENT_RECORD, new String[] { ACTION_READ }) }),

            // auditor (clearance 4): one 4-element IN, two single ==
            new RoleSpec("auditor", 4,
                    new Permission[] {
                            new Permission(RESOURCE_AUDIT_LOG,
                                    new String[] { ACTION_READ, ACTION_WRITE, ACTION_CREATE, ACTION_AUDIT }),
                            new Permission(RESOURCE_PATIENT_RECORD, new String[] { ACTION_READ }),
                            new Permission(RESOURCE_BILLING_RECORD, new String[] { ACTION_READ }) }),

            // researcher (clearance 1): all single == "read"
            new RoleSpec("researcher", 1,
                    new Permission[] { new Permission(RESOURCE_PATIENT_RECORD, new String[] { ACTION_READ }),
                            new Permission(RESOURCE_LAB_RESULT, new String[] { ACTION_READ }),
                            new Permission(RESOURCE_IMAGING, new String[] { ACTION_READ }) }) };

    // Resource types accessible during emergencies
    private static final String[] EMERGENCY_RESOURCE_TYPES = { RESOURCE_PATIENT_RECORD, RESOURCE_LAB_RESULT,
            RESOURCE_PRESCRIPTION, RESOURCE_IMAGING, RESOURCE_CARE_NOTE };

    static final int            STAFF_PER_DEPARTMENT   = 5;
    static final int            TEAMS_PER_DEPARTMENT   = 2;
    private static final double CROSS_DEPT_PROBABILITY = 0.05;
    private static final int    REQUESTS               = 500;

    private static final String PREFIX_DEPT  = "dept_";
    private static final String PREFIX_TEAM  = "team_";
    private static final String PREFIX_STAFF = "staff_";

    private static final CombiningAlgorithm ALGORITHM = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    private record StaffMember(String id, String role, int clearance, int departmentIndex) {}

    // Per department: 32 permit + 1 sensitivity deny
    static final int PERMITS_PER_DEPARTMENT  = 32;
    static final int POLICIES_PER_DEPARTMENT = PERMITS_PER_DEPARTMENT + 1;
    static final int GLOBAL_POLICIES         = EMERGENCY_RESOURCE_TYPES.length;

    /**
     * Generates the hospital scenario.
     * <p>
     * Entity graph: Staff -> Team -> Department (parent edges for transitive
     * closure). Each staff member belongs to one primary team in their
     * department, with p=0.05 probability of cross-department team membership.
     * <p>
     * Policies are deterministic for a given numDepartments. The seed controls
     * entity graph randomization and subscription generation.
     *
     * @param numDepartments number of departments (scaling parameter). Each
     * department adds 33 policies (32 permit + 1 deny). n=300 yields ~10,000.
     * @param seed RNG seed for entity graph and subscription generation
     * @return scenario with 33*n + 5 policies and 500 subscriptions
     */
    public static Scenario generate(int numDepartments, long seed) {
        val rng          = new Random(seed);
        val staffGraph   = ObjectValue.builder();
        val staffMembers = new ArrayList<StaffMember>();
        var totalStaff   = 0;

        // Departments (no parents in the graph)
        for (int d = 0; d < numDepartments; d++) {
            staffGraph.put(PREFIX_DEPT + d, Value.ofArray());
        }

        // Teams: each team belongs to exactly one department
        for (int d = 0; d < numDepartments; d++) {
            for (int t = 0; t < TEAMS_PER_DEPARTMENT; t++) {
                staffGraph.put(PREFIX_TEAM + globalTeamIndex(d, t), Value.ofArray(Value.of(PREFIX_DEPT + d)));
            }
        }

        // Staff: random role, primary team in home department, optional
        // cross-department memberships
        val parents = new ArrayList<Value>();
        for (int d = 0; d < numDepartments; d++) {
            for (int s = 0; s < STAFF_PER_DEPARTMENT; s++) {
                val staffId     = PREFIX_STAFF + totalStaff;
                val roleSpec    = ROLE_SPECS[rng.nextInt(ROLE_SPECS.length)];
                val primaryTeam = globalTeamIndex(d, rng.nextInt(TEAMS_PER_DEPARTMENT));

                parents.clear();
                parents.add(Value.of(PREFIX_TEAM + primaryTeam));

                for (int od = 0; od < numDepartments; od++) {
                    if (od != d && rng.nextDouble() < CROSS_DEPT_PROBABILITY) {
                        parents.add(Value.of(PREFIX_TEAM + globalTeamIndex(od, rng.nextInt(TEAMS_PER_DEPARTMENT))));
                    }
                }

                staffGraph.put(staffId, Value.ofArray(parents.toArray(Value[]::new)));
                staffMembers.add(new StaffMember(staffId, roleSpec.role(), roleSpec.clearance(), d));
                totalStaff++;
            }
        }

        val policies = generatePolicies(numDepartments);

        val requestRng    = new Random(seed + OopslaConstants.REQUEST_RNG_SEED_OFFSET);
        val subscriptions = generateSubscriptions(numDepartments, staffMembers, requestRng);

        val variables = ObjectValue.builder().put("staffGraph", staffGraph.build()).build();

        return new Scenario("hospital-" + numDepartments, () -> policies, variables, ALGORITHM, subscriptions, null);
    }

    private static int globalTeamIndex(int dept, int localTeam) {
        return dept * TEAMS_PER_DEPARTMENT + localTeam;
    }

    private static List<String> generatePolicies(int numDepartments) {
        val policies = new ArrayList<String>(POLICIES_PER_DEPARTMENT * numDepartments + GLOBAL_POLICIES);

        for (int d = 0; d < numDepartments; d++) {
            for (val roleSpec : ROLE_SPECS) {
                for (val perm : roleSpec.permissions()) {
                    policies.add(departmentPermitPolicy(d, roleSpec.role(), perm));
                }
            }
            policies.add(sensitivityDenyPolicy(d));
        }

        for (val resourceType : EMERGENCY_RESOURCE_TYPES) {
            policies.add(emergencyPolicy(resourceType));
        }

        return policies;
    }

    /**
     * Generates a department-scoped permit policy.
     * <p>
     * Structure: subject.role == R AND resource.type == T AND action check
     * AND resource.department == D AND memberOf[(subject.id)] has D.
     * <p>
     * The action check is either {@code action == "X"} (single action) or
     * {@code action in ["X", "Y", ...]} (multi-action IN-list).
     */
    private static String departmentPermitPolicy(int dept, String role, Permission perm) {
        val deptId          = PREFIX_DEPT + dept;
        val actionCondition = perm.actions().length == 1 ? "action == \"%s\"".formatted(perm.actions()[0])
                : "action in [%s]".formatted(actionList(perm.actions()));

        return """
                policy "dept-%d-%s-%s"
                permit
                    subject.role == "%s";
                    resource.type == "%s";
                    %s;
                    resource.department == "%s";
                    var memberOf = graph.transitiveClosureSet(staffGraph);
                    memberOf[(subject.id)] has "%s";
                """.formatted(dept, role, perm.resourceType(), role, perm.resourceType(), actionCondition, deptId,
                deptId);
    }

    /**
     * Generates a per-department sensitivity deny policy.
     * <p>
     * Denies access when the resource sensitivity level exceeds the subject's
     * clearance. With PRIORITY_DENY combining, this overrides any matching
     * permit policy for the same department.
     */
    private static String sensitivityDenyPolicy(int dept) {
        return """
                policy "sensitivity-deny-dept-%d"
                deny
                    resource.department == "%s";
                    resource.sensitivity > subject.clearance;
                """.formatted(dept, PREFIX_DEPT + dept);
    }

    /**
     * Generates a global emergency override policy.
     * <p>
     * Emergency staff can access critical resource types in any department.
     * Uses an IN-list of emergency actions, creating additional predicate
     * overlap with department-scoped policies.
     */
    private static String emergencyPolicy(String resourceType) {
        return """
                policy "emergency-%s"
                permit
                    subject.role == "emergency";
                    resource.type == "%s";
                    action in ["read", "write", "prescribe", "override"];
                """.formatted(resourceType, resourceType);
    }

    private static String actionList(String[] actions) {
        val sb = new StringBuilder();
        for (int i = 0; i < actions.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(actions[i]).append('"');
        }
        return sb.toString();
    }

    // Actions weighted toward those that appear in more policies.
    // "read" appears in nearly every role, "write"/"create" in most, others are
    // rare.
    private static final String[] WEIGHTED_ACTIONS = { ACTION_READ, ACTION_READ, ACTION_READ, ACTION_READ, ACTION_READ,
            ACTION_READ, ACTION_READ, ACTION_WRITE, ACTION_WRITE, ACTION_WRITE, ACTION_CREATE, ACTION_CREATE,
            ACTION_ORDER, ACTION_PRESCRIBE, ACTION_APPROVE, ACTION_BILL, ACTION_AUDIT, ACTION_DELETE };

    // Resource types weighted toward those accessed by more roles.
    // PatientRecord: 7 roles, LabResult/CareNote: 5, others fewer.
    private static final String[] WEIGHTED_RESOURCE_TYPES = { RESOURCE_PATIENT_RECORD, RESOURCE_PATIENT_RECORD,
            RESOURCE_PATIENT_RECORD, RESOURCE_LAB_RESULT, RESOURCE_LAB_RESULT, RESOURCE_CARE_NOTE, RESOURCE_CARE_NOTE,
            RESOURCE_PRESCRIPTION, RESOURCE_IMAGING, RESOURCE_SCHEDULE, RESOURCE_BILLING_RECORD, RESOURCE_AUDIT_LOG,
            RESOURCE_REFERRAL };

    private static List<AuthorizationSubscription> generateSubscriptions(int numDepartments, List<StaffMember> staff,
            Random rng) {
        val subscriptions = new ArrayList<AuthorizationSubscription>(REQUESTS);
        for (int i = 0; i < REQUESTS; i++) {
            val s            = staff.get(rng.nextInt(staff.size()));
            val action       = WEIGHTED_ACTIONS[rng.nextInt(WEIGHTED_ACTIONS.length)];
            val resourceType = WEIGHTED_RESOURCE_TYPES[rng.nextInt(WEIGHTED_RESOURCE_TYPES.length)];
            val resourceDept = rng.nextDouble() < 0.8 ? s.departmentIndex() : rng.nextInt(numDepartments);
            val sensitivity  = rng.nextDouble() < 0.7 ? 1 : rng.nextInt(4) + 1;
            subscriptions.add(buildSubscription(s, action, resourceType, resourceDept, sensitivity));
        }
        return subscriptions;
    }

    private static AuthorizationSubscription buildSubscription(StaffMember staff, String action, String resourceType,
            int deptIndex, int sensitivity) {
        return MAPPER.readValue("""
                {
                    "subject": {"id": "%s", "role": "%s", "clearance": %d},
                    "action": "%s",
                    "resource": {"type": "%s", "department": "%s", "sensitivity": %d}
                }
                """.formatted(staff.id(), staff.role(), staff.clearance(), action, resourceType,
                PREFIX_DEPT + deptIndex, sensitivity), AuthorizationSubscription.class);
    }

}
