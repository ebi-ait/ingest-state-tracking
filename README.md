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

* Modify `docker-compose.yaml` to have the latest images you need.

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