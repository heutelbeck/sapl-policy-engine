package io.sapl.broker.impl;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
class ScratchpadTests {

    private Flux<Val> dummyAttributeSource = Flux.range(0, 100).delayElements(Duration.ofSeconds(1L)).map(Val::of)
            .log();

    private Map<String, ActiveAttribute> attributeMap = new ConcurrentHashMap<>();

    @Test
    void foo() throws InterruptedException {
        var key  = "some.attribute";
        var sub1 = evaluateAttribute(key, false, Duration.ofMillis(500L)).subscribe(v -> log.info("SUB1:" + v));
        Thread.sleep(2000L);
        var sub2 = evaluateAttribute(key, false, Duration.ofMillis(500L)).subscribe(v -> log.info("SUB2:" + v));
        Thread.sleep(2000L);
        sub1.dispose();
        Thread.sleep(2000L);
        sub2.dispose();
        Thread.sleep(1000L);
        var sub3 = evaluateAttribute(key, false, Duration.ofMillis(500L)).subscribe(v -> log.info("SUB3:" + v));
        Thread.sleep(3000L);
        sub3.dispose();
        log.info("ALL DISPOSED");
        Thread.sleep(8000L);
    }

    /*
     * Strategies: * Dedicated Fresh Connection * Reuse connection and cache
     *
     */
    private Flux<Val> evaluateAttribute(String key, boolean fresh, Duration initialTimeOut) {
        if (fresh) {
            // lookup attribute
            return TimeOutWrapper.wrap(dummyAttributeSource, initialTimeOut);
        }
        return TimeOutWrapper.wrap(attributeMap.compute(key, this::evaluate).attribute(), initialTimeOut);
    }

    private ActiveAttribute evaluate(String key, ActiveAttribute attribute) {
        if (attribute == null) {
            // lookup source
            final var source = dummyAttributeSource;
            return new ActiveAttribute(key, source, attributeMap);
        }
        return attribute;
    }

    public static class ActiveAttribute {
        static final Duration DEFAULT_GRACE_PERIOD = Duration.ofMillis(3000L);

        private String                       key;
        private Flux<Val>                    reusableSource;
        private Map<String, ActiveAttribute> attributeIndex;

        public ActiveAttribute(String key, Flux<Val> source, Map<String, ActiveAttribute> attributeIndex) {
            this(key, source, attributeIndex, DEFAULT_GRACE_PERIOD);
        }

        public ActiveAttribute(String key, Flux<Val> source, Map<String, ActiveAttribute> attributeIndex,
                Duration gracePeriod) {
            this.key            = key;
            this.attributeIndex = attributeIndex;
            // @formatter:off
            this.reusableSource = source.doOnCancel(this::dropFromAttributeIndex)
                                        .doAfterTerminate(this::dropFromAttributeIndex)
                                        .replay(1).refCount(1)

                                        .publish().refCount(1, gracePeriod);
            // @formatter:on
        }

        public Flux<Val> attribute() {
            return reusableSource;
        }

        private void dropFromAttributeIndex() {
            attributeIndex.remove(this.key, this);
        }
    }
}
