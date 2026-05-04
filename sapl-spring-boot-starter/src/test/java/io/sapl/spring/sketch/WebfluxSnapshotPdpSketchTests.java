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

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

/**
 * Throwaway sketch — Webflux/Reactor shape for the snapshot-driven PDP
 * API. Companion to {@link VirtualThreadSnapshotPdpSketchTests}.
 * <p>
 * Key contrast with the VT variant: <strong>no trigger loop thread</strong>.
 * The store callback IS the trigger loop. When the store fires
 * {@code onChange}, the body re-evaluates synchronously and pushes the
 * result onto a {@link Sinks.Many}. Idle subscriptions cost a Sink and
 * a few atomic refs — no parked thread, no mailbox, no dirty flag, no
 * wake/notify machinery.
 * <p>
 * That's where Webflux's scaling advantage actually comes from: not
 * from wrapping the VT shape in Reactor, but from collapsing the
 * trigger loop into the callback itself. The {@link SnapshotPdp}
 * interface differs from the VT one (returns {@code Flux<Value>}
 * instead of a pull-based handle) precisely because the consumer
 * model is push, not pull.
 * <p>
 * Re-entrance handling: if multiple PIP threads fire {@code onChange}
 * concurrently, the synchronized block in {@code evalOnce} serializes
 * evaluations. Last-writer-wins per cache key is preserved by reading
 * a fresh snapshot on every entry.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.main.web-application-type=reactive")
@AutoConfigureWebTestClient
@DisplayName("Webflux snapshot PDP — SSE shape sketch")
class WebfluxSnapshotPdpSketchTests {

    // ------------------------------------------------------------------------
    // 1. THE DISCOVERY TARGET — client-facing API shape
    // ------------------------------------------------------------------------

    /**
     * Reactor-flavoured PDP entry point. Unlike the VT variant which
     * returns a pull-based {@code DecisionSubscription}, this returns a
     * push-based {@link Flux} the consumer subscribes to via Reactor's
     * standard machinery. Cancellation propagates through Reactor's
     * normal {@code doFinally} hooks; no separate close handle.
     */
    interface SnapshotPdp {
        Flux<Value> subscribe(CompiledExpression expression, EvaluationContext baseCtx);
    }

    // ------------------------------------------------------------------------
    // 2. SCAFFOLDING — stub store + reactive PDP impl
    // ------------------------------------------------------------------------

    /**
     * Minimal in-memory AttributeStore stub. Identical to the VT sketch's
     * stub — duplicated here so both files stand alone.
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

        void waitForRegistration(String attributeName, Duration timeout) throws InterruptedException {
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
     * SnapshotPdp impl that runs each subscription's "trigger loop" as
     * the store's onChange callback. No threads, no mailbox.
     */
    static class ReactiveSnapshotPdp implements SnapshotPdp {

        private final StubAttributeStore store;

        ReactiveSnapshotPdp(StubAttributeStore store) {
            this.store = store;
        }

        @Override
        public Flux<Value> subscribe(CompiledExpression expression, EvaluationContext baseCtx) {
            val sink        = Sinks.many().unicast().<Value>onBackpressureBuffer();
            val lastEmitted = new AtomicReference<Value>();
            val currentDeps = new AtomicReference<Set<AttributeFinderInvocation>>(Set.of());
            val idHolder    = new AtomicReference<StubAttributeStore.EvaluationId>();
            val lock        = new Object();

            // The "trigger loop." Called once at subscription time and
            // again whenever the store fires onChange. Synchronized so
            // re-entrant invocations from multiple PIP threads serialise
            // and each sees a fresh snapshot.
            val evalOnce = (Runnable) () -> {
                synchronized (lock) {
                    val id = idHolder.get();
                    if (id == null) {
                        return;
                    }
                    val ctx = baseCtx.withSnapshot(store.snapshot(id));
                    if (!(expression instanceof StreamOperator op)) {
                        if (expression instanceof Value v) {
                            sink.tryEmitNext(v);
                        }
                        sink.tryEmitComplete();
                        return;
                    }
                    val r       = op.evaluate(ctx);
                    val newDeps = r.dependencies().keySet();
                    if (!newDeps.equals(currentDeps.get())) {
                        currentDeps.set(newDeps);
                        store.update(id, newDeps);
                    }
                    val v = r.result();
                    if (v != null && !v.equals(lastEmitted.get())) {
                        lastEmitted.set(v);
                        sink.tryEmitNext(v);
                    }
                }
            };

            val id = store.open(evalOnce);
            idHolder.set(id);
            evalOnce.run();   // initial round; registers deps before any external poke

            return sink.asFlux().onBackpressureLatest().doFinally(signal -> store.close(id));
        }
    }

    // ------------------------------------------------------------------------
    // Inlined parser/compiler bits (duplicated from VT sketch — both files
    // self-contained per their throwaway nature).
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
    // 3. THE ENDPOINT — Webflux returning a Flux<String> as SSE
    // ------------------------------------------------------------------------

    @SpringBootApplication
    static class App {

        @Bean
        StubAttributeStore store() {
            return new StubAttributeStore();
        }

        @Bean
        SnapshotPdp pdp(StubAttributeStore store) {
            return new ReactiveSnapshotPdp(store);
        }

        /**
         * Standalone entry-point for benchmarking. Boots on a fixed port,
         * starts a publisher that ticks a fresh value into the stub store
         * every 1 ms, and waits forever. Run via:
         * {@code java -cp <test-classpath>
         *   io.sapl.spring.sketch.WebfluxSnapshotPdpSketchTests$App}
         */
        public static void main(String[] args) {
            val ctx   = new org.springframework.boot.SpringApplication(App.class).run("--server.port=8082",
                    "--spring.main.web-application-type=reactive", "--logging.level.root=WARN");
            val store = ctx.getBean(StubAttributeStore.class);

            Thread.startVirtualThread(() -> {
                long counter = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        store.publishByName("time.now", Value.of("v" + counter++));
                    } catch (IllegalStateException ignored) {
                        // No subscribers yet.
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
            Flux<String> decide() {
                return pdp.subscribe(STREAM_EXPR, BASE_CTX).map(ValueJsonMarshaller::toPrettyString);
            }

            // Single-value endpoints — one per stratum. Each uses its
            // pre-compiled expression. Value / Pure paths open no
            // subscription; Stream path subscribes, takes first value
            // (Flux.next() cancels upstream after the first item;
            // doFinally fires and closes the store entry). Three
            // separate handlers (rather than a switch on a string
            // parameter) so the bytecode shows exactly which path is
            // exercised during measurement.

            @GetMapping(path = "/decideOnce/value", produces = MediaType.TEXT_PLAIN_VALUE)
            Mono<String> decideOnceValue() {
                return Mono.fromCallable(() -> ValueJsonMarshaller.toPrettyString((Value) VALUE_EXPR));
            }

            @GetMapping(path = "/decideOnce/pure", produces = MediaType.TEXT_PLAIN_VALUE)
            Mono<String> decideOncePure() {
                return Mono.fromCallable(
                        () -> ValueJsonMarshaller.toPrettyString(((PureOperator) PURE_EXPR).evaluate(BASE_CTX)));
            }

            @GetMapping(path = "/decideOnce/stream", produces = MediaType.TEXT_PLAIN_VALUE)
            Mono<String> decideOnceStream() {
                return pdp.subscribe(STREAM_EXPR, BASE_CTX).next().map(ValueJsonMarshaller::toPrettyString);
            }
        }
    }

    // ------------------------------------------------------------------------
    // 4. THE TEST — drives a full round-trip via WebTestClient
    // ------------------------------------------------------------------------

    @Autowired
    private StubAttributeStore store;
    @Autowired
    private WebTestClient      webTestClient;

    @Test
    @DisplayName("publishes propagate as pretty-printed SSE events")
    void publishesAppearAsSseEvents() {
        // Side-thread publisher: WebTestClient.exchange() blocks until response
        // headers, which Webflux holds until the first SSE emission. So the
        // publishes must run out-of-band: a side thread waits for the
        // controller's trigger callback to register its deps, then drives the
        // publishes. The main thread consumes the response body afterwards.
        val publisher = Thread.startVirtualThread(() -> {
            try {
                store.waitForRegistration("time.now", Duration.ofSeconds(5));
                store.publishByName("time.now", Value.of("first"));
                Thread.sleep(50);
                store.publishByName("time.now", Value.of("second"));
                Thread.sleep(50);
                store.publishByName("time.now", Value.error("clock unavailable"));
            } catch (InterruptedException ignored) {
                /* test ending */ }
        });

        val flux = webTestClient.mutate().responseTimeout(Duration.ofSeconds(10)).build().get().uri("/decide")
                .accept(MediaType.TEXT_EVENT_STREAM).exchange().returnResult(String.class).getResponseBody();

        StepVerifier.create(flux).expectNext("\"first\"").expectNext("\"second\"")
                .expectNextMatches(s -> s.startsWith("ERROR[message=\"clock unavailable\"")).thenCancel()
                .verify(Duration.ofSeconds(10));

        publisher.interrupt();
    }

    private String httpGet(String path) {
        return webTestClient.get().uri(path).exchange().expectStatus().isOk().expectBody(String.class).returnResult()
                .getResponseBody();
    }

    @Test
    @DisplayName("decideOnce/value returns immediately, no subscription opened")
    void decideOnceWithValueExpression() {
        val body = httpGet("/decideOnce/value");

        org.assertj.core.api.Assertions.assertThat(body).isEqualTo("1");
    }

    @Test
    @DisplayName("decideOnce/pure evaluates once against the context, no subscription opened")
    void decideOnceWithPureExpression() {
        val body = httpGet("/decideOnce/pure");

        org.assertj.core.api.Assertions.assertThat(body).isEqualTo("\"test\"");
    }

    @Test
    @DisplayName("decideOnce/stream subscribes, takes first value, closes")
    void decideOnceWithStreamExpression() {
        val publisher = Thread.startVirtualThread(() -> {
            try {
                store.waitForRegistration("time.now", Duration.ofSeconds(5));
                store.publishByName("time.now", Value.of("only"));
            } catch (InterruptedException ignored) {
                /* test ending */ }
        });

        val body = webTestClient.mutate().responseTimeout(Duration.ofSeconds(5)).build().get().uri("/decideOnce/stream")
                .exchange().expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();

        publisher.interrupt();
        org.assertj.core.api.Assertions.assertThat(body).isEqualTo("\"only\"");
    }
}
