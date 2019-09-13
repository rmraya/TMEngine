#!/bin/bash

cd "$(dirname "$0")/"

export CP="lib/mariadb-java-client-2.4.3.jar"

bin/java -cp $CP --module-path lib com.maxprograms.tmserver.TmServer $@