package com.aerospike.unit.exception;

/**
 * Checked Exception to be used in cases such as Aeorospike start commands fails to spawn the aerospike server
 */

public class AerospikeServerUnableToSpawnException extends Exception {
    public AerospikeServerUnableToSpawnException(String message) {
        super(message);
    }

    public AerospikeServerUnableToSpawnException(String message, Throwable e) {
        super(message, e);
    }
}
