package org.citydb.web.service;

public class OperationException extends Exception {

    public OperationException() {
        super();
    }

    public OperationException(String message) {
        super(message);
    }

    public OperationException(Throwable cause) {
        super(cause);
    }

    public OperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
