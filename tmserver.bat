@echo off
pushd "%~dp0" 

set CP="lib\mariadb-java-client-2.4.3.jar"

bin/java -cp %CP% --module-path lib com.maxprograms.tmserver.TmServer $@