package org.humancellatlas.ingest.util;

import java.util.HashSet;
import java.util.concurrent.ScheduledFuture;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.joda.time.DateTime;

import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class MetadataDocumentEventBarrage {
    private Queue<MetadataDocumentTransition> queuedTransitionEvents = new PriorityQueue<>(Comparator.comparing(MetadataDocumentTransition::getDateTime));
    private Collection<MetadataDocumentTransition> transitionEvents = new HashSet<>();
    private final ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(2);
    /**
     * start the barrage of events and aim it at the submission state monitor
     *
     * @param stateMonitor
     */
    public void commence(SubmissionStateMonitor stateMonitor) {
        this.prepareBarrage(3000);

        while(!queuedTransitionEvents.isEmpty()) {
            MetadataDocumentTransition nextEvent = queuedTransitionEvents.peek();
            if (nextEvent.getDateTime().isBeforeNow()) {
                nextEvent = queuedTransitionEvents.remove();
                // call a method on the monitor depending on the target state for this event
                switch (nextEvent.getTargetState()) {
                    case DRAFT:
                        stateMonitor
                            .notifyOfNewMetadataDocument(nextEvent.getMetadataDocumentReference(),
                                nextEvent.getSubmissionEnvelopeReference());
                    case VALIDATING:
                        stateMonitor.notifyOfValidatingMetadataDocument(
                            nextEvent.getMetadataDocumentReference(),
                            nextEvent.getSubmissionEnvelopeReference());
                    case VALID:
                        stateMonitor.notifyOfValidatedMetadataDocument(
                            nextEvent.getMetadataDocumentReference(),
                            nextEvent.getSubmissionEnvelopeReference(), true);
                    case INVALID:
                        stateMonitor.notifyOfValidatedMetadataDocument(
                            nextEvent.getMetadataDocumentReference(),
                            nextEvent.getSubmissionEnvelopeReference(), false);
                }
            } else {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    continue;
                }
            }
        }
    }

    public void addToBarrage(MetadataDocumentTransitionLifecycle lifecycle) {
        this.addToBarrage(lifecycle.getTransitions());
    }

    public void addToBarrage(Collection<MetadataDocumentTransition> transitionEvents) {
        this.transitionEvents.addAll(transitionEvents);
    }

    /**
     * adds Dates to each transition event to denote when they should occur.
     * Adds an offset to each date to account for the time required to compute this method
     */
    private void prepareBarrage(int offset){
        DateTime offsetDate = DateTime.now().plusMillis(offset);
        for(MetadataDocumentTransition event : transitionEvents) {
            event.setDateTime(offsetDate.plusMillis((event.getEventTime().intValue())));
        }

        this.queuedTransitionEvents.addAll(this.transitionEvents);
    }
}