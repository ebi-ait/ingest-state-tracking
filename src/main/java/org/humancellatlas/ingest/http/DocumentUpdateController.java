package org.humancellatlas.ingest.http;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.humancellatlas.ingest.messaging.model.MetadataDocumentMessage;
import org.humancellatlas.ingest.messaging.service.MessageHandler;
import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequiredArgsConstructor
public class DocumentUpdateController {
    private final @NonNull MessageHandler messageHandler;

    @RequestMapping(path = "state-updates/metadata-documents", method = RequestMethod.POST)
    public ResponseEntity metadataDocumentStateUpdate(@RequestBody MetadataDocumentMessage message){
        messageHandler.handleMetadataDocumentUpdate(message);
        return ResponseEntity.ok(ResponseEntity.EMPTY);
    }
}
