#!/bin/bash

echo "Project ${1}"
echo "Argument ${2}"

cd "${BASH_SOURCE%/*}"
cd "../../../.."
pwd

./gradlew run -DDEBUG=true -Dspring.profiles.active=test,igo -PprogramArgs=-p,$1,-o,output,-rerunReason,TEST,$2
cd ~
