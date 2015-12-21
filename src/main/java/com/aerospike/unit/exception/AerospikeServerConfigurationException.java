package com.aerospike.unit.exception;

/**
 * Checked Exception to be used in cases such as while replacing parameters in Aerospike configurations
 */

public class AerospikeServerConfigurationException extends Exception {
    public AerospikeServerConfigurationException(String message) {
        super(message);
    }

    public AerospikeServerConfigurationException(String message, Throwable e) {
        super(message, e);
    }
}
