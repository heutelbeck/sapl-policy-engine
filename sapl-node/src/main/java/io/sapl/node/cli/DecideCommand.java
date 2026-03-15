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

import static io.sapl.node.cli.PdpSetup.ERROR_REMOTE_CONNECTION;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;

import javax.net.ssl.SSLException;

import lombok.val;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Streams authorization decisions as NDJSON to stdout. Each policy change emits
 * a new decision line. Runs until interrupted (Ctrl+C) or the stream
 * completes.
 */
@Command(name = "decide", description = "Stream authorization decisions as NDJSON", mixinStandardHelpOptions = true)
class DecideCommand implements Callable<Integer> {

    static final String ERROR_EVALUATION_FAILED = "Error: Evaluation failed: %s.";

    @Spec
    CommandSpec spec;

    @Mixin
    PdpOptions pdpOptions;

    @Override
    public Integer call() {
        val      err   = spec.commandLine().getErr();
        val      out   = spec.commandLine().getOut();
        PdpSetup setup = null;
        try {
            setup = PdpSetup.open(pdpOptions, err);
            if (setup == null)
                return 1;
            Runtime.getRuntime().addShutdownHook(new Thread(setup::shutdown));
            val pdp    = setup.pdp();
            val mapper = setup.mapper();
            val sub    = SubscriptionResolver.resolve(pdpOptions.subscriptionInput, mapper);
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
        } catch (SSLException e) {
            err.println(ERROR_REMOTE_CONNECTION.formatted(e.getMessage()));
            return 1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (RuntimeException e) {
            err.println(ERROR_EVALUATION_FAILED.formatted(e.getMessage()));
            return 1;
        } finally {
            if (setup != null)
                setup.shutdown();
        }
    }

}
