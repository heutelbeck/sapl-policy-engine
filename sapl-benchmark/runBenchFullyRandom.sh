#!/bin/bash

SEED=$RANDOM
RUNS=300
ITER=1


echo "IMPROVED"
time java -Xms4096m -Xmx14000m -jar target/sapl-benchmark-springboot-2.0.0-SNAPSHOT.jar \
  -random -index IMPROVED -iter $ITER -sapl.random.seed=$SEED --sapl.number.of.benchmark.runs=$RUNS

echo "SIMPLE"
time java -Xms4096m -Xmx14000m -jar target/sapl-benchmark-springboot-2.0.0-SNAPSHOT.jar \
  -random -index SIMPLE -iter $ITER -sapl.random.seed=$SEED --sapl.number.of.benchmark.runs=$RUNS