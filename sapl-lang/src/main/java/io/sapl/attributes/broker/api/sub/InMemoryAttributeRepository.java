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
package io.sapl.attributes.broker.api.sub;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

import io.sapl.api.interpreter.Val;
import io.sapl.attributes.broker.impl.AttributeStream;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class InMemoryAttributeRepository implements AttributeRepository {
    private static final Duration INFINITE          = Duration.ofSeconds(Long.MAX_VALUE, 999999999L);
    private static final Duration CLEANUP_INTERVALL = Duration.ofMillis(100L);

    private record Attribute(String fullyQualifiedName, Val value, Duration ttl, TimeOutStrategy timeOutStrategy) {}

    private record TimeOut(String fullyQualifiedName, Instant timeOut, TimeOutStrategy timeOutStrategy)
            implements Comparable<TimeOut> {

        @Override
        public int compareTo(TimeOut o) {
            return timeOut.compareTo(o.timeOut);
        }
    }

    private final Clock      clock;
    private final Disposable timoutTask;
    private final Object     lock = new Object();

    private Map<String, Attribute>       repository    = new ConcurrentHashMap<>();
    private Map<String, AttributeStream> subscriptions = new ConcurrentHashMap<>();
    private PriorityQueue<TimeOut>       timeOutQueue  = new PriorityQueue<>();

    public InMemoryAttributeRepository(Clock clock) {
        log.debug("Initializing InMemoryAttributeRepository.");
        this.clock      = clock;
        this.timoutTask = Flux.interval(CLEANUP_INTERVALL).doOnNext(i -> repository.compute("", (key, current) -> {
                            // acquire a lock on the repository before doing the time-out checks, because
                            // the
                            // checkTimeOut is not atomic on the queue.
                            this.checkTimeOut();
                            return current;
                        })).subscribe();
    }

    private void checkTimeOut() {
        final var now = clock.instant();
        synchronized (lock) {
            // This operation is the reason for not using a concurrent queue.
            // It cannot implement an atomic operation like this and may create race
            // conditions.
            // Therefore this operation has to be manually locked, and the manual locking on
            // the internal queue makes the use of a concurrent queue redundant.
            var head = timeOutQueue.element();
            while (null != head) {
                if (head.timeOut.isBefore(now)) {
                    if (head.timeOutStrategy == TimeOutStrategy.BECOME_UNDEFINED) {
                        publish(head.fullyQualifiedName, Val.UNDEFINED, INFINITE, TimeOutStrategy.REMOVE);
                    } else { // TimeOutStrategy.REMOVE
                        remove(head.fullyQualifiedName);
                    }
                } else {
                    timeOutQueue.add(head);
                    break;
                }
                head = timeOutQueue.element();
            }
        }
    }

    public void shutdown() {
        log.debug("Shutdown of InMemoryAttributeRepository.");
        timoutTask.dispose();
    }

    @Override
    public Mono<Void> publishAttribute(String fullyQualifiedName, Val value, Duration ttl,
            TimeOutStrategy timeOutStrategy) {
        return Mono.fromRunnable(() -> publish(fullyQualifiedName, value, ttl, timeOutStrategy));
    }

    private void publish(String fullyQualifiedName, Val value, Duration ttl, TimeOutStrategy timeOutStrategy) {
        repository.compute(fullyQualifiedName, (name, currentValue) -> {
            final var newAttribute = new Attribute(fullyQualifiedName, value, ttl, timeOutStrategy);
            synchronized (lock) {
                timeOutQueue.removeIf(t -> t.fullyQualifiedName.equals(fullyQualifiedName));
                if (!ttl.equals(INFINITE)) {
                    timeOutQueue.add(new TimeOut(fullyQualifiedName, clock.instant().plus(ttl), timeOutStrategy));
                }
            }
            final var subscription = subscriptions.get(fullyQualifiedName);
            if (null != subscription) {
                subscription.publish(value.withTrace(getClass()));
            }
            return newAttribute;
        });
    }

    @Override
    public Mono<Void> publishAttribute(String fullyQualifiedName, Val value, Duration ttl) {
        return publishAttribute(fullyQualifiedName, value, ttl, TimeOutStrategy.REMOVE);
    }

    @Override
    public Mono<Void> publishAttribute(String fullyQualifiedName, Val value) {
        return publishAttribute(fullyQualifiedName, value, INFINITE, TimeOutStrategy.REMOVE);
    }

    @Override
    public Mono<Void> removeAttribute(String fullyQualifiedName) {
        return Mono.fromRunnable(() -> remove(fullyQualifiedName));
    }

    private void remove(String fullyQualifiedName) {
        final var oldAttribute = repository.remove(fullyQualifiedName);
        if (null != oldAttribute) {
            synchronized (lock) {
                timeOutQueue.removeIf(t -> t.fullyQualifiedName.equals(fullyQualifiedName));
            }
            final var activeSubscription = subscriptions.get(oldAttribute.fullyQualifiedName());
            activeSubscription.publish(Val.error(String.format(
                    "For attribute '%s', no attribute finder/PIP is registered, and no value is published in the attribute repository.",
                    fullyQualifiedName)).withTrace(getClass()));
        }
    }

    void unsubscribeFromAttribute(String fullyQualifiedName) {
        subscriptions.remove(fullyQualifiedName);
    }

    @Override
    public Flux<Val> subscribeToAttribute() {
        return Flux.empty();
    }

}
