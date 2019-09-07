@echo off
pushd "%~dp0" 
bin\java.exe --module-path lib -m tmengine/com.maxprograms.tmserver.TmServer %* 