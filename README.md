# Ingest State Tracking

Service for tracking the state of submissions through the ingestion process

# Testing locally

* Modify `docker-compose.yaml` to have the latest images you need. Check latest image for each repositories in [ebi-ait organization in quay.io](https://quay.io/organization/ebi-ait)

* Running, killing, stopping `docker-compose` containers
```bash
docker-compose -p st up -d
docker-compose -p st stop
docker-compose -p st kill
```

* Checking logs
```bash
docker logs -f st_core_1 
```

# Submission state diagram

```mermaid
stateDiagram
[*] --> Pending
Pending --> Draft
Draft --> MetadataValidating
MetadataValidating --> MetadataValid
MetadataValid --> Draft
MetadataValidating --> MetadataInvalid
MetadataInvalid --> Draft
MetadataValid-->GraphValidationRequested
GraphValidationRequested--> GraphValidating
GraphValidating-->GraphValid
GraphValidationRequested--> GraphValidating
GraphValidating-->GraphInvalid
GraphInvalid-->GraphValidationRequested
GraphValid --> Submitted : submit to hca?
GraphValid --> Submitted : submit to archives and get accessions?
Submitted --> Processing
Submitted --> Exporting
Processing --> Archiving
Archiving --> Archived
Archived --> Exporting : submit to hca?
Exporting --> Exported
Exported --> Draft : when a metadata is updated
Exported --> Cleanup : delete data files?
Cleanup --> Completed
Completed --> [*]
```


# State tracking - sequence diagram

## PENDING to VALID / INVALID
```mermaid
sequenceDiagram
  participant User
  participant UI
  participant Broker
  participant Core
  participant State as State Tracker
  participant Validator
  participant Staging Manager
  participant Upload
  participant Upload Area

  User->>UI: uploads test spreadsheet
  UI-->>Broker: request import
  Broker-->>Broker: parse spreadsheet <br/>and converts rows to json's 
  Broker-->>Core: creates submission entity

  Core-->>Core: creates submission entity
  Core-->>State: sends message new submission is created <br/> and it inits submissionState to PENDING
  State-->>State: creates a state machine object for submission<br/> and it inits its state to PENDING
  
  Core-->>Staging Manager: sends message to request for an upload area from Upload Service
  Staging Manager-->>Upload: requests to create and upload area directory
  Upload-->>Staging Manager: responds with upload area location
  Staging Manager-->>Core: sends message which contains upload area location
  Core-->>Core: stores upload area location to submission envelope entity

  Broker-->>Core: creates metadata entities <br/> and links them to submission and project
  Core-->>Core: creates metadata entities with initial validationState DRAFT
  Core-->>State: sends message that there new metadata in DRAFT

  Core-->>Validator: sends messages for each metadata added
  Validator-->>Core: sets metadata validationState to VALIDATING
  Core-->>State: sends a message that a metadata is set to VALIDATING

  Validator-->>Validator: validates metadata json against json schema and ontology api
  Validator-->>Core: sets metadata validationState as VALID or INVALID + validation error
  Validator-->>Core: for file metadata, sets metadata validationState as INVALID if data is not uploaded yet
  Core-->>State: sends a message that a metadata is set to VALID/INVALID

  State-->>State: checks if submission should be set to DRAFT/VALIDATING/VALID/INVALID
  State-->>Core: sets submission state to DRAFT/VALIDATING/VALID/INVALID <br/> PUT /commitValidating or <br/> PUT /commitValid ...
  
  User->>Upload Area: using hca-util cli, <br/> syncs test files from hca-util to Upload Service's upload area
  Upload Area-->>Upload: notifies that a data file is uploaded
  Upload-->>Upload: checksums the data file
  Upload-->>Core: sends message that the data file is uploaded and checksummed
  Core-->>Core: stores cloudUrl and checksum from Upload Service in file metadata 
  Core-->>Validator: sends message for file metadata that that was updated with cloudUrl
  Validator-->>Upload: for file metadata, once metadata is set to VALID and has cloudUrl,<br/> it requests for data file validation
  Upload-->>Upload: does file validation
  Upload-->>Core: sends core the file validation job result
  Core-->>Core: sets file metadata validation state to VALID/INVALID
  Core-->>State: sends a message that a file metadata is set to VALID/INVALID

  State-->>State: checks if submission should be set to DRAFT/VALIDATING/VALID/INVALID
  State-->>Core: if all metadata are VALID, sets submission state to VALID <br/> PUT /commitValid
```

## VALID -> GRAPH VALID
```mermaid
sequenceDiagram
  participant User
  participant UI
  participant Broker
  participant Core
  participant State as State Tracker
  participant GV as Graph Validator

  User->>UI: submission is Valid, requests to validate Graph
  UI-->>Core: requests for graph validation
  Core-->> GV: sends message to validate graph
  GV-->>Core: request to set to GRAPH VALIDATING
  Core-->>State: notifies of state transition to GRAPH VALIDATING
  State-->>State: checks if valid transition
  State-->>Core: requests to set submission to GRAPH VALIDATING
  Core-->>Core: sets submission to GRAPH VALIDATING

  GV-->>Core: if there are errors, sets the graph validation errors in the metadata
  GV-->>Core: sends GRAPH INVALID event
  Core-->>State: sends GRAPH INVALID event
  State-->>State: checks if valid transition
  State-->>Core: requests to set submission to GRAPH INVALID
  Core-->>Core: sets submission to GRAPH INVALID
  
  GV-->>Core: if there no errors, sends GRAPH VALID event
  Core-->>State: sends GRAPH VALID event
  State-->>State: checks if valid transition
  State-->>Core: requests to set submission to GRAPH VALID
  Core-->>Core: sets submission to GRAPH VALID
```

## GRAPH VALID -> ARCHIVED
```mermaid
sequenceDiagram
  participant User
  participant UI
  participant Core
  participant State as State Tracker
  participant Exporter
  participant Archiver
  participant DSP
  
  User->>UI: chooses to submit to the EBI archives and clicks Submit
  UI-->>Core: request to submit to the archives
  Core-->>Core: gets assay process
  Core-->>Exporter: sends messages per assay
  
  Exporter-->>Exporter: receives message for an assay
  Exporter-->>State: sends messages when a message is processing <br/> and when it's finished.
  State-->>Core: sets submission state to PROCESSING
  Exporter-->>Exporter: generates bundle manifests for an assay
  Exporter-->>State: sends a message when it finished processing an assay

  State-->>State: keeps track of all assay messages and checks if all are finished
  State-->>Core: when all assay messages finished, it sets submissionState to ARCHIVING<br/> this signals that user can start the manual process
  
  User->>Archiver: manually triggers archiving
  Archiver-->>Archiver: converts metadata
  Archiver-->>DSP: creates DSP submission and creates metadata
  User->>DSP: does manual process to upload files for sequencing runs
  User->>DSP: wait for the DSP submission to be valid and submittable
  User->>Archiver: requests to submit submission
  Archiver-->>DSP: submits submission
  Archiver-->>DSP: retrieves accessions
  Archiver-->>Core: updates metadata with retrieved accessions
  Archiver-->>Core: sets status to ARCHIVED

  Core-->>Core: generates the Export link
  UI-->>UI: displays Export button when Export links is present

```

## ARCHIVED -> EXPORTED

```mermaid
sequenceDiagram
  participant User
  participant UI
  participant State as State Tracker
  participant Core
  participant Staging Manager
  participant GCPTS as GCP Transfer Service
  participant Exporter
  participant Terra
  
  User->>UI: clicks Export button to submit to HCA
  UI-->>Core: requests for export, triggers exporting
  Core-->>Exporter: sends messages per assay
  Exporter-->>State: sends a message when a message is being processed <br/> and when it's finished.
  State-->>Core: sets submission state to EXPORTING <br/>when not all messages have finished yet
 
  Exporter-->>GCPTS: if needed to export data, triggers data file transfer
  GCPTS-->>Terra: transfers data files to Terra staging area from upload area
  Exporter-->>Exporter: waits til data transfer is complete
  Exporter-->>Core: crawls graph from assay process to donor
  Exporter-->>Terra: creates all metadata files included in the graph in the Terra staging area
  Exporter-->>Terra:  creates links.json file in the Terra staging area
  State-->>State: keeps track that all messages are processed
  State-->>Core: sets submission state to EXPORTED
  
  User->> Core: waits until submission is EXPORTED
  
```

## EXPORTED -> COMPLETED

```mermaid
sequenceDiagram
  participant User
  participant UI
  participant Core
  participant State as State Tracker
  participant Staging Manager
  
  UI-->>UI: displays Delete upload area button
  User->>UI: clicks Delete upload area button
  UI-->>Core: requests for cleanup
  Core->>State: sends message for cleanup
  State->>Core: sets submission to cleanup <br/> PUT /commitCleanup
  Core-->>Staging Manager: sends message to delete upload area
  Staging Manager-->> Upload: requests to delete the upload area 
  Staging Manager-->> Core: sets the submission to COMPLETE
  Core-->>State: sends message for COMPLETE
```