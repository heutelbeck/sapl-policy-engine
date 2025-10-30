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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.api.AttributeKey;
import io.sapl.attributes.broker.api.AttributeRepository;
import io.sapl.attributes.broker.api.AttributeRepository.TimeOutStrategy;
import io.sapl.attributes.broker.api.PersistedAttribute;
import lombok.val;

/**
 * Tests timeout management in InMemoryAttributeRepository.
 * <p>
 * These tests use Lovecraftian scenarios to verify attributes being published
 * with various TTL settings and observing them timing out according to their
 * configured strategies. The cosmic horror theme provides engaging test data
 * while testing real authorization and access control scenarios:
 * <ul>
 * <li>Elder Sign protections that expire over time</li>
 * <li>Investigator sanity degrading to undefined states</li>
 * <li>Sealed interdimensional gates reopening</li>
 * <li>Forbidden tome access permissions with time limits</li>
 * <li>Summoning rituals that can be interrupted</li>
 * <li>Patient transformation tracking in sanitarium records</li>
 * </ul>
 */
class InMemoryAttributeRepositoryTimeoutTest {

    private InMemoryAttributeRepository repository;
    private HeapAttributeStorage        storage;

    @BeforeEach
    void setUp() {
        val clock = Clock.systemUTC();
        storage    = new HeapAttributeStorage();
        repository = new InMemoryAttributeRepository(clock, storage);
    }

