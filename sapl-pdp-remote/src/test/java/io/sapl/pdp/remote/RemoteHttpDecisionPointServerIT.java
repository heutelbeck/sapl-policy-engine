package io.sapl.pdp.remote;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import javax.net.ssl.SSLException;


@Slf4j
@DirtiesContext
@Testcontainers
@SpringBootTest
 @ActiveProfiles(profiles = "quiet")
public class RemoteHttpDecisionPointServerIT {
    private static final int SAPL_SERVER_HTTP_PORT = 8080;
    private static final int SAPL_SERVER_HTTPS_PORT = 8443;
    private static final int SAPL_SERVER_RSOCKET_PORT = 7000;
    private static final String CONTAINER_IMAGE = "ghcr.io/heutelbeck/sapl-server-lt:2.1.0-SNAPSHOT";

    AuthorizationSubscription permittedSubscription = AuthorizationSubscription.of(
            "Willi",
            "eat",
            "apple"
    );

    AuthorizationSubscription deniedSubscription = AuthorizationSubscription.of(
            "Willi",
            "eat",
            "ice"
    );

    @SpringBootConfiguration
    static class TestConfiguration {
    }

    private void requestDecision(PolicyDecisionPoint pdp) {
        StepVerifier.create(pdp.decide(permittedSubscription))
                .expectNext(AuthorizationDecision.PERMIT)
                .thenCancel()
                .verify();
        StepVerifier.create(pdp.decide(deniedSubscription))
                .expectNext(AuthorizationDecision.NOT_APPLICABLE)
                .thenCancel()
                .verify();
    }

