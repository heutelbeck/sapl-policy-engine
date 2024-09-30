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

package io.sapl.server.ce.model.setup;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
class ApplicationYml {
    private final File                file;
    private final Map<String, Object> map              = new HashMap<>();
    private boolean                   hasBeenChanged   = false;
    private static final Pattern      splitPathPattern = Pattern.compile("\\.(?![^\\[\\]]*])");

    ApplicationYml(File f) {
        this.file = f;
    }

    void initMap() throws IOException {

        if (file.exists()) {
            InputStream                                  inputStream   = Files.newInputStream(file.toPath());
            ObjectMapper                                 objectMapper  = new ObjectMapper(new YAMLFactory());
            TypeReference<LinkedHashMap<String, Object>> typeReference = new TypeReference<>() {};
            try {
                this.addNormalizedAt("", objectMapper.readValue(inputStream, typeReference));
            } catch (IOException e) {
                log.warn(file + " is an invalid yml-file. Setup wizard will create an new, empty file");

            }
        } else {
            log.info(file + " does not exist. Setup wizard will create it on save");
        }
    }

    public void persistYmlFile() throws IOException {
        if (!hasBeenChanged) {
            return;
        }
        if (file.getParentFile().mkdirs()) {
            log.info("Created directory " + file.getParent());
        }
        if (file.createNewFile()) {
            log.info("Created file " + file);
        }
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
        objectMapper.writeValue(file, map);
        hasBeenChanged = false;
    }

    /**
     * Add recursively a Map to map and normalize properties like a.b.c: d to
     * a=(b=(c=d)) This will prevent reading/writing properties that are doubled in
     * a file by different notations
     */
    @SuppressWarnings("unchecked")
    private void addNormalizedAt(String path, Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key   = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                addNormalizedAt(path + key + ".", (Map<String, Object>) value);
            } else {
                this.setAt(path + key, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public Object getAt(String path) {
        String[]            p          = splitPathPattern.split(path);
        Map<String, Object> currentMap = this.map;
        for (String key : p) {
            Object obj = currentMap.get(key);
            if (obj instanceof Map) {
                currentMap = (Map<String, Object>) obj;
            } else {
                return obj;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public void setAt(String path, Object value) {
        if (!existsAt(path) || !getAt(path).equals(value)) {
            hasBeenChanged = true;
        }
        String[]            p          = splitPathPattern.split(path);
        Map<String, Object> currentMap = map;
        for (int i = 0; i < p.length; i++) {
            String key = p[i];
            if (i == p.length - 1) {
                currentMap.put(key, value);
            } else {
                currentMap.computeIfAbsent(key, k -> new HashMap<>());
                currentMap = (Map<String, Object>) currentMap.get(key);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public boolean existsAt(String path) {
        String[]            p          = splitPathPattern.split(path);
        Map<String, Object> currentMap = this.map;
        for (String key : p) {
            Object obj = currentMap.get(key);
            if (obj != null) {
                if (obj instanceof Map) {
                    currentMap = (Map<String, Object>) obj;
                } else {
                    return true;
                }
            } else {
                return currentMap.containsKey(key);
            }
        }
        return false;
    }

}
