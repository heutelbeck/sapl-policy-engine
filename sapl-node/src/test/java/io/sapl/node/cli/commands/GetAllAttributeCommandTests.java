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

class GetAllAttributeCommandTests {
    @Nested
    @DisplayName("argument parsing")
    class ArgumentParsingTests {

        @Test
        @DisplayName("--help produces help text and exits with code 0")
        void whenHelpThenExitZeroWithHelpText() {
            val out = new StringWriter();
            val cmd = new CommandLine(new GetAllAttributeCommand());
            cmd.setOut(new PrintWriter(out));
            val exitCode = cmd.execute("--help");
            assertThat(exitCode).isZero();
            assertThat(out.toString()).contains("getall", "--url", "--limit", "--offset");
        }
    }

    @Nested
    @DisplayName("when getall command is used")
    class WhenAttributeApiResponds {
        private static final String ATTRIBUTES = """
                [{"entity":"alice","name":"attribute.test","arguments":[],"value":"test"},\
                {"entity":null,"name":"system.mode","arguments":["prod"],"value":true}]""";

        private final StringWriter out = new StringWriter();
        private final StringWriter err = new StringWriter();
        private final CommandLine  cmd = new CommandLine(new GetAllAttributeCommand());
        private AttributeApiStub   apiServer;

        @BeforeEach
        void setUp() throws IOException {
            apiServer = new AttributeApiStub();
            cmd.setOut(new PrintWriter(out));
            cmd.setErr(new PrintWriter(err));
        }

        @AfterEach
        void tearDown() {
            apiServer.close();
        }

        @Test
        @DisplayName("then the base path is used and attribute key and value is returned")
        void thenBasePathIsReturnedAndAttributeKeyAndValueReturned() {
            apiServer.respondWith(200, ATTRIBUTES);
            val exitCode = cmd.execute("--url", apiServer.url());

            assertThat(exitCode).isZero();
            assertThat(apiServer.lastRequest().uri()).hasToString("/api/attributes");
            assertThat(out.toString()).contains(ATTRIBUTES);
        }

        @Test
        @DisplayName("then an empty response is show as empty correctly")
        void thenEmptyResponseIsReturnedCorrectly() {
            apiServer.respondWith(200, "");
            val exitCode = cmd.execute("--url", apiServer.url());

            assertThat(exitCode).isZero();
            assertThat(apiServer.lastRequest().uri()).hasToString("/api/attributes");
            assertThat(out.toString()).contains("");
        }

        @Test
        @DisplayName("then limit and offset are set correctly")
        void thenLimitAndOffsetAreSetCorrectly() {
            apiServer.respondWith(200, ATTRIBUTES);
            val exitCode = cmd.execute("--url", apiServer.url(), "--limit", "5", "--offset", "5");

            assertThat(exitCode).isZero();
            assertThat(apiServer.lastRequest().uri()).hasToString("/api/attributes?limit=5&offset=5");
        }

        @Test
        @DisplayName("then a negative limit and offset are ignored")
        void thenNegativeLimitAndOffsetAreIgnored() {
            apiServer.respondWith(200, ATTRIBUTES);
            val exitCode = cmd.execute("--url", apiServer.url(), "--limit", "-5", "--offset", "-5");

            assertThat(exitCode).isZero();
            assertThat(apiServer.lastRequest().uri()).hasToString("/api/attributes");
        }

        @Test
        @DisplayName("then a non 2xx response is handled")
        void thenNon2xxResponseIsHandled() {
            apiServer.respondWith(503, "Internal server error");
            val exitCode = cmd.execute("--url", apiServer.url());

            assertThat(exitCode).isEqualTo(1);
            assertThat(apiServer.lastRequest().uri()).hasToString("/api/attributes");
            assertThat(out.toString()).isEmpty();
            assertThat(err.toString()).contains("503");
        }
    }
}
