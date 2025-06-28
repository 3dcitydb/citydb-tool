package org.citydb.core.cache;

public class MapStoreException extends RuntimeException {

    public MapStoreException() {
    }

    public MapStoreException(String message) {
        super(message);
    }

    public MapStoreException(Throwable cause) {
        super(cause);
    }

    public MapStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
