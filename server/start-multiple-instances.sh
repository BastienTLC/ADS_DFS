#!/bin/bash

# Get the number of nodes from the first argument
NUM_NODES=$1
# Get m value from second argument
M_VALUE=$2

# Check if the number of nodes is provided
if [ -z "$NUM_NODES" ] || [ -z "$M_VALUE" ]; then
  echo "Usage: $0 <number_of_nodes> <m_value>"
  exit 1
fi

# Path to the JAR file
JAR_PATH="server/target/server-0.0.1-SNAPSHOT.jar"

# Start the bootstrap instance if there's at least one node
if [ "$NUM_NODES" -ge 1 ]; then
  echo "Starting the bootstrap instance..."
  java -jar $JAR_PATH --bootstrap --mValue=$M_VALUE &
fi

sleep 5

# Start additional instances if there are more than one node
for ((i=2; i<=NUM_NODES; i++)); do
  echo "Starting instance $i ..."
  java -jar $JAR_PATH --mValue=$M_VALUE &
  sleep 5
done

# Wait for all background processes to finish
wait