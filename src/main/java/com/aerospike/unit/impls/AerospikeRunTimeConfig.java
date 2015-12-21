package com.aerospike.unit.impls;

import java.util.List;

import lombok.Data;

@Data
public class AerospikeRunTimeConfig {
    private int servicePort;
    private int fabricPort;
    private int infoPort;
    private int multicastPort;
    private String replicationfactor = "1";
    private String baseDirAerospike;
    private String dirForAerospikeExecutable;
    private String nameSpaceName = "test";
    private String memorySize = "64M";
    private String logFilePath;
    private List<String> nameSpaceNameList;
}
