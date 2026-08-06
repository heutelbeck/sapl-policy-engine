package io.sapl.node.cli.commands;

import org.springframework.web.util.UriComponentsBuilder;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name = "delete", mixinStandardHelpOptions = true, description = "Removes an attribute from the attribute repository of a running SAPL node")
public class DeleteAttributeCommand extends BaseAttributeCommand {

    @CommandLine.Option(names = "--entity", description = "The subject or resource the attribute belongs to (omit for global attributes)")
    String entity;

    @CommandLine.Option(names = "--name", required = true, description = "The attribute name e.g. role or user.role")
    String name;

    @CommandLine.Option(names = "--arguments", description = "Comma-separated list of arguments", defaultValue = "", split = ",")
    List<String> arguments;

    @CommandLine.Option(names = "--pdpid", description = "The id of the PDP the attribute is used for", defaultValue = "default")
    String pdpId;

    @Override
    public Integer call() {
        var uriBuilder = UriComponentsBuilder.fromUriString(url + attributePath(entity, name)).queryParam("pdpid",
                pdpId);
        arguments.stream().filter(s -> !s.isEmpty()).forEach(arg -> uriBuilder.queryParam("arg", arg));

        var response = webClient.delete().uri(uriBuilder.build().toUri()).retrieve().toEntity(Void.class).block();

        return response != null && response.getStatusCode().is2xxSuccessful() ? 0 : 1;
    }
}
