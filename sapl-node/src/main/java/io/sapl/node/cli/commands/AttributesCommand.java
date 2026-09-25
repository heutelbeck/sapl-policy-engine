package io.sapl.node.cli.commands;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(name = "attributes", mixinStandardHelpOptions = true, header = "Publish, get or remove attributes from the repository", description = {
        " Modifies, publishes, deletes or gets attribute from a given attribute storage." }, subcommands = {
                PublishAttributeCommand.class, DeleteAttributeCommand.class, GetAttributeCommand.class,
                GetAllAttributeCommand.class, CountAttributeCommand.class })

/**
 * Attribute command without any action. It exists just for structural reason to show the subcommands for
 * publish, delete, get, getall and count.
 */
// @formatter:on
public class AttributesCommand implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        return 0;
    }
}
