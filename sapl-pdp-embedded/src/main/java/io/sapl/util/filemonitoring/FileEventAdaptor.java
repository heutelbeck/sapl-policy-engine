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
package io.sapl.util.filemonitoring;

import java.io.File;

import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.FluxSink;

@RequiredArgsConstructor
public class FileEventAdaptor extends FileAlterationListenerAdaptor {

    private final FluxSink<FileEvent> emitter;

    @Override
    public void onFileCreate(File file) {
        emitter.next(new FileCreatedEvent(file));
    }

    @Override
    public void onFileDelete(File file) {
        emitter.next(new FileDeletedEvent(file));
    }

    @Override
    public void onFileChange(File file) {
        emitter.next(new FileChangedEvent(file));
    }

}