    // HTTP Protocol
    @Test
    void whenRequestingDecisionFromHttpPdp_withNoAuth_thenDecisionIsProvided() {
        var container = new GenericContainer<>(DockerImageName.parse(CONTAINER_IMAGE))
                .withClasspathResourceMapping("test_policies.sapl", "/pdp/data/test_policies.sapl", BindMode.READ_ONLY)
                .withExposedPorts(SAPL_SERVER_HTTP_PORT)
                .waitingFor(Wait.forListeningPort());
        container.start();
        log.info("connecting to: " + "http://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_HTTP_PORT));
        var pdp = RemotePolicyDecisionPoint.builder()
                .http()
                .baseUrl("http://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_HTTP_PORT))
                .build();
        requestDecision(pdp);
    }

    // HTTPS Protocol
    @Test
    void whenRequestingDecisionFromHttpsPdp_withNoAuth_thenDecisionIsProvided() throws SSLException {
        var container = new GenericContainer<>(DockerImageName.parse(CONTAINER_IMAGE))
                .withClasspathResourceMapping("test_policies.sapl", "/pdp/data/test_policies.sapl", BindMode.READ_ONLY)
                .withExposedPorts(SAPL_SERVER_HTTPS_PORT)
                .waitingFor(Wait.forListeningPort())
                .withEnv("spring_profiles_active", "local")
                .withEnv("io_sapl_pdp_embedded_policies-path", "/pdp/data")
                .withEnv("server_address", "0.0.0.0")
                .withEnv("server_ssl_enabled", "True")
                .withEnv("io_sapl_server-lt_allowNoAuth", "True");
        container.start();
        var pdp = RemotePolicyDecisionPoint.builder()
                .http()
                .baseUrl("https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_HTTPS_PORT))
                .withUnsecureSSL()
                .build();
        requestDecision(pdp);
        container.stop();
    }

    @Test
    void whenRequestingDecisionFromHttpsPdp_withBasicAuth_thenDecisionIsProvided() throws SSLException {
        var container = new GenericContainer<>(DockerImageName.parse(CONTAINER_IMAGE))
                .withClasspathResourceMapping("test_policies.sapl", "/pdp/data/test_policies.sapl", BindMode.READ_ONLY)
                .withExposedPorts(SAPL_SERVER_HTTPS_PORT)
                .waitingFor(Wait.forListeningPort())
                .withEnv("spring_profiles_active", "local")
                .withEnv("io_sapl_pdp_embedded_policies-path", "/pdp/data")
                .withEnv("server_address", "0.0.0.0")
                .withEnv("server_ssl_enabled", "True")
                .withEnv("io_sapl_server-lt_allowNoAuth", "False");
        container.start();
        var pdp = RemotePolicyDecisionPoint.builder()
                .http()
                .baseUrl("https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_HTTPS_PORT))
                .basicAuth("YJidgyT2mfdkbmL", "Fa4zvYQdiwHZVXh")
                .withUnsecureSSL()
                .build();
        requestDecision(pdp);
        container.stop();
    }

    @Test
    void whenRequestingDecisionFromHttpsPdp_withApiKeyAuth_thenDecisionIsProvided() throws SSLException {
        var SAPL_API_KEY = "abD12344cdefDuwg8721";
        var container = new GenericContainer<>(DockerImageName.parse(CONTAINER_IMAGE))
                .withClasspathResourceMapping("test_policies.sapl", "/pdp/data/test_policies.sapl", BindMode.READ_ONLY)
                .withExposedPorts(SAPL_SERVER_HTTPS_PORT)
                .waitingFor(Wait.forListeningPort())
                .withEnv("spring_profiles_active", "local")
                .withEnv("io_sapl_pdp_embedded_policies-path", "/pdp/data")
                .withEnv("server_address", "0.0.0.0")
                .withEnv("server_ssl_enabled", "True")
                .withEnv("io_sapl_server-lt_allowNoAuth", "False")
                .withEnv("io_sapl_server-lt_allowApiKeyAuth", "True")
                .withEnv("io_sapl_server-lt_allowedApiKeys", SAPL_API_KEY);
        container.start();
        var pdp = RemotePolicyDecisionPoint.builder()
                .http()
                .baseUrl("https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_HTTPS_PORT))
                .apiKey(SAPL_API_KEY)
                .withUnsecureSSL()
                .build();
        requestDecision(pdp);
        container.stop();
    }

    @Test
    void whenRequestingDecisionFromHttpsPdp_withOauth2Auth_thenDecisionIsProvided() throws SSLException {
        var oauth2Container = new GenericContainer<>(DockerImageName.parse("ghcr.io/navikt/mock-oauth2-server:0.5.8"))
                .withExposedPorts(8080)
                .waitingFor(Wait.forListeningPort());
        oauth2Container.start();

        var container = new GenericContainer<>(DockerImageName.parse(CONTAINER_IMAGE))
                .withClasspathResourceMapping("test_policies.sapl", "/pdp/data/test_policies.sapl", BindMode.READ_ONLY)
                .withExposedPorts(SAPL_SERVER_HTTPS_PORT)
                .waitingFor(Wait.forListeningPort())
                .withEnv("spring_profiles_active", "local")
                .withEnv("io_sapl_pdp_embedded_policies-path", "/pdp/data")
                .withEnv("server_address", "0.0.0.0")
                .withEnv("server_ssl_enabled", "True")
                .withEnv("io_sapl_server-lt_allowNoAuth", "False")
                .withEnv("io_sapl_server-lt_allowOauth2Auth", "True")
                .withExtraHost("auth-host", "host-gateway")
                .withEnv("spring_security_oauth2_resourceserver_jwt_issuer-uri", "http://auth-host:"
                        + oauth2Container.getMappedPort(8080) + "/default");
        container.start();

        var clientRegistrationRepository = new ReactiveClientRegistrationRepository() {
            @Override
            public Mono<ClientRegistration> findByRegistrationId(String registrationId) {
                return Mono.just(ClientRegistration
                        .withRegistrationId("saplPdp")
                        .tokenUri("http://auth-host:" + oauth2Container.getMappedPort(8080) + "/default/token")
                        .clientId("0oa62xybztegSdqtZ5d7")
                        .clientSecret("v6WUqDre1B4WMejey-6sklb5kZW7C5RB2iftv_sq")
                        .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                        .scope("sapl")
                        .build());
            }
        };
        var pdp = RemotePolicyDecisionPoint.builder()
                .http()
                .baseUrl("https://" + container.getHost() + ":" + container.getMappedPort(SAPL_SERVER_HTTPS_PORT))
                .withUnsecureSSL()
                .oauth2(clientRegistrationRepository, "saplPdp")
                .build();
        requestDecision(pdp);
        oauth2Container.stop();
        container.stop();
    }
}

