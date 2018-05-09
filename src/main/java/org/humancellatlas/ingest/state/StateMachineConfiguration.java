package org.humancellatlas.ingest.state;

import lombok.AllArgsConstructor;
import lombok.NonNull;
import org.humancellatlas.ingest.messaging.Constants;
import org.humancellatlas.ingest.state.monitor.util.BundleTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.EnumStateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineConfigurationConfigurer;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.guard.Guard;
import org.springframework.statemachine.persist.StateMachineRuntimePersister;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.humancellatlas.ingest.state.MetadataDocumentInfo.*;
import static org.humancellatlas.ingest.state.SubmissionEvent.*;
import static org.humancellatlas.ingest.state.SubmissionState.*;

/**
 * Javadocs go here!
 *
 * @author tburdett
 * @date 26/11/2017
 */
@Configuration
@EnableStateMachineFactory
@AllArgsConstructor
public class StateMachineConfiguration extends EnumStateMachineConfigurerAdapter<SubmissionState, SubmissionEvent> {
    private final @Autowired StateMachineRuntimePersister<SubmissionState, SubmissionEvent, String> stateMachineRuntimePersister;

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void configure(StateMachineStateConfigurer<SubmissionState, SubmissionEvent> states) throws Exception {
        states.withStates()
                .initial(PENDING)
                .state(DRAFT)
                .state(VALIDATING)
                .state(VALID)
                .state(INVALID)
                .junction(VALIDATION_STATE_EVAL_JUNCTION)
                .state(SUBMITTED)
                .junction(PROCESSING_STATE_EVAL_JUNCTION)
                .state(PROCESSING)
                .state(CLEANUP)
                .end(COMPLETE);
    }

    public void configure(StateMachineTransitionConfigurer<SubmissionState, SubmissionEvent> transitions) throws Exception{
        transitions
            .withExternal()
                .source(PENDING).target(DRAFT)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
            /* draft, validating, valid or invalid? */
            .withExternal()
                .source(DRAFT).target(VALIDATION_STATE_EVAL_JUNCTION)
                .action(addOrUpdateContent())
                .event(DOCUMENT_PROCESSED)
                .and()
            .withExternal()
                .source(VALIDATING).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
            .withExternal()
                .source(VALID).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
            .withExternal()
                .source(INVALID).target(VALIDATION_STATE_EVAL_JUNCTION)
                .event(DOCUMENT_PROCESSED)
                .action(addOrUpdateContent())
                .and()
            .withJunction()
                .source(VALIDATION_STATE_EVAL_JUNCTION)
                .first(INVALID, documentsInvalidGuard())
                .then(VALIDATING, documentsValidatingGuard())
                .then(VALID, allValidGuard())
                .last(DRAFT)
                .and()
            /* valid -> submitted */
            .withExternal()
                .source(VALID).target(SUBMITTED)
                .event(SUBMISSION_REQUESTED)
                .and()
            /* still processing or complete? */
            .withExternal()
                .source(SUBMITTED).target(PROCESSING_STATE_EVAL_JUNCTION)
                .event(BUNDLE_STATE_UPDATE)
                .action(addOrUpdateBundleContent())
                .and()
            .withExternal()
                .source(PROCESSING).target(PROCESSING_STATE_EVAL_JUNCTION)
                .event(BUNDLE_STATE_UPDATE)
                .action(addOrUpdateBundleContent())
                .and()
            .withJunction()
                .source(PROCESSING_STATE_EVAL_JUNCTION)
                .first(PROCESSING, stillProcessingGuard())
                .last(CLEANUP)
                .and()
            /* Processing -> cleanup -> complete*/
            .withExternal()
                .source(PROCESSING).target(CLEANUP)
                .event(CLEANUP_STARTED)
                .and()
            .withExternal()
                .source(PROCESSING).target(SUBMITTED)
                .event(PROCESSING_FAILED)
                .and()
            .withExternal()
                .source(CLEANUP).target(COMPLETE)
                .event(ALL_TASKS_COMPLETE);

    }

    private Guard<SubmissionState, SubmissionEvent> documentsInvalidGuard() {
        return context -> {
            Map<String, MetadataDocumentState> docMap = Collections.synchronizedMap(getMetadataDocumentTrackerFromContext(context));

            for (Object key : docMap.keySet()) {
                if (key.getClass() != String.class) {
                    // extra content somehow?
                    log.error("An extended state key was fubared");
                    return false;
                }
                else {
                    String documentId = (String) key;
                    Object value = docMap.get(documentId);
                    if (value.getClass() != MetadataDocumentState.class) {
                        // extra content somehow?
                        log.error("An extended state value was fubared");
                        return false;
                    }
                    else {
                        MetadataDocumentState documentState = (MetadataDocumentState) value;
                        log.debug(String.format("Testing content from extended state. Document tracker: { %s : %s }",
                            documentId,
                            documentState));
                        if (documentState.equals(MetadataDocumentState.INVALID)) {
                            return true;
                        }
                    }
                }
            }

            // check if all documents attached to the current state engine extended context are valid
            return false;
        };
    }

