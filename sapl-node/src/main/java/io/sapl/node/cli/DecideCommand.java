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
package io.sapl.node.cli;

import java.util.concurrent.CountDownLatch;

import org.springframework.boot.SpringApplication;

import io.sapl.api.pdp.PolicyDecisionPoint;
import lombok.val;
import picocli.CommandLine.Command;
import tools.jackson.databind.json.JsonMapper;

/**
 * Streams authorization decisions as NDJSON to stdout. Each policy change emits
 * a new decision line. Runs until interrupted (Ctrl+C) or the policy stream
 * completes.
 */
@Command(name = "decide", description = "Stream authorization decisions as NDJSON", mixinStandardHelpOptions = true)
class DecideCommand extends AbstractPdpCommand {

    @Override
    public Integer call() {
        val err = spec.commandLine().getErr();
        val out = spec.commandLine().getOut();

        val resolved = resolvePolicyConfiguration(err);
        if (resolved == null) {
            return 1;
        }

        val springArgs = buildSpringArgs(resolved);

        try {
            val context = bootHeadlessContext(springArgs);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> SpringApplication.exit(context)));

            val pdp    = context.getBean(PolicyDecisionPoint.class);
            val mapper = context.getBean(JsonMapper.class);
            val sub    = SubscriptionResolver.resolve(subscriptionInput, mapper);
            val latch  = new CountDownLatch(1);

            pdp.decide(sub).doOnNext(decision -> {
                out.println(mapper.writeValueAsString(decision));
                out.flush();
            }).doOnError(e -> {
                err.println(ERROR_EVALUATION_FAILED.formatted(e.getMessage()));
                latch.countDown();
            }).doOnComplete(latch::countDown).subscribe();

            latch.await();
            return 0;
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (RuntimeException e) {
            err.println(ERROR_EVALUATION_FAILED.formatted(e.getMessage()));
            return 1;
        }
    }

}
