package io.sapl.geo.traccar;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.geo.connection.shared.ConnectionBase;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
abstract class TraccarBase extends ConnectionBase{

    final Logger       logger = LoggerFactory.getLogger(getClass());
    
    protected final ObjectMapper mapper;
    
    private int    sessionId;
    protected  String sessionCookie;
   
    private URI uri;
    
    protected static final String DEVICE_ID  = "deviceId";
    protected static final String POSITIONS  = "positions";
    protected static final String ALTITUDE   = "altitude";
    protected static final String LASTUPDATE = "fixTime";
    protected static final String ACCURACY   = "accuracy";
    protected static final String LATITUDE   = "latitude";
    protected static final String LONGITUDE  = "longitude";
    
    protected Mono<String> establishSession(String user, String password, String serverName, String protocol) throws PolicyEvaluationException {
              
        
        try {
            uri = new URI(String.format("%s://%s/api/session", protocol, serverName));

            var bodyProperties = new HashMap<String, String>() {
                private static final long serialVersionUID = 1L;

            };

            bodyProperties.put("email", user);
            bodyProperties.put("password", password);

            var form = bodyProperties.entrySet().stream()
                    .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                    .collect(Collectors.joining("&"));

            var client = WebClient.builder().build();

            return client.post().uri(uri).header("Content-Type", "application/x-www-form-urlencoded").bodyValue(form)
                    .retrieve()
                    .onStatus(status -> status.isError() || status.is4xxClientError() || status.is5xxServerError(),
                            response -> { 
                                logger.info("---- onstatus 4 5: {} {} ", response.statusCode().is4xxClientError(), response.statusCode().is5xxServerError());
                                return Mono.error(new PolicyEvaluationException(
                                    "Session could not be established. Server responded with "
                                            + response.statusCode().value()));})
                    .toEntity(String.class).flatMap(response -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            var setCookieHeader = response.getHeaders().getFirst("set-cookie");
                            if (setCookieHeader != null) {
                                sessionCookie = setCookieHeader;
                                // session = createTraccarSession(response.getBody());
                                setSessionId(response.getBody());
                                logger.info("Traccar Session {} established.", sessionId);
                                return Mono.just(setCookieHeader);
                            } else {
                                return Mono.error(
                                        new PolicyEvaluationException("No session cookie found in the response."));
                            }
                        } else {
                            logger.info("---- not 4 5: {} {}", response.getStatusCode().isError());
                            return Mono.error(new PolicyEvaluationException(
                                    "Session could not be established. Server responded with "
                                            + response.getStatusCode().value()));
                        }
                    });
            
        } catch (Exception e) {
            logger.info("exception");
            throw new PolicyEvaluationException(e);
        }

    }

    private void setSessionId(String json) {
        try {
            var sessionJson = mapper.readTree(json);
            if (sessionJson.has("id")) {
                this.sessionId = sessionJson.get("id").asInt();
            }
        } catch (Exception e) {

            throw new PolicyEvaluationException(e);
        }
    }
    
    
    protected void disconnect() throws PolicyEvaluationException {
        var errorMsg = "Traccar-Client could not be disconnected";
        this.closeTraccarSession().subscribe(result -> {
            if (result) {
                logger.info("Traccar-Client disconnected.");
            } else {
                throw new PolicyEvaluationException(errorMsg);
            }
        }, error -> {
            throw new PolicyEvaluationException(errorMsg, error);
        });
    }
    
    private Mono<Boolean> closeTraccarSession() throws PolicyEvaluationException {

        var client = WebClient.builder().defaultHeader("cookie", sessionCookie).build();

        return client.delete().uri(uri).retrieve().toBodilessEntity().flatMap(response -> {
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("Traccar Session {} closed successfully.", sessionId);
                return Mono.just(true);
            } else {
                logger.error(String.format("Failed to close Traccar Session %s. Status code: %s", sessionId,
                        response.getStatusCode()));
                return Mono.just(false);
            }
        });

    }
}
