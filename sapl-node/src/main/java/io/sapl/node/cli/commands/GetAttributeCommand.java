package io.sapl.node.cli.commands;

import org.springframework.web.util.UriComponentsBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;
import java.io.IOException;
import java.util.List;

@CommandLine.Command(name = "get", mixinStandardHelpOptions = true, description = "Gets an attribute from the attribute repository")
public class GetAttributeCommand extends BaseAttributeCommand {

    @Mixin
    FileMixin file;

    @CommandLine.Option(names = "--entity", description = "The subject or resource the attribute belongs to (omit for global attributes)")
    String entity;

    @CommandLine.Option(names = "--name", required = true, description = "The attribute name e.g. role or user.role")
    String name;

    @CommandLine.Option(names = "--arguments", description = "Comma-separated list of arguments", defaultValue = "", split = ",")
    List<String> arguments;

    @CommandLine.Option(names = "--pdpid", description = "The id of the PDP the attribute is used for", defaultValue = "default")
    String pdpId;

    @Override
    public Integer call() throws Exception {
        var uriBuilder = UriComponentsBuilder.fromUriString(url + attributePath(entity, name)).queryParam("pdpid",
                pdpId);
        arguments.stream().filter(s -> !s.isEmpty()).forEach(arg -> uriBuilder.queryParam("arg", arg));

        var response = webClient.get().uri(uriBuilder.build().toUri()).retrieve().toEntity(String.class).block();

        print(response != null ? response.getBody() : "");
        return response != null && response.getStatusCode().is2xxSuccessful() ? 0 : 1;
    }

    private void print(String content) throws IOException {
        file.getFileWriter().println(content);
    }
}
