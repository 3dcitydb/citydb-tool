package org.citydb.web.exception;

import org.springframework.http.HttpStatus;

public class ServiceException extends Exception {
    private final HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

    public ServiceException() {
        super();
    }

    public ServiceException(String message) {
        super(message);
    }

    public ServiceException(Throwable cause) {
        super(cause);
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public String getFullMessage() {
        StringBuilder builder = new StringBuilder(getMessage());
        Throwable e = getCause();
        while (e != null) {
            builder.append(System.lineSeparator()).append("cause: ").append(e.getMessage());
            e = e.getCause();
        }
        return builder.toString();
    }

    public HttpStatus getHttpStatus() {
        return status;
    }
}
