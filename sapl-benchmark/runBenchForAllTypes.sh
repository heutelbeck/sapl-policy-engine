#!/bin/bash

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

