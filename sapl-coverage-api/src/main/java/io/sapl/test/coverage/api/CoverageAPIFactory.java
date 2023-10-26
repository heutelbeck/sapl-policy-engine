/*
 * Streaming Attribute Policy Language (SAPL) Engine
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.test.coverage.api;

import java.nio.file.Path;

import lombok.experimental.UtilityClass;

/**
 * Factory for {@link CoverageHitReader} and {@link CoverageHitRecorder}.
 */
@UtilityClass
public class CoverageAPIFactory {

    /**
     * Constructs a CoverageHitRecorder implementation.
     * 
     * @param basedir where to find the hit files
     * @return {@link CoverageHitReader}
     */
    public static CoverageHitReader constructCoverageHitReader(Path basedir) {
        return new CoverageHitAPIFile(basedir);
    }

    /**
     * Constructs a CoverageHitRecorder implementation and create empty
     * Coverage-Hit-Files if they don't exist
     * 
     * @param basedir where to write the hit files
     * @return {@link CoverageHitRecorder}
     */
    public static CoverageHitRecorder constructCoverageHitRecorder(Path basedir) {
        var recorder = new CoverageHitAPIFile(basedir);
        recorder.createCoverageHitFiles();
        return recorder;
    }

}
