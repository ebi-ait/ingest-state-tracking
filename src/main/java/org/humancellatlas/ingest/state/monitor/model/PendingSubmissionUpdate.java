package org.humancellatlas.ingest.state.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.humancellatlas.ingest.model.SubmissionEnvelopeReference;
import org.humancellatlas.ingest.state.SubmissionState;

/**
 * Created by rolando on 15/02/2018.
 */
@AllArgsConstructor
@Data
public class PendingSubmissionUpdate {
    private final SubmissionEnvelopeReference envelopeReference;
    private final SubmissionState toState;

    @Override
    public boolean equals(Object obj){
        if (obj == null) {
            return false;
        }
        if (!PendingSubmissionUpdate.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final PendingSubmissionUpdate other = (PendingSubmissionUpdate) obj;

        try {
            return this.envelopeReference.getId().equals(other.getEnvelopeReference().getId())
                    && this.envelopeReference.getCallbackLocation().equals(other.getEnvelopeReference().getCallbackLocation());
        } catch (RuntimeException e) {
            return super.equals(obj);
        }
    }
}
