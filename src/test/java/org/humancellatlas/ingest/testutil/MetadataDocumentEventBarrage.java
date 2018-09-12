package org.humancellatlas.ingest.testutil;

import java.util.HashSet;
import org.humancellatlas.ingest.state.monitor.SubmissionStateMonitor;
import org.joda.time.DateTime;

import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Queue;

public class MetadataDocumentEventBarrage {
    private Queue<MetadataDocumentTransition> queuedTransitionEvents = new PriorityQueue<>(Comparator.comparing(MetadataDocumentTransition::getDateTime));
    private Collection<MetadataDocumentTransition> transitionEvents = new HashSet<>();
    /**
     * start the barrage of events and aim it at the submission state monitor
     *
     * @param stateMonitor
     */
    public void commence(SubmissionStateMonitor stateMonitor) {
        this.prepareBarrage(1000);

        while(!queuedTransitionEvents.isEmpty()) {
            MetadataDocumentTransition nextEvent = queuedTransitionEvents.peek();
            if (nextEvent.getDateTime().isBeforeNow()) {
                nextEvent = queuedTransitionEvents.remove();
                // call a method on the monitor depending on the target state for this event
                stateMonitor
                    .notifyOfMetadataDocumentState(nextEvent.getMetadataDocumentReference(),
                                                   nextEvent.getSubmissionEnvelopeReference(),
                                                   nextEvent.getTargetState());
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