package io.sapl.geo.traccar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    
    /**
     * @param settings a {@link JsonNode} containing the settings
     * @return a {@link Flux}<{@link Val}
     */
    public Flux<Val> getGeofences(JsonNode settings) {
        

        var deviceId = getDeviceId(settings);
        geoMapper     = new GeoMapper(LATITUDE, LONGITUDE, ALTITUDE, LASTUPDATE, ACCURACY, mapper);
        
        var server   = getServer(settings);
        var protocol = getProtocol(settings);
        return establishSession(getUser(settings), getPassword(settings), server, protocol).flatMapMany(cookie -> { 

            return getFlux(cookie, getResponseFormat(settings, mapper), deviceId, protocol, server,  getLatitudeFirst(settings))
                    .map(Val::of).onErrorResume(e -> Flux.just(Val.error(e.getMessage()))).doFinally(s -> disconnect());
        });

   }
    

    private Flux<JsonNode> getFlux(String cookie, GeoPipResponseFormat format,
            Integer deviceId, String protocol, String server, boolean latitudeFirst) throws PolicyEvaluationException {

        try {
           
            var flux = getGeofences1(format, deviceId, protocol, server, latitudeFirst)
                    .map(res -> mapper.convertValue(res, JsonNode.class));

            logger.info("Traccar-Client connected.");
            return flux;

   
        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }

    }
    
    
    
   private Flux<List<Geofence>> getGeofences1(GeoPipResponseFormat format, Integer deviceId, String protocol, String server, boolean latitudeFirst) {


        return getGeofences(deviceId, protocol, server)
                .flatMap(fences -> mapGeofences(format, fences, latitudeFirst));

    }
 
    
   private Flux<JsonNode> getGeofences(Integer deviceId, String protocol, String server) {

        var webClient = new ReactiveWebClient(mapper);
        var baseURL            = protocol + "://" + server;
        var params = new HashMap<String, String>();
                
        var template = """
                {
                    "baseUrl" : "%s",
                    "path" : "%s",
                    "accept" : "%s",
                    "repetitions" : 1,
                    "headers" : {
                        "cookie" : "%s"
                    }
                """;
        
        if(deviceId != null) {
            params.put("deviceId", Integer.toString(deviceId));
            template = template.concat("""
                    ,
                    "urlParameters" : {
                        "deviceId":"%s"
                    }
                    """
                );
        }
        template = template.concat("}");
        
        Val request  = Val.of("");
        try {

            request = Val.ofJson(String.format(template, baseURL, "api/geofences", MediaType.APPLICATION_JSON_VALUE,
                    sessionCookie, deviceId));
        } catch (Exception e) {
            throw new PolicyEvaluationException(e);
        }

        
        return webClient.httpRequest(HttpMethod.GET, request)   
                .map(Val::get);
    }

    private Mono<List<Geofence>> mapGeofences(GeoPipResponseFormat format, JsonNode in,
            boolean latitudeFirst) {
        List<Geofence> fenceRes = new ArrayList<>();

        try {

            fenceRes = geoMapper.mapTraccarGeoFences(in, format, mapper, latitudeFirst);

        } catch (Exception e) {
            return Mono.error(e);
        }

        return Mono.just(fenceRes);

    }
    

    protected static Integer getDeviceId(JsonNode requestSettings) {
        if (requestSettings.has(DEVICEID_CONST)) {
            return requestSettings.findValue(DEVICEID_CONST).asInt();
        } else {

            return null;
        }
    }
}
