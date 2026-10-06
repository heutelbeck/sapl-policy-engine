package io.sapl.node.cli.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpHeaders;

import lombok.val;
import picocli.CommandLine;
import static org.assertj.core.api.Assertions.assertThat;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@DisplayName("Base attribute commands")
class BaseAttributeCommandTests {
    @Nested
    @DisplayName("when a base attribute command is used")
    class WhenAttributeApiResponds {
        private static final String NAME = "attribute.test";
        private static final String URL  = "http://localhost:1";

        private final StringWriter out = new StringWriter();
        private final CommandLine  cmd = new CommandLine(new GetAttributeCommand());
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
        @DisplayName("then basic auth credentials are sent as basic auth header")
        void thenBasicAuthCredentialsAreSentAsBasicAuthorizationHeader() {
            cmd.execute("--url", apiServer.url(), "--name", NAME, "--basic-auth", "user:pw");
            assertThat(apiServer.lastRequest().headers().getFirst("Authorization")).isEqualTo(
                    "Basic " + Base64.getEncoder().encodeToString("user:pw".getBytes(StandardCharsets.UTF_8)));
            assertThat(apiServer.lastRequest().headers().getFirst("X-SAPL-Client")).isEqualTo("cli");
        }

        @Test
        @DisplayName("then api key auth credentials are sent as bearer")
        void thenApiKeyAuthIsSentAsBearer() {
            cmd.execute("--url", apiServer.url(), "--name", NAME, "--token", "sapl_somekey12345");
            assertThat(apiServer.lastRequest().headers().getFirst("Authorization"))
                    .isEqualTo("Bearer sapl_somekey12345");
        }

        @ParameterizedTest
        @CsvSource(nullValues = "NULL", value = { "true,  true", "TRUE,  true", "false, false", "42,    42",
                "4.2,   4.2", "text,  text" })
        @DisplayName("then literals are parsed into the matching type")
        void thenLiteralsAreParsedIntoMatchingType(String input, String expected) {
            val parsed = new GetAttributeCommand().parseLiteral(input);
            assertThat(String.valueOf(parsed)).isEqualTo(expected);
        }

        @Test
        @DisplayName("then null is parsed into the null type")
        void thenNullIsParsedIntoNullType() {
            assertThat(new GetAttributeCommand().parseLiteral("null")).isNull();
        }

        @Test
        @DisplayName("then the set url is set instead of the environment variable")
        void thenUrlOptionIsUsedInsteadOfTheEnvironmentVar() {
            val command = commandWith(Map.of("SAPL_URL", "http://localhost:1"), "--name", NAME, "--url", URL);
            assertThat(command.resolvedURL()).isEqualTo(URL);
        }

        @Test
        @DisplayName("then the environment variable is used when url parameter is missing")
        void thenEnvironmentVarIsUsedWhenUrlParameterIsMissing() {
            val command = commandWith(Map.of("SAPL_URL", "http://localhost:1"), "--name", NAME);
            assertThat(command.resolvedURL()).isEqualTo("http://localhost:1");
        }

        @Test
        @DisplayName("then the default url is used when nothing is set")
        void thenDefaultURLIsSetWhenNothingIsSet() {
            assertThat(commandWith(Map.of(), "--name", NAME).resolvedURL()).isEqualTo("http://localhost:8080");
        }

        @Test
        @DisplayName("then basic auth is sent as basic header")
        void thenBasicAuthIsSentAsBasicHeader() {
            val headers = headersOf(commandWith(Map.of("SAPL_BASIC_AUTH", "user:pw"), "--name", NAME));
            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).startsWith("Basic ");
        }

        @Test
        @DisplayName("then a token from an environment variable is set as bearer header")
        void thenTokenAsEnvironmentVariableIsSetAsBearerHeader() {
            val headers = headersOf(commandWith(Map.of("SAPL_BEARER_TOKEN", "sapl_test12345"), "--name", NAME));
            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).startsWith("Bearer sapl_test12345");
        }

        @Test
        @DisplayName("then token parameter is set instead of environment variable")
        void thenTokenParameterIsSetInsteadOfEnvironment() {
            val headers = headersOf(commandWith(Map.of("SAPL_BEARER_TOKEN", "sapl_test12345"), "--name", NAME,
                    "--token", "sapl_12345test"));
            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).startsWith("Bearer sapl_12345test");
        }

        @Test
        @DisplayName("then no authentication header is set without credentials")
        void thenNoAuthHeaderIsSetWhenCredentialsAreMissing() {
            val headers = headersOf(commandWith(Map.of(), "--name", NAME));
            assertThat(headers.getFirst(HttpHeaders.AUTHORIZATION)).isNull();
        }

        private GetAttributeCommand commandWith(Map<String, String> environment, String... args) {
            val command = new GetAttributeCommand();
            command.environment = environment::get;
            new CommandLine(command).parseArgs(args);
            return command;
        }

        private HttpHeaders headersOf(GetAttributeCommand command) {
            val headers = new HttpHeaders();
            command.authHeaders().accept(headers);
            return headers;
        }
    }
}
