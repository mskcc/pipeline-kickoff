# pipeline-kickoff

http://plvpipetrack1.mskcc.org:8099/display/PipelineInitiation/Analysis+Pipeline+Initiation+Home

Pipeline kickoff gathers information from LIMS (using LIMS API) and file system and creates manifest files which are used by analysis pipeline. 
This code is run as a cron job retrieving recently delivered requests for which those files can be generated.

Arguments:
 -p -> project id, eg. -p 12345_A
 -e -> run as exome
 -f -> force generation of files even when there are errors

Code base was inherited from BIC and needs refactoring and better test coverage. 
