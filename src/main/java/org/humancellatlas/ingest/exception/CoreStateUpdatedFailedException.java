package org.humancellatlas.ingest.exception;

/**
 * Created by rolando on 15/02/2018.
 */
public class CoreStateUpdatedFailedException extends Exception {
    public CoreStateUpdatedFailedException(String msg){
        super(msg);
    }

    public CoreStateUpdatedFailedException(String msg, Throwable e){
        super(msg, e);
    }

}
