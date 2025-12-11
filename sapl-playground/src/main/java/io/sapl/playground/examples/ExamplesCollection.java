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

import io.sapl.api.pdp.CombiningAlgorithm;
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
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                       "subject"     : { "username": "alice", "role": "employee" },
                       "action"      : "access",
                       "resource"    : "system",
                       "environment" : null
                    }
                    """, DEFAULT_VARIABLES);

    /* Documentation Examples */

    private static final Example DOCUMENTATION_AT_A_GLANCE = new Example("at-a-glance", "Introduction Policy",
            "Compartmentalize read access by department", List.of("""
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
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
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
                    """, DEFAULT_VARIABLES);

    private static final Example DOCUMENTATION_BUSINESS_HOURS = new Example("business-hours",
            "Time-based policy to deny access", "A time-based denying policy.",
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
            CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                       "subject"     : { "role": "doctor", "department": "cardiology"},
                       "action"      : "read",
                       "resource"    : { "type": "patient_record", "department": "cardiology" },
                       "environment" : null
                    }
                    """, DEFAULT_VARIABLES);

    private static final Example DOCUMENTATION_DENY_OVERRIDES = new Example("deny-overrides-demo",
            "Deny Overrides Algorithm",
            "Multiple policies where a single DENY blocks access despite PERMIT decisions being present.", List.of("""
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
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                       "subject"     : { "authenticated": true, "role": "user", "status": "suspended" },
                       "action"      : "access",
                       "resource"    : "system"
                    }
                    """, DEFAULT_VARIABLES);

    /* Medical Examples */

    private static final Example MEDICAL_EMERGENCY_OVERRIDE = new Example("emergency-override",
            "Emergency Access Override (Breaking the Glass)",
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
            CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                       "subject"     : { "username": "house", "position": "doctor", "department": "diagnostics" },
                       "action"      : "read",
                       "resource"    : { "type": "patient_record", "id": 123, "department": "cardiology" },
                       "environment" : { "location": "emergency room" }
                    }
                    """, DEFAULT_VARIABLES);

    /* Geographic Examples */

    private static final Example GEOGRAPHIC_INSIDE_PERIMETER = new Example("geo-permit-inside-perimeter",
            "Geo-fence: inside perimeter", "Permit if a point is inside a polygon perimeter using GeoJSON.", List.of("""
                    // Grants access only when the subject location lies completely within the
                    // allowed perimeter supplied with the resource as a GeoJSON Polygon.
                    // Uses topological containment (no projection required).

                    policy "permit-inside-perimeter"
                    permit
                        // Policy is applicable for the 'access' action
                        action == "access"
                    where
                        // Containment check: subject.location âˆˆ resource.perimeter
                        geo.within(subject.location, resource.perimeter);
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                      "subject"     : { "username": "alice",
                                        "location": { "type": "Point", "coordinates": [5, 5] } },
                      "action"      : "access",
                      "resource"    : { "perimeter": {
                                          "type": "Polygon",
                                          "coordinates": [[[0,0],[0,10],[10,10],[10,0],[0,0]]]
                                        } }
                    }
                    """, DEFAULT_VARIABLES);

    private static final Example GEOGRAPHIC_NEAR_FACILITY = new Example("geo-permit-near-facility",
            "Proximity: geodesic distance â‰¤ 200 m",
            "Permit when the subject is within 200 meters (WGS84) of the facility.", List.of("""
                    // Grants access if the geodesic distance on WGS84 between the subject position and
                    // the facility location is at most 200 meters. Uses geo.isWithinGeodesicDistance.

                    policy "permit-near-facility"
                    permit
                        action == "access"
                    where
                        // Geodesic proximity check in meters (WGS84)
                        geo.isWithinGeodesicDistance(subject.location, resource.facility.location, 200);
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                      "subject"     : { "username": "bob",
                                        "location": { "type": "Point",
                                                      "coordinates": [13.4050, 52.5200] } },
                      "action"      : "access",
                      "resource"    : { "facility": { "location": { "type": "Point",
                                                                     "coordinates": [13.4065, 52.5210] } } }
                    }
                    """, DEFAULT_VARIABLES);

    private static final Example GEOGRAPHIC_DENY_INTERSECTS_RESTRICTED = new Example("geo-deny-intersects-restricted",
            "Deny when requested area intersects restricted zone",
            "Deny access if a requested area overlaps any restricted area.", List.of("""
                    // Denies access when the requested area overlaps a restricted zone. Intersections of any
                    // dimension trigger a denial. Disjoint regions would pass this test.

                    policy "deny-over-restricted-area"
                    deny
                        action.type == "export"
                    where
                        // Block if there is any spatial overlap with restricted zones
                        geo.intersects(action.requestedArea, environment.restrictedArea);
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
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
                    """, DEFAULT_VARIABLES);

    private static final Example GEOGRAPHIC_WAYPOINTS_SUBSET = new Example("geo-permit-waypoints-subset",
            "Waypoints must be subset of authorized set",
            "Permit only if all requested waypoints are contained in the authorized set.", List.of("""
                    // Ensures every requested waypoint is in the pre-authorized set. Uses subset over
                    // GeometryCollections of Points. Suitable for corridor/anchor validation.

                    policy "permit-authorized-waypoints"
                    permit
                        action.type == "navigate"
                    where
                        // All action.waypoints must be elements of subject.authorizedPoints
                        geo.subset(action.waypoints, subject.authorizedPoints);
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
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
                    """, DEFAULT_VARIABLES);

    private static final Example GEOGRAPHIC_BUFFER_TOUCH = new Example("geo-permit-buffer-touch",
            "Adjacency via buffer-touch", "Permit when a buffered asset footprint just touches the inspection path.",
            List.of("""
                    // Creates a buffer around the asset footprint and checks whether the buffer boundary
                    // touches the inspection path. This models strict adjacency without overlap.

                    policy "permit-adjacent-buffer-touch"
                    permit
                        action.type == "inspect"
                    where
                        // Buffer width is in same units as coordinates; for planar toy data this is fine
                        geo.touches(geo.buffer(resource.assetFootprint, 10), action.inspectionPath);
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                      "subject"     : { "username": "erin" },
                      "action"      : { "type": "inspect",
                                       "inspectionPath": { "type": "LineString",
                                                 "coordinates": [[10,0],[20,0]] } },
                      "resource"    : { "assetFootprint": { "type": "Point", "coordinates": [0,0] } },
                      "environment" : null
                    }
                    """, DEFAULT_VARIABLES);

    private static final Example GEOGRAPHIC_WKT_INSIDE_ZONE = new Example("geo-permit-wkt-inside-zone",
            "Normalize WKT then check containment", "Convert WKT to GeoJSON and check within against an allowed zone.",
            List.of("""
                    // Converts a WKT point to GeoJSON using geo.wktToGeoJSON, then checks containment
                    // against the allowed zone polygon.

                    policy "permit-wkt-inside-zone"
                    permit
                        action.type == "ingest"
                    where
                        var geom = geo.wktToGeoJSON(action.geometryWkt);
                        geo.within(geom, resource.allowedZone);
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                      "subject"     : { "username": "frank" },
                      "action"      : { "type": "ingest", "geometryWkt": "POINT (5 5)" },
                      "resource"    : { "allowedZone": {
                                          "type": "Polygon",
                                          "coordinates": [[[0,0],[0,10],[10,10],[10,0],[0,0]]]
                                        } },
                      "environment" : null
                    }
                    """, DEFAULT_VARIABLES);

    /* Access Control Examples */

    private static final Example ACCESS_CONTROL_BELL_LAPADULA_BASIC = new Example("bell-lapadula-basic",
            "Bell-LaPadula: NATO Classification",
            "Confidentiality model with security clearances - no read up, no write down", List.of("""
                    // Bell-LaPadula Model: Basic Confidentiality Policy
                    //
                    // The Bell-LaPadula model enforces confidentiality through two core properties:
                    //
                    // 1. Simple Security Property (ss-property): "no read up"
                    //    A subject cannot read objects at a higher classification level.
                    //    This prevents unauthorized disclosure of classified information.
                    //
                    // 2. *-Property (Star Property): "no write down"
                    //    A subject cannot write to objects at a lower classification level.
                    //    This prevents classified information from leaking to lower levels.
                    //
                    // Security levels (from lowest to highest):
                    // UNCLASSIFIED < CONFIDENTIAL < SECRET < TOP_SECRET

                    policy "bell-lapadula read access"
                    permit
                        action == "read"
                    where
                        // Simple Security Property: subject clearance >= object classification
                        // This implements "no read up" - cannot read above clearance level
                        subject.clearance_level >= resource.classification_level;
                    """, """
                    policy "bell-lapadula write access"
                    permit
                        action == "write"
                    where
                        // *-Property (Star Property): subject clearance <= object classification
                        // This implements "no write down" - cannot write below clearance level
                        // Prevents information from flowing downward to less secure levels
                        subject.clearance_level <= resource.classification_level;
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                      "subject": {
                        "username": "alice",
                        "clearance_level": 3,
                        "clearance_name": "SECRET"
                      },
                      "action": "read",
                      "resource": {
                        "document_id": "DOC-2024-089",
                        "classification_level": 2,
                        "classification_name": "CONFIDENTIAL",
                        "title": "Budget Report Q4"
                      }
                    }
                    """, """
                    {
                      "classification_levels": {
                        "UNCLASSIFIED": 0,
                        "CONFIDENTIAL": 2,
                        "SECRET": 3,
                        "TOP_SECRET": 4
                      }
                    }
                    """);

    private static final Example ACCESS_CONTROL_BELL_LAPADULA_COMPARTMENTS = new Example("bell-lapadula-compartments",
            "Bell-LaPadula: Classification + Compartments",
            "Extended Bell-LaPadula with departmental compartmentalization", List.of("""
                    // Bell-LaPadula Model with Compartments (Departments)
                    //
                    // This extends the basic Bell-LaPadula model with compartmentalization.
                    // Access requires BOTH sufficient clearance level AND membership in the
                    // appropriate compartment (department).
                    //
                    // This models real-world scenarios where:
                    // - A TOP_SECRET clearance doesn't grant access to ALL top secret documents
                    // - Information is additionally compartmentalized by need-to-know
                    // - Cross-department access requires explicit authorization

                    policy "compartmentalized read access"
                    permit
                        action == "read"
                    where
                        // Must satisfy clearance level (no read up)
                        subject.clearance_level >= resource.classification_level;

                        // Must also satisfy compartment requirement (need-to-know)
                        // Subject's departments must contain at least one of the resource's required departments
                        array.containsAny(subject.departments, resource.required_departments);
                    """, """
                    policy "compartmentalized write access"
                    permit
                        action == "write"
                    where
                        // *-Property: no write down
                        subject.clearance_level <= resource.classification_level;

                        // Compartment check for write operations
                        array.containsAny(subject.departments, resource.required_departments);
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                      "subject": {
                        "username": "bob",
                        "clearance_level": 4,
                        "clearance_name": "TOP_SECRET",
                        "departments": ["intelligence", "cyber-operations"]
                      },
                      "action": "read",
                      "resource": {
                        "document_id": "TS-CYBER-2024-445",
                        "classification_level": 4,
                        "classification_name": "TOP_SECRET",
                        "required_departments": ["cyber-operations"],
                        "title": "Advanced Persistent Threat Analysis"
                      }
                    }
                    """, """
                    {
                      "classification_levels": {
                        "UNCLASSIFIED": 0,
                        "CONFIDENTIAL": 2,
                        "SECRET": 3,
                        "TOP_SECRET": 4
                      }
                    }
                    """);

    private static final Example ACCESS_CONTROL_BREWER_NASH_FINANCIAL = new Example("brewer-nash-financial",
            "Brewer-Nash: Financial Conflict of Interest",
            "Chinese Wall model preventing insider trading and conflicts of interest", List.of("""
                    // Brewer-Nash Model (Chinese Wall Policy) - Financial Sector
                    //
                    // The Brewer-Nash model prevents conflicts of interest by dynamically restricting
                    // access based on what information a user has already accessed.
                    //
                    // Key Concepts:
                    // - Conflict of Interest (COI) Classes: Groups of competing entities
                    // - Once accessing entity A, cannot access competing entity B
                    // - CAN access entities in different COI classes
                    // - CAN access the same entity multiple times
                    //
                    // Example: Investment analyst who accesses Bank-A's data cannot subsequently
                    // access Bank-B's data (same COI class), but CAN access Oil-Company-X's data
                    // (different COI class).
                    //
                    // Architecture:
                    // - Generic DENY policy enforces Chinese Wall (applies to ALL actions)
                    // - Domain-specific PERMIT policies grant capabilities
                    // - DENY_OVERRIDES ensures conflicts always block access
                    //
                    // Note: There are two Brewer-Nash examples. They share this exact same policy.
                    //       This demonstrates how we achieved clear separation of concerns between
                    //       Brewer-Nash conflict of interest handling and other domain specific
                    //       capabilities modelling.

                    policy "brewer-nash-guard"
                    deny
                        // No target expression - applies to ALL requests
                    where
                        // Determine which COI classes contain the requested entity
                        var requestedCoiClasses = array.flatten(
                            coiClasses[?(resource.entity in @.entities)].conflict_class
                        );

                        // Get all entities the subject has previously accessed
                        var previouslyAccessedEntities = subject.access_history[*].entity;

                        // Find which COI classes contain any of the previously accessed entities
                        var accessedCoiClasses = coiClasses[?(
                            array.containsAny(@.entities, previouslyAccessedEntities)
                        )].conflict_class;

                        // Check if there's overlap between requested and accessed COI classes
                        var conflictExists = array.containsAny(requestedCoiClasses, accessedCoiClasses);

                        // DENY if: conflict detected AND attempting to access a different entity
                        // ALLOW if: no conflict OR accessing the same entity again
                        conflictExists && !(resource.entity in previouslyAccessedEntities);
                    """, """
                    policy "financial-analysts-read-company-data"
                    permit
                        action == "read" & resource.type == "financial_report"
                    where
                        subject.role == "financial_analyst";
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                      "subject": {
                        "username": "analyst_sarah",
                        "role": "financial_analyst",
                        "access_history": [
                          { "entity": "GlobalBank", "timestamp": "2024-10-01T09:00:00Z" },
                          { "entity": "OilCorpAlpha", "timestamp": "2024-10-05T14:30:00Z" }
                        ]
                      },
                      "action": "read",
                      "resource": {
                        "entity": "PetroEnergy",
                        "type": "financial_report",
                        "document_id": "FIN-2024-Q3"
                      }
                    }
                    """, """
                    {
                      "coiClasses": [
                        {
                          "conflict_class": "banking",
                          "entities": ["GlobalBank", "CityFinancial", "NationalBank"]
                        },
                        {
                          "conflict_class": "energy",
                          "entities": ["OilCorpAlpha", "PetroEnergy", "GasGiant"]
                        },
                        {
                          "conflict_class": "technology",
                          "entities": ["TechStartupX", "CloudInnovate"]
                        }
                      ]
                    }
                    """);

    private static final Example ACCESS_CONTROL_BREWER_NASH_CONSULTING = new Example("brewer-nash-consulting",
            "Brewer-Nash: Multi-Industry Consulting", "Chinese Wall with multiple conflict classes for consulting firm",
            List.of("""
                    // Brewer-Nash Model - Consulting Firm with Multiple COI Classes
                    //
                    // This demonstrates a consulting firm scenario where consultants can work
                    // with clients across different industries, but face restrictions within
                    // each industry's conflict of interest class.
                    //
                    // Example Conflict Classes:
                    // - Pharmaceutical: PharmaCo, MediGen, HealthDrugs
                    // - Technology: TechCorp, SoftwareInc, CloudSystems
                    // - Automotive: AutoMaker, CarDesign, VehicleTech
                    //
                    // A consultant can work with PharmaCo AND TechCorp simultaneously (different COI),
                    // but cannot work with both PharmaCo and MediGen (same COI).
                    //
                    // Architecture:
                    // - Same generic DENY policy enforces Chinese Wall
                    // - Different PERMIT policy grants consultant capabilities
                    // - Demonstrates reusability of the guard policy across domains
                    //
                    // Note: There are two Brewer-Nash examples. They share this exact same policy.
                    //       This demonstrates how we achieved clear separation of concerns between
                    //       Brewer-Nash conflict of interest handling and other domain specific
                    //       capabilities modelling.

                    policy "brewer-nash-guard"
                    deny
                        // No target expression - applies to ALL requests
                    where
                        // Determine which COI classes contain the requested entity
                        var requestedCoiClasses = array.flatten(
                            coiClasses[?(resource.entity in @.entities)].conflict_class
                        );

                        // Get all entities the subject has previously accessed
                        var previouslyAccessedEntities = subject.access_history[*].entity;

                        // Find which COI classes contain any of the previously accessed entities
                        var accessedCoiClasses = coiClasses[?(
                            array.containsAny(@.entities, previouslyAccessedEntities)
                        )].conflict_class;

                        // Check if there's overlap between requested and accessed COI classes
                        var conflictExists = array.containsAny(requestedCoiClasses, accessedCoiClasses);

                        // DENY if: conflict detected AND attempting to access a different entity
                        // ALLOW if: no conflict OR accessing the same entity again
                        conflictExists && !(resource.entity in previouslyAccessedEntities);
                    """, """
                    policy "consultants-access-client-data"
                    permit
                        action == "access"
                    where
                        subject.role == "consultant";
                    obligation
                        {
                            "type": "log_access",
                            "message": "Consultant " + subject.username + " accessed client " + resource.entity,
                            "audit": true
                        }
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                      "subject": {
                        "username": "consultant_mike",
                        "role": "consultant",
                        "access_history": [
                          { "entity": "PharmaCo", "project": "supply-chain", "date": "2024-09-15" },
                          { "entity": "TechCorp", "project": "cloud-migration", "date": "2024-10-01" }
                        ]
                      },
                      "action": "access",
                      "resource": {
                        "entity": "SoftwareInc",
                        "type": "strategic_plan",
                        "project": "market-expansion"
                      }
                    }
                    """, """
                    {
                      "coiClasses": [
                        {
                          "conflict_class": "pharmaceutical",
                          "entities": ["PharmaCo", "MediGen", "HealthDrugs", "BioPharma"]
                        },
                        {
                          "conflict_class": "technology",
                          "entities": ["TechCorp", "SoftwareInc", "CloudSystems", "DataAnalytics"]
                        },
                        {
                          "conflict_class": "automotive",
                          "entities": ["AutoMaker", "CarDesign", "VehicleTech", "ElectricMotors"]
                        },
                        {
                          "conflict_class": "retail",
                          "entities": ["ShopMart", "RetailGiant", "OnlineStore"]
                        }
                      ]
                    }
                    """);

    private static final Example ACCESS_CONTROL_BIBA_INTEGRITY = new Example("biba-integrity", "Biba Integrity Model",
            "Integrity protection with no read down, no write up", List.of("""
                    // Biba Integrity Model
                    //
                    // The Biba model is the dual of Bell-LaPadula, focusing on INTEGRITY
                    // rather than confidentiality. It prevents corruption of high-integrity
                    // data by low-integrity sources.
                    //
                    // Two core properties:
                    //
                    // 1. Simple Integrity Property: "no read down"
                    //    Subjects cannot read objects at a lower integrity level.
                    //    Prevents high-integrity subjects from being contaminated by
                    //    low-integrity data.
                    //
                    // 2. *-Integrity Property: "no write up"
                    //    Subjects cannot write to objects at a higher integrity level.
                    //    Prevents low-integrity subjects from corrupting high-integrity data.
                    //
                    // Integrity levels (from lowest to highest):
                    // LOW < MEDIUM < HIGH
                    //
                    // Example: In software development, production code (HIGH) should not
                    // be contaminated by untested user input (LOW).

                    policy "biba read integrity"
                    permit
                        action == "read"
                    where
                        // Simple Integrity Property: subject integrity >= object integrity
                        // "no read down" - prevents reading less trustworthy data
                        // High-integrity processes should not consume low-integrity data
                        subject.integrity_level >= resource.integrity_level;
                    """, """
                    policy "biba write integrity"
                    permit
                        action == "write"
                    where
                        // *-Integrity Property: subject integrity <= object integrity
                        // "no write up" - prevents writing to more trustworthy data
                        // Low-integrity processes cannot corrupt high-integrity data
                        subject.integrity_level <= resource.integrity_level;
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
                    {
                      "subject": {
                        "username": "build_service",
                        "process": "automated_build",
                        "integrity_level": 3,
                        "integrity_name": "HIGH"
                      },
                      "action": "read",
                      "resource": {
                        "resource_id": "source-code-main",
                        "resource_type": "production_code",
                        "integrity_level": 3,
                        "integrity_name": "HIGH",
                        "path": "/src/main/java/core"
                      }
                    }
                    """, """
                    {
                      "integrity_levels": {
                        "LOW": 1,
                        "MEDIUM": 2,
                        "HIGH": 3
                      },
                      "integrity_examples": {
                        "HIGH": "Production code, verified data, system binaries",
                        "MEDIUM": "Tested code, validated user input, internal tools",
                        "LOW": "User input, external data, temporary files"
                      }
                    }
                    """);

    private static final Example ACCESS_CONTROL_RBAC = new Example("role-based-access-control",
            "A Role-based Access Control Model", "Demonstrates implementing RBAC in SAPL", List.of("""
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
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
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
                    """);

    private static final Example ACCESS_CONTROL_HIERARCHICAL_RBAC = new Example(
            "hierarchical-role-based-access-control", "Hierarchical RBAC",
            "Hierarchical Demonstrates implementing RBAC in SAPL", List.of("""
                    policy "Hierarchical RBAC"
                    permit
                    where
                      // take the role graph from the variables and calculate the reachable roles
                      var effectiveRoles = graph.reachable(rolesHierarchy, subject.roles);

                      // from the permission filter the assignments whose role name is contained in the
                      // list of effective permissions and take the permissions of that assignment
                      // then flatten re resulting array of arrays.
                      var effectivePermissions = array.flatten(permissions[?(@.role in effectiveRoles)]..permissions);

                      // Finally check if the required permission action is contained in the
                      // effectivePermission of the subject.
                      { "action" : action, "type" : resource.type } in effectivePermissions;
                    """), CombiningAlgorithm.DENY_OVERRIDES, """
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
                    """);

    /* Category Definitions */

    private static final ExampleCategory DOCUMENTATION = new ExampleCategory("Documentation", "BOOK", 1,
            List.of(DOCUMENTATION_AT_A_GLANCE, DOCUMENTATION_BUSINESS_HOURS, DOCUMENTATION_DENY_OVERRIDES));

    private static final ExampleCategory MEDICAL = new ExampleCategory("Medical", "HEART", 2,
            List.of(MEDICAL_EMERGENCY_OVERRIDE));

    private static final ExampleCategory GEOGRAPHIC = new ExampleCategory("Geographic", "GLOBE", 4,
            List.of(GEOGRAPHIC_INSIDE_PERIMETER, GEOGRAPHIC_NEAR_FACILITY, GEOGRAPHIC_DENY_INTERSECTS_RESTRICTED,
                    GEOGRAPHIC_WAYPOINTS_SUBSET, GEOGRAPHIC_BUFFER_TOUCH, GEOGRAPHIC_WKT_INSIDE_ZONE));

    private static final ExampleCategory ACCESS_CONTROL = new ExampleCategory("Access Control", "LOCK", 5,
            List.of(ACCESS_CONTROL_RBAC, ACCESS_CONTROL_HIERARCHICAL_RBAC, ACCESS_CONTROL_BELL_LAPADULA_BASIC,
                    ACCESS_CONTROL_BELL_LAPADULA_COMPARTMENTS, ACCESS_CONTROL_BREWER_NASH_FINANCIAL,
                    ACCESS_CONTROL_BREWER_NASH_CONSULTING, ACCESS_CONTROL_BIBA_INTEGRITY));

    private static final List<ExampleCategory> ALL_CATEGORIES = List.of(ACCESS_CONTROL, DOCUMENTATION, MEDICAL,
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
