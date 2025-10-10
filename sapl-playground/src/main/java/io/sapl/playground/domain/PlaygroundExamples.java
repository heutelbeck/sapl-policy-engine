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
package io.sapl.playground.domain;

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
public class PlaygroundExamples {

    private static final String DEFAULT_VARIABLES = """
            {
              "systemMode" : "production"
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
            List.of(new Example("basic-permit", "Basic Permit Policy",
                    "Simple policy that permits access based on authentication status", List.of("""
                            policy "basic permit"
                            permit
                                subject.authenticated == true
                            """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                            {
                               "subject"     : { "authenticated": true, "username": "alice" },
                               "action"      : "read",
                               "resource"    : "document",
                               "environment" : null
                            }
                            """, DEFAULT_VARIABLES),
                    new Example("role-based-access", "Role-Based Access Control",
                            "Demonstrates RBAC with department-based compartmentalization", List.of("""
                                    policy "compartmentalize read access by department"
                                    permit
                                        resource.type == "patient_record" & action == "read"
                                    where
                                        subject.role == "doctor";
                                        resource.department == subject.department;
                                    """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                                    {
                                       "subject"     : { "role": "doctor", "department": "cardiology"},
                                       "action"      : "read",
                                       "resource"    : { "type": "patient_record", "department": "cardiology" },
                                       "environment" : null
                                    }
                                    """, """
                                    {
                                      "some": "value"
                                    }
                                    """),
                    new Example("deny-overrides-demo", "Deny Overrides Algorithm",
                            "Multiple policies where a single DENY blocks access despite PERMIT policies", List.of("""
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
                                       "resource"    : "system",
                                       "environment" : null
                                    }
                                    """, DEFAULT_VARIABLES)));

    private static final ExampleCategory MEDICAL = new ExampleCategory("Medical", VaadinIcon.HEART, 2,
            List.of(new Example("emergency-override", "Emergency Access Override",
                    "Emergency personnel can override normal access restrictions", List.of("""
                            policy "emergency override"
                            permit
                                resource.type == "patient_record"
                            where
                                subject.role == "emergency_doctor";
                            """), PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES, """
                            {
                               "subject"     : { "role": "emergency_doctor", "department": "emergency" },
                               "action"      : "read",
                               "resource"    : { "type": "patient_record", "department": "cardiology" },
                               "environment" : null
                            }
                            """, DEFAULT_VARIABLES),
                    new Example("medical-access-layers", "Layered Medical Access Control",
                            "Multiple policies controlling access to patient records with permit overrides", List.of("""
                                    policy "deny non-medical staff"
                                    deny
                                        resource.type == "patient_record"
                                    where
                                        !(subject.role in ["doctor", "nurse", "emergency_doctor"]);
                                    """, """
                                    policy "permit department access"
                                    permit
                                        resource.type == "patient_record" & action == "read"
                                    where
                                        subject.department == resource.department;
                                    """, """
                                    policy "permit emergency access"
                                    permit
                                        resource.type == "patient_record"
                                    where
                                        subject.role == "emergency_doctor";
                                    """), PolicyDocumentCombiningAlgorithm.PERMIT_OVERRIDES, """
                                    {
                                       "subject"     : { "role": "emergency_doctor", "department": "emergency" },
                                       "action"      : "read",
                                       "resource"    : { "type": "patient_record", "department": "cardiology" },
                                       "environment" : null
                                    }
                                    """, DEFAULT_VARIABLES)));

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
                               "resource"    : { "type": "ai_model", "name": "gpt-medical", "data_classification": 2 },
                               "environment" : null
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
                                       "resource"    : { "type": "ai_model", "name": "small-classifier", "size": 500, "verified": false },
                                       "environment" : null
                                    }
                                    """,
                            DEFAULT_VARIABLES)));

    private static final ExampleCategory GEOGRAPHIC = new ExampleCategory("Geographic", VaadinIcon.GLOBE, 4,
            List.of(new Example("location-based-access", "Location-Based Access",
                    "Access restricted to specific geographic regions", List.of("""
                            policy "regional access"
                            permit
                                action == "access_system"
                            where
                                subject.country in ["US", "CA", "MX"];
                            """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                            {
                               "subject"     : { "username": "user1", "country": "US" },
                               "action"      : "access_system",
                               "resource"    : "application",
                               "environment" : null
                            }
                            """, DEFAULT_VARIABLES),
                    new Example("geo-compliance-policies", "Geographic Compliance Layers",
                            "Multiple policies enforcing regional data residency and access rules", List.of("""
                                    policy "deny sanctioned countries"
                                    deny
                                        action == "access_data"
                                    where
                                        subject.country in ["XX", "YY"];
                                    """, """
                                    policy "permit EU data access from EU"
                                    permit
                                        action == "access_data" & resource.region == "EU"
                                    where
                                        subject.country in ["DE", "FR", "IT", "ES", "NL"];
                                    """, """
                                    policy "permit US data access from US"
                                    permit
                                        action == "access_data" & resource.region == "US"
                                    where
                                        subject.country == "US";
                                    """), PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, """
                                    {
                                       "subject"     : { "username": "user1", "country": "DE" },
                                       "action"      : "access_data",
                                       "resource"    : { "type": "database", "region": "EU" },
                                       "environment" : null
                                    }
                                    """, DEFAULT_VARIABLES)));

    private static final List<ExampleCategory> ALL_CATEGORIES = List.of(DOCUMENTATION, MEDICAL, AI, GEOGRAPHIC);

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
