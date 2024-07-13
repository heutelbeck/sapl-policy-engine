package io.sapl.geo.traccar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.connection.shared.GeoMapper;
import io.sapl.geo.model.Geofence;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.pip.http.ReactiveWebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TraccarGeofences extends TraccarBase {
    
private GeoMapper geoMapper;
    
    
    public TraccarGeofences(ObjectMapper mapper) {
        
        super(mapper);
    }

    public Flux<Val> getGeofences(JsonNode settings) {
        
        geoMapper     = new GeoMapper(getDeviceId(settings), LATITUDE, LONGITUDE, ALTITUDE, LASTUPDATE, ACCURACY, mapper);
    
        var server   = getServer(settings);
        var url      = "ws://" + server + "/api/socket";
        var protocol = getProtocol(settings);
        return establishSession(getUser(settings), getPassword(settings), server, protocol).flatMapMany(cookie -> { 

            return getFlux(url, cookie, getResponseFormat(settings, mapper), getDeviceId(settings), protocol, server,  getLatitudeFirst(settings))
                    .map(Val::of).onErrorResume(e -> Flux.just(Val.error(e.getMessage()))).doFinally(s -> disconnect());
        });

   }
    

    private Flux<ObjectNode> getFlux(String url, String cookie, GeoPipResponseFormat format,
            int deviceId, String protocol, String server, boolean latitudeFirst) throws PolicyEvaluationException {

        try {
           
            var flux = getGeofences1(format, deviceId, protocol, server, latitudeFirst)
                    .map(res -> mapper.convertValue(res, ObjectNode.class));

            logger.info("Traccar-Client connected.");
            return flux;

   
        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }

    }
    
    
    
   private Flux<List<Geofence>> getGeofences1(GeoPipResponseFormat format, int deviceId, String protocol, String server, boolean latitudeFirst) {


            return getGeofences(deviceId, protocol, server)
                    .doOnNext(x -> {
                        
                        var a = x;
                    })
                    .flatMap(fences -> mapGeofences(format, fences, latitudeFirst))
                    .doOnNext(x -> {
                        
                        var a = x;
                    })
                    ;

    }

    
    
   private Flux<JsonNode> getGeofences(int deviceId, String protocol, String server) {

        var webClient = new ReactiveWebClient(mapper);
        var baseURL            = protocol + "://" + server;
        var params = new HashMap<String, String>();
        params.put("deviceId", Integer.toString(deviceId));

        var template = """
                {
                    "baseUrl" : "%s",
                    "path" : "%s",
                    "accept" : "%s",
                    "repetitions" : 1,
                    "headers" : {
                        "cookie" : "%s"
                    },
                    "urlParameters" : {
                        "deviceId":"%s"
                    }
                }
                """;
        Val request  = Val.of("");
        try {

            request = Val.ofJson(String.format(template, baseURL, "api/geofences", MediaType.APPLICATION_JSON_VALUE,
                    sessionCookie, deviceId));
        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }

        
        return webClient.httpRequest(HttpMethod.GET, request).map(Val::get);
    }

    Mono<List<Geofence>> mapGeofences(GeoPipResponseFormat format, JsonNode in,
            boolean latitudeFirst) {
        List<Geofence> fenceRes = new ArrayList<>();

        try {

            fenceRes = geoMapper.mapTraccarGeoFences(in, format, mapper, latitudeFirst);

        } catch (Exception e) {
            return Mono.error(e);
        }

        return Mono.just(fenceRes);

    }
    
}
