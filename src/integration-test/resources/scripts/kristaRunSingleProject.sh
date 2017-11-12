#!/bin/bash

jdk8="/home/kristakaz/jdk/jdk1.8.0_121"
java8="${jdk8}/bin/java"
echo "Project ${1}"
echo "Argument ${2}"
cd ~/krista/pipeline_kickoff_prod/exemplar

if [ "${2}" == "debug" ] || [ "${3}" == "debug" ]; then
	echo "Running in debug mode"
	debugArg="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5008"
fi

${java8} ${debugArg} -cp lib/*:classes org.mskcc.kickoff.lims.CreateManifestSheet -p ${1} -o output -rerunReason TEST ${2}
cd ~
