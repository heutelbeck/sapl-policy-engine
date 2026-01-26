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
package io.sapl.server.lt;

import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;

@Slf4j
@EnableCaching
@SpringBootApplication
@ComponentScan("io.sapl.server")
@EnableConfigurationProperties(SAPLServerLTProperties.class)
public class SAPLServerLTApplication {

    public static void main(String[] args) {
        if (args.length == 0) {
            SpringApplication.run(SAPLServerLTApplication.class, args);
        } else {
            if ("-basicCredentials".equals(args[0])) {
                log.info("Generating new Argon2 encoded secret...");
                log.info("Key             : {}", SecretGenerator.newKey());
                val secret = SecretGenerator.newSecret();
                log.info("Secret Plaintext: {}", secret);
                val  encodedSecret = SecretGenerator.encodeWithArgon2(secret);
                log.info("Secret Encoded  : {}", encodedSecret);
            } else if ("-apiKey".equals(args[0])) {
                log.info("Generating new API Key...");
                val  key    = SecretGenerator.newKey();
                val  secret = SecretGenerator.newApiKey();
                val  apiKey = "sapl_" + key + "_" + secret;
                log.info("ApiKey Plaintext: {}", apiKey);
                log.info("ApiKey Encoded  : {}", SecretGenerator.encodeWithArgon2(apiKey));
            }
        }
    }

}
