#!/bin/bash

java --version

#for i in {1..10}
#do
	SEED=$RANDOM
	RUNS=1
	ITER=1

	echo "IMPROVED - $SEED"
	time java -Xms4096m -Xmx14000m -jar target/sapl-benchmark-springboot-1.0.0-SNAPSHOT.jar -index IMPROVED -iter $ITER --sapl.random.seed=$SEED --sapl.number.of.benchmark.runs=$RUNS
	

	#echo "SIMPLE - $SEED"
	#time java -Xms4096m -Xmx14000m -jar target/sapl-benchmark-springboot-1.0.0-SNAPSHOT.jar -index SIMPLE -iter $ITER --sapl.random.seed=$SEED --sapl.number.of.benchmark.runs=$RUNS

	#done

