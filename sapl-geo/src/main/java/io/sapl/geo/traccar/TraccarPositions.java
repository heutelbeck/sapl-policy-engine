package io.sapl.geo.traccar;


import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.connection.shared.GeoMapper;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.pip.http.ReactiveWebClient;

import reactor.core.publisher.Flux;


public class TraccarPositions extends TraccarBase{


    
    private GeoMapper geoMapper;
    
    
    public TraccarPositions(ObjectMapper mapper) {
        
        super(mapper);
    }


    /**
     * @param settings a {@link JsonNode} containing the settings
     * @return a {@link Flux}<{@link Val}
     */
    public Flux<Val> getPositions(JsonNode settings) {
    
        geoMapper     = new GeoMapper(LATITUDE, LONGITUDE, ALTITUDE, LASTUPDATE, ACCURACY, mapper);
    
        var server   = getServer(settings);
        var url      = "ws://" + server + "/api/socket";
    
        return establishSession(getUser(settings), getPassword(settings), server,getProtocol(settings)).flatMapMany(cookie -> { 

            return getFlux(url, cookie, getResponseFormat(settings, mapper), getDeviceId(settings), getLatitudeFirst(settings))
                    .map(Val::of).onErrorResume(e -> Flux.just(Val.error(e.getMessage()))).doFinally(s -> disconnect());
        });

   }
    
    private Flux<ObjectNode> getFlux(String url, String cookie, GeoPipResponseFormat format,
            int deviceId, boolean latitudeFirst) throws PolicyEvaluationException {

        var client = new ReactiveWebClient(mapper);

        var template = """
                {
                    "baseUrl" : "%s",
                    "accept" : "%s",
                    "headers" : {
                        "cookie": "%s"
                    }
                }
                """;

        try {
            var request = Val.ofJson(String.format(template, url, MediaType.APPLICATION_JSON_VALUE, cookie));

            var flux = client.consumeWebSocket(request).map(Val::get)
                    .flatMap(msg -> mapPosition(msg, format, latitudeFirst, deviceId))
                    .map(res -> mapper.convertValue(res, ObjectNode.class));

            logger.info("Traccar-Client connected.");
            return flux;

        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }

    }
    
    Flux<GeoPipResponse> mapPosition(JsonNode in, GeoPipResponseFormat format, boolean latitudeFirst, int deviceId) {
        JsonNode pos = getPositionFromMessage(in, deviceId);

        if (pos.has(DEVICE_ID)) {

            return Flux.just(geoMapper.mapPosition(deviceId, pos, format, latitudeFirst));
        }

        return Flux.just();
    }

    
    
    
    private JsonNode getPositionFromMessage(JsonNode in, int deviceId) {

        if (in.has(POSITIONS)) {
            var pos1 = (ArrayNode) in.findValue(POSITIONS);
            for (var p : pos1) {
                if (p.findValue(DEVICE_ID).toPrettyString().equals(Integer.toString(deviceId))) {
                    return p;
                }
            }
        }

        return mapper.createObjectNode();

    }
    
    
    
}
