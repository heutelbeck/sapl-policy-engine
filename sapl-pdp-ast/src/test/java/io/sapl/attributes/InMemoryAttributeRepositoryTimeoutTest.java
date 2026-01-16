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
package io.sapl.attributes;

import io.sapl.api.attributes.AttributeKey;
import io.sapl.api.attributes.AttributeRepository;
import io.sapl.api.attributes.AttributeRepository.TimeOutStrategy;
import io.sapl.api.attributes.PersistedAttribute;
import io.sapl.api.model.Value;
import lombok.val;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Tests timeout management in InMemoryAttributeRepository.
 * <p>
 * These tests use scenarios to verify attributes being published
 * with various TTL settings and observing
 * them timing out according to their configured strategies. The cosmic horror
 * theme provides engaging test data while
 * testing real authorization and access control scenarios:
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
        val key = new AttributeKey(Value.of("investigator-carter"), "protection.elderSign", List.of());

        repository
                .publishAttribute(Value.of("investigator-carter"), "protection.elderSign",
                        Value.of("Ward active against Yog-Sothoth"), Duration.ofSeconds(2), TimeOutStrategy.REMOVE)
                .block();

        val initialValue = storage.get(key).block();
        assertThat(initialValue).isNotNull();
        assertThat(initialValue.value()).isEqualTo(Value.of("Ward active against Yog-Sothoth"));

        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage.get(key).block()).isNull());
    }

    @Test
    void whenAttributeTimesOutWithBecomeUndefinedStrategy_thenBecomesUndefinedPermanently() {
        val key = new AttributeKey(Value.of("investigator-wilmarth"), "sanity.current", List.of());

        repository.publishAttribute(Value.of("investigator-wilmarth"), "sanity.current", Value.of(65),
                Duration.ofSeconds(2), TimeOutStrategy.BECOME_UNDEFINED).block();

        val initialValue = storage.get(key).block();
        assertThat(initialValue).isNotNull();
        assertThat(initialValue.value()).isEqualTo(Value.of(65));

        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100)).untilAsserted(() -> {
            val value = storage.get(key).block();
            assertThat(value).isNotNull();
            assertThat(value.value()).isEqualTo(Value.UNDEFINED);
        });

        val finalValue = storage.get(key).block();
        assertThat(finalValue).isNotNull();
        assertThat(finalValue.ttl()).isEqualTo(AttributeRepository.INFINITE);
    }

    @Test
    void whenMultipleAttributesPublishedWithDifferentTTLs_thenEachTimesOutIndependently() {
        repository.publishAttribute(Value.of("gate-location-innsmouth"), "seal.active",
                Value.of("Sealed by novice cultist - weak binding"), Duration.ofSeconds(1), TimeOutStrategy.REMOVE)
                .block();

        repository.publishAttribute(Value.of("gate-location-arkham"), "seal.active",
                Value.of("Sealed by Miskatonic professor - moderate binding"), Duration.ofSeconds(2),
                TimeOutStrategy.REMOVE).block();

        repository.publishAttribute(Value.of("gate-location-antarctica"), "seal.active",
                Value.of("Sealed by Elder Thing inscription - strong binding"), Duration.ofSeconds(3),
                TimeOutStrategy.REMOVE).block();

        await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage
                        .get(new AttributeKey(Value.of("gate-location-innsmouth"), "seal.active", List.of())).block())
                        .isNull());

        await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage
                        .get(new AttributeKey(Value.of("gate-location-arkham"), "seal.active", List.of())).block())
                        .isNull());

        await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage
                        .get(new AttributeKey(Value.of("gate-location-antarctica"), "seal.active", List.of())).block())
                        .isNull());
    }

    @Test
    void whenAttributeUpdatedBeforeTimeout_thenOldTimeoutCancelledAndNewTimeoutScheduled() {
        val key = new AttributeKey(Value.of("librarian-armitage"), "access.necronomicon", List.of());

        repository.publishAttribute(Value.of("librarian-armitage"), "access.necronomicon",
                Value.of("Chapter IV: Of Evill Sorceries - PERMITTED"), Duration.ofSeconds(2), TimeOutStrategy.REMOVE)
                .block();

        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofMillis(1100))
                .until(() -> storage.get(key).block() != null);

        repository.publishAttribute(Value.of("librarian-armitage"), "access.necronomicon",
                Value.of("Chapter VII: The Dunwich Horror Counterspell - PERMITTED"), Duration.ofSeconds(3),
                TimeOutStrategy.REMOVE).block();

        val updatedValue = storage.get(key).block();
        assertThat(updatedValue).isNotNull();
        assertThat(updatedValue.value())
                .isEqualTo(Value.of("Chapter VII: The Dunwich Horror Counterspell - PERMITTED"));

        await().pollDelay(Duration.ofMillis(1500)).atMost(Duration.ofMillis(1600)).until(() -> {
            val stillValueid = storage.get(key).block();
            return stillValueid != null && stillValueid.value()
                    .equals(Value.of("Chapter VII: The Dunwich Horror Counterspell - PERMITTED"));
        });

        await().atMost(Duration.ofSeconds(2)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(storage.get(key).block()).isNull());
    }

    @Test
    void whenAttributeManuallyRemoved_thenScheduledTimeoutCancelled() {
        val key = new AttributeKey(Value.of("ritual-site-miskatonic"), "summoning.active", List.of());

        repository
                .publishAttribute(Value.of("ritual-site-miskatonic"), "summoning.active",
                        Value.of("Hound of Tindalos - MATERIALIZING"), Duration.ofSeconds(10), TimeOutStrategy.REMOVE)
                .block();

        assertThat(storage.get(key).block()).isNotNull();

        repository.removeAttribute(Value.of("ritual-site-miskatonic"), "summoning.active").block();

        assertThat(storage.get(key).block()).isNull();

        await().pollDelay(Duration.ofSeconds(2)).untilAsserted(() -> assertThat(storage.get(key).block()).isNull());
    }

    @Test
    void whenRepositoryInitializedWithExpiredAttributes_thenRemovesStaleAndRecoversActive() {
        val heapStorage = new HeapAttributeStorage();
        val clock       = Clock.systemUTC();

        val patientMarsh     = new AttributeKey(Value.of("patient-marsh-obed"), "transformation.deepOne", List.of());
        val patientGilman    = new AttributeKey(Value.of("patient-gilman-walter"), "corruption.dreamlands", List.of());
        val patientOldRecord = new AttributeKey(Value.of("patient-whateley-wilbur"), "entity.status", List.of());

        heapStorage.put(patientMarsh, new PersistedAttribute(
                Value.of("Stage 2: Webbed fingers developing, gills forming - ACTIVE METAMORPHOSIS"), clock.instant(),
                Duration.ofHours(1), TimeOutStrategy.REMOVE, clock.instant().plus(Duration.ofHours(1)))).block();

        heapStorage.put(patientGilman, new PersistedAttribute(
                Value.of("Exposure to Nyarlathotep in dreams - 2 seconds until complete possession"), clock.instant(),
                Duration.ofSeconds(2), TimeOutStrategy.REMOVE, clock.instant().plus(Duration.ofSeconds(2)))).block();

        heapStorage.put(patientOldRecord,
                new PersistedAttribute(Value.of("Patient deceased after Dunwich incident - ARCHIVED"),
                        clock.instant().minus(Duration.ofHours(1)), Duration.ofMinutes(30), TimeOutStrategy.REMOVE,
                        clock.instant().minus(Duration.ofMinutes(30))))
                .block();

        val recoveredRepo = new InMemoryAttributeRepository(clock, heapStorage);
        assertThat(recoveredRepo).isNotNull();
        assertThat(heapStorage.get(patientOldRecord).block()).isNull();
        assertThat(heapStorage.get(patientMarsh).block()).isNotNull();
        assertThat(heapStorage.get(patientGilman).block()).isNotNull();

        await().atMost(Duration.ofSeconds(3)).pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> assertThat(heapStorage.get(patientGilman).block()).isNull());

        assertThat(heapStorage.get(patientMarsh).block()).isNotNull();
    }

}
