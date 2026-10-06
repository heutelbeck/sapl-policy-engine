package io.sapl.node.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

@DisplayName("attribute command tests")
class AttributesCommandTests {
    private final StringWriter out = new StringWriter();
    private final CommandLine  cmd = new CommandLine(new AttributesCommand());

    @BeforeEach
    void setUp() {
        cmd.setOut(new PrintWriter(out));
    }

    @Test
    @DisplayName("when help is requested then all subcommands are listed")
    void whenHelpIsRequestedThenAllSubcommandsAreListed() {
        cmd.execute("--help");
        assertThat(out.toString()).contains("publish", "delete", "get", "getall", "count");
        assertThat(cmd.execute()).isZero();
    }
}
