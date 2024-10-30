#!/bin/bash


PIDS=$(pgrep -f "server-0.0.1-SNAPSHOT.jar")


if [ -z "$PIDS" ]; then 
    echo "No running instances of server-0.0.1-SNAPSHOT.jar found."
    exit 0
fi

echo "Terminating the following instances of server-0.0.1-SNAPSHOT.jar: $PIDS"
kill -15 $PIDS


sleep 2  


PIDS=$(pgrep -f "server-0.0.1-SNAPSHOT.jar")
if [ -z "$PIDS" ]; then 
    echo "All instances have been gracefully terminated."
else
    echo "Some instances could not be terminated gracefully and are still running: $PIDS"
    echo "Consider using kill -9 if they do not stop on their own."
fi
