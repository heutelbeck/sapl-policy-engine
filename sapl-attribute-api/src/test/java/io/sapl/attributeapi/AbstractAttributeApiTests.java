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
package io.sapl.attributeapi;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Backend-agnostic API tests. Each subclass wires a different
 * AttributeRepository implementation and runs this full test suite against it.
 */
@AutoConfigureMockMvc
abstract class AbstractAttributeApiTests {

    @Autowired
    protected MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        cleanRepository();
    }

    /** Subclasses clean their backend between tests. */
    protected abstract void cleanRepository();

    @Test
    @DisplayName("PUT /api/attributes/{name} returns 201")
    void publishGlobalAttribute() throws Exception {
        mockMvc.perform(
                put("/api/attributes/sapl.test.role").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        { "value": "test_1",
                          "ttl": 60
                         }
                        """)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("PUT /api/attributes/sapl.test/{name} returns 201")
    void publishAttributeWithEntity() throws Exception {
        mockMvc.perform(put("/api/attributes/sapl.test/sapl.test.role").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "value": "test_2",
                          "ttl": 60
                          }
                        """)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("GET count of /api/attributes?count=true returns total of stored attributes for current tenant")
    void publishMultipleAttributeWithEntity() throws Exception {
        MvcResult before      = mockMvc.perform(get("/api/attributes?count=true")).andExpect(status().isOk())
                .andReturn();
        long      countBefore = Long.parseLong(before.getResponse().getContentAsString());

        for (int i = 0; i < 100; i++) {
            mockMvc.perform(put("/api/attributes/sapl.test/test.attribute" + i).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            { "value": "test_%d",
                              "ttl": 60
                              }
                            """.formatted(i))).andExpect(status().isCreated());
        }
        MvcResult after      = mockMvc.perform(get("/api/attributes?count=true")).andExpect(status().isOk())
                .andReturn();
        long      countAfter = Long.parseLong(after.getResponse().getContentAsString());

        assertThat(countAfter - countBefore).isEqualTo(100);
    }

    @Test
    @DisplayName("GET set of /api/attributes?limit=20?offset=X returns 20 attributes in a given order")
    void requestMultipleAttributesWithLimitAndOffset() throws Exception {
        List<String> pushedNames = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            String name = "limit.offset" + i;
            pushedNames.add(name);
            mockMvc.perform(put("/api/attributes/sapl.test/" + name).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("""
                            { "value": "limitOffset_%d",
                              "ttl": 60
                              }
                            """.formatted(i))).andExpect(status().isCreated());
        }

        List<String> expectedOrder  = new ArrayList<>(pushedNames);
        List<String> collectedNames = new ArrayList<>();
        Collections.sort(expectedOrder); // Sort lexixal like in the backends

        for (int offset = 0; offset < 100; offset += 20) {
            List<String> expectedPage = new ArrayList<>(expectedOrder.subList(offset, offset + 20));

            MvcResult firstRequest  = mockMvc.perform(get("/api/attributes?limit=20&offset=" + offset))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[*].name").value(expectedPage)).andReturn();
            MvcResult secondRequest = mockMvc.perform(get("/api/attributes?limit=20&offset=" + offset))
                    .andExpect(status().isOk()).andReturn();

            // Repeated LIMIT/OFFSET request has to return the same hash twice
            int firstHash  = firstRequest.getResponse().getContentAsString().hashCode();
            int secondHash = secondRequest.getResponse().getContentAsString().hashCode();
            assertThat(firstHash).as("offset %d: gleicher Request, gleicher Hash", offset).isEqualTo(secondHash);

            collectedNames.addAll(expectedPage);
        }

        assertThat(collectedNames).containsExactlyElementsOf(pushedNames.stream().sorted().toList()).hasSize(100);
    }

    @Test
    @DisplayName("PUT /api/attributes/sapl.test/{name} returns error for unqualified name")
    void publishAttributeWithInvalidAttributeName() throws Exception {
        MvcResult result = mockMvc.perform(
                put("/api/attributes/sapl.test/sapl").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        { "value": "test_3",
                          "ttl": 60
                         }
                        """)).andExpect(status().isBadRequest()).andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("fully qualified name");
    }

    @Test
    @DisplayName("GET /api/attributes/sapl.test/{name} returns test_4 value")
    void getGlobalAttribute() throws Exception {
        mockMvc.perform(put("/api/attributes/sapl.test.deletion").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "value": "test_4",
                          "ttl": 60
                          }
                        """)).andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/attributes/sapl.test.deletion")).andExpect(status().isOk())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("\"test_4\"");
    }

    @Test
    @DisplayName("DELETE /api/attributes/sapl.test/{name} returns 201")
    void publishAndDeleteAttribute() throws Exception {
        mockMvc.perform(put("/api/attributes/sapl.test/sapl.test.publishAndDelete").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "value": "test_5",
                          "ttl": 60
                          }
                        """)).andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/attributes/sapl.test/sapl.test.publishAndDelete"))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("\"test_5\"");

        mockMvc.perform(delete("/api/attributes/sapl.test/sapl.test.publishAndDelete").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("TTL expires for /api/attributes/sapl.test/{name} and shows no content")
    void ttlExpires() throws Exception {
        mockMvc.perform(put("/api/attributes/sapl.test/sapl.test.ttlExpired").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("""
                        { "value": "test_6",
                          "ttl": 1
                          }
                        """)).andExpect(status().isCreated());

        Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> mockMvc
                .perform(get("/api/attributes/sapl.test/sapl.test.ttlExpired")).andExpect(status().isNotFound()));
    }

    @Test
    @DisplayName("PUT /api/attributes/{name} returns 201 on create, 200 on update")
    void publishTwiceReturnsCreatedThenOk() throws Exception {
        mockMvc.perform(
                put("/api/attributes/sapl.test.upsert").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        { "value": "test_7",
                          "ttl": 60
                         }
                        """)).andExpect(status().isCreated());

        mockMvc.perform(
                put("/api/attributes/sapl.test.upsert").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        { "value": "test_7_updated",
                          "ttl": 60
                         }
                        """)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/attributes?count=true returns the number of published attributes")
    void countReflectsPublishedAttributes() throws Exception {
        mockMvc.perform(
                put("/api/attributes/sapl.test.count").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("""
                        { "value": "test_8",
                          "ttl": 60
                         }
                        """)).andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/attributes?count=true")).andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).isEqualTo("1");
    }
}
