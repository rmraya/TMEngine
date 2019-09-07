#!/bin/bash

cd "$(dirname "$0")/"

bin/java --module-path lib -m tmengine/com.maxprograms.tmserver.TmServer $@