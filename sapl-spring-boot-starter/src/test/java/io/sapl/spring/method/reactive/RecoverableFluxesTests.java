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
package io.sapl.spring.method.reactive;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RecoverableFluxesTests {

    // Simulates the PEP behavior: values and errors with onErrorContinue support
    private Flux<String> createPepLikeFlux(String... pattern) {
        return Flux.fromArray(pattern).flatMap(item -> {
            if ("DENY".equals(item)) {
                return Mono.error(new AccessDeniedException("Access denied"));
            } else if ("ERROR".equals(item)) {
                return Mono.error(new RuntimeException("Other error"));
            }
            return Mono.just(item);
        });
    }

    // ========== recover(Flux<T>) tests ==========

    @Test
    void recover_dropsAccessDeniedAndContinues() {
        var source = createPepLikeFlux("A", "DENY", "B", "DENY", "C");

        StepVerifier.create(RecoverableFluxes.recover(source)).expectNext("A", "B", "C").verifyComplete();
    }

    @Test
    void recover_propagatesOtherErrors() {
        var source = createPepLikeFlux("A", "ERROR", "B");

        StepVerifier.create(RecoverableFluxes.recover(source)).expectNext("A").expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void recover_completesNormallyWithNoErrors() {
        var source = createPepLikeFlux("A", "B", "C");

        StepVerifier.create(RecoverableFluxes.recover(source)).expectNext("A", "B", "C").verifyComplete();
    }

    @Test
    void recover_handlesEmptyFlux() {
        Flux<String> source = Flux.empty();

        StepVerifier.create(RecoverableFluxes.recover(source)).verifyComplete();
    }

    @Test
    void recover_handlesOnlyAccessDenied() {
        var source = createPepLikeFlux("DENY", "DENY");

        StepVerifier.create(RecoverableFluxes.recover(source)).verifyComplete();
    }

    // ========== recover(Flux<T>, Consumer<AccessDeniedException>) tests ==========

    @Test
    void recoverWithConsumer_invokesConsumerOnAccessDenied() {
        var          source           = createPepLikeFlux("A", "DENY", "B");
        List<String> capturedMessages = new ArrayList<>();

        StepVerifier.create(RecoverableFluxes.recover(source, error -> capturedMessages.add(error.getMessage())))
                .expectNext("A", "B").verifyComplete();

        assertThat(capturedMessages).containsExactly("Access denied");
    }

    @Test
    void recoverWithConsumer_invokesConsumerMultipleTimes() {
        var           source = createPepLikeFlux("A", "DENY", "B", "DENY", "C", "DENY");
        AtomicInteger count  = new AtomicInteger();

        StepVerifier.create(RecoverableFluxes.recover(source, error -> count.incrementAndGet()))
                .expectNext("A", "B", "C").verifyComplete();

        assertThat(count.get()).isEqualTo(3);
    }

    @Test
    void recoverWithConsumer_propagatesOtherErrors() {
        var           source = createPepLikeFlux("A", "DENY", "ERROR");
        AtomicInteger count  = new AtomicInteger();

        StepVerifier.create(RecoverableFluxes.recover(source, error -> count.incrementAndGet())).expectNext("A")
                .expectError(RuntimeException.class).verify();

        assertThat(count.get()).isEqualTo(1);
    }

    // ========== recoverWith(Flux<T>, Supplier<T>) tests ==========

    @Test
    void recoverWith_emitsReplacementOnAccessDenied() {
        var source = createPepLikeFlux("A", "DENY", "B");

        StepVerifier.create(RecoverableFluxes.recoverWith(source, () -> "REPLACED")).expectNext("A", "REPLACED", "B")
                .verifyComplete();
    }

    @Test
    void recoverWith_emitsMultipleReplacements() {
        var source = createPepLikeFlux("A", "DENY", "B", "DENY", "C");

        StepVerifier.create(RecoverableFluxes.recoverWith(source, () -> "X")).expectNext("A", "X", "B", "X", "C")
                .verifyComplete();
    }

    @Test
    void recoverWith_propagatesOtherErrors() {
        var source = createPepLikeFlux("A", "DENY", "ERROR");

        StepVerifier.create(RecoverableFluxes.recoverWith(source, () -> "REPLACED")).expectNext("A", "REPLACED")
                .expectError(RuntimeException.class).verify();
    }

    @Test
    void recoverWith_supplierCalledEachTime() {
        var           source  = createPepLikeFlux("DENY", "DENY", "DENY");
        AtomicInteger counter = new AtomicInteger();

        StepVerifier.create(RecoverableFluxes.recoverWith(source, () -> "R" + counter.incrementAndGet()))
                .expectNext("R1", "R2", "R3").verifyComplete();
    }

    // ========== recoverWith(Flux<T>, Consumer, Supplier) tests ==========

    @Test
    void recoverWithConsumerAndSupplier_bothInvoked() {
        var          source   = createPepLikeFlux("A", "DENY", "B");
        List<String> captured = new ArrayList<>();

        StepVerifier.create(
                RecoverableFluxes.recoverWith(source, error -> captured.add(error.getMessage()), () -> "REPLACED"))
                .expectNext("A", "REPLACED", "B").verifyComplete();

        assertThat(captured).containsExactly("Access denied");
    }

    @Test
    void recoverWithConsumerAndSupplier_multipleInvocations() {
        var           source        = createPepLikeFlux("DENY", "A", "DENY", "B", "DENY");
        AtomicInteger consumerCount = new AtomicInteger();
        AtomicInteger supplierCount = new AtomicInteger();

        StepVerifier.create(RecoverableFluxes.recoverWith(source, error -> consumerCount.incrementAndGet(), () -> {
            supplierCount.incrementAndGet();
            return "X";
        })).expectNext("X", "A", "X", "B", "X").verifyComplete();

        assertThat(consumerCount.get()).isEqualTo(3);
        assertThat(supplierCount.get()).isEqualTo(3);
    }

    @Test
    void recoverWithConsumerAndSupplier_propagatesOtherErrors() {
        var           source = createPepLikeFlux("A", "DENY", "ERROR");
        AtomicInteger count  = new AtomicInteger();

        StepVerifier.create(RecoverableFluxes.recoverWith(source, error -> count.incrementAndGet(), () -> "X"))
                .expectNext("A", "X").expectError(RuntimeException.class).verify();

        assertThat(count.get()).isEqualTo(1);
    }

    // ========== Ordering tests ==========

    @Test
    void recoverWith_preservesOrderingWithAccessDenied() {
        var source = createPepLikeFlux("1", "DENY", "2", "DENY", "3");

        StepVerifier.create(RecoverableFluxes.recoverWith(source, () -> "X")).expectNext("1", "X", "2", "X", "3")
                .verifyComplete();
    }

    // ========== Edge cases ==========

    @Test
    void recoverWith_worksWithDifferentTypes() {
        Flux<Integer> source = Flux.just(1, 2, 3).flatMap(i -> {
            if (i == 2) {
                return Mono.error(new AccessDeniedException("No 2"));
            }
            return Mono.just(i);
        });

        StepVerifier.create(RecoverableFluxes.recoverWith(source, () -> -1)).expectNext(1, -1, 3).verifyComplete();
    }

}
