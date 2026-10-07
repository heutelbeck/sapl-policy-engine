package io.sapl.node.cli.commands;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import lombok.val;
import picocli.CommandLine;

class CountAttributeCommandTests {

    @Nested
    @DisplayName("when arguments are called")
    class ArgumentParsingTests {
        @Test
        @DisplayName("then --help produces help text and exits with code 0")
        void whenHelpThenExitZeroWithHelpText() {
            val out = new StringWriter();
            val cmd = new CommandLine(new CountAttributeCommand());
            cmd.setOut(new PrintWriter(out));
            val exitCode = cmd.execute("--help");
            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("count", "--url", "--output");
        }
    }

    @Nested
    @DisplayName("when count command is used")
    class WhenAttributeApiResponds {
        private final StringWriter out = new StringWriter();
        private final CommandLine  cmd = new CommandLine(new CountAttributeCommand());
        private AttributeApiStub   apiServer;

        @BeforeEach
        void setUp() throws IOException {
            apiServer = new AttributeApiStub();
            cmd.setOut(new PrintWriter(out));
        }

        @AfterEach
        void tearDown() {
            apiServer.close();
        }

        @Test
        @DisplayName("then the number of entries for a pdp id is returned")
        void thenCountReturnsTheNumberOfEntriesForThePdpId() {
            apiServer.respondWith(200, "5");
            val exitCode = cmd.execute("--url", apiServer.url());

            assertThat(exitCode).isZero();
            assertThat(apiServer.lastRequest().toString()).contains("/api/attributes?count=true");
            assertThat(out.toString()).contains("5");
        }
    }
}
