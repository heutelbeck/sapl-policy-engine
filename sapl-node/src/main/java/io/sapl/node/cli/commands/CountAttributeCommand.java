package io.sapl.node.cli.commands;

import java.io.IOException;
import java.util.Objects;

import org.springframework.web.util.UriComponentsBuilder;
import picocli.CommandLine;
import picocli.CommandLine.Mixin;

@CommandLine.Command(name = "count", mixinStandardHelpOptions = true, description = "Counts the amount of attributes for a given pdp id")
public class CountAttributeCommand extends BaseAttributeCommand {
    @Mixin
    FileMixin file;

    @Override
    public Integer call() throws Exception {
        var uriBuilder = UriComponentsBuilder.fromUriString(resolvedURL() + "/api/attributes").queryParam("count",
                "true");
        var response   = Objects.requireNonNull(webClient.get().uri(uriBuilder.build().toUri()).headers(authHeaders())
                .retrieve().toEntity(String.class).block());

        print(Objects.requireNonNullElse(response.getBody(), ""));
        return response.getStatusCode().is2xxSuccessful() ? 0 : 1;
    }

    private void print(String content) throws IOException {
        file.getFileWriter().println(content);
    }
}
