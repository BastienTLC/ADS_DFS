#!/bin/bash

# Get the number of nodes from the first argument
NUM_NODES=$1

# Check if the number of nodes is provided
if [ -z "$NUM_NODES" ]; then
  echo "Usage: $0 <number_of_nodes>"
  exit 1
fi

# Path to the JAR file
JAR_PATH="server/target/server-0.0.1-SNAPSHOT.jar"

# Start the bootstrap instance if there's at least one node
if [ "$NUM_NODES" -ge 1 ]; then
  echo "Starting the bootstrap instance..."
  java -jar $JAR_PATH --bootstrap &
fi

sleep 5

# Start additional instances if there are more than one node
for ((i=2; i<=NUM_NODES; i++)); do
  echo "Starting instance $i without specific arguments..."
  java -jar $JAR_PATH &
  sleep 1
done

# Wait for all background processes to finish
wait