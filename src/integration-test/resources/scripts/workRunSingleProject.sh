#!/bin/bash

echo "Project ${1}"
echo "Argument ${2}"
cd ~/work/pipeline-kickoff-refactored

debugMode="false"
if [ "${2}" == "debug" ] || [ "${3}" == "debug" ]; then
	debugMode="true"
	echo "Running in debug mode"
fi

./gradlew run -DDEBUG=${debugMode} -Dspring.profiles.active=prod,igo -PprogramArgs=-p,$1,-o,output,-rerunReason,TEST,$2

cd ~
