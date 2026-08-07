package io.sapl.node.cli.commands;

import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponentsBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.stream.Collectors;

@Command(name = "publish", mixinStandardHelpOptions = true, description = "Publish an attribute into the attribute repository of a running SAPL node")
public class PublishAttributeCommand extends BaseAttributeCommand {

    @Option(names = "--entity", description = "The subject or resource the attribute belongs to (omit for global attributes)")
    String entity;

    @Option(names = "--name", required = true, description = "The attribute name e.g. role or user.role")
    String name;

    @Option(names = "--value", required = true, description = "The value of the attribute (JSON literal or plain string)")
    String value;

    @Option(names = "--arguments", description = "Comma-separated list of arguments", defaultValue = "", split = ",")
    List<String> arguments;

    @Option(names = "--ttl", description = "Time to live in seconds. Omit or use -1 for permanent attributes.", defaultValue = "-1")
    Long ttl;

    @Option(names = "--pdpid", description = "The id of the PDP the attribute is used for", defaultValue = "default")
    String pdpId;

    @Override
    public Integer call() {
        var argsJson = arguments.stream().filter(s -> !s.isEmpty()).map(this::toJsonValue)
                .collect(Collectors.joining(",", "[", "]"));
        var ttlJson  = ttl >= 0 ? String.valueOf(ttl) : "null";
        var json     = """
                {
                    "value": %s,
                    "arguments": %s,
                    "ttl": %s,
                    "pdpid": "%s"
                }
                """.formatted(toJsonValue(value), argsJson, ttlJson, pdpId);

        var uri      = UriComponentsBuilder.fromUriString(url + attributePath(entity, name)).build().toUri();
        var response = webClient.put().uri(uri).contentType(MediaType.APPLICATION_JSON).bodyValue(json).retrieve()
                .toEntity(String.class).block();

        return response != null && response.getStatusCode().is2xxSuccessful() ? 0 : 1;
    }
}
