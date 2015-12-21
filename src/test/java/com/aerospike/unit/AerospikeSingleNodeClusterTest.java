package com.aerospike.unit;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.aerospike.client.*;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.unit.impls.AerospikeRunTimeConfig;
import com.aerospike.unit.impls.AerospikeSingleNodeCluster;
import com.aerospike.unit.utils.AerospikeUtils;

public class AerospikeSingleNodeClusterTest {

    private Key key;
    private AerospikeClient client;
    private String memorySize = "64M";
    private String setName = "BIGTABLE";
    private String binName = "BIGBIN";
    private String keyToBeInserted = "key1";
    public static final String luaFile = "example.lua";
    private String nameSpaceNameRandom = "kunalSpace";
    private AerospikeSingleNodeCluster cluster;
    private AerospikeRunTimeConfig runtimConfig;
    public static final Logger LOG = LoggerFactory.getLogger(AerospikeSingleNodeClusterTest.class);
    private final WritePolicy policy = new WritePolicy();

    private String getFilePathInBuildPath(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        LOG.info("Lua file Path is : " + file.getAbsolutePath());
        return file.getAbsolutePath();
    }

    @BeforeClass
    public void setup() throws Exception {
        // Instatiate the cluster with NameSpaceName , memory Size.
        // One can use the default constructor and retieve nameSpace,Memory info from cluster.getRunTimeConfiguration();
        cluster = new AerospikeSingleNodeCluster(nameSpaceNameRandom, memorySize);
        cluster.start();
        // Get the runTime configuration of the cluster
        runtimConfig = cluster.getRunTimeConfiguration();
        client = new AerospikeClient("127.0.0.1", runtimConfig.getServicePort());
        AerospikeUtils.printNodes(client);
    }


    @Test
    public void clientBinPutGetTest() throws Exception {
        key = new Key(runtimConfig.getNameSpaceName(), setName, keyToBeInserted);
        Map<String, Map<Integer, Integer>> map = new HashMap<String, Map<Integer, Integer>>();
        Map<Integer, Integer> temp = new HashMap<Integer, Integer>();
        temp.put(10, 1);
        temp.put(20, 2);
        temp.put(30, 3);
        map.put("src", temp);
        Bin bin = new Bin(binName, map);
        // policy.sendKey = true;
        client.put(policy, key, bin);
        Record result = client.get(policy, key);
        Assert.assertNotNull(result);
        Assert.assertEquals(result.getValue(binName).toString(), "{src={20=2, 10=1, 30=3}}");
    }


    @Test
    public void clientLuaRegisterAndGetTest() throws Exception {
        // Register the Lua
        AerospikeUtils.registerUdf(client, null, getFilePathInBuildPath(luaFile), luaFile, Language.LUA);
        // execute the command in Lua
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> result =
                (Map<Integer, Integer>) client.execute(null, key, "example", "readBin", Value.get(binName));
        Assert.assertEquals(result.toString(), "{src={20=2, 10=1, 30=3}}");
    }


    @AfterClass
    public void cleanup() throws Exception {
        if (cluster != null) {
            // Stop the cluster
            cluster.stop(true);
        }
        if (client != null) {
            client.close();
        }
    }
}
