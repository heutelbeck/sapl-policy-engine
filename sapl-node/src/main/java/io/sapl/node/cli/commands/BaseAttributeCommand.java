package io.sapl.node.cli.commands;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import reactor.netty.http.HttpProtocol;

import java.util.concurrent.Callable;

public abstract class BaseAttributeCommand implements Callable<Integer> {

    @Spec
    protected CommandSpec spec;

    @Option(names = "--url", required = true, description = "API endpoint of a running SAPL attribute API (e.g. http://localhost:8080)")
    protected String url;

    protected final WebClient webClient = WebClient.builder().clientConnector(
            new ReactorClientHttpConnector(reactor.netty.http.client.HttpClient.create().protocol(HttpProtocol.HTTP11)))
            .build();

    protected Object parseLiteral(String s) {
        if ("true".equalsIgnoreCase(s))
            return Boolean.TRUE;
        if ("false".equalsIgnoreCase(s))
            return Boolean.FALSE;
        if ("null".equalsIgnoreCase(s))
            return null;

        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ignored) {
            // try next, if its not a long
        }

        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
            // try next (string), if its not a double
        }
        return s;
    }

    protected String attributePath(String entity, String name) {
        return (entity != null && !entity.isBlank()) ? "/api/attributes/" + entity + "/" + name
                : "/api/attributes/" + name;
    }
}
