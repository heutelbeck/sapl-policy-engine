package io.sapl.geo.connection.owntracks;

import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

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


	private GeoMapper geoMapper;
	private int deviceId;
	private final Logger logger = LoggerFactory.getLogger(getClass());
    private static final String ALTITUDE    = "alt";
    private static final String LASTUPDATE  = "created_at";
    private static final String ACCURACY    = "acc";
    private static final String LATITUDE    = "lat";
    private static final String LONGITUDE   = "lon";
	
    protected static final String HTTP_BASIC_AUTH_USER             = "user";
	
	private ReactiveWebClient client;
	
    private OwnTracksConnection(ObjectMapper mapper, int deviceId) throws PolicyEvaluationException {
    	
    	client = new ReactiveWebClient(mapper);
    	this.deviceId = deviceId;
    	geoMapper = new GeoMapper(deviceId, LATITUDE, LONGITUDE, ALTITUDE, LASTUPDATE, ACCURACY, mapper);
    
    }

    public static OwnTracksConnection getNew(ObjectMapper mapper, int deviceId)   {

        return new OwnTracksConnection(mapper, deviceId);
    }

    
    public static Flux<Val> connect(JsonNode settings, ObjectMapper mapper) {

        try {
            var connection = getNew(mapper, getDeviceId(settings));
            return connection.getFlux(getHttpBasicAuthUser(settings), getPassword(settings), getServer(settings),
            		getProtocol(settings), getUser(settings), getResponseFormat(settings, mapper), mapper).map(Val::of);

        } catch (Exception e) {
            return Flux.just(Val.error(e));
        }

    }
    
    private Flux<ObjectNode> getFlux(String httpBasicAuthUser, String password, String server, String protocol, String user, GeoPipResponseFormat format, ObjectMapper mapper) {
    	
    	
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
    	Val request;
		try {
			request = Val.ofJson(String.format(html, url, MediaType.APPLICATION_JSON_VALUE, basicAuthHeader));
			
			
		 } catch (Exception e) {
			throw new PolicyEvaluationException(e);
		 }
		 
		 var flux = client.httpRequest(HttpMethod.GET, request)
					.flatMap(v -> mapPosition(v.get(), format, mapper))
					.map(res -> mapper.convertValue(res, ObjectNode.class));
		 logger.info("OwnTracks-Client connected.");
		 return flux;
    }
	
    
    public Flux<GeoPipResponse> mapPosition(JsonNode in, GeoPipResponseFormat format, ObjectMapper mapper) {
  
    	var response = geoMapper.mapPosition(in.get(0), format);
    	var res = in.findValue("inregions");
    	
    	response.setGeoFences(geoMapper.mapOwnTracksInRegions(res, mapper));
    	
    	return Flux.just(response);
 
    }
    
   
    
    private static String getHttpBasicAuthUser(JsonNode requestSettings) throws PolicyEvaluationException {
        if (requestSettings.has(HTTP_BASIC_AUTH_USER)) {
            return requestSettings.findValue(HTTP_BASIC_AUTH_USER).asText();
        } else {
            throw new PolicyEvaluationException("No Basic-Auth-User found");

        }

    }
    
}
