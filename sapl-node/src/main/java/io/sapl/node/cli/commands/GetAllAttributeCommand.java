package io.sapl.node.cli.commands;

import java.io.IOException;
import java.util.Objects;

import org.springframework.web.util.UriComponentsBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;

@CommandLine.Command(name = "getall", mixinStandardHelpOptions = true, description = "Gets all attributes from the attribute repository for a given pdp id")
public class GetAllAttributeCommand extends BaseAttributeCommand {
    @Mixin
    FileMixin file;

    @CommandLine.Option(names = "--limit", description = "Limit the amount of attributes that are shown in the output")
    Integer limit;

    @CommandLine.Option(names = "--offset", description = "The first position to start with")
    Integer offset;

    @Override
    public Integer call() throws Exception {
        var uriBuilder = UriComponentsBuilder.fromUriString(resolvedURL() + "/api/attributes");

        if (limit != null && limit > 0) {
            uriBuilder.queryParam("limit", limit);
        }

        if (offset != null && offset >= 0) {
            uriBuilder.queryParam("offset", offset);
        }

        var response = Objects.requireNonNull(webClient.get().uri(uriBuilder.build().toUri()).headers(authHeaders())
                .retrieve().toEntity(String.class).block());

        print(Objects.requireNonNullElse(response.getBody(), ""));
        return response.getStatusCode().is2xxSuccessful() ? 0 : 1;
    }

    private void print(String content) throws IOException {
        file.getFileWriter().println(content);
    }
}
