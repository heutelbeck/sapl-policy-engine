#!/usr/bin/bash

if [ -z "${TRAVIS}" ]; then
    echo "Local build - set TRAVIS=true for testing travis' behavior"
    TRAVIS_BRANCH="local"
    TRAVIS_PULL_REQUEST="false"
fi

if [ "${TRAVIS_BRANCH}" == "master" ]; then
    if [ "${TRAVIS_PULL_REQUEST}" == "false" ]; then
        echo "Building master"
        mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent install sonar:sonar -Dsonar.host.url=http://sonar.ftk.de:9000  -Dsonar.login=${SONAR_TOKEN} -Dsonar.exclusions=**/xtext-gen/**/*,**/xtend-gen/**/*,**/emf-gen/**/* --batch-mode
        mvn deploy -DskipTests=true -Dmaven.javadoc.skip=true --batch-mode
    else
        echo "Building pull request"
        mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent verify --batch-mode
    fi
else
    echo "Building branch ${TRAVIS_BRANCH}"
    mvn clean org.jacoco:jacoco-maven-plugin:prepare-agent verify --batch-mode
fi
