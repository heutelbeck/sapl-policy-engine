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
package io.sapl.attributes.broker.impl;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeKey;
import io.sapl.attributes.broker.api.AttributeRepository;
import io.sapl.attributes.broker.api.AttributeRepository.TimeOutStrategy;
import io.sapl.attributes.broker.api.PersistedAttribute;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates and tests timeout management in InMemoryAttributeRepository.
 * <p>
 * These tests use Lovecraftian scenarios to demonstrate attributes being
 * published
 * with various TTL settings and observing them timing out according to their
 * configured strategies. The cosmic horror theme provides engaging, thematic
 * examples
 * while testing real authorization and access control scenarios:
 * <ul>
 * <li>Elder Sign protections that expire over time</li>
 * <li>Investigator sanity degrading to undefined states</li>
 * <li>Sealed interdimensional gates reopening</li>
 * <li>Forbidden tome access permissions with time limits</li>
 * <li>Summoning rituals that can be interrupted</li>
 * <li>Patient transformation tracking in sanitarium records</li>
 * </ul>
 * <p>
 * All scenarios make sense within Lovecraft lore and demonstrate practical
 * timeout management patterns for authorization systems.
 */
@Slf4j
class InMemoryAttributeRepositoryTimeoutTest {

    private InMemoryAttributeRepository repository;
    private HeapAttributeStorage        storage;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.systemUTC();
        storage    = new HeapAttributeStorage();
        repository = new InMemoryAttributeRepository(clock, storage);
        log.info("=== Test Setup Complete ===");
    }

    @Test
    void demonstratesBasicPublishAndTimeout() {
        log.info("=== DEMO: Elder Sign Protection Expires ===");

        val key = new AttributeKey(Val.of("investigator-carter"), "protection.elderSign", List.of());

        // Publish attribute with 2-second TTL
        log.info("Carving Elder Sign on door... protection lasts 2 seconds...");
        repository
                .publishAttribute(Val.of("investigator-carter"), "protection.elderSign",
                        Val.of("Ward active against Yog-Sothoth"), Duration.ofSeconds(2), TimeOutStrategy.REMOVE)
                .block();

        // Verify attribute exists
        val initialValue = storage.get(key).block();
        assertThat(initialValue).isNotNull();
        assertThat(initialValue.value()).isEqualTo(Val.of("Ward active against Yog-Sothoth"));
        log.info("-> Elder Sign carved successfully: {}", initialValue.value());

        // Wait for timeout
        log.info("The chalk fades... the sign weakens...");
        Awaitility.await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            val value = storage.get(key).block();
            assertThat(value).isNull();
        });

        log.info("-> Protection expired. The door is no longer warded.");
        log.info("=== DEMO Complete ===\n");
    }

    @Test
    void demonstratesBecomeUndefinedStrategy() {
        log.info("=== DEMO: Investigator Sanity Degradation ===");

        val key = new AttributeKey(Val.of("investigator-wilmarth"), "sanity.current", List.of());

        // Publish with BECOME_UNDEFINED strategy
        log.info("Professor Wilmarth's sanity score recorded after witnessing a Shoggoth (2-second trauma)...");
        repository.publishAttribute(Val.of("investigator-wilmarth"), "sanity.current", Val.of(65), // Sanity reduced
                // from initial 80
                Duration.ofSeconds(2), TimeOutStrategy.BECOME_UNDEFINED).block();

        val initialValue = storage.get(key).block();
        Assertions.assertNotNull(initialValue);
        assertThat(initialValue.value()).isEqualTo(Val.of(65));
        log.info("-> Sanity recorded: {}/100", initialValue.value());

        // Wait for timeout
        log.info("The horror consumes rational thought... sanity becomes unknowable...");
        Awaitility.await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            val value = storage.get(key).block();
            assertThat(value).isNotNull();
            assertThat(value.value()).isEqualTo(Val.UNDEFINED);
        });

        val finalValue = storage.get(key).block();
        Assertions.assertNotNull(finalValue);
        log.info("-> Sanity state after cosmic exposure: {} (mind shattered beyond measurement)", finalValue.value());
        assertThat(finalValue.ttl()).isEqualTo(AttributeRepository.INFINITE);
        log.info("-> Condition is permanent (TTL: INFINITE)");
    }

    @Test
    void demonstratesMultipleAttributesWithDifferentTTLs() {
        log.info("=== DEMO: Multiple Sealed Gates with Staggered Expirations ===");

        // Publish three sealed gates with different seal durations
        log.info("Sealing three interdimensional gates with varying ritual strengths...");

        repository.publishAttribute(Val.of("gate-location-innsmouth"), "seal.active",
                        Val.of("Sealed by novice cultist - weak binding"), Duration.ofSeconds(1), TimeOutStrategy.REMOVE)
                .block();

        repository.publishAttribute(Val.of("gate-location-arkham"), "seal.active",
                Val.of("Sealed by Miskatonic professor - moderate binding"), Duration.ofSeconds(2),
                TimeOutStrategy.REMOVE).block();

        repository.publishAttribute(Val.of("gate-location-antarctica"), "seal.active",
                Val.of("Sealed by Elder Thing inscription - strong binding"), Duration.ofSeconds(3),
                TimeOutStrategy.REMOVE).block();

        log.info("-> All three gates sealed");

        // Wait for first seal to break
        log.info("The weakest seal crumbles first... (1 second)");
        Awaitility.await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage
                        .get(new AttributeKey(Val.of("gate-location-innsmouth"), "seal.active", List.of())).block())
                        .isNull());
        log.info("-> Innsmouth gate REOPENED - novice seal failed");

        // Wait for second seal to break
        log.info("The professor's seal weakens...");
        Awaitility.await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(
                        storage.get(new AttributeKey(Val.of("gate-location-arkham"), "seal.active", List.of())).block())
                        .isNull());
        log.info("-> Arkham gate REOPENED - academic knowledge insufficient");

        // Wait for third seal to break
        log.info("Even the Elder inscription fades with time...");
        Awaitility.await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage
                        .get(new AttributeKey(Val.of("gate-location-antarctica"), "seal.active", List.of())).block())
                        .isNull());
        log.info("-> Antarctica gate REOPENED - all seals have failed");
        log.info("-> The barriers between dimensions have fallen. Ph'nglui mglw'nafh Cthulhu R'lyeh wgah'nagl fhtagn.");
        log.info("=== DEMO Complete ===\n");
    }

    @Test
    void demonstratesAttributeUpdateCancelsOldTimeout() {
        log.info("=== DEMO: Renewing Forbidden Tome Access ===");

        val key = new AttributeKey(Val.of("librarian-armitage"), "access.necronomicon", List.of());

        // Publish with 2-second TTL
        log.info("Granting Dr. Armitage 2 seconds to consult the Necronomicon (Latin edition)...");
        repository.publishAttribute(Val.of("librarian-armitage"), "access.necronomicon",
                        Val.of("Chapter IV: Of Evill Sorceries - PERMITTED"), Duration.ofSeconds(2), TimeOutStrategy.REMOVE)
                .block();

        // Wait 1 second, then update with new 3-second TTL
        log.info("Reading in progress...");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("Dr. Armitage requests extension - critical passage found! Granting 3 more seconds...");
        repository.publishAttribute(Val.of("librarian-armitage"), "access.necronomicon",
                Val.of("Chapter VII: The Dunwich Horror Counterspell - PERMITTED"), Duration.ofSeconds(3),
                TimeOutStrategy.REMOVE).block();

        val updatedValue = storage.get(key).block();
        Assertions.assertNotNull(updatedValue);
        assertThat(updatedValue.value()).isEqualTo(Val.of("Chapter VII: The Dunwich Horror Counterspell - PERMITTED"));
        log.info("-> Access updated: {}", updatedValue.value());

        // After 2 seconds total, access should still be valid (original timeout was
        // cancelled)
        log.info("Waiting 1.5 more seconds (2.5s total from start)...");
        try {
            Thread.sleep(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        val stillValid = storage.get(key).block();
        assertThat(stillValid).isNotNull();
        assertThat(stillValid.value()).isEqualTo(Val.of("Chapter VII: The Dunwich Horror Counterspell - PERMITTED"));
        log.info("-> Access still valid after 2.5s (original 2s timeout was cancelled)");

        // Wait for new timeout
        log.info("Time limit approaching... the book must be resealed...");
        Awaitility.await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage.get(key).block()).isNull());

        log.info("-> Access revoked. The Necronomicon returns to its locked vault.");
        log.info("=== DEMO Complete ===\n");
    }

    @Test
    void demonstratesManualRemovalCancelsTimeout() {
        log.info("=== DEMO: Banishing Summoned Entity Before Full Manifestation ===");

        val key = new AttributeKey(Val.of("ritual-site-miskatonic"), "summoning.active", List.of());

        log.info("Cultists begin ritual to summon a Hound of Tindalos (10-second manifestation)...");
        repository
                .publishAttribute(Val.of("ritual-site-miskatonic"), "summoning.active",
                        Val.of("Hound of Tindalos - MATERIALIZING"), Duration.ofSeconds(10), TimeOutStrategy.REMOVE)
                .block();

        assertThat(storage.get(key).block()).isNotNull();
        log.info("-> Summoning in progress - angles in spacetime beginning to form...");

        log.info("Investigators burst in! Disrupting the ritual with Elder Signs!");
        repository.removeAttribute(Val.of("ritual-site-miskatonic"), "summoning.active").block();

        assertThat(storage.get(key).block()).isNull();
        log.info("-> Summoning interrupted - the Hound retreats to the angles between time");

        // Wait to ensure timeout doesn't fire
        log.info("Waiting 2 seconds to verify the creature does not manifest...");
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertThat(storage.get(key).block()).isNull();
        log.info("-> No manifestation occurred. The ritual was successfully disrupted.");
        log.info("-> The investigators saved Miskatonic from unspeakable horror.");
        log.info("=== DEMO Complete ===\n");
    }

    @Test
    void demonstratesInitializationRecovery() {
        log.info("=== DEMO: Recovery After Arkham Sanitarium Records System Restart ===");

        // Pre-populate storage before initialization
        val storage = new HeapAttributeStorage();
        val clock   = Clock.systemUTC();

        log.info("Pre-populating patient transformation records (system crashed mid-observation)...");
        val patientMarsh     = new AttributeKey(Val.of("patient-marsh-obed"), "transformation.deepOne", List.of());
        val patientGilman    = new AttributeKey(Val.of("patient-gilman-walter"), "corruption.dreamlands", List.of());
        val patientOldRecord = new AttributeKey(Val.of("patient-whateley-wilbur"), "entity.status", List.of());

        storage.put(patientMarsh, new PersistedAttribute(
                Val.of("Stage 2: Webbed fingers developing, gills forming - ACTIVE METAMORPHOSIS"), clock.instant(),
                Duration.ofHours(1), TimeOutStrategy.REMOVE, clock.instant().plus(Duration.ofHours(1)))).block();

        storage.put(patientGilman, new PersistedAttribute(
                Val.of("Exposure to Nyarlathotep in dreams - 2 seconds until complete possession"), clock.instant(),
                Duration.ofSeconds(2), TimeOutStrategy.REMOVE, clock.instant().plus(Duration.ofSeconds(2)))).block();

        storage.put(patientOldRecord,
                new PersistedAttribute(Val.of("Patient deceased after Dunwich incident - ARCHIVED"),
                        clock.instant().minus(Duration.ofHours(1)), // Record from before crash
                        Duration.ofMinutes(30), TimeOutStrategy.REMOVE, clock.instant().minus(Duration.ofMinutes(30)) // Already
                        // expired
                )).block();

        log.info("-> Storage pre-populated: 2 active cases, 1 stale record to be removed");

        // Initialize repository - should recover non-expired attributes and remove stale ones
        log.info("Restarting patient monitoring system (recovering active transformations)...");
        val recoveredRepo = new InMemoryAttributeRepository(clock, storage);

        // Verify expired attribute was removed from storage
        assertThat(storage.get(patientOldRecord).block()).isNull(); // Stale attribute removed during recovery
        assertThat(storage.get(patientMarsh).block()).isNotNull();
        assertThat(storage.get(patientGilman).block()).isNotNull();
        log.info("-> Recovered 2 active cases. Expired Whateley record removed from storage.");

        // Verify short-lived attribute times out
        log.info("Patient Gilman's possession progresses rapidly...");
        Awaitility.await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage.get(patientGilman).block()).isNull());

        log.info("-> Gilman case closed - patient lost to the Crawling Chaos");
        log.info("-> Marsh transformation continues under long-term observation (1 hour remaining)");
        assertThat(storage.get(patientMarsh).block()).isNotNull();
        log.info("=== DEMO Complete ===\n");
    }

}