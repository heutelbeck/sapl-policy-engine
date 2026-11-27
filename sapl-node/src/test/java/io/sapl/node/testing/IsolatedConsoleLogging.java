/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.node.testing;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import lombok.val;

/**
 * Isolates test classes that run the node or a CLI command with the logging
 * configuration the node ships. The node then logs to the console, so this
 * extension discards the console of the test JVM while the class runs and
 * restores the logging configuration of the test classpath afterwards, so later
 * test classes are not affected.
 * <p>
 * Register it before {@code OutputCaptureExtension}: the capture then still
 * records what the node logs for assertions, but the build console stays quiet.
 */
public final class IsolatedConsoleLogging implements BeforeAllCallback, AfterAllCallback {

    private static final Namespace NAMESPACE = Namespace.create(IsolatedConsoleLogging.class);

    private record Console(PrintStream out, PrintStream err) {}

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getStore(NAMESPACE).put(Console.class, new Console(System.out, System.err));
        val discarding = new PrintStream(OutputStream.nullOutputStream(), true, StandardCharsets.UTF_8);
        System.setOut(discarding);
        System.setErr(discarding);
    }

    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        val console = context.getStore(NAMESPACE).remove(Console.class, Console.class);
        if (console != null) {
            System.setOut(console.out());
            System.setErr(console.err());
        }
        val loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        loggerContext.reset();
        new ContextInitializer(loggerContext).autoConfig();
    }

}
