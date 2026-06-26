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
package io.sapl.test;

import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeBroker;
import io.sapl.test.MockingFunctionBroker.ArgumentMatcher;
import io.sapl.test.MockingFunctionBroker.ArgumentMatchers;
import io.sapl.test.verification.AttributeInvocationRecord;
import io.sapl.test.verification.MockVerificationError;
import io.sapl.test.verification.Times;
import lombok.NonNull;
import lombok.val;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

/**
 * {@link AttributeBroker} mocking facility for tests. Mocks are
 * registered against attribute name + matchers + entity matcher.
 * Values are emitted via {@link #emit(String, Value)} keyed by
 * mockId. Invocations are recorded for after-the-fact verification.
 * <p>
 * PIP-aware gate semantic: mock registration acts as a PIP registration
 * (gate stays closed until a value lands via {@link #emit}, unless an
 * initial value was supplied at mock registration). Explicit
 * {@link #register(String)} and {@link #register(String, Value)} cover
 * non-mock attribute names. Keys with no mock, no delegate, and no
 * explicit registration auto-materialise as {@link Value#UNDEFINED} so
 * the gate opens deterministically rather than hanging silently.
 * <p>
 * Re-fire on dep growth: when a callback returns expanded dependencies,
 * any newly added auto-fills (UNDEFINED for unregistered, primed value
 * for registered-with-initial, mock current value for matched mocks)
 * cause the callback to fire once more so the consumer observes the
 * new mailbox state, provided the gate is open after the new bindings.
 * <p>
 * State mutations and reads are guarded by the broker's intrinsic lock.
 * Callbacks fire outside the lock so consumer-side close, evaluation,
 * or follow-on broker operations don't re-enter under the lock.
 */
public final class MockingAttributeBroker implements AttributeBroker {

    private static final String ERROR_ARGUMENTS_MUST_USE_ARGS     = "Arguments must be created via args().";
    private static final String ERROR_INITIAL_DEPS_EMPTY          = "initialDependencies must not be empty";
    private static final String ERROR_MOCK_ID_ALREADY_REGISTERED  = "Mock ID '%s' is already registered.";
    private static final String ERROR_MOCK_ID_BLANK               = "Mock ID must not be blank.";
    private static final String ERROR_NO_MOCK_FOR_ID              = "No mock registered with ID '%s'.";
    private static final String ERROR_RETURNED_DEPS_EMPTY         = "Subscription %s returned empty/null dependencies; close externally instead.";
    private static final String ERROR_SUBSCRIPTION_ID_BLANK       = "subscriptionId must not be blank";
    private static final String ERROR_SUBSCRIPTION_ID_IN_USE      = "subscriptionId already open: %s";
    private static final String ERROR_VERIFICATION_FAILED         = "Attribute verification failed for '%s'.%n";
    private static final String ERROR_VERIFICATION_NO_INVOCATIONS = "%nNo invocations of '%s' were recorded.";
    private static final String ERROR_VERIFICATION_RECORDED       = "%nRecorded invocations of '%s':";

    private final Map<String, AttributeMock>              mocksById            = new HashMap<>();
    private final Map<String, List<AttributeMock>>        mocksByName          = new HashMap<>();
    private final Map<String, Value>                      currentValueByMockId = new HashMap<>();
    private final Map<SubscriptionKey, AttributeSnapshot> mailbox              = new HashMap<>();
    private final Map<SubscriptionKey, String>            keyToMockId          = new HashMap<>();
    private final Map<SubscriptionKey, ForwardEntry>      forwards             = new HashMap<>();
    private final Map<String, SubscriptionImpl>           subs                 = new HashMap<>();
    private final Map<String, @Nullable Value>            registeredPips       = new HashMap<>();
    private final List<AttributeInvocationRecord>         invocations          = new CopyOnWriteArrayList<>();
    private final AtomicLong                              sequenceCounter      = new AtomicLong(0);
    private AttributeBroker                               delegate;

    public void setDelegate(AttributeBroker delegate) {
        this.delegate = delegate;
    }

    /**
     * Register a non-mock PIP for {@code attributeName}. Subsequent
     * subscriptions whose dep set contains keys with this name leave
     * those keys unbound (gate stays closed for that key) until a
     * publish-equivalent value arrives.
     */
    public synchronized void register(String attributeName) {
        registeredPips.put(attributeName, null);
    }

