#!/bin/bash

set -e

PORT=${1:-8081}

javac -cp "lib/sqlite-jdbc-3.54.1.0.jar" -d bin src/Sac_Dos.java

java -cp "bin:lib/sqlite-jdbc-3.54.1.0.jar" Sac_Dos "$PORT"
