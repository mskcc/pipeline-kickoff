#!/bin/bash

echo "Project ${1}"
echo "Argument ${2}"

cd "${BASH_SOURCE%/*}"
cd "../../../.."
pwd

debugMode="false"
if [ "${2}" == "debug" ] || [ "${3}" == "debug" ]; then
	debugMode="true"
	echo "Running in debug mode"
fi

./gradlew run -DDEBUG=${debugMode} -Dspring.profiles.active=dev,igo -PprogramArgs=-p,$1,-rerunReason,TEST,-o,output,$2
cd ~
