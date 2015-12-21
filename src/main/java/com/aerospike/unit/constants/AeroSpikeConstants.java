package com.aerospike.unit.constants;

import lombok.Setter;

public class AeroSpikeConstants {

    public static final int BOOTSTRAPTIME = 10000;
    public static final int DEFAULTMULTICASTPORT = 9918;

    // Dir names inside the dynamic directories
    public static final String AEROSPIKEDIRNAME = "aerospike-server";
    public static final String AEROSPIKEPROCESSDIRNAME = "aerospike-process";

    // relative path of the files
    public static final String STARTSCRIPT = "./share/libexec/aerospike-start";
    public static final String STOPSCRIPT = "./share/libexec/aerospike-stop";
    public static final String CONFIGSCRIPT = "./share/etc/aerospike.conf";
    public static final String AEROSPIKELOG = "./var/log/aerospike.log";
    public static final String AEROSPIKECMD = "./bin/aerospike";

    // custom config files
    public static final String AEROSPIKECUSTOMSTARTSCRIPT = "aerospike-start";
    public static final String AEROSPIKECUSTOMSTOPSCRIPT = "aerospike-stop";
    public static final String AEROSPIKECUSTOMCONFIGSCRIPT = "aerospike.conf";
    public static final String AEROSPIKECUSTOMNAMESPACETEMPLATE = "namespace.template";

    // Params to be replaced in aerospike.conf
    public static final String NAMESPACEINCONF = "<NameSpace>";
    public static final String REPFACTORINCONF = "<RF>";
    public static final String MEMSIZEINCONF = "<MEMSIZE>";


    public @Setter static String URL = "http://www.aerospike.com/download/server/3.6.0/artifact/tgz";

    public static final String AEROSPIKEDOWNLOADEDFILENAME = "aerospike.tgz";

    public static final String[] CMDFORAEROSPIKEINIT = {AEROSPIKECMD, "init"};
    public static final String[] CMDFORAEROSPIKESTART = {AEROSPIKECMD, "start"};
    public static final String[] CMDFORAEROSPIKESTOP = {AEROSPIKECMD, "stop"};
    public static final String[] CMDFORAEROSPIKESTATUS = {AEROSPIKECMD, "status"};
}
