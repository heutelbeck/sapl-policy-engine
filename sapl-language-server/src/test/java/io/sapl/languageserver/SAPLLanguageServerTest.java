package io.sapl.languageserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SAPLLanguageServerTest {

    @Test
    void testCommandLineRunner() {
        var saplLanguageServer = new SAPLLanguageServer();
        assertDoesNotThrow(saplLanguageServer::commandLineRunner);
    }
}
