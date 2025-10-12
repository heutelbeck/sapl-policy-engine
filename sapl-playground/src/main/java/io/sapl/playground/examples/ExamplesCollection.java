/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.playground.examples;

import com.vaadin.flow.component.icon.VaadinIcon;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Optional;

/**
 * Repository of playground examples organized by category.
 * Provides access to predefined policy scenarios for learning and testing.
 */
@UtilityClass
public class ExamplesCollection {

    private static final String DEFAULT_VARIABLES = """
            {
            }
            """;

    /**
     * Default settings loaded on application startup.
     * Simple time-based policy demonstrating the time PIP.
     */
    public static final Example DEFAULT_SETTINGS = new Example("default", "Default Time-Based Access",
            "Simple time-based access control using the time policy information point", List.of("""
                    policy "business_hours_access"
                    permit
                        action == "access"
                    where
                        var now = <time.now>;
                        var hour = time.hourOf(now);
                        hour >= 9 && hour < 17;
                    """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                    {
                       "subject"     : { "username": "alice", "role": "employee" },
                       "action"      : "access",
                       "resource"    : "system",
                       "environment" : null
                    }
                    """, DEFAULT_VARIABLES);

    private static final ExampleCategory DOCUMENTATION = new ExampleCategory("Documentation", VaadinIcon.BOOK, 1,
            List.of(new Example("at-a-glance", "Introduction Policy", "Compartmentalize read access by department",
                    List.of("""
                            /* Example policy found in Section 1.1 of the SAPL Documentation.
                             *
                             * This is a simple Attribute-bases Access Control Policy
                             */
                            policy "compartmentalize read access by department"
                            permit
                                // This policy is evaluated if this expression evaluates to true.
                                // I.e., the policy is applicable if the resource is of type "patient_record"
                                // and a user, i.e., subject, is attempting to read it.
                                resource.type == "patient_record" & action == "read"
                            where
                                // In this case the subject must have the attribute role with value "doctor"
                                // Note, that "role" is just like any other attribute here.
                                subject.role == "doctor";
                                // And the patient record and the subject must both originate from the same department.
                                resource.department == subject.department;
                            """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                            {
                              "subject": {
                                "username": "alice",
                                "role": "doctor",
                                "department": "cardiology"
                              },
                              "action": "read",
                              "resource": {
                                "type": "patient_record",
                                "patientId": 123,
                                "department": "cardiology"
                              },
                              "environment": {
                                "timestamp": "2025-10-06T14:30:00Z"
                              }
                            }
                            """, DEFAULT_VARIABLES),
                    new Example("business-hours", "Time-based policy to deny access", "A time-based denying policy.",
                            List.of("""
                                    // Time-based deny policy from Section 1.3 of the SAPL Documentation.
                                    //
                                    // If this policy demonstrates how access rights can be controlled by
                                    // dynamic attributes without polling data.
                                    policy "deny access outside business hours"
                                    deny // If this policy's expressions evaluate to true, it will emit a DENY
                                        resource.type == "patient_record" & action == "read"
                                    where
                                        // The <> operator denotes access to external attributes.
                                        // The following is a Boolean attribute of the current time
                                        // time.localTimeIsBetween emits a single event when the attribute
                                        // is subscribed to and then again an event when the next relevant
                                        // transition of the hour of the local time is reached.
                                        // ! just negates the Boolean value.
                                        !<time.localTimeIsBetween("08:00:00", "18:00:00")>;

                                        // Contrast this with this implementation:
                                        // var currentHour = time.hourOf(<time.now>); // Note this always counts as a line evaluating to true
                                        // !(currentHour >= 9 && currentHour < 17);

                                        // Comment the first version and uncomment the two lines of
                                        // the second version to the the effect.
                                        // <time.now> polls the clock every second and then reevaluates the rule.
                                        // <tile.localTimeIsBetween> instead schedules an event in the future
                                        // and does not require any polling. This is an example for a temporal
                                        // logic policy.
                                    """),
                            PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                                    {
                                       "subject"     : { "role": "doctor", "department": "cardiology"},
                                       "action"      : "read",
                                       "resource"    : { "type": "patient_record", "department": "cardiology" },
                                       "environment" : null
                                    }
                                    """, DEFAULT_VARIABLES),
                    new Example("deny-overrides-demo", "Deny Overrides Algorithm",
                            "Multiple policies where a single DENY blocks access despite PERMIT decisions being present.",
                            List.of("""
                                    policy "permit authenticated users"
                                    permit
                                        subject.authenticated == true
                                    """, """
                                    policy "deny suspended users"
                                    deny
                                        subject.status == "suspended"
                                    """, """
                                    policy "permit regular users"
                                    permit
                                        subject.role == "user"
                                    """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                                    {
                                       "subject"     : { "authenticated": true, "role": "user", "status": "suspended" },
                                       "action"      : "access",
                                       "resource"    : "system"
                                    }
                                    """, DEFAULT_VARIABLES)));

    private static final ExampleCategory MEDICAL = new ExampleCategory("Medical", VaadinIcon.HEART, 2,
            List.of(new Example("emergency-override", "Emergency Access Override (Breaking the Glass)",
                    "Emergency personnel can override normal access restrictions",
                    List.of("""
                            // This example contains two policies.
                            // This policy grans doctors access to all patient records if they access them
                            // from the emergency room.
                            // The other policy only grants access if the patient and the doctor are assigned to
                            // the same department.
                            //
                            // Note: This policy contains an obligation. This clearly communicates to the system
                            // that it must send an email to the ethical board to review if this policy may have been
                            // abused outside of its intended use.
                            policy "emergency override"
                            permit
                                resource.type == "patient_record"
                            where
                                // ensures that this policy and obligation only triggers if the doctor would otherwise not have access.
                                resource.department != subject.department;
                                // check if access happens from the emergency room.
                                environment.location == "emergency room";
                            obligation {
                                         "type": "send email",
                                         "to": "ethical_board@princeton-plainsboro.med",
                                         "subject": "Emergency Access Detected",
                                         "message": "The record of patient " + resource.id +
                                                    " has been accessed from the emergency room by " +
                                                    subject.username +". Please review the process!"
                                       }
                            """,
                            """
                                    policy "compartmentalize read access by department"
                                    permit
                                        resource.type == "patient_record" & action == "read"
                                    where
                                        subject.role == "doctor";
                                        resource.department == subject.department;
                                    """),
                    PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES,
                    """
                            {
                               "subject"     : { "username": "house", "position": "doctor", "department": "diagnostics" },
                               "action"      : "read",
                               "resource"    : { "type": "patient_record", "id": 123, "department": "cardiology" },
                               "environment" : { "location": "emergency room" }
                            }
                            """,
                    DEFAULT_VARIABLES)));

    private static final ExampleCategory AI = new ExampleCategory("AI", VaadinIcon.AUTOMATION, 3,
            List.of(new Example("model-access-control", "AI Model Access Control",
                    "Controls access to AI models based on training data classification", List.of("""
                            policy "ai model access"
                            permit
                                action == "inference" & resource.type == "ai_model"
                            where
                                subject.clearance_level >= resource.data_classification;
                            """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                            {
                               "subject"     : { "username": "researcher", "clearance_level": 3 },
                               "action"      : "inference",
                               "resource"    : { "type": "ai_model", "name": "gpt-medical", "data_classification": 2 }
                            }
                            """, DEFAULT_VARIABLES),
                    new Example("ai-governance-policies", "AI Governance with Multiple Policies",
                            "Demonstrates first-applicable algorithm where policy order determines outcome", List.of("""
                                    policy "block unverified models"
                                    deny
                                        action == "deploy" & resource.type == "ai_model"
                                    where
                                        !resource.verified;
                                    """, """
                                    policy "allow admin deployment"
                                    permit
                                        action == "deploy"
                                    where
                                        subject.role == "ml_admin";
                                    """, """
                                    policy "allow researcher deployment of small models"
                                    permit
                                        action == "deploy" & resource.type == "ai_model"
                                    where
                                        subject.role == "researcher";
                                        resource.size < 1000;
                                    """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES,
                            """
                                    {
                                       "subject"     : { "role": "researcher", "username": "alice" },
                                       "action"      : "deploy",
                                       "resource"    : { "type": "ai_model", "name": "small-classifier", "size": 500, "verified": false }
                                    }
                                    """,
                            DEFAULT_VARIABLES)));

    private static final ExampleCategory GEOGRAPHIC = new ExampleCategory("Geographic", VaadinIcon.GLOBE, 4, List.of(
            new Example("geo-permit-inside-perimeter", "Geo-fence: inside perimeter",
                    "Permit if a point is inside a polygon perimeter using GeoJSON.", List.of("""
                            // Grants access only when the subject location lies completely within the
                            // allowed perimeter supplied with the resource as a GeoJSON Polygon.
                            // Uses topological containment (no projection required).

                            policy "permit-inside-perimeter"
                            permit
                                // Policy is applicable for the 'access' action
                                action == "access"
                            where
                                // Containment check: subject.location ∈ resource.perimeter
                                geo.within(subject.location, resource.perimeter);
                            """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                            {
                              "subject"     : { "username": "alice",
                                                "location": { "type": "Point", "coordinates": [5, 5] } },
                              "action"      : "access",
                              "resource"    : { "perimeter": {
                                                  "type": "Polygon",
                                                  "coordinates": [[[0,0],[0,10],[10,10],[10,0],[0,0]]]
                                                } }
                            }
                            """, DEFAULT_VARIABLES),

            new Example("geo-permit-near-facility", "Proximity: geodesic distance ≤ 200 m",
                    "Permit when the subject is within 200 meters (WGS84) of the facility.", List.of("""
                            // Grants access if the geodesic distance on WGS84 between the subject position and
                            // the facility location is at most 200 meters. Uses geo.isWithinGeodesicDistance.

                            policy "permit-near-facility"
                            permit
                                action == "access"
                            where
                                // Geodesic proximity check in meters (WGS84)
                                geo.isWithinGeodesicDistance(subject.location, resource.facility.location, 200);
                            """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                            {
                              "subject"     : { "username": "bob",
                                                "location": { "type": "Point",
                                                              "coordinates": [13.4050, 52.5200] } },
                              "action"      : "access",
                              "resource"    : { "facility": { "location": { "type": "Point",
                                                                             "coordinates": [13.4065, 52.5210] } } }
                            }
                            """, DEFAULT_VARIABLES),

            new Example("geo-deny-intersects-restricted", "Deny when requested area intersects restricted zone",
                    "Deny access if a requested area overlaps any restricted area.", List.of("""
                            // Denies access when the requested area overlaps a restricted zone. Intersections of any
                            // dimension trigger a denial. Disjoint regions would pass this test.

                            policy "deny-over-restricted-area"
                            deny
                                action.type == "export"
                            where
                                // Block if there is any spatial overlap with restricted zones
                                geo.intersects(action.requestedArea, environment.restrictedArea);
                            """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES,
                    """
                            {
                              "subject"     : { "username": "carol" },
                              "action"      : { "type": "export",
                                               "requestedArea": { "type": "Polygon",
                                                                     "coordinates": [[[0,0],[0,10],[10,10],[10,0],[0,0]]] } },
                              "resource"    : { },
                              "environment" : {
                                "restrictedArea": { "type": "Polygon",
                                                    "coordinates": [[[5,5],[5,15],[15,15],[15,5],[5,5]]] }
                              }
                            }
                            """,
                    DEFAULT_VARIABLES),

            new Example("geo-permit-waypoints-subset", "Waypoints must be subset of authorized set",
                    "Permit only if all requested waypoints are contained in the authorized set.", List.of("""
                            // Ensures every requested waypoint is in the pre-authorized set. Uses subset over
                            // GeometryCollections of Points. Suitable for corridor/anchor validation.

                            policy "permit-authorized-waypoints"
                            permit
                                action.type == "navigate"
                            where
                                // All action.waypoints must be elements of subject.authorizedPoints
                                geo.subset(action.waypoints, subject.authorizedPoints);
                            """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                            {
                              "subject"     : { "username": "dave",
                                                "authorizedPoints": { "type": "GeometryCollection",
                                                  "geometries": [
                                                    { "type": "Point", "coordinates": [1,1] },
                                                    { "type": "Point", "coordinates": [2,2] },
                                                    { "type": "Point", "coordinates": [3,3] }
                                                  ] } },
                              "action"      : { "type": "navigate",
                                               "waypoints": { "type": "GeometryCollection",
                                                    "geometries": [
                                                      { "type": "Point", "coordinates": [1,1] },
                                                      { "type": "Point", "coordinates": [2,2] }
                                                    ] } },
                              "resource"     : "something"
                            }
                            """, DEFAULT_VARIABLES),

            new Example("geo-permit-buffer-touch", "Adjacency via buffer-touch",
                    "Permit when a buffered asset footprint just touches the inspection path.", List.of("""
                            // Creates a buffer around the asset footprint and checks whether the buffer boundary
                            // touches the inspection path. This models strict adjacency without overlap.

                            policy "permit-adjacent-buffer-touch"
                            permit
                                action.type == "inspect"
                            where
                                // Buffer width is in same units as coordinates; for planar toy data this is fine
                                geo.touches(geo.buffer(resource.assetFootprint, 10), action.inspectionPath);
                            """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                            {
                              "subject"     : { "username": "erin" },
                              "action"      : { "type": "inspect",
                                               "inspectionPath": { "type": "LineString",
                                                         "coordinates": [[10,0],[20,0]] } },
                              "resource"    : { "assetFootprint": { "type": "Point", "coordinates": [0,0] } },
                              "environment" : null
                            }
                            """, DEFAULT_VARIABLES),

            new Example("geo-permit-wkt-inside-zone", "Normalize WKT then check containment",
                    "Convert WKT to GeoJSON and check within against an allowed zone.", List.of("""
                            // Converts a WKT point to GeoJSON using geo.wktToGeoJSON, then checks containment
                            // against the allowed zone polygon.

                            policy "permit-wkt-inside-zone"
                            permit
                                action.type == "ingest"
                            where
                                var geom = geo.wktToGeoJSON(action.geometryWkt);
                                geo.within(geom, resource.allowedZone);
                            """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                            {
                              "subject"     : { "username": "frank" },
                              "action"      : { "type": "ingest", "geometryWkt": "POINT (5 5)" },
                              "resource"    : { "allowedZone": {
                                                  "type": "Polygon",
                                                  "coordinates": [[[0,0],[0,10],[10,10],[10,0],[0,0]]]
                                                } },
                              "environment" : null
                            }
                            """, DEFAULT_VARIABLES)));

    private static final ExampleCategory ACCESS_CONTROL = new ExampleCategory("Access Control", VaadinIcon.LOCK, 3,
            List.of(new Example("role-based-access-control", "A Role-based Access Control Model",
                    "Demonstrates implementing RBAC in SAPL", List.of("""
                            // Implements RBAC in SAPL

                            policy "Simple RBAC"
                            permit
                               // Note: The global mapping between roles and permissions is stored the the
                               // variables of the PDP. In the playground, see the leftmost tab.
                               //
                               // All fields in the variables object are automatically bound tho variables with
                               // the name of the field. Therefore, permissions automatically become a fist-class
                               // object accessible in all policies.
                               //
                               // This RBAC expression can be combined with any other access control model to implement
                               // hybrid approaches.

                               { "type" : resource.type, "action": action } in permissions[(subject.role)]

                               // Note: if the permissions are only relevant for this one policy, they can also be
                               // embedded into the policy by defining a constant variable 'permissions':
                               //
                               // policy "RBAC"
                               // permit
                               // where
                               //   var permissions = { ... };
                               //   { "type" : resource.type, "action": action } in permissions[(subject.role)];
                            """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                            {
                               "subject"     : { "username": "alice", "role": "customer" },
                               "action"      : "read",
                               "resource"    : { "type" : "product availability"}
                            }
                            """, """
                            {
                              "permissions" : {
                                "customer" : [
                                    { "type": "product availability", "action": "read"  },
                                    { "type": "customer email",       "action": "read"  },
                                    { "type": "customer email",       "action": "write" },
                                    { "type": "shopping basked",      "action": "read"  },
                                    { "type": "shopping basked",      "action": "write" }
                                  ],
                                "warehousing" : [
                                    { "type": "product availability", "action": "read"  },
                                    { "type": "product availability", "action": "write" }
                                  ],
                                "accounting" : [
                                    { "type": "payroll", "action": "read"  },
                                    { "type": "payroll", "action": "write" }
                                ]
                              }
                            }
                            """),
                    new Example("hierarchical-role-based-access-control", "Hierarchical RBAC",
                            "Hierarchical Demonstrates implementing RBAC in SAPL", List.of("""
                                    policy "Hierarchical RBAC"
                                    permit
                                    where
                                      var effectiveRoles = graph.reachable(rolesHierarchy, subject.roles);
                                      var effectivePermissions = array.flatten(permissions[?(@.role in effectiveRoles)]..permissions);
                                      { "action" : action, "type" : resource.type } in effectivePermissions;
                                    """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                                    {
                                       "subject"     : { "username": "alice", "roles": [ "cso", "market-analyst" ] },
                                       "action"      : "read",
                                       "resource"    : { "type" : "alerts"}
                                    }
                                    """, """
                                    {
                                      "rolesHierarchy": {
                                        "cso": ["security-manager", "it-operations-manager", "compliance-manager"],
                                        "security-manager": ["secops-analyst", "threat-hunter"],
                                        "it-operations-manager": ["site-reliability-engineer", "platform-admin"],
                                        "compliance-manager": ["internal-auditor", "risk-analyst"]
                                      },
                                      "permissions": [
                                        {
                                          "role": "secops-analyst",
                                          "permissions": [
                                            { "type": "alerts", "action": "read" },
                                            { "type": "incidents", "action": "update" },
                                            { "type": "service logs", "action": "read" }
                                          ]
                                        },
                                        {
                                          "role": "threat-hunter",
                                          "permissions": [
                                            { "type": "service logs", "action": "read" },
                                            { "type": "alerts", "action": "read" },
                                            { "type": "hunt reports", "action": "create" }
                                          ]
                                        },
                                        {
                                          "role": "site-reliability-engineer",
                                          "permissions": [
                                            { "type": "service", "action": "deploy" },
                                            { "type": "service", "action": "rollback" },
                                            { "type": "service logs", "action": "read" }
                                          ]
                                        },
                                        {
                                          "role": "platform-admin",
                                          "permissions": [
                                            { "type": "service config", "action": "update" },
                                            { "type": "service", "action": "restart" }
                                          ]
                                        },
                                        {
                                          "role": "internal-auditor",
                                          "permissions": [
                                            { "type": "audit logs", "action": "read" },
                                            { "type": "audit logs", "action": "export" },
                                            { "type": "audit reports", "action": "read" }
                                          ]
                                        },
                                        {
                                          "role": "risk-analyst",
                                          "permissions": [
                                            { "type": "risk register", "action": "read" },
                                            { "type": "risk register", "action": "update" },
                                            { "type": "policies", "action": "read" }
                                          ]
                                        },
                                        {
                                          "role": "security-manager",
                                          "permissions": [
                                            { "type": "incidents", "action": "close" },
                                            { "type": "policies", "action": "approve" }
                                          ]
                                        },
                                        {
                                          "role": "it-operations-manager",
                                          "permissions": [
                                            { "type": "service", "action": "approve-deployment" }
                                          ]
                                        },
                                        {
                                          "role": "compliance-manager",
                                          "permissions": [
                                            { "type": "audit reports", "action": "approve" },
                                            { "type": "exceptions", "action": "approve" }
                                          ]
                                        },
                                        {
                                          "role": "cso",
                                          "permissions": [
                                            { "type": "policies", "action": "approve" },
                                            { "type": "risk register", "action": "approve" },
                                            { "type": "audit reports", "action": "approve" }
                                          ]
                                        },
                                        {
                                          "role": "market-analyst",
                                          "permissions": [
                                            { "type": "customer statistics", "action": "read" }
                                          ]
                                        }
                                      ]
                                    }
                                    """)));

    private static final List<ExampleCategory> ALL_CATEGORIES = List.of(DOCUMENTATION, ACCESS_CONTROL, MEDICAL, AI,
            GEOGRAPHIC);

    /**
     * Gets all example categories in display order.
     *
     * @return ordered list of example categories
     */
    public static List<ExampleCategory> getAllCategories() {
        return ALL_CATEGORIES.stream().sorted((a, b) -> Integer.compare(a.order(), b.order())).toList();
    }

    /**
     * Finds an example by its slug identifier.
     *
     * @param slug the unique slug identifier
     * @return the example if found
     */
    public static Optional<Example> findBySlug(String slug) {
        return ALL_CATEGORIES.stream().flatMap(category -> category.examples().stream())
                .filter(example -> example.slug().equals(slug)).findFirst();
    }
}
