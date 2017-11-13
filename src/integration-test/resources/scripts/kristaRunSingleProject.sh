#!/bin/bash

echo "Project ${1}"
echo "Argument ${2}"
cd ~/sources/pipeline-kickoff/krista/pipeline_kickoff_prod/exemplar

if [ "${2}" == "debug" ] || [ "${3}" == "debug" ]; then
	echo "Running in debug mode"
	debugArg="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5007"
fi

~/jdk/bin/java ${debugArg} -cp lib/*:classes org.mskcc.kickoff.lims.CreateManifestSheet -p ${1} -o output -rerunReason TEST ${2}
cd ~
