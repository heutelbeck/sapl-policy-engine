#!/bin/bash
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


java --version

#for i in {1..10}
#do
  JAR_NAME=sapl-benchmark-2.0.0-SNAPSHOT.jar
	SEED=$RANDOM
	RUNS=1
	ITER=1
	PROFILE=5k

	echo "IMPROVED - $SEED"
	time java -Xms4096m -Xmx14000m -Dspring.profiles.active=$PROFILE -jar target/$JAR_NAME \
	  -index IMPROVED -iter $ITER --sapl.random.seed=$SEED --sapl.number.of.benchmark.runs=$RUNS
	

	echo "SIMPLE - $SEED"
	time java -Xms4096m -Xmx14000m -Dspring.profiles.active=$PROFILE -jar target/$JAR_NAME \
	  -index SIMPLE -iter $ITER --sapl.random.seed=$SEED --sapl.number.of.benchmark.runs=$RUNS

	#done

