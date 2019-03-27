# pipeline-kickoff

http://plvpipetrack1.mskcc.org:8099/display/PipelineInitiation/Analysis+Pipeline+Initiation+Home

Pipeline kickoff gathers information from LIMS (using LIMS API) and file system and creates manifest files which are used by analysis pipeline. 
This code is run as a cron job retrieving recently delivered requests for which those files can be generated.

This code base is a combination of bic (`tag: v1.1.9-bic`) and rolsin (`tag: v1.25.2-roslin`) branch.

### Arguments
* `-p, PROJECT_ID`. e.g. `-p 12345_A`
* `-pipeline, PIPELINE_NAME`. e.g., `-pipeline, bic`, `-pipeline, roslin`

### Test

* unit test: roslin & bic 
* integrationTest: roslin 
* regressionTest: roslin & bic 
