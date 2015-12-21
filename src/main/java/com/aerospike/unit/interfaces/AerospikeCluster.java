package com.aerospike.unit.interfaces;


public interface AerospikeCluster {
    public void start() throws Exception;

    public void stop(boolean cleanUp) throws Exception;
}
