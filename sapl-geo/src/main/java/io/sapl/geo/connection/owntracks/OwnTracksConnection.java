package io.sapl.geo.connection.owntracks;

import java.util.Base64;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.api.interpreter.Val;
import io.sapl.geo.connection.shared.ConnectionBase;
import io.sapl.geo.connection.shared.GeoMapper;
import io.sapl.geo.pip.GeoPipResponse;
import io.sapl.geo.pip.GeoPipResponseFormat;
import io.sapl.pip.http.ReactiveWebClient;
import reactor.core.publisher.Flux;

public class OwnTracksConnection extends ConnectionBase{

	

    private static final String ALTITUDE    = "alt";
    private static final String LASTUPDATE  = "created_at";
    private static final String ACCURACY    = "acc";
    private static final String LATITUDE    = "lat";
    private static final String LONGITUDE   = "lon";
	
    protected static final String HTTP_BASIC_AUTH_USER             = "user";
	
	private ReactiveWebClient client;
	
    private OwnTracksConnection(ObjectMapper mapper) throws PolicyEvaluationException {

    	client = new ReactiveWebClient(mapper);
    
    }

    public static OwnTracksConnection getNew(ObjectMapper mapper)   {

        return new OwnTracksConnection(mapper);
    }

    
    public static Flux<Val> connect(JsonNode settings, ObjectMapper mapper) {

        try {
            var connection = getNew(mapper);
            return connection.getFlux(getHttpBasicAuthUser(settings), getPassword(settings), getServer(settings),
            		getProtocol(settings), getUser(settings), getDeviceId(settings), getResponseFormat(settings, mapper), mapper).map(Val::of);

        } catch (Exception e) {
            return Flux.just(Val.error(e));
        }

    }
    
    private Flux<ObjectNode> getFlux(String httpBasicAuthUser, String password, String server, String protocol, String user, int deviceId, GeoPipResponseFormat format, ObjectMapper mapper) {
    	
    	
    	var valueToEncode = String.format("%s:%s", httpBasicAuthUser, password);
        var basicAuthHeader = "Basic " + Base64.getEncoder().encodeToString(valueToEncode.getBytes());
        
        var url = String.format("%s://%s/api/0/last?user=%s&device=%s", protocol, server, user, deviceId);
    	
    	var html        = """
                {
                    "baseUrl" : "%s",
                    "accept" : "%s",
                    "headers" : {
                    	"Authorization": "%s"
                	}
                }
                """;
	 
		 try {
			var val1 = Val.ofJson(String.format(html, url, MediaType.APPLICATION_JSON_VALUE, basicAuthHeader));
			
			client.httpRequest(HttpMethod.GET, val1)//.map(Val::toString)
			.doOnNext(a->{
				//System.out.println("-!-"+a);
			})
			.flatMap(v -> mapPosition(v.get(), deviceId, format, mapper))
			.map(res -> mapper.convertValue(res, ObjectNode.class))
			.doOnNext(a->{
				System.out.println("-!!"+a.toString());
			})
			.subscribe()
			
			;
		 } catch (JsonProcessingException e) {
			throw new PolicyEvaluationException(e);
		 }
	 
	 
    	return null;
    	
    }
	
    
    public Flux<GeoPipResponse> mapPosition(JsonNode in, int deviceId, GeoPipResponseFormat format, ObjectMapper mapper) {
  
    	var geoMapper = new GeoMapper(deviceId, LATITUDE, LONGITUDE, ALTITUDE, LASTUPDATE, ACCURACY);
    	var a = geoMapper.mapPosition(in.get(0), format, mapper);
    	return Flux.just(geoMapper.mapPosition(in.get(0), format, mapper));
        	
    	
    }
    
    
    private static String getHttpBasicAuthUser(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(HTTP_BASIC_AUTH_USER)) {
            return requestSettings.findValue(HTTP_BASIC_AUTH_USER).asText();
        } else {
            throw new PolicyEvaluationException("No Basic-Auth-User found");

        }

    }
    
}
