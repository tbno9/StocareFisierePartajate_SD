#!/usr/bin/env bash
# Porneste 3 noduri pe porturi diferite (n1 = coordonator), in fundal.
# Logurile ajung in logs/n1.log, logs/n2.log, logs/n3.log.
# Oprire: kill $(cat logs/*.pid)
set -euo pipefail

cd "$(dirname "$0")/.."
mvn -q package -DskipTests
mkdir -p logs

CP=target/classes
MAIN=ro.ubm.sd.node.StorageNode

start_node() {
  local id=$1 port=$2 extra=${3:-}
  java -cp "$CP" "$MAIN" --id "$id" --port "$port" --dir "data/$id" $extra \
    > "logs/$id.log" 2>&1 &
  echo $! > "logs/$id.pid"
  echo "pornit $id (pid $!) pe portul $port"
}

start_node n1 7001 --coordinator
start_node n2 7002
start_node n3 7003

sleep 1
cat logs/n1.log logs/n2.log logs/n3.log
echo
echo "Client: java -cp $CP ro.ubm.sd.client.Client --coordinator localhost:7001"