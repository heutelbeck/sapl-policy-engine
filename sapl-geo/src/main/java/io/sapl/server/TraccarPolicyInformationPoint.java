package io.sapl.server;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pip.EnvironmentAttribute;
import io.sapl.api.pip.PolicyInformationPoint;
import io.sapl.api.validation.JsonObject;
import io.sapl.geo.traccar.TraccarGeofences;
import io.sapl.geo.traccar.TraccarPositions;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
@PolicyInformationPoint(name = PostGisPolicyInformationPoint.NAME, description = PostGisPolicyInformationPoint.DESCRIPTION)
public class TraccarPolicyInformationPoint {

    public static final String NAME = "traccar";

    public static final String DESCRIPTION = "PIP for geographical data from traccar.";

    private final ObjectMapper mapper;
    
    private static final String TRACCAR_DEFAULT_CONFIG = "TRACCAR_DEFAULT_CONFIG";

    @EnvironmentAttribute(name = "position")
    public Flux<Val> position(Map<String, Val> auth, @JsonObject Val variables) {

        return new TraccarPositions(auth.get(TRACCAR_DEFAULT_CONFIG).get(), mapper).getPositions(variables.get());

    }

    @EnvironmentAttribute(name = "position")
    public Flux<Val> position(@JsonObject Val auth, @JsonObject Val variables) {

        try {
            return new TraccarPositions(auth.get(), mapper).getPositions(variables.get());

        } catch (Exception e) {
            return Flux.just(Val.error(e.getMessage()));
        }
    }
    
    @EnvironmentAttribute(name = "position")
    public Flux<Val> geofences(Map<String, Val> auth, @JsonObject Val variables) {

        return new TraccarGeofences(auth.get(TRACCAR_DEFAULT_CONFIG).get(), mapper).getGeofences(variables.get());

    }

    @EnvironmentAttribute(name = "position")
    public Flux<Val> geofences(@JsonObject Val auth, @JsonObject Val variables) {

        try {
            return new TraccarGeofences(auth.get(), mapper).getGeofences(variables.get());

        } catch (Exception e) {
            return Flux.just(Val.error(e.getMessage()));
        }
    }
    
}