    /**
     * Register a non-mock PIP for {@code attributeName} with an
     * initial value. Subscriptions whose dep set contains keys with
     * this name see the initial value in the mailbox at bind time.
     */
    public synchronized void register(String attributeName, Value initialValue) {
        registeredPips.put(attributeName, initialValue);
    }

    public synchronized void mockEnvironmentAttribute(@NonNull String mockId, @NonNull String attributeName,
            @NonNull SaplTestFixture.Parameters arguments) {
        mockEnvironmentAttribute(mockId, attributeName, arguments, null);
    }

    public synchronized void mockEnvironmentAttribute(@NonNull String mockId, @NonNull String attributeName,
            @NonNull SaplTestFixture.Parameters arguments, Value initialValue) {
        validateMockId(mockId);
        if (!(arguments instanceof ArgumentMatchers(List<ArgumentMatcher> matchers))) {
            throw new IllegalArgumentException(ERROR_ARGUMENTS_MUST_USE_ARGS);
        }
        registerMock(attributeName, new AttributeMock(mockId, null, matchers, true), initialValue);
    }

    public synchronized void mockAttribute(@NonNull String mockId, @NonNull String attributeName,
            @NonNull ArgumentMatcher entityMatcher, @NonNull SaplTestFixture.Parameters arguments) {
        mockAttribute(mockId, attributeName, entityMatcher, arguments, null);
    }

    public synchronized void mockAttribute(@NonNull String mockId, @NonNull String attributeName,
            @NonNull ArgumentMatcher entityMatcher, @NonNull SaplTestFixture.Parameters arguments, Value initialValue) {
        validateMockId(mockId);
        if (!(arguments instanceof ArgumentMatchers(List<ArgumentMatcher> matchers))) {
            throw new IllegalArgumentException(ERROR_ARGUMENTS_MUST_USE_ARGS);
        }
        registerMock(attributeName, new AttributeMock(mockId, entityMatcher, matchers, false), initialValue);
    }

    public void emit(@NonNull String mockId, @NonNull Value value) {
        List<SubscriptionImpl> toFire;
        synchronized (this) {
            if (!mocksById.containsKey(mockId)) {
                throw new IllegalStateException(ERROR_NO_MOCK_FOR_ID.formatted(mockId));
            }
            currentValueByMockId.put(mockId, value);
            val now = Instant.now();
            for (val entry : keyToMockId.entrySet()) {
                if (entry.getValue().equals(mockId)) {
                    mailbox.put(entry.getKey(), new AttributeSnapshot(value, now));
                }
            }
            toFire = collectFireable();
        }
        for (val sub : toFire) {
            sub.fireCallback();
        }
    }

