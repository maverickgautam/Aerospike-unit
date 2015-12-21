package com.aerospike.unit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.unit.impls.AerospikeRunTimeConfig;
import com.aerospike.unit.impls.AerospikeSingleNodeCluster;
import com.aerospike.unit.utils.AerospikeUtils;

public class AerospikeSingleNodeMultiNamespaceClusterTest {

    private Key key;
    private AerospikeClient client;
    private String setName = "BIGTABLE";
    private String binName = "BIGBIN";
    private String keyToBeInserted = "key1";
    private List<String> nameSpaceList;
    private AerospikeSingleNodeCluster cluster;
    private AerospikeRunTimeConfig runtimConfig;
    public static final Logger LOG = LoggerFactory.getLogger(AerospikeSingleNodeCluster.class);
    private final WritePolicy policy = new WritePolicy();

    @BeforeClass
    public void setup() throws Exception {
        // Instatiate the cluster with Multiple NameSpaceNames
        nameSpaceList = new ArrayList<>();
        nameSpaceList.add("userStore");
        nameSpaceList.add("ucm");
        nameSpaceList.add("dimData");
        cluster = new AerospikeSingleNodeCluster(nameSpaceList);
        cluster.start();
        // Get the runTime configuration of the cluster
        runtimConfig = cluster.getRunTimeConfiguration();
        client = new AerospikeClient("127.0.0.1", runtimConfig.getServicePort());
        AerospikeUtils.printNodes(client);
    }

    public void clientPutAndGetInANamespace(String nameSpace) throws Exception {
        key = new Key(nameSpace, setName, keyToBeInserted);
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
    public void testNameSpace() throws Exception {
        for (String namespace : runtimConfig.getNameSpaceNameList()) {
            clientPutAndGetInANamespace(namespace);
        }
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
