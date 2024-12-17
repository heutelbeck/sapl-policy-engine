package io.sapl.geo.pip;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.ParseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.geo.library.GeographicFunctionLibrary;
import io.sapl.geo.pip.traccar.TraccarPolicyInformationPoint;
import io.sapl.geo.schemata.TraccarSchemata;
import io.sapl.pip.http.ReactiveWebClient;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import reactor.test.StepVerifier;

@Slf4j
class TraccarPolicyInformationPointTests {
    private static final ObjectMapper      MAPPER   = new ObjectMapper();
    private static final ReactiveWebClient CLIENT   = new ReactiveWebClient(MAPPER);
    private static final Val               SETTINGS = settings();

    @SneakyThrows
    private static Val settings() {
        return Val.ofJson("""
                {
                    "baseUrl": "https://demo.traccar.org",
                    "userName": "dheutelbeck@ftk.de",
                    "password": "A5V5Wf*f",
                    "pollingIntervalMs": 250
                }
                """);
    }

    @Test
    void serverTest() {
        final var sut             = new TraccarPolicyInformationPoint(CLIENT);
        final var attributestream = sut.server(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, SETTINGS)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.MAP_URL))
                .verifyComplete();
    }

    @Test
    void devicesTest() {
        final var sut             = new TraccarPolicyInformationPoint(CLIENT);
        final var attributestream = sut.devices(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, SETTINGS)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().isArray()).verifyComplete();
    }

    @Test
    void deviceTest() {
        final var sut             = new TraccarPolicyInformationPoint(CLIENT);
        final var attributestream = sut
                .device(Val.of("10677"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, SETTINGS)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.NAME)).verifyComplete();
    }

    @Test
    void traccarPositionTest() {
        final var sut             = new TraccarPolicyInformationPoint(CLIENT);
        final var attributestream = sut
                .traccarPosition(Val.of("10677"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, SETTINGS))
                .next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.LONGITUDE))
                .verifyComplete();
    }

    @Test
    void positionTest() {
        final var sut             = new TraccarPolicyInformationPoint(CLIENT);
        final var attributestream = sut
                .position(Val.of("10677"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, SETTINGS)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has("coordinates")).verifyComplete();
    }

    @Test
    void geofencesTest() {
        final var sut             = new TraccarPolicyInformationPoint(CLIENT);
        final var attributestream = sut.geofences(Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, SETTINGS))
                .next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().isArray()).verifyComplete();
    }

    @Test
    void geofenceTest() {
        final var sut             = new TraccarPolicyInformationPoint(CLIENT);
        final var attributestream = sut
                .traccarGeofence(Val.of("892"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, SETTINGS)).next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has(TraccarSchemata.AREA)).verifyComplete();
    }

    @Test
    void geofenceGeometryTest() {
        final var sut             = new TraccarPolicyInformationPoint(CLIENT);
        final var attributestream = sut
                .geofenceGeometry(Val.of("892"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, SETTINGS)).log()
                .next();
        StepVerifier.create(attributestream).expectNextMatches(a -> a.get().has("coordinates")).verifyComplete();
    }

    @Test
    void checkContainsCompatibilityBetweenLocationsAndFences() throws ParseException {
        final var sut      = new TraccarPolicyInformationPoint(CLIENT);
        final var position = sut
                .position(Val.of("10677"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, SETTINGS)).blockFirst();
        final var fence    = sut
                .geofenceGeometry(Val.of("892"), Map.of(TraccarPolicyInformationPoint.TRACCAR_CONFIG, SETTINGS))
                .blockFirst();
        log.info("fence:    {}", fence);
        log.info("position: {}", position);
        log.info("f contains p: {}", GeographicFunctionLibrary.contains(fence, position));
        assertTrue(GeographicFunctionLibrary.contains(fence, position).getBoolean());
    }
}