    @Override
    public AttributeBroker.Subscription open(String subscriptionId, Set<SubscriptionKey> initialDependencies,
            Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_BLANK);
        }
        if (initialDependencies == null || initialDependencies.isEmpty()) {
            throw new IllegalArgumentException(ERROR_INITIAL_DEPS_EMPTY);
        }
        SubscriptionImpl sub;
        boolean          fireImmediately;
        synchronized (this) {
            if (subs.containsKey(subscriptionId)) {
                throw new IllegalArgumentException(ERROR_SUBSCRIPTION_ID_IN_USE.formatted(subscriptionId));
            }
            sub = new SubscriptionImpl(subscriptionId, new HashSet<>(initialDependencies), onUpdate);
            // bindKeys may synchronously fire delegate callbacks that walk subs to find
            // fireable subscriptions. Defer adding this sub to the map so a sync-fire
            // during bindKeys cannot double-fire (once via delegate, once via the
            // explicit fire below).
            bindKeys(initialDependencies);
            subs.put(subscriptionId, sub);
            fireImmediately = sub.allDepsFulfilled();
            if (fireImmediately) {
                sub.gateOpen = true;
            }
        }
        if (fireImmediately) {
            sub.fireCallback();
        }
        return sub;
    }

    @Override
    public synchronized void close() {
        for (val sub : subs.values()) {
            sub.closed = true;
        }
        subs.clear();
        for (val fwd : forwards.values()) {
            fwd.delegateSub.close();
        }
        forwards.clear();
        mailbox.clear();
        keyToMockId.clear();
        registeredPips.clear();
    }

    public synchronized boolean hasMock(String mockId) {
        return mocksById.containsKey(mockId);
    }

    public synchronized boolean hasMockForAttribute(String attributeName) {
        val list = mocksByName.get(attributeName);
        return list != null && !list.isEmpty();
    }

    public synchronized void clearAllMocks() {
        mocksById.clear();
        mocksByName.clear();
        currentValueByMockId.clear();
        clearInvocations();
    }

    public void clearInvocations() {
        invocations.clear();
        sequenceCounter.set(0);
    }

    public List<AttributeInvocationRecord> getInvocations() {
        return List.copyOf(invocations);
    }

    public List<AttributeInvocationRecord> getInvocations(String attributeName) {
        return invocations.stream().filter(r -> r.attributeName().equals(attributeName)).toList();
    }

    public void verifyEnvironmentAttribute(@NonNull String attributeName, @NonNull SaplTestFixture.Parameters arguments,
            @NonNull Times times) {
        if (!(arguments instanceof ArgumentMatchers(List<ArgumentMatcher> matchers))) {
            throw new IllegalArgumentException(ERROR_ARGUMENTS_MUST_USE_ARGS);
        }
        val matched = countMatchingInvocations(attributeName, null, matchers, true);
        if (!times.verify(matched)) {
            throw new MockVerificationError(buildVerificationMessage(attributeName, times, matched));
        }
    }

    public void verifyAttribute(@NonNull String attributeName, @NonNull ArgumentMatcher entityMatcher,
            @NonNull SaplTestFixture.Parameters arguments, @NonNull Times times) {
        if (!(arguments instanceof ArgumentMatchers(List<ArgumentMatcher> matchers))) {
            throw new IllegalArgumentException(ERROR_ARGUMENTS_MUST_USE_ARGS);
        }
        val matched = countMatchingInvocations(attributeName, entityMatcher, matchers, false);
        if (!times.verify(matched)) {
            throw new MockVerificationError(buildVerificationMessage(attributeName, times, matched));
        }
    }

    public void verifyEnvironmentAttributeCalled(@NonNull String attributeName,
            @NonNull SaplTestFixture.Parameters arguments) {
        verifyEnvironmentAttribute(attributeName, arguments, Times.atLeast(1));
    }

    public void verifyAttributeCalled(@NonNull String attributeName, @NonNull ArgumentMatcher entityMatcher,
            @NonNull SaplTestFixture.Parameters arguments) {
        verifyAttribute(attributeName, entityMatcher, arguments, Times.atLeast(1));
    }

    private void validateMockId(String mockId) {
        if (mockId.isBlank()) {
            throw new IllegalArgumentException(ERROR_MOCK_ID_BLANK);
        }
        if (mocksById.containsKey(mockId)) {
            throw new IllegalArgumentException(ERROR_MOCK_ID_ALREADY_REGISTERED.formatted(mockId));
        }
    }

    private void registerMock(String attributeName, AttributeMock mock, Value initialValue) {
        mocksByName.computeIfAbsent(attributeName, k -> new ArrayList<>()).add(mock);
        mocksById.put(mock.mockId(), mock);
        if (initialValue != null) {
            currentValueByMockId.put(mock.mockId(), initialValue);
        }
    }

    private boolean bindKeys(Set<SubscriptionKey> keys) {
        val     now         = Instant.now();
        boolean mailboxGrew = false;
        for (val key : keys) {
            mailboxGrew |= bindKey(key, now);
        }
        return mailboxGrew;
    }

    private boolean bindKey(SubscriptionKey key, Instant now) {
        if (keyToMockId.containsKey(key)) {
            // Mock-bound: shared across consumers, no refcount needed (mocks live for the
            // broker lifetime).
            return false;
        }
        val existingForward = forwards.get(key);
        if (existingForward != null) {
            existingForward.refcount++;
            return false;
        }
        val match = findMostSpecificMatch(key.invocation());
        recordInvocation(key.invocation());
        if (match.isPresent()) {
            return seedFromMockMatch(key, match.get().mockId(), now);
        }
        if (registeredPips.containsKey(key.invocation().attributeName())) {
            return seedFromRegisteredPip(key, now);
        }
        if (delegate != null) {
            openDelegateForward(key);
            return false;
        }
        return seedAsUndefined(key, now);
    }

    private boolean seedFromMockMatch(SubscriptionKey key, String mockId, Instant now) {
        keyToMockId.put(key, mockId);
        val current = currentValueByMockId.get(mockId);
        if (current == null || mailbox.containsKey(key)) {
            return false;
        }
        mailbox.put(key, new AttributeSnapshot(current, now));
        return true;
    }

    private boolean seedFromRegisteredPip(SubscriptionKey key, Instant now) {
        val initial = registeredPips.get(key.invocation().attributeName());
        if (initial == null || mailbox.containsKey(key)) {
            return false;
        }
        mailbox.put(key, new AttributeSnapshot(initial, now));
        return true;
    }

    private boolean seedAsUndefined(SubscriptionKey key, Instant now) {
        if (mailbox.containsKey(key)) {
            return false;
        }
        mailbox.put(key, new AttributeSnapshot(Value.UNDEFINED, now));
        return true;
    }

    private void releaseKeys(Set<SubscriptionKey> keys) {
        for (val key : keys) {
            val fwd = forwards.get(key);
            if (fwd == null) {
                continue;
            }
            fwd.refcount--;
            if (fwd.refcount == 0) {
                fwd.delegateSub.close();
                forwards.remove(key);
                mailbox.remove(key);
            }
        }
    }

    private void openDelegateForward(SubscriptionKey key) {
        val singleKeyDeps = Set.of(key);
        val forwardId     = "mock-broker-forward-" + UUID.randomUUID();
        val delegateSub   = delegate.open(forwardId, singleKeyDeps, snap -> {
                              onDelegateForwardUpdate(key, snap);
                              return singleKeyDeps;
                          });
        forwards.put(key, new ForwardEntry(delegateSub, 1));
    }

    private void onDelegateForwardUpdate(SubscriptionKey key, Map<SubscriptionKey, AttributeSnapshot> snap) {
        List<SubscriptionImpl> toFire;
        synchronized (this) {
            val incoming = snap.get(key);
            if (incoming != null) {
                mailbox.put(key, incoming);
            }
            toFire = collectFireableForKey(key);
        }
        for (val sub : toFire) {
            sub.fireCallback();
        }
    }

    private List<SubscriptionImpl> collectFireableForKey(SubscriptionKey key) {
        val toFire = new ArrayList<SubscriptionImpl>();
        for (val sub : subs.values()) {
            if (sub.closed || !sub.deps.contains(key)) {
                continue;
            }
            if (!sub.gateOpen && sub.allDepsFulfilled()) {
                sub.gateOpen = true;
                toFire.add(sub);
            } else if (sub.gateOpen) {
                toFire.add(sub);
            }
        }
        return toFire;
    }

    private Optional<AttributeMock> findMostSpecificMatch(AttributeFinderInvocation invocation) {
        val attributeMocks = mocksByName.get(invocation.attributeName());
        if (attributeMocks == null || attributeMocks.isEmpty()) {
            return Optional.empty();
        }
        val isEnv = invocation.isEnvironmentAttributeInvocation();
        return attributeMocks.stream().filter(m -> m.isEnvironmentAttribute() == isEnv)
                .filter(m -> m.matches(invocation.entity(), invocation.arguments()))
                .max(Comparator.comparingInt(AttributeMock::specificity));
    }

    private void recordInvocation(AttributeFinderInvocation invocation) {
        invocations.add(new AttributeInvocationRecord(invocation.attributeName(), invocation.entity(),
                invocation.arguments(), sequenceCounter.getAndIncrement()));
    }

    private List<SubscriptionImpl> collectFireable() {
        val toFire = new ArrayList<SubscriptionImpl>();
        for (val sub : subs.values()) {
            if (sub.closed) {
                continue;
            }
            if (!sub.gateOpen && sub.allDepsFulfilled()) {
                sub.gateOpen = true;
                toFire.add(sub);
            } else if (sub.gateOpen) {
                toFire.add(sub);
            }
        }
        return toFire;
    }

    private int countMatchingInvocations(String name, ArgumentMatcher entityMatcher, List<ArgumentMatcher> argMatchers,
            boolean isEnvironment) {
        return (int) invocations.stream().filter(r -> r.attributeName().equals(name))
                .filter(r -> isEnvironment == r.isEnvironmentAttribute())
                .filter(r -> isEnvironment || entityMatcher == null || entityMatcher.matches(r.entity()))
                .filter(r -> argumentsMatch(r.arguments(), argMatchers)).count();
    }

    private boolean argumentsMatch(List<Value> arguments, List<ArgumentMatcher> matchers) {
        if (arguments.size() != matchers.size()) {
            return false;
        }
        for (int i = 0; i < arguments.size(); i++) {
            if (!matchers.get(i).matches(arguments.get(i))) {
                return false;
            }
        }
        return true;
    }

    private String buildVerificationMessage(String attributeName, Times times, int actualCount) {
        val sb = new StringBuilder();
        sb.append(ERROR_VERIFICATION_FAILED.formatted(attributeName));
        sb.append(times.failureMessage(actualCount));
        val attributeInvocations = getInvocations(attributeName);
        if (attributeInvocations.isEmpty()) {
            sb.append(ERROR_VERIFICATION_NO_INVOCATIONS.formatted(attributeName));
        } else {
            sb.append(ERROR_VERIFICATION_RECORDED.formatted(attributeName));
            for (val inv : attributeInvocations) {
                sb.append("\n  - ").append(inv);
            }
        }
        return sb.toString();
    }

    private static final class ForwardEntry {
        final AttributeBroker.Subscription delegateSub;
        int                                refcount;

        ForwardEntry(AttributeBroker.Subscription delegateSub, int refcount) {
            this.delegateSub = delegateSub;
            this.refcount    = refcount;
        }
    }

    private record AttributeMock(
            String mockId,
            ArgumentMatcher entityMatcher,
            List<ArgumentMatcher> argumentMatchers,
            boolean isEnvironmentAttribute) {

        AttributeMock {
            argumentMatchers = List.copyOf(argumentMatchers);
        }

        boolean matches(Value entity, List<Value> arguments) {
            if (arguments.size() != argumentMatchers.size()) {
                return false;
            }
            if (!isEnvironmentAttribute && entityMatcher != null && !entityMatcher.matches(entity)) {
                return false;
            }
            for (int i = 0; i < arguments.size(); i++) {
                if (!argumentMatchers.get(i).matches(arguments.get(i))) {
                    return false;
                }
            }
            return true;
        }

        int specificity() {
            int entitySpecificity = (entityMatcher != null) ? entityMatcher.specificity() : 0;
            int argsSpecificity   = argumentMatchers.stream().mapToInt(ArgumentMatcher::specificity).sum();
            return entitySpecificity + argsSpecificity;
        }
    }

    private final class SubscriptionImpl implements AttributeBroker.Subscription {
        final String                                                                  id;
        final Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate;
        Set<SubscriptionKey>                                                          deps;
        boolean                                                                       gateOpen;
        boolean                                                                       closed;

        SubscriptionImpl(String id,
                Set<SubscriptionKey> deps,
                Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> onUpdate) {
            this.id       = id;
            this.deps     = deps;
            this.onUpdate = onUpdate;
        }

        @Override
        public void close() {
            synchronized (MockingAttributeBroker.this) {
                closed = true;
                subs.remove(id);
                releaseKeys(deps);
                // Note: keyToMockId entries kept in case other subs share the key.
                // Mocks live for the broker lifetime. Only delegate forwards are refcounted.
            }
        }

        boolean allDepsFulfilled() {
            for (val key : deps) {
                if (!mailbox.containsKey(key)) {
                    return false;
                }
            }
            return true;
        }

        Map<SubscriptionKey, AttributeSnapshot> currentSnapshot() {
            val result = HashMap.<SubscriptionKey, AttributeSnapshot>newHashMap(deps.size());
            for (val key : deps) {
                val v = mailbox.get(key);
                if (v != null) {
                    result.put(key, v);
                }
            }
            return Map.copyOf(result);
        }

        void fireCallback() {
            Map<SubscriptionKey, AttributeSnapshot>                                 snapshot;
            Function<Map<SubscriptionKey, AttributeSnapshot>, Set<SubscriptionKey>> cb;
            synchronized (MockingAttributeBroker.this) {
                if (closed) {
                    return;
                }
                snapshot = currentSnapshot();
                cb       = onUpdate;
            }
            val newDeps = cb.apply(snapshot);
            if (newDeps == null || newDeps.isEmpty()) {
                throw new IllegalStateException(ERROR_RETURNED_DEPS_EMPTY.formatted(id));
            }
            boolean refire;
            synchronized (MockingAttributeBroker.this) {
                if (closed) {
                    return;
                }
                if (newDeps.equals(deps)) {
                    refire = false;
                } else {
                    val added = new HashSet<>(newDeps);
                    added.removeAll(deps);
                    val removed = new HashSet<>(deps);
                    removed.removeAll(newDeps);
                    bindKeys(added);
                    releaseKeys(removed);
                    deps = new HashSet<>(newDeps);
                    val nowFulfilled    = allDepsFulfilled();
                    val addedHasMailbox = added.stream().anyMatch(mailbox::containsKey);
                    refire   = addedHasMailbox && nowFulfilled;
                    gateOpen = nowFulfilled;
                }
            }
            if (refire) {
                fireCallback();
            }
        }
    }
}
