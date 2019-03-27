# pipeline-kickoff

http://plvpipetrack1.mskcc.org:8099/display/PipelineInitiation/Analysis+Pipeline+Initiation+Home

Pipeline kickoff gathers information from LIMS (using LIMS API) and file system and creates manifest files which are used by analysis pipeline. 
This code is run as a cron job retrieving recently delivered requests for which those files can be generated.

### Latest Versions
The latest release version is `1.1.9` for `BIC`, `1.25.2` for `ROSLIN`. 

**NOTE**
* Code base was inherited from `BIC` and needs refactoring and better test coverage.
* The combination of `BIC` and `ROSLIN` is in branch [`SAP-296-combine-bic-and-roslin-branch`](https://github.com/mskcc/pipeline-kickoff/tree/SAP-296-combine-bic-and-roslin-branch), which combines bic (`tag: v1.1.9-bic`) and rolsin (`tag: v1.25.2-roslin`) branch.

### Arguments
* `-p, PROJECT_ID`. e.g. `-p 12345_A`
* `-e`. run as exome
* `-f`. force generation of files even when there are errors