    private Guard<SubmissionState, SubmissionEvent> documentsValidatingGuard() {
        return context -> {
            Map<String, MetadataDocumentState> docMap = Collections.synchronizedMap(getMetadataDocumentTrackerFromContext(context));

            for (Object key : docMap.keySet()) {
                if (key.getClass() != String.class) {
                    // extra content somehow?
                    log.error("An extended state key was fubared");
                    return false;
                }
                else {
                    String documentId = (String) key;
                    Object value = docMap.get(documentId);
                    if (value.getClass() != MetadataDocumentState.class) {
                        // extra content somehow?
                        log.error("An extended state value was fubared");
                        return false;
                    }
                    else {
                        MetadataDocumentState documentState = (MetadataDocumentState) value;
                        log.debug(String.format("Testing content from extended state. Document tracker: { %s : %s }",
                            documentId,
                            documentState));
                        if (documentState.equals(MetadataDocumentState.VALIDATING)) {
                            return true;
                        }
                    }
                }
            }

            // check if all documents attached to the current state engine extended context are valid
            return false;
        };
    }


    private Guard<SubmissionState, SubmissionEvent> allValidGuard() {
        return context -> {
            Map<String, MetadataDocumentState> docMap = Collections.synchronizedMap(getMetadataDocumentTrackerFromContext(context));

            return docMap.entrySet().size() == 0;
        };
    }

    private Action<SubmissionState, SubmissionEvent> addOrUpdateContent() {
        return context -> {
            // retrieve the id of the document
            String documentId = context.getMessageHeaders().get(DOCUMENT_ID, String.class);

            // retrieve the state of the document
            MetadataDocumentState documentState =
                    context.getMessageHeaders().get(DOCUMENT_STATE, MetadataDocumentState.class);

            // add the document and it's state to the extended context
            log.debug(String.format("Adding content to extended state. Document tracker: { %s : %s }",
                                    documentId,
                                    documentState));
            Map<String, MetadataDocumentState> metadataDocumentTracker = getMetadataDocumentTrackerFromContext(context);

            if(metadataDocumentTracker == null) {
                // add the metadata document state map
                metadataDocumentTracker = new HashMap<>();
                context.getExtendedState().getVariables().put(Constants.METADATA_DOCUMENT_TRACKER, metadataDocumentTracker);
            }

            if(! documentState.equals(MetadataDocumentState.VALID)) {
                metadataDocumentTracker.put(documentId, documentState);
            } else {
                if ( metadataDocumentTracker.containsKey(documentId)){
                    metadataDocumentTracker.remove(documentId);
                }
            }
        };
    }

    private Guard<SubmissionState, SubmissionEvent> stillProcessingGuard() {
        return context -> {
            BundleTracker bundleTracker = getBundleTrackerFromContext(context);
            return ! bundleTracker.bundlesCompleted();
        };
    }
    private Action<SubmissionState, SubmissionEvent> addOrUpdateBundleContent() {
        return context -> {
            String bundleableProcessId = context.getMessageHeaders().get(DOCUMENT_ID, String.class);
            MetadataDocumentState bundleProcessState = context.getMessageHeaders().get(DOCUMENT_STATE, MetadataDocumentState.class);

            BundleTracker bundleTracker = (BundleTracker) context.getExtendedState().getVariables().get(Constants.BUNDLE_TRACKER);
            // is this the first bundle notification event? if so, initialize the bundleTracker
            if(bundleTracker == null) {
                String envelopeUuid = context.getMessageHeaders().get(ENVELOPE_UUID, String.class);
                int numBundlesExpected = context.getMessageHeaders().get(BUNDLES_TOTAL_EXPECTED, Integer.class);

                bundleTracker = new BundleTracker(numBundlesExpected, envelopeUuid);
                context.getExtendedState().getVariables().put(Constants.BUNDLE_TRACKER, bundleTracker);
            }

            if(bundleProcessState.equals(MetadataDocumentState.COMPLETE)
                    && bundleTracker.getBundleableProcessStateMap().get(bundleableProcessId).equals(MetadataDocumentState.PROCESSING)) {
                bundleTracker.completedBundle(bundleableProcessId);
            } else {
                bundleTracker.processingAssay(bundleableProcessId);
            }
        };
    }

    private Map<String, MetadataDocumentState> getMetadataDocumentTrackerFromContext(StateContext<SubmissionState, SubmissionEvent> context) {
        return (Map<String, MetadataDocumentState>) context.getExtendedState().getVariables().get(Constants.METADATA_DOCUMENT_TRACKER);
    }

    private BundleTracker getBundleTrackerFromContext(StateContext<SubmissionState, SubmissionEvent> context) {
        return (BundleTracker) context.getExtendedState().getVariables().get(Constants.BUNDLE_TRACKER);
    }
}