    @Test
    void whenAttributePublishedWithTTL_thenRemovesAfterTimeout() {
        val key = new AttributeKey(Val.of("investigator-carter"), "protection.elderSign", List.of());

        repository
                .publishAttribute(Val.of("investigator-carter"), "protection.elderSign",
                        Val.of("Ward active against Yog-Sothoth"), Duration.ofSeconds(2), TimeOutStrategy.REMOVE)
                .block();

        val initialValue = storage.get(key).block();
        assertThat(initialValue).isNotNull();
        assertThat(initialValue.value()).isEqualTo(Val.of("Ward active against Yog-Sothoth"));

        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage.get(key).block()).isNull());
    }

    @Test
    void whenAttributeTimesOutWithBecomeUndefinedStrategy_thenBecomesUndefinedPermanently() {
        val key = new AttributeKey(Val.of("investigator-wilmarth"), "sanity.current", List.of());

        repository.publishAttribute(Val.of("investigator-wilmarth"), "sanity.current", Val.of(65),
                Duration.ofSeconds(2), TimeOutStrategy.BECOME_UNDEFINED).block();

        val initialValue = storage.get(key).block();
        assertThat(initialValue).isNotNull();
        assertThat(initialValue.value()).isEqualTo(Val.of(65));

        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            val value = storage.get(key).block();
            assertThat(value).isNotNull();
            assertThat(value.value()).isEqualTo(Val.UNDEFINED);
        });

        val finalValue = storage.get(key).block();
        assertThat(finalValue).isNotNull();
        assertThat(finalValue.ttl()).isEqualTo(AttributeRepository.INFINITE);
    }

    @Test
    void whenMultipleAttributesPublishedWithDifferentTTLs_thenEachTimesOutIndependently() {
        repository.publishAttribute(Val.of("gate-location-innsmouth"), "seal.active",
                Val.of("Sealed by novice cultist - weak binding"), Duration.ofSeconds(1), TimeOutStrategy.REMOVE)
                .block();

        repository.publishAttribute(Val.of("gate-location-arkham"), "seal.active",
                Val.of("Sealed by Miskatonic professor - moderate binding"), Duration.ofSeconds(2),
                TimeOutStrategy.REMOVE).block();

        repository.publishAttribute(Val.of("gate-location-antarctica"), "seal.active",
                Val.of("Sealed by Elder Thing inscription - strong binding"), Duration.ofSeconds(3),
                TimeOutStrategy.REMOVE).block();

        await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage
                        .get(new AttributeKey(Val.of("gate-location-innsmouth"), "seal.active", List.of())).block())
                        .isNull());

        await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(
                        storage.get(new AttributeKey(Val.of("gate-location-arkham"), "seal.active", List.of())).block())
                        .isNull());

        await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage
                        .get(new AttributeKey(Val.of("gate-location-antarctica"), "seal.active", List.of())).block())
                        .isNull());
    }

    @Test
    void whenAttributeUpdatedBeforeTimeout_thenOldTimeoutCancelledAndNewTimeoutScheduled() {
        val key = new AttributeKey(Val.of("librarian-armitage"), "access.necronomicon", List.of());

        repository.publishAttribute(Val.of("librarian-armitage"), "access.necronomicon",
                Val.of("Chapter IV: Of Evill Sorceries - PERMITTED"), Duration.ofSeconds(2), TimeOutStrategy.REMOVE)
                .block();

        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofMillis(1100))
                .until(() -> storage.get(key).block() != null);

        repository.publishAttribute(Val.of("librarian-armitage"), "access.necronomicon",
                Val.of("Chapter VII: The Dunwich Horror Counterspell - PERMITTED"), Duration.ofSeconds(3),
                TimeOutStrategy.REMOVE).block();

        val updatedValue = storage.get(key).block();
        assertThat(updatedValue).isNotNull();
        assertThat(updatedValue.value()).isEqualTo(Val.of("Chapter VII: The Dunwich Horror Counterspell - PERMITTED"));

        await().pollDelay(Duration.ofMillis(1500)).atMost(Duration.ofMillis(1600)).until(() -> {
            val stillValid = storage.get(key).block();
            return stillValid != null
                    && stillValid.value().equals(Val.of("Chapter VII: The Dunwich Horror Counterspell - PERMITTED"));
        });

        await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage.get(key).block()).isNull());
    }

    @Test
    void whenAttributeManuallyRemoved_thenScheduledTimeoutCancelled() {
        val key = new AttributeKey(Val.of("ritual-site-miskatonic"), "summoning.active", List.of());

        repository
                .publishAttribute(Val.of("ritual-site-miskatonic"), "summoning.active",
                        Val.of("Hound of Tindalos - MATERIALIZING"), Duration.ofSeconds(10), TimeOutStrategy.REMOVE)
                .block();

        assertThat(storage.get(key).block()).isNotNull();

        repository.removeAttribute(Val.of("ritual-site-miskatonic"), "summoning.active").block();

        assertThat(storage.get(key).block()).isNull();

        await().pollDelay(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(storage.get(key).block()).isNull());
    }

    @Test
    void whenRepositoryInitializedWithExpiredAttributes_thenRemovesStaleAndRecoversActive() {
        val heapStorage = new HeapAttributeStorage();
        val clock       = Clock.systemUTC();

        val patientMarsh     = new AttributeKey(Val.of("patient-marsh-obed"), "transformation.deepOne", List.of());
        val patientGilman    = new AttributeKey(Val.of("patient-gilman-walter"), "corruption.dreamlands", List.of());
        val patientOldRecord = new AttributeKey(Val.of("patient-whateley-wilbur"), "entity.status", List.of());

        heapStorage.put(patientMarsh, new PersistedAttribute(
                Val.of("Stage 2: Webbed fingers developing, gills forming - ACTIVE METAMORPHOSIS"), clock.instant(),
                Duration.ofHours(1), TimeOutStrategy.REMOVE, clock.instant().plus(Duration.ofHours(1)))).block();

        heapStorage.put(patientGilman, new PersistedAttribute(
                Val.of("Exposure to Nyarlathotep in dreams - 2 seconds until complete possession"), clock.instant(),
                Duration.ofSeconds(2), TimeOutStrategy.REMOVE, clock.instant().plus(Duration.ofSeconds(2)))).block();

        heapStorage.put(patientOldRecord,
                new PersistedAttribute(Val.of("Patient deceased after Dunwich incident - ARCHIVED"),
                        clock.instant().minus(Duration.ofHours(1)), Duration.ofMinutes(30), TimeOutStrategy.REMOVE,
                        clock.instant().minus(Duration.ofMinutes(30))))
                .block();

        val recoveredRepo = new InMemoryAttributeRepository(clock, heapStorage);

        assertThat(heapStorage.get(patientOldRecord).block()).isNull();
        assertThat(heapStorage.get(patientMarsh).block()).isNotNull();
        assertThat(heapStorage.get(patientGilman).block()).isNotNull();

        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(heapStorage.get(patientGilman).block()).isNull());

        assertThat(heapStorage.get(patientMarsh).block()).isNotNull();
    }

}
