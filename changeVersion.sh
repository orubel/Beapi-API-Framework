#!/bin/bash

MAIN_CLASS=beapi_backend-0.1.jar

# ***********************************************
# ***********************************************

ARGS="-Xms1024m -Xmx2048m -XX:PermSize=64m -XX:MaxPermSize=128m -XX:+CMSClassUnloadingEnabled -XX:+UseConcMarkSweepGC -XX:+CMSIncrementalMode -XX:+CMSIncrementalPacing -XX:CMSIncrementalDutyCycle=10 -XX:+UseParNewGC -XX:MaxGCPauseMillis=100 -XX:MaxGCMinorPauseMillis=50 -server"

exec java $MAIN_CLASS $ARGS
