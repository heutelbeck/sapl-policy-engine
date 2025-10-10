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
                            """, DEFAULT_VARIABLES)));

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
