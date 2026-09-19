package io.sapl.node.cli.commands;

import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.sapl.node.cli.options.RemoteConnectionOptions;
import io.sapl.node.cli.support.PdpSetup;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import reactor.netty.http.HttpProtocol;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public abstract class BaseAttributeCommand implements Callable<Integer> {
    private static final String DEFAULT_URL = "http://localhost:8080";

    @Spec
    protected CommandSpec spec;

    @Option(names = "--url", description = "API endpoint of a running SAPL attribute API (e.g. http://localhost:8080)")
    protected String url;

    @ArgGroup(exclusive = true)
    protected RemoteConnectionOptions.AuthOptions auth;

    protected final WebClient webClient = WebClient.builder().clientConnector(
            new ReactorClientHttpConnector(reactor.netty.http.client.HttpClient.create().protocol(HttpProtocol.HTTP11)))
            .build();

    protected Serializable parseLiteral(String s) {
        if ("true".equalsIgnoreCase(s))
            return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s))
            return Boolean.FALSE;
        if ("null".equalsIgnoreCase(s))
            return null;

        try {
            return Long.valueOf(s);
        } catch (NumberFormatException ignored) {
            // try next, if its not a long
        }

        try {
            return Double.valueOf(s);
        } catch (NumberFormatException ignored) {
            // try next (string), if its not a double
        }
        return s;
    }

    protected String attributePath(String entity, String name) {
        return (entity != null && !entity.isBlank()) ? "/api/attributes/" + entity + "/" + name
                : "/api/attributes/" + name;
    }

    /**
     * Resolves the given URL and checks if it's empty
     *
     * @return The resolved URL
     */
    protected String resolvedURL() {
        if (url != null) {
            return url;
        }
        var env = System.getenv("SAPL_URL");
        return env != null ? env : DEFAULT_URL;
    }

    /**
     * Sets the right HTTP header for the chosen authentication. >HTTP Basic for the
     * --basic-auth option and Bearer for the --token option (JWT and OAuth2/JWT).
     *
     * @return A function that sets the Authorization header on a header for an
     * outgoing request. No-op if no credentials were configured.
     */
    protected Consumer<HttpHeaders> authHeaders() {
        if (auth != null && auth.basicAuth != null) {
            var parsed = PdpSetup.parseBasicAuth(auth.basicAuth);
            return headers -> {
                headers.setBasicAuth(parsed.username(), parsed.password());
                headers.set("X-SAPL-Client", "cli");
            };
        }

        if (auth != null && auth.token != null) {
            return headers -> headers.setBearerAuth(auth.token);
        }

        var envBasicAuth = System.getenv("SAPL_BASIC_AUTH");
        if (envBasicAuth != null) {
            var parsed = PdpSetup.parseBasicAuth(envBasicAuth);
            return headers -> headers.setBasicAuth(parsed.username(), parsed.password());
        }

        var envToken = System.getenv("SAPL_BEARER_TOKEN");
        if (envToken != null) {
            return headers -> headers.setBearerAuth(envToken);
        }

        return headers -> {};
    }
}
