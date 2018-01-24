package org.humancellatlas.ingest.util;

import lombok.Data;
import org.humancellatlas.ingest.model.MetadataDocumentReference;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.MetadataDocumentState;

import java.util.*;

@Data
public class MetadataDocumentTransitionLifecycle {
    private Collection<MetadataDocumentTransition> transitions = new ArrayList<>();
    private MetadataDocumentReference documentReference;
    private SubmissionEnvelopeReference envelopeReference;

    private MetadataDocumentTransitionLifecycle() {
    }

    public static class Builder {
        private MetadataDocumentReference documentReference;
        private SubmissionEnvelopeReference envelopeReference;
        private List<MetadataDocumentState> stateTransitions;

        public Builder(MetadataDocumentReference documentReference, SubmissionEnvelopeReference envelopeReference) {
            this.documentReference = documentReference;
            this.envelopeReference = envelopeReference;
            this.stateTransitions = new ArrayList<>();
        }

        public MetadataDocumentTransitionLifecycle.Builder addStateTransition(MetadataDocumentState state) {
            this.stateTransitions.add(state);
            return this;
        }

        public MetadataDocumentTransitionLifecycle build() {
            MetadataDocumentTransitionLifecycle transitionLifecycle = new MetadataDocumentTransitionLifecycle();
            transitionLifecycle.setDocumentReference(this.documentReference);
            transitionLifecycle.setEnvelopeReference(this.envelopeReference);
            transitionLifecycle.setTransitions(transitionLifecycle.generateMetadataDocumentTransitionLifecycle(this.documentReference, this.envelopeReference, this.stateTransitions, 15000));
            return transitionLifecycle;
        }
    }

    /**
     * Given an ordered collection of states, generates a corresponding ordered collection of state transitions occuring
     * at random intervals throughout lifeCycleDuration
     *
     * @param documentReference
     * @param envelopeReference
     * @param states
     * @param lifeCycleDuration
     * @return
     */
    public Collection<MetadataDocumentTransition> generateMetadataDocumentTransitionLifecycle(MetadataDocumentReference documentReference, SubmissionEnvelopeReference envelopeReference, List<MetadataDocumentState> states, long lifeCycleDuration) {
        List<Long> transitionTimes = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            transitionTimes.add((long) new Random().nextDouble() * lifeCycleDuration);
        }
        Collections.sort(transitionTimes);

        Collection<MetadataDocumentTransition> transitions = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            transitions.add(new MetadataDocumentTransition(documentReference, envelopeReference, transitionTimes.get(i), null, states.get(i)));
        }
        return transitions;
    }
}
