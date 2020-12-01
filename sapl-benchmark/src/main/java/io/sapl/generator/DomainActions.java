/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.generator;

import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@RequiredArgsConstructor
public class DomainActions {

    private final List<String> actionList;
    private final boolean unrestrictedAccess;

    public static final DomainActions CRUD = new DomainActions(Arrays.asList("create", "read", "update", "delete"),
            false);
    public static final DomainActions READ_ONLY = new DomainActions(Collections.singletonList("read"),
            false);
    public static final DomainActions NONE = new DomainActions(Collections.emptyList(), false);


    public List<String> generateActionsForResource(String resource) {
        return getActionList().stream().map(action -> action + resource).collect(Collectors.toList());
    }


    public static List<String> generateCustomActionList(DomainData domainData) {
        int numberOfActions = domainData.getDice().nextInt(domainData.getNumberOfActions()) + 1;
        List<String> actionList = new ArrayList<>(numberOfActions);

        for (int i = 0; i < numberOfActions; i++) {
            actionList.add(String.format("action.%03d", domainData.getDice().nextInt(1000) + 1));
        }

        return new DomainActions(actionList, true).getActionList();
    }

    public static List<String> generateActionListByCount(int actionCount) {
        List<String> actionList = new ArrayList<>(actionCount);

        for (int i = 0; i < actionCount; i++) {
            actionList.add(String.format("action.%03d", i));
        }

        return actionList;
    }
}
