#!/bin/bash

source "${BASH_SOURCE%/*}/utils.sh"

#arguments=("noArg")
arguments=("noArg" "-noPortal" "-f" "-exome" "-s")

init() {
	jdk8=~/jdk
	java8="${jdk8}/bin/java"

    cd "${BASH_SOURCE%/*}"
    cd "../../../../.."
    echo "Project directory: ${projectDir}"
    pwd

    testDir="$(pwd)/test"

	echo "Clearing test directory ${testDir}"
	find ${testDir} -mindepth 1 -delete

	expectedPath="${testDir}/expectedOutput"
	mkdir -p ${expectedPath}

	actualPath="${testDir}/actualOutput"
	mkdir -p ${actualPath}

	prodKickoff=~/krista/pipeline_kickoff_prod
	prodTestKickoff="${testDir}/pipeline_kickoff_prod/exemplar"

	currentKickoff="pipeline-kickoff"
	currentTestKickoff="${testDir}/pipeline-kickoff"

	testResults="${testDir}/testResults"
	failingDir="${testResults}/failing"
	mkdir -p ${failingDir}

	succeededProjectsList="${testResults}/succeededProjects.txt"

	archivePath=~/testIfs/projects/BIC/archive/
	echo "Succeeded projects path: ${succeededProjectsList}"
}

getArgName() {
	echo "${1//[![:alnum:]]}"
}

getArgToPass() {
	if [ "$1" == "noArg" ]; then
		echo  ""
	else
		echo "$1"
	fi
}

getOutputPath() {
	argName=$(getArgName $3)
	echo "${1}/${2}/${argName}"
}

