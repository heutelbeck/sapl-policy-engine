package io.sapl.broker.impl;

import java.time.Clock;

import org.junit.jupiter.api.Test;

import io.sapl.broker.pip.time.TimePolicyInformationPoint;

class AnnotationPolicyInformationPointLoaderTests {

    @Test
    void loadTest() {
        final var loader = new AnnotationPolicyInformationPointLoader(null);
        final var clock  = Clock.systemDefaultZone();
        loader.loadPolicyInformationPoint(new TimePolicyInformationPoint(clock));
    }
}
