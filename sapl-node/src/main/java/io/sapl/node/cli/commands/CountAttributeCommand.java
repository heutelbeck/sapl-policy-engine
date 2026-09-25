package io.sapl.node.cli.commands;

import java.io.IOException;
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
        var response   = webClient.get().uri(uriBuilder.build().toUri()).headers(authHeaders()).retrieve()
                .toEntity(String.class).block();

        print(response != null ? response.getBody() : "");
        return response != null && response.getStatusCode().is2xxSuccessful() ? 0 : 1;
    }

    private void print(String content) throws IOException {
        file.getFileWriter().println(content);
    }
}
