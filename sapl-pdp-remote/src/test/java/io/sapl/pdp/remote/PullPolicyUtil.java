/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp.remote;

import org.testcontainers.images.AbstractImagePullPolicy;
import org.testcontainers.images.ImageData;
import org.testcontainers.images.ImagePullPolicy;
import org.testcontainers.utility.DockerImageName;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PullPolicyUtil {
    public ImagePullPolicy neverPull() {
        return new AbstractImagePullPolicy() {
            @Override
            protected boolean shouldPullCached(DockerImageName imageName, ImageData localImageData) {
                return false;
            }

            @Override
            public boolean shouldPull(DockerImageName imageName) {
                return false;
            }
        };
    }
}
