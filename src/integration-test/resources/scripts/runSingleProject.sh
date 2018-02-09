#!/bin/bash

echo "Project ${1}"
echo "Argument ${2}"

cd "${BASH_SOURCE%/*}"
cd "../../../.."
pwd

./gradlew run -Dspring.profiles.active=dev,igo,hold -PprogramArgs=-p,$1,-o,output,-rerunReason,TEST,$2
cd ~
