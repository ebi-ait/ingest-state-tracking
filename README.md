# Ingest State Tracking

Service for tracking the state of submissions through the ingestion process

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

# State tracking - sequence diagram

```mermaid
sequenceDiagram
  participant User
  participant Core
  participant Exporter
  participant State as State Tracker
  participant Archiver
  participant DSP

  User->>Broker: uploads test spreadsheet
  Broker-->>Core: creates  metadata entities
  Core-->> Staging Manager: requests for upload area
  Staging Manager-->> Core: returns upload area
  Core-->> Validator: requests for metadata validation
  User->> Upload Area: using hca-util cli, <br/> syncs test files from hca-util to Upload Service's upload area
  Core-->> Validator: requests for file metadata file validation
  Validator-->>Upload: requests for data file validation
  Upload -->> Upload: does file validation
  Upload -->> Core: sets file validation job result
  State -->> Core: sets submission state to VALID
  User-->>UI: chose to submit to EBI Archives and clicks Submit
  UI-->>Core: request to submit
  Core -->> Core: gets assay process
  Core->>Exporter: sends messages per assay
  Exporter->>Exporter: generates bundle manifests
  Exporter->>State: sends messages when a message is processing <br/> and when it's finished.
  State ->> Core: sets submission state to PROCESSING
  State ->> State: keeps track that all messages are processed
  State ->> Core: sets submission state to ARCHIVING
  User-->>Archiver: triggers archiving
  Archiver->>Archiver: converts metadata
  Archiver-->>DSP: creates submission and <br/> creates metadata (no data because data upload is manual)
  Archiver->>DSP: wait for the DSP submission to be valid and submittable
  Archiver->>DSP: submits submission
  Archiver-->>DSP: retrieves accession
  Archiver-->>Core: updates accessions
  Archiver-->>Core: sets status to ARCHIVED
  UI->>UI: display Export button
  User-->>UI: clicks Export button
  UI->>Core: request for export, triggers exporting
  Core->>Exporter: sends messages per assay
  Exporter->>State: sends messages when a message is being processed <br/> and when it's finished.
  State ->> Core: sets submission state to EXPORTING
  State ->> State: keeps track that all messages are processed
  State ->> Core: sets submission state to EXPORTED
  Exporter->>GCPTS: triggers data file transfer
  GCPTS->Terra: transfers data files to Terra staging area
  Exporter->>Terra: creates metadata files to the Terra staging area
  User ->> Core: polls until submission is EXPORTED, test passes!
```