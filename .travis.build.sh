#!/usr/bin/bash
#
# Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

if [ -z "${TRAVIS}" ]; then
    echo "Local build - set TRAVIS=true for testing travis' behavior"
    TRAVIS_BRANCH="local"
    TRAVIS_PULL_REQUEST="false"
fi
if [ "${TRAVIS_BRANCH}" == "master" ]; then
    if [ "${TRAVIS_PULL_REQUEST}" == "false" ]; then
        echo "Building master"
        mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install org.jacoco:jacoco-maven-plugin:report sonar:sonar deploy -Dsonar.host.url=https://sonar.ftk.de -Dsonar.login=${SONAR_TOKEN} -Dsonar.exclusions=**/xtext-gen/**/*,**/xtend-gen/**/*,**/emf-gen/**/* --batch-mode
        mvn deploy -DskipTests=true -Dmaven.javadoc.skip=true --batch-mode
        echo "${DOCKERHUB_TOKEN}" | docker login -u "${DOCKERHUB_USERNAME}" --password-stdin
        mvn dockerfile:build -pl sapl-server-lt -Pdocker || exit 1
        mvn dockerfile:push -pl sapl-server-lt -Pdocker || exit 1
    else
        echo "Building pull request"
        mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent verify --batch-mode
    fi
else
    echo "Building branch ${TRAVIS_BRANCH}"
    mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent verify --batch-mode
fi