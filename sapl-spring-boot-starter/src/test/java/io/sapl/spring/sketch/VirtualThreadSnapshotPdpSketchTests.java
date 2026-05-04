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
package io.sapl.spring.sketch;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.sapl.api.attributes.AttributeBroker;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.CompiledExpression;
import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.PureOperator;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.ast.Expression;
import io.sapl.compiler.document.AstTransformer;
import io.sapl.compiler.expressions.CompilationContext;
import io.sapl.compiler.expressions.ExpressionCompiler;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.grammar.antlr.SAPLLexer;
import io.sapl.grammar.antlr.SAPLParser;
import lombok.val;
import reactor.core.publisher.Flux;

/**
 * Throwaway sketch — virtual-thread shape for the snapshot-driven PDP API.
 * <p>
 * Purpose: discover the client-facing API for streaming decisions in a
 * reactor-free core. Whatever shape this lands at constrains the inner
 * AttributeStore + trigger-loop API, so we look top-down before pinning
 * the inner contracts.
 * <p>
 * Three things to read in this file:
 * <ol>
 * <li>{@link SnapshotPdp} and {@link DecisionSubscription} — the API
 * surface under discovery. Pull-based, blocking, no Reactor.</li>
 * <li>{@link App.SketchController#decide} — the SSE endpoint that wraps
 * a subscription using a virtual thread per request.</li>
 * <li>The test method — drives an end-to-end round-trip through HTTP +
 * SSE + the trigger loop + the store.</li>
 * </ol>
 * Everything else ({@link StubAttributeStore},
 * {@link VirtualThreadSnapshotPdp},
 * the inline parser bits) is scaffolding to make the demonstration
 * runnable. Real implementations live elsewhere when this graduates.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Virtual-thread snapshot PDP — SSE shape sketch")
class VirtualThreadSnapshotPdpSketchTests {

    // ------------------------------------------------------------------------
    // 1. THE DISCOVERY TARGET — client-facing API shape
    // ------------------------------------------------------------------------

    /**
     * Reactor-free PDP entry point. Hands back a pull-based subscription
     * the consumer drives at its own pace.
     */
    interface SnapshotPdp {

        /**
         * Subscribe to the decision stream of {@code expression} evaluated
         * against {@code baseCtx} plus whatever attribute snapshots arrive
         * over time. Returns immediately; values are pulled from the
         * subscription handle on demand.
         */
        DecisionSubscription subscribe(CompiledExpression expression, EvaluationContext baseCtx);
    }

    /**
     * Pull-based handle for one decision stream. {@link #next()} blocks
     * until a fresh value is available (or the subscription is closed).
     * {@link #close()} cancels and releases all underlying resources.
     */
    interface DecisionSubscription extends AutoCloseable {

        /**
         * Block until the next decision value, or until close. Returns
         * {@code null} when the subscription has been closed and no more
         * values will arrive.
         */
        @Nullable
        Value next() throws InterruptedException;

        /** Cancel and release. Idempotent. May be called from any thread. */
        @Override
        void close();
    }

    // ------------------------------------------------------------------------
    // 2. SCAFFOLDING — stub store + trigger loop
    // ------------------------------------------------------------------------

    /**
     * Minimal in-memory AttributeStore stub. Callback-based, single
     * subscriber per evaluation id. A test hook publishes values
     * directly into the cache.
     */
    static class StubAttributeStore {

        record EvaluationId(String value) {}

        private static class Instance {
            volatile Set<AttributeFinderInvocation> deps = Set.of();
            final Runnable                          onChange;

            Instance(Runnable cb) {
                this.onChange = cb;
            }
        }

        private final Map<EvaluationId, Instance>                       instances = new ConcurrentHashMap<>();
        private final Map<AttributeFinderInvocation, AttributeSnapshot> cache     = new ConcurrentHashMap<>();

        EvaluationId open(Runnable onChange) {
            val id = new EvaluationId(UUID.randomUUID().toString());
            instances.put(id, new Instance(onChange));
            return id;
        }

        void update(EvaluationId id, Set<AttributeFinderInvocation> deps) {
            val i = instances.get(id);
            if (i != null) {
                val previous = i.deps;
                i.deps = deps;
                // Real-store semantic: when a consumer adds a dep that is
                // already cached, hand it the current value immediately so
                // it doesn't have to wait for the next publish.
                for (val dep : deps) {
                    if (!previous.contains(dep) && cache.containsKey(dep)) {
                        i.onChange.run();
                        break;
                    }
                }
            }
        }

        void close(EvaluationId id) {
            instances.remove(id);
        }

        Map<AttributeFinderInvocation, AttributeSnapshot> snapshot(EvaluationId id) {
            val i = instances.get(id);
            if (i == null) {
                return Map.of();
            }
            val snap = new HashMap<AttributeFinderInvocation, AttributeSnapshot>();
            for (val dep : i.deps) {
                val entry = cache.get(dep);
                if (entry != null) {
                    snap.put(dep, entry);
                }
            }
            return snap;
        }

        // ----- test hooks -----

        void publish(AttributeFinderInvocation invocation, Value value) {
            cache.put(invocation, new AttributeSnapshot(value, Instant.now()));
            for (val i : instances.values()) {
                if (i.deps.contains(invocation)) {
                    i.onChange.run();
                }
            }
        }

        /** Convenience: publish to whichever invocation has this attribute name. */
        void publishByName(String attributeName, Value value) {
            for (val i : instances.values()) {
                for (val dep : i.deps) {
                    if (dep.attributeName().equals(attributeName)) {
                        publish(dep, value);
                        return;
                    }
                }
            }
            throw new IllegalStateException("no instance has registered a dependency on " + attributeName);
        }

        /** Block until at least one instance has registered a dep with this name. */
        void waitForRegistration(String attributeName, java.time.Duration timeout) throws InterruptedException {
            val deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                for (val i : instances.values()) {
                    for (val dep : i.deps) {
                        if (dep.attributeName().equals(attributeName)) {
                            return;
                        }
                    }
                }
                Thread.sleep(20);
            }
            throw new IllegalStateException("registration timeout for " + attributeName);
        }
    }

    /**
     * SnapshotPdp impl that runs each subscription's trigger loop on a
     * virtual thread. {@code next()} reads from a per-subscription
     * mailbox; the loop publishes to it whenever the result changes.
     */
    static class VirtualThreadSnapshotPdp implements SnapshotPdp {

        private final StubAttributeStore store;

        VirtualThreadSnapshotPdp(StubAttributeStore store) {
            this.store = store;
        }

        @Override
        public DecisionSubscription subscribe(CompiledExpression expression, EvaluationContext baseCtx) {
            return new TriggerLoopSubscription(expression, baseCtx, store);
        }
    }

    static class TriggerLoopSubscription implements DecisionSubscription {

        private final CompiledExpression expression;
        private final EvaluationContext  baseCtx;
        private final StubAttributeStore store;
        // Single-slot mailbox: only the most recent unread value matters.
        // If the trigger loop publishes faster than the consumer reads,
        // intermediate values are correctly discarded — same snapshot
        // semantic as everything else in this pipeline.
        private final AtomicReference<Value> mailbox       = new AtomicReference<>();
        private final Object                 mailboxSignal = new Object();
        private volatile boolean             closed        = false;

        private final AtomicBoolean dirty    = new AtomicBoolean(true);
        private final Object        wakeLock = new Object();
        private final Thread        loop;
        private volatile boolean    running  = true;

        TriggerLoopSubscription(CompiledExpression expression, EvaluationContext baseCtx, StubAttributeStore store) {
            this.expression = expression;
            this.baseCtx    = baseCtx;
            this.store      = store;
            this.loop       = Thread.startVirtualThread(this::run);
        }

        private void wake() {
            dirty.set(true);
            synchronized (wakeLock) {
                wakeLock.notifyAll();
            }
        }

        private void publish(Value v) {
            mailbox.set(v);   // overwrites any unread previous value
            synchronized (mailboxSignal) {
                mailboxSignal.notifyAll();
            }
        }

        private void markClosed() {
            closed = true;
            synchronized (mailboxSignal) {
                mailboxSignal.notifyAll();
            }
        }

        private void run() {
            val                            id          = store.open(this::wake);
            Set<AttributeFinderInvocation> currentDeps = Set.of();
            Value                          lastEmitted = null;
            try {
                while (running) {
                    synchronized (wakeLock) {
                        while (running && !dirty.get()) {
                            wakeLock.wait();
                        }
                    }
                    if (!running) {
                        break;
                    }
                    dirty.set(false);

                    val ctx = baseCtx.withSnapshot(store.snapshot(id));
                    if (!(expression instanceof StreamOperator op)) {
                        if (expression instanceof Value v) {
                            publish(v);
                        }
                        running = false;
                        break;
                    }
                    val r = op.evaluate(ctx);

                    val newDeps = r.dependencies().keySet();
                    if (!newDeps.equals(currentDeps)) {
                        store.update(id, newDeps);
                        currentDeps = newDeps;
                    }

                    val v = r.result();
                    if (v != null && !v.equals(lastEmitted)) {
                        lastEmitted = v;
                        publish(v);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                publish(Value.error("trigger loop failed: " + t.getMessage()));
                running = false;
            } finally {
                store.close(id);
                markClosed();
            }
        }

        @Override
        public Value next() throws InterruptedException {
            synchronized (mailboxSignal) {
                while (mailbox.get() == null && !closed) {
                    mailboxSignal.wait();
                }
                return mailbox.getAndSet(null); // null when closed and drained
            }
        }

        @Override
        public void close() {
            running = false;
            synchronized (wakeLock) {
                wakeLock.notifyAll();
            }
            loop.interrupt();
        }
    }

    // ------------------------------------------------------------------------
    // Inlined parser/compiler bits (cross-module test-source isn't visible;
    // duplicating SaplTesting just enough to compile a single expression).
    // ------------------------------------------------------------------------

    private static final FunctionBroker  FUNCTIONS       = new DefaultFunctionBroker();
    private static final AttributeBroker NOOP_ATTRIBUTES = new AttributeBroker() {
                                                             @Override
                                                             public Flux<Value> attributeStream(
                                                                     AttributeFinderInvocation invocation) {
                                                                 return Flux.just(Value.error(
                                                                         "legacy broker not used in snapshot path"));
                                                             }

                                                             @Override
                                                             public List<Class<?>> getRegisteredLibraries() {
                                                                 return List.of();
                                                             }
                                                         };

    private static class StandaloneTransformer extends AstTransformer {
        StandaloneTransformer() {
            initializeImportMap(Map.of());
        }
    }

    static CompiledExpression compileExpression(String source) {
        val charStream  = CharStreams.fromString(source);
        val lexer       = new SAPLLexer(charStream);
        val tokenStream = new CommonTokenStream(lexer);
        val parser      = new SAPLParser(tokenStream);
        lexer.removeErrorListeners();
        parser.removeErrorListeners();
        val expression = (Expression) new StandaloneTransformer().visit(parser.expression());
        val ctx        = new CompilationContext(FUNCTIONS, NOOP_ATTRIBUTES);
        return ExpressionCompiler.compile(expression, ctx);
    }

    static EvaluationContext baseEvaluationContext() {
        val sub = AuthorizationSubscription.of(Value.of("test"), Value.of("test"), Value.of("test"), Value.of("test"));
        return EvaluationContext.of("sketchPdp", "sketchConfig", "sketchSub", sub, FUNCTIONS, NOOP_ATTRIBUTES);
    }

    // ------------------------------------------------------------------------
    // 3. THE ENDPOINT — SSE wrapping the pull-based subscription via virtual thread
    // ------------------------------------------------------------------------

    @SpringBootApplication
    static class App {

        @Bean
        StubAttributeStore store() {
            return new StubAttributeStore();
        }

        @Bean
        SnapshotPdp pdp(StubAttributeStore store) {
            return new VirtualThreadSnapshotPdp(store);
        }

        /**
         * Standalone entry-point for benchmarking. Boots on a fixed port,
         * starts a publisher that ticks a fresh value into the stub store
         * every 1 ms, and waits forever. Run via:
         * {@code java -cp <test-classpath>
         *   io.sapl.spring.sketch.VirtualThreadSnapshotPdpSketchTests$App}
         */
        public static void main(String[] args) {
            val ctx   = new org.springframework.boot.SpringApplication(App.class).run("--server.port=8081",
                    "--spring.threads.virtual.enabled=true", "--logging.level.root=WARN");
            val store = ctx.getBean(StubAttributeStore.class);

            // Publisher: emit a fresh value every 1 ms so the stream
            // endpoint always has a current snapshot to return.
            Thread.startVirtualThread(() -> {
                long counter = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        store.publishByName("time.now", Value.of("v" + counter++));
                    } catch (IllegalStateException ignored) {
                        // No subscribers yet; spin until first request opens one.
                    }
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            });
        }

        @RestController
        static class SketchController {

            // Pre-compiled at class-load time. Each endpoint uses its
            // corresponding constant. Ensures benchmarks measure dispatch
            // + eval cost only, never the compiler.
            private static final CompiledExpression VALUE_EXPR  = compileExpression("1");
            private static final CompiledExpression PURE_EXPR   = compileExpression("subject");
            private static final CompiledExpression STREAM_EXPR = compileExpression("<time.now>");
            private static final EvaluationContext  BASE_CTX    = baseEvaluationContext();

            private final SnapshotPdp pdp;

            SketchController(SnapshotPdp pdp) {
                this.pdp = pdp;
            }

            @GetMapping(path = "/decide", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
            SseEmitter decide() {
                val emitter = new SseEmitter(0L);
                val sub     = pdp.subscribe(STREAM_EXPR, BASE_CTX);

                Thread.startVirtualThread(() -> {
                    try {
                        Value v;
                        while ((v = sub.next()) != null) {
                            emitter.send(SseEmitter.event().data(ValueJsonMarshaller.toPrettyString(v)));
                        }
                        emitter.complete();
                    } catch (Exception e) {
                        emitter.completeWithError(e);
                    } finally {
                        sub.close();
                    }
                });

                emitter.onCompletion(sub::close);
                emitter.onTimeout(sub::close);
                return emitter;
            }

            // Single-value endpoints — one per stratum. Each uses its
            // pre-compiled expression. Value / Pure paths open no
            // subscription; Stream path subscribes, takes first value,
            // closes. Three separate handlers (rather than a switch on a
            // string parameter) so the bytecode shows exactly which path
            // is exercised during measurement.

            @GetMapping(path = "/decideOnce/value", produces = MediaType.TEXT_PLAIN_VALUE)
            String decideOnceValue() {
                return ValueJsonMarshaller.toPrettyString((Value) VALUE_EXPR);
            }

            @GetMapping(path = "/decideOnce/pure", produces = MediaType.TEXT_PLAIN_VALUE)
            String decideOncePure() {
                return ValueJsonMarshaller.toPrettyString(((PureOperator) PURE_EXPR).evaluate(BASE_CTX));
            }

            @GetMapping(path = "/decideOnce/stream", produces = MediaType.TEXT_PLAIN_VALUE)
            String decideOnceStream() throws InterruptedException {
                try (val sub = pdp.subscribe(STREAM_EXPR, BASE_CTX)) {
                    return ValueJsonMarshaller.toPrettyString(sub.next());
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // 4. THE TEST — drives a full round-trip
    // ------------------------------------------------------------------------

    @Autowired
    private StubAttributeStore store;
    @LocalServerPort
    private int                port;

    @Test
    @DisplayName("publishes propagate as pretty-printed SSE events")
    void publishesAppearAsSseEvents() throws Exception {
        val client      = HttpClient.newHttpClient();
        val request     = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/decide")).GET().build();
        val collected   = new LinkedBlockingQueue<String>();
        val readerReady = new CountDownLatch(1);

        val consumer = Thread.startVirtualThread(() -> {
            try {
                val resp = client.send(request, BodyHandlers.ofInputStream());
                try (val reader = new BufferedReader(new InputStreamReader(resp.body()))) {
                    readerReady.countDown();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data:")) {
                            collected.put(line.substring(5).trim());
                        }
                    }
                }
            } catch (Exception ignored) {
                /* expected on close / interrupt */
            }
        });

        readerReady.await(2, TimeUnit.SECONDS);
        store.waitForRegistration("time.now", java.time.Duration.ofSeconds(2));

        store.publishByName("time.now", Value.of("first"));
        val event1 = collected.poll(2, TimeUnit.SECONDS);

        store.publishByName("time.now", Value.of("second"));
        val event2 = collected.poll(2, TimeUnit.SECONDS);

        store.publishByName("time.now", Value.error("clock unavailable"));
        val event3 = collected.poll(2, TimeUnit.SECONDS);

        consumer.interrupt();

        assertThat(event1).isEqualTo("\"first\"");
        assertThat(event2).isEqualTo("\"second\"");
        assertThat(event3).startsWith("ERROR[message=\"clock unavailable\"");
    }

    private String httpGet(String path) throws Exception {
        val client   = HttpClient.newHttpClient();
        val request  = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        val response = client.send(request, BodyHandlers.ofString());
        return response.body();
    }

    @Test
    @DisplayName("decideOnce/value returns immediately, no subscription opened")
    void decideOnceWithValueExpression() throws Exception {
        val body = httpGet("/decideOnce/value");

        assertThat(body).isEqualTo("1");
    }

    @Test
    @DisplayName("decideOnce/pure evaluates once against the context, no subscription opened")
    void decideOnceWithPureExpression() throws Exception {
        val body = httpGet("/decideOnce/pure");

        assertThat(body).isEqualTo("\"test\"");   // BASE_CTX set subject="test"
    }

    @Test
    @DisplayName("decideOnce/stream subscribes, takes first value, closes")
    void decideOnceWithStreamExpression() throws Exception {
        val publisher = Thread.startVirtualThread(() -> {
            try {
                store.waitForRegistration("time.now", java.time.Duration.ofSeconds(5));
                store.publishByName("time.now", Value.of("only"));
            } catch (InterruptedException ignored) {
                /* test ending */ }
        });

        val body = httpGet("/decideOnce/stream");

        publisher.interrupt();
        assertThat(body).isEqualTo("\"only\"");
    }
}
