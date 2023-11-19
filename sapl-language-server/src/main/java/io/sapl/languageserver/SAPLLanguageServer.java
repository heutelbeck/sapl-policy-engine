/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.languageserver;

import org.eclipse.xtext.ide.server.ServerLauncher;
import org.eclipse.xtext.ide.server.ServerModule;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

/**
 * Spring boot CLI application which starts a language server for sapl policy
 * files. The language server uses standard input and output for communication.
 * Spring boot CLI application which starts a language server for sapl policy
 * files. The language server uses standard input and output for communication.
 */
@SpringBootApplication
@ComponentScan({ "io.sapl.grammar.ide.contentassist" })
public class SAPLLanguageServer {

    public static void main(String[] args) {
        SpringApplication.run(SAPLLanguageServer.class, args);
        SpringApplication app = new SpringApplication(SAPLLanguageServer.class);
        // deactivate the spring boot banner so as not to interfere with the stdio
        // communication between lsp client and
        // server
        app.setBannerMode(Banner.Mode.OFF);
        app.run(args);
    }

    @Bean
    public CommandLineRunner commandLineRunner() {
        return (args) -> ServerLauncher.launch(SAPLLanguageServer.class.getName(), args, new ServerModule());
    }
}