runTrunk() {
	header3 "Running [PROD] version of Pipeline Kickoff for project ${1} with argument $2"
	
	echo "Changing directory to ${prodTestKickoff}"
	cd ${prodTestKickoff}
	outputPath=$(getOutputPath $expectedPath $1 $2)
	echo "Output path: ${outputPath}"
	mkdir -p ${outputPath}
	argToPass=$(getArgToPass $2)
	echo "Argument passed: ${argToPass}"
	${java8} -cp lib/*:classes org.mskcc.kickoff.lims.CreateManifestSheet -p ${1} -o ${outputPath} -rerunReason TEST ${argToPass}
	cd ~
}

runCurrent() {
	header3 "Running [CURRENT] version of Pipeline Kickoff for project ${1} with argument $2"
	echo "Chaging directory to ${currentTestKickoff}"
	cd ${currentTestKickoff}
	outputPath=$(getOutputPath $actualPath $1 $2)
	echo "Output path: ${outputPath}"
	mkdir -p ${outputPath}
	argToPass=$(getArgToPass $2)
	echo "Argument passed: ${argToPass}"
	./gradlew run -Dspring.profiles.active=test,igo -PprogramArgs=-p,${1},-o,${outputPath},-rerunReason,TEST,${argToPass}
	#${java8} -cp .:libs/*:build/classes/main:build/resources/main -Dspring.profiles.active=dev org.mskcc.kickoff.lims.CreateManifestSheet -p ${1} -o ${outputPath} -rerunReason TEST ${argToPass}
	cd ~
}

runTest() {
	header3 "Running [TEST] comparing trunk and current for project $1 with argument $2"
	echo "Changing directory to ${currentTestKickoff}"
	cd ${currentTestKickoff}
	actual=$(getOutputPath $actualPath $1 $2)
	expected=$(getOutputPath $expectedPath $1 $2)
	echo "Actual output path: $actual"
	echo "Expected output path: $expected"
	./gradlew integrationTest -Dspring.profiles.active=test,igo -Darg=${2} -Dproject=${1} -DexpectedOutput=${expected} -DactualOutput=${actual} -DfailingOutputPath=${failingDir} -DsucceededProjectsList=${succeededProjectsList}
	#${java8} -cp .:libs/*:build/classes/main:build/classes/integrationTest:build/resources/integrationTest -Dspring.profiles.active=dev -Darg=${2} -Dproject=${1} -DexpectedOutput=${expected} -DactualOutput=${actual} -DfailingOutputPath=${failingDir} -DsucceededProjectsList=${succeededProjectsList} org.junit.runner.JUnitCore org.mskcc.kickoff.characterisationTest.RegressionTest
	cd ~
}

copySourceCode() {
    echo "Copying prod source code from: ${prodKickoff} to test directory: ${testDir}"
    rsync -az --exclude '.*' ${prodKickoff} "${testDir}/"

    echo "Copying current kickoff source code from: ${currentKickoff} to test directory: ${testDir}"
    rsync -az --exclude '.*' ${currentKickoff} "${testDir}/"
}

clearOutputPaths() {
	if [ ${forceTrunk} = "true" ]; then
		find ${expectedPath} -mindepth 1 -delete
	fi
	find ${actualPath} -mindepth 1 -delete
	rm -f ${failedProjectsList}
	rm -f ${succeededProjectsList}
}

clearArchivePath() {
	echo "Clearning archive path ${archivePath}"
	find ${archivePath} -mindepth 1 -delete
}

printResults() {
	allSucceeded="true"
	echo "Test results"
		IFS=$'\n' read -d '' -r -a succeededProjects < "${succeededProjectsList}"
		for project in "${projects[@]}"; 
		do
			for arg in "${arguments[@]}";
			do
				projectWithArg="${project}${arg}"
				if [[ "${succeededProjects[@]}" =~ "${projectWithArg}" ]]; then
					success "$project with arg $arg"
				else
					error "$project with arg $arg"
					allSucceeded="false"
				fi
			done
		done

	if [ ${allSucceeded} == "false" ]; then
		echo "For full information about failed tests visit: ${failingDir}"	
	fi
}


forceTrunk="false"
if [ $# -gt 0 ] && [ $1 = "force" ]; then
	forceTrunk="true"
fi	
init $1

projects=(
"01234_EWA" # not exising project
"02756_B" # !BicAutorunnable, Recipe WholeExome, Xenograft
"03498_D" # Canno be run through Create manifest sheet
"04298_C" # Recipe WholeGenomeSeq
"04298_D" # !manual Demux
"04495" # manual Demux
"04657_D" # ChIPSeq recipe
"04919_G" # Exemplar Sample status Failed-Complete
"05257_AX" # investigator patient IDs are problematic
"05372_B" # Request name *PACT*
"05500_AZ"  # !BicAutorunnable && "NOT_AUTORUNNABLE" in Readme
"05514_I" # IMPACT bait set
"05583_F" #pairing changes
"05600" # No Status Sequence Analysis QC
"05667_AB"  #pairing changes
"05667_AT"  #pairing changes
"05667_AW"  #pairing changes
"05667_AY"
"05684_D" # KK- NimlegenHybridizationProtocol1
"05737_R" # HEMEPACT_v3 bait set, species in Xenograft
"05816_AA"
"05873_H" # Failed Sequence Analysis QC
"05971_G" # IMPACT bait set, species in Xenograft
"06049_I"  #pairing changes
"06049_R"  #pairing changes
"06049_U"  #pairing changes
"06208_D" # Agilient Capture KAPA Libary
"06259_B"
"06362" #no sample level qc
"06477_E" # !KAPAAgilentCaptureProtocol2, very slow project
"06507" # Request with 2 samples with same name
"06507_D" # rename FASTQ
"06507_E"
"06836_E" # IMPACT bait set, two samples are failed in post process QC
"06907_J"
"06912_B" # Failed Reprocess Sequence Analysis QC
"06938_M" # Exemplar Sample status Failed-Complete
"06990_E"
"07037_O"
"07165_D"
"07275" # germline-no pipeline run
"07306_D" # Request with 2 samples with same name
"07323_F"  #pairing changes
"07372_B"
"07437_B" # BR7 and BR11 match each other, neither one matches corresponding DMP normal
"07473" # Under review Sequence Analysis QC
"07507_B" # no recipe in the sample sheet
"07520"
"07527_B"
"08192_E" # no tumor
)

projectsList=$(printf ",%s" "${projects[@]}")

header1 "Running Pipeline Kickoff tests for projects ${projectsList:1}"

copySourceCode

echo "Clearing output paths: ${expectedPath} and ${actualPath}"
clearOutputPaths

for project in ${projects[*]}
do
	for arg in "${arguments[@]}"
	do
		header2 "Running test for project ${project} with argument ${arg}"
		clearArchivePath
		expectedDir=$(getOutputPath $expectedPath $project $arg)
		echo "Expected dir: ${expectedDir}"
		if [ ${forceTrunk} = "false" ] && [ -d ${expectedDir} ]; then
			echo "Skipping running trunk as output for project ${project} already exists"
			echo "To force always running trunk even when trunk output is already generated pass 'force' argument to script"
		else
			runTrunk $project $arg
		fi
		runCurrent $project $arg
		runTest $project $arg
	done
done

printResults
