package com.aerospike.unit.impls;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.hadoop.util.Shell.ShellCommandExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerospike.unit.constants.AeroSpikeConstants;
import com.aerospike.unit.exception.AerospikeServerConfigurationException;
import com.aerospike.unit.exception.AerospikeServerUnableToSpawnException;
import com.aerospike.unit.interfaces.AerospikeCluster;
import com.aerospike.unit.utils.AerospikeUtils;
import com.aerospike.unit.utils.PortAvailabilityUtils;

public class AerospikeSingleNodeCluster implements AerospikeCluster {


    private InputStream customConfigHandler;
    private InputStream customStartScriptHandler;
    private InputStream customStopScriptHandler;
    private InputStream customNamespaceTemplate;
    // Dynamically created dir in /tmp.
    private StringBuilder tempBaseAerospikeDir;
    // dir inside the baseDir.
    private StringBuilder tempActualAerospikeDir;
    private StringBuilder tempAerospikeProcessDir;
    private StringBuilder logFile;
    private File fileHandlerForBaseAerospikeDir;
    private File fileHandlerForActualAerospikeDir;
    private File fileHandlerForActualAerospikeProcessDir;

    private boolean aerospikeServerRunning = false;
    private final AerospikeRunTimeConfig runTimeConfig = new AerospikeRunTimeConfig();
    private static final Logger LOGGER = LoggerFactory.getLogger(AerospikeSingleNodeCluster.class);

    /*
     * Execute command in a shell by Spawning separate process
     * @prama execstring the command to be executed
     * @param file dir the directory where the command needs to be executed
     * @param env any environment variable that needs to be set
     * @timeout time interval after which the command needs to be executed
     * @return returns the output of the executed command
     */
    public String commnadExecutor(String[] execString, File dir, Map<String, String> env, long timeout)
            throws IOException {
        ShellCommandExecutor exec = new ShellCommandExecutor(execString, dir, env, timeout);
        LOGGER.info("Command to be executed :  " + exec.toString());
        LOGGER.info("Command working Directory is :  " + dir.getAbsolutePath());
        exec.execute();
        LOGGER.info("Status of the command executed, 0 means succesful :  " + exec.getExitCode());
        LOGGER.info("Output of the command executed  :  " + exec.getOutput());
        return exec.getOutput();
    }


    /*
     * function to get the runTime Conf of the Aerospike server. As Aerospike ports and dir are formed at runtime and
     * dynamic in nature. Clients can us this class to figure out the port to connect to
     */
    public AerospikeRunTimeConfig getRunTimeConfiguration() {
        if (!aerospikeServerRunning) {
            return null;
        }
        return runTimeConfig;
    }


    /*
     * helps in figuring out free port on a machine
     * @param numFreePortsRequired number of consecutive free ports needed
     * @return LinkedHashSet<Integer> Hashset which contain free ports, size of HashSet == (numFreePortsRequired +1)
     */

    private Set<Integer> getfreeConsecutivePorts(int numFreePortsRequired) {
        Set<Integer> randomPortsSet = new LinkedHashSet<Integer>();
        boolean acquiredConsecutivePorts = false;
        do {
            randomPortsSet.clear();
            int port = PortAvailabilityUtils.randomFreePort();
            randomPortsSet.add(port);
            for (int i = 1; i <= numFreePortsRequired; i++) {
                port += 1;
                if (PortAvailabilityUtils.available(port)) {
                    randomPortsSet.add(port);
                } else {
                    break;
                }
            }
            if (randomPortsSet.size() == numFreePortsRequired + 1) {
                acquiredConsecutivePorts = true;
            }
        } while (!acquiredConsecutivePorts);
        for (Integer port : randomPortsSet) {
            LOGGER.info("Free port figured out is :  " + port);
        }
        return randomPortsSet;
    }


    private InputStream getFilePathInBuildPath(String fileName) {
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource(fileName).getFile());
        LOGGER.info("File in the classpath is   : " + file.getAbsolutePath());
        return (classLoader.getResourceAsStream(fileName));
    }

    private void listFiles(File path) {
        String[] fileList = path.getParentFile().list();
        LOGGER.info("Files present in : " + path.getParentFile().getAbsolutePath());
        for (String name : fileList) {
            LOGGER.info(name);
        }
    }


    private void deleteAndReplace(File destination, InputStream source) throws IOException {

        LOGGER.info("Destination  file to be replaced is: " + destination.getAbsolutePath());
        String content = IOUtils.toString(source, "UTF-8");
        File tempFile = new File(destination.getAbsolutePath());
        if (!tempFile.delete()) {
            throw new IOException("Not able to delete file : " + destination.getAbsolutePath());
        };
        FileUtils.writeStringToFile(tempFile, content, "UTF-8");
        listFiles(destination);
    }


    /*
     * Constructor for Spawning a Single Node nameSpace: test , MemorySize: 64M , RF: 1 , MulticastDetection: false
     * See{@linktourlhttps://www.aerospike.com/docs/guide/FAQ.html} search for "Aerospike server item"
     */

    public AerospikeSingleNodeCluster() {
        JVMShutdownHook jvmShutdownHook = new JVMShutdownHook();
        Runtime.getRuntime().addShutdownHook(jvmShutdownHook);

        // Load the custom scripts from class path
        customConfigHandler = getFilePathInBuildPath(AeroSpikeConstants.AEROSPIKECUSTOMCONFIGSCRIPT);
        customStartScriptHandler = getFilePathInBuildPath(AeroSpikeConstants.AEROSPIKECUSTOMSTARTSCRIPT);
        customStopScriptHandler = getFilePathInBuildPath(AeroSpikeConstants.AEROSPIKECUSTOMSTOPSCRIPT);
        customNamespaceTemplate = getFilePathInBuildPath(AeroSpikeConstants.AEROSPIKECUSTOMNAMESPACETEMPLATE);

        // building the base Dir, it is prefixed with systemNanoSeconds to make sure the path is unique
        tempBaseAerospikeDir =
                new StringBuilder().append(System.getProperty("java.io.tmpdir")).append(File.separator).append("Aero")
                        .append(File.separator).append(System.currentTimeMillis() / 1000);
        fileHandlerForBaseAerospikeDir = new File(tempBaseAerospikeDir.toString());
        if (fileHandlerForBaseAerospikeDir.exists()) {
            LOGGER.info(tempBaseAerospikeDir.toString() + " directory already exists, deleting it recursively");
            AerospikeUtils.deleteDir(fileHandlerForBaseAerospikeDir);
        }
        fileHandlerForBaseAerospikeDir.mkdirs();
        LOGGER.info(tempBaseAerospikeDir.toString()
                + " Dir has been freshly created, Aerospike executables will be downloaded here");
        LOGGER.info("About to get Three Random ports ");


        // Obtain three random free ports which can be bind by the aerospike server
        Set<Integer> randomPortsSet = getfreeConsecutivePorts(3);

        runTimeConfig.setBaseDirAerospike(fileHandlerForBaseAerospikeDir.getAbsolutePath());
        Integer[] ports = randomPortsSet.toArray(new Integer[0]);
        // populating the runTimeConfigurationBean class, which can be used by the client.
        runTimeConfig.setServicePort(ports[0]);
        runTimeConfig.setFabricPort(ports[1]);
        runTimeConfig.setInfoPort(ports[3]);
        // Multicast port being set to a random port, hence Multi node cluster detection not possible
        runTimeConfig.setMulticastPort(ports[2]);
        runTimeConfig.setReplicationfactor("1");
    }

    /*
     * Constructor for Spawning a Single Node
     * @param nameSpace : name of the Namespace
     * @param memorySize : memory size with which the server is spawned (64M for test , 4G default ) 
     * Value of memorySizeshould be a unsigned int, M -> MB ,G-> GB Limits RF will be set to 1 multiCastDetection is false See {@linktourl
     * https://www.aerospike.com/docs/guide/FAQ.html} search for"Aerospike server item"
     */
    public AerospikeSingleNodeCluster(String nameSpace, String memorySize) {
        this();
        runTimeConfig.setMemorySize(memorySize);
        runTimeConfig.setNameSpaceName(nameSpace);
    }

    /*
     * Constructor for Spawning a Single Node in a fast way while "local Testing"
     * @param nameSpace : name of the Namespace
     * @param memorySize : memory size with which the server is spawned (64M for test , 4G default )
     * @url can be changed to "file:///usr/share/aerospike.tgz", by maintaing a local copy of the tarBall Value of
     * memorySize should be a unsigned int, M -> MB ,G-> GB Limits RF will be set to 1, multiCastDetection is false See
     * {@linktourl https://www.aerospike.com/docs/guide/FAQ.html} search for"Aerospike server item"
     */
    public AerospikeSingleNodeCluster(String nameSpace, String memorySize, String url) {
        this();
        runTimeConfig.setMemorySize(memorySize);
        runTimeConfig.setNameSpaceName(nameSpace);
        AeroSpikeConstants.setURL(url);
    }


    /*
     * Constructor for Spawning a Single Node, with Multicast enabling Handler provided for MultiNode Cluster formation
     * @param nameSpace : name of the Namespace
     * @param memorySize : memory size with which the server is spawned (64M for test , 4G default )
     * @replicationFactor : RF to be used in case of MultiNodes
     * @enableMulticastDetection set it to true, for nodes detection in case of MultiNodes Cluster Value of memorySize
     * should be a unsigned int, M -> MB , G->GB Limits See {@linktourl https://www.aerospike.com/docs/guide/FAQ.html}
     * search for "Aerospike server item"
     */
    public AerospikeSingleNodeCluster(String nameSpace, String memorySize, int replicationFactor,
            boolean enableMulticastDetection) {
        this(nameSpace, memorySize);
        if (enableMulticastDetection) {
            runTimeConfig.setMulticastPort(AeroSpikeConstants.DEFAULTMULTICASTPORT);
        }
        runTimeConfig.setReplicationfactor(Integer.valueOf(replicationFactor).toString());
    }
    
    /*
     * Constructor for Spawning a Single Node with MultipleNameSpace
     * @param nameSpaceList : List of name of the Namespaces
     * memorySize : memory size is set to 64M , Replication Factor : 1
     */
    public AerospikeSingleNodeCluster(List<String> nameSpaceList) {
        this();
        runTimeConfig.setNameSpaceNameList(nameSpaceList);
    }



    private void prepareConfigsForAeropsike() throws Exception {

        LOGGER.info("Wget will take little time Please have patience : ");
        String baseDir = tempBaseAerospikeDir.toString();

        // Wget the aerospike tar.gz
        AerospikeUtils.wget(AeroSpikeConstants.URL, baseDir, AeroSpikeConstants.AEROSPIKEDOWNLOADEDFILENAME);
        // Untar the file
        AerospikeUtils.untarGzFiles(baseDir + File.separatorChar + AeroSpikeConstants.AEROSPIKEDOWNLOADEDFILENAME,
                baseDir);


        tempActualAerospikeDir =
                new StringBuilder(fileHandlerForBaseAerospikeDir.toString()).append(File.separator).append(
                        AeroSpikeConstants.AEROSPIKEDIRNAME);
        tempAerospikeProcessDir =
                new StringBuilder(fileHandlerForBaseAerospikeDir.toString()).append(File.separator).append(
                        AeroSpikeConstants.AEROSPIKEPROCESSDIRNAME);

        fileHandlerForActualAerospikeDir = new File(tempActualAerospikeDir.toString());
        fileHandlerForActualAerospikeProcessDir = new File(tempAerospikeProcessDir.toString());

        runTimeConfig.setDirForAerospikeExecutable(fileHandlerForActualAerospikeProcessDir.getAbsolutePath());
        try {

            String content = IOUtils.toString(customConfigHandler, "UTF-8");
            String nameSpaceTemplateContent = IOUtils.toString(customNamespaceTemplate, "UTF-8");
            
            // Replace NameSpace, Replication Factor, MEMORY SIZE M ->MB, G for GB
            content =
                    content.replaceAll(AeroSpikeConstants.NAMESPACEINCONF, runTimeConfig.getNameSpaceName())
                            .replaceAll(AeroSpikeConstants.REPFACTORINCONF, runTimeConfig.getReplicationfactor())
                            .replaceAll(AeroSpikeConstants.MEMSIZEINCONF, runTimeConfig.getMemorySize());


            if (runTimeConfig.getNameSpaceNameList() != null) {
                for (String nameSpaceName : runTimeConfig.getNameSpaceNameList()) {
                    String tempNameSpaceContent = new String(nameSpaceTemplateContent);
                    tempNameSpaceContent =
                            tempNameSpaceContent
                                    .replaceAll(AeroSpikeConstants.NAMESPACEINCONF, nameSpaceName)
                                    .replaceAll(AeroSpikeConstants.REPFACTORINCONF,
                                            runTimeConfig.getReplicationfactor())
                                    .replaceAll(AeroSpikeConstants.MEMSIZEINCONF, runTimeConfig.getMemorySize());
                    content = content.concat("\n").concat(tempNameSpaceContent);
                }
            }

            
            File tempFile =
                    new File(new StringBuilder(tempActualAerospikeDir).append(File.separatorChar)
                            .append(AeroSpikeConstants.CONFIGSCRIPT).toString());
            tempFile.delete();
            FileUtils.writeStringToFile(tempFile, content, "UTF-8");


            // delete and replace the Start and Stop scripts by custom scripts.
            deleteAndReplace(
                    new File(new StringBuilder(tempActualAerospikeDir).append(File.separatorChar)
                            .append(AeroSpikeConstants.STARTSCRIPT).toString()), customStartScriptHandler);
            deleteAndReplace(
                    new File(new StringBuilder(tempActualAerospikeDir).append(File.separatorChar)
                            .append(AeroSpikeConstants.STOPSCRIPT).toString()), customStopScriptHandler);


            // Aerospike Init is to be executed
            commnadExecutor(
                    new String[] {AeroSpikeConstants.AEROSPIKECMD, "init", "--home",
                            tempAerospikeProcessDir.toString(), "--service-port",
                            Integer.toString(runTimeConfig.getServicePort()), "--fabric-port",
                            Integer.toString(runTimeConfig.getFabricPort()), "--info-port",
                            Integer.toString(runTimeConfig.getInfoPort()), "--multicast-port",
                            Integer.toString(runTimeConfig.getMulticastPort())}, fileHandlerForActualAerospikeDir,
                    null, 0L);

            LOGGER.info("Configuring files for Aerospike complete  ");
        } catch (Exception e) {
            AerospikeUtils.deleteDir(fileHandlerForBaseAerospikeDir);
            throw new AerospikeServerConfigurationException("Some issue occured while substituting aerospike configs",
                    e);
        } finally {
            closeStreams();
            // deleteDir(fileHandlerForBaseAerospikeDir);
        }
    }


    public void start() throws Exception {

        if (aerospikeServerRunning) {
            throw new Exception("Cannot restart the aerospike server, since Aerospike Server is already running");
        }
        prepareConfigsForAeropsike();
        try {
            LOGGER.info("Aerospike Single node is about to be started  ");
            commnadExecutor(AeroSpikeConstants.CMDFORAEROSPIKESTART, fileHandlerForActualAerospikeProcessDir, null, 0L);
            commnadExecutor(AeroSpikeConstants.CMDFORAEROSPIKESTATUS, fileHandlerForActualAerospikeProcessDir, null, 0L);
            LOGGER.info("Aerospike Single node is spawning up, sleeping for 10secs ");


            // 10 secs is enough for Aerospike server to BootStrap
            Thread.sleep(AeroSpikeConstants.BOOTSTRAPTIME);
            // hunt for the cake in the server log to ensure, Server has spawned up smoothly
            logFile =
                    new StringBuilder(fileHandlerForActualAerospikeProcessDir.getAbsolutePath()).append(
                            File.separatorChar).append(AeroSpikeConstants.AEROSPIKELOG);
            runTimeConfig.setLogFilePath(logFile.toString());
            String s = FileUtils.readFileToString(new File(logFile.toString()), "UTF-8");
            if (s.indexOf("cake") == -1) {
                throw new Exception("Message cake was not found in the log Please check!!");
            } else {
                LOGGER.info("Hey cake was found aerospike server started");
            }
            // setting this flag true indicating Aerospike has started
            aerospikeServerRunning = true;
            LOGGER.info("Aerospike Runtime Configs are ");
            LOGGER.info("Service port : " + runTimeConfig.getServicePort());
            LOGGER.info("fabric port  : " + runTimeConfig.getFabricPort());
            LOGGER.info("Info port    : " + runTimeConfig.getInfoPort());
            LOGGER.info("Base Dir     : " + runTimeConfig.getBaseDirAerospike());
            LOGGER.info("Actual Dir   : " + runTimeConfig.getDirForAerospikeExecutable());
            LOGGER.info("NameSpaceName: " + runTimeConfig.getNameSpaceName());
            LOGGER.info("MemorySize   : " + runTimeConfig.getMemorySize());
            LOGGER.info("MultiCastPort: " + runTimeConfig.getMulticastPort());
            LOGGER.info("RF           : " + runTimeConfig.getReplicationfactor());
            LOGGER.info("LOg file Path: " + runTimeConfig.getLogFilePath());


        } catch (Exception e) {
            e.printStackTrace();
            closeStreams();
            commnadExecutor(AeroSpikeConstants.CMDFORAEROSPIKESTOP, fileHandlerForActualAerospikeProcessDir, null, 0L);
            AerospikeUtils.deleteDir(fileHandlerForBaseAerospikeDir);
            throw new AerospikeServerUnableToSpawnException("Some issue occured while spawning aerospike", e);
        }
    }


    private void stop() throws Exception {
        try {
            if (!aerospikeServerRunning) {
                LOGGER.info("Aerospike is not running hence Nothing to shut Down ");
                return;
            }
            LOGGER.info("Aerospike Node will be Shut Down ");
            commnadExecutor(AeroSpikeConstants.CMDFORAEROSPIKESTATUS, fileHandlerForActualAerospikeProcessDir, null, 0L);
            commnadExecutor(AeroSpikeConstants.CMDFORAEROSPIKESTOP, fileHandlerForActualAerospikeProcessDir, null, 0L);
            aerospikeServerRunning = false;
        } finally {
            // Delete the dir on the local filesystem for graceful and clean exit
            closeStreams();
            AerospikeUtils.deleteDir(fileHandlerForBaseAerospikeDir);
        }
    }


    
    public void stop(boolean cleanUp) throws Exception {
        if (cleanUp == true) {
            stop();
        } else {
            closeStreams();
            LOGGER.info("Aerospike Node will be Shut Down But BaseDir " + tempBaseAerospikeDir
                    + "  will not be deleted");
            commnadExecutor(AeroSpikeConstants.CMDFORAEROSPIKESTOP, fileHandlerForActualAerospikeProcessDir, null, 0L);
            aerospikeServerRunning = false;
        }
    }

    private void blindShutDown() throws Exception {
        try {
            LOGGER.info("Aerospike Node will be attempted for blind Shut Down ");
            commnadExecutor(AeroSpikeConstants.CMDFORAEROSPIKESTOP, fileHandlerForActualAerospikeProcessDir, null, 0L);
            aerospikeServerRunning = false;
        } finally {
            // Delete the dir on the local filesystem for graceful and clean exit
            closeStreams();
            AerospikeUtils.deleteDir(fileHandlerForBaseAerospikeDir);
        }
    }

    private class JVMShutdownHook extends Thread {
        public void run() {
            System.out.println("JVM Shutdown Hook: Thread initiated.");
            try {
                AerospikeSingleNodeCluster.this.blindShutDown();
            } catch (Exception e) {
            }
        }
    }

    private void closeStreams() {
        IOUtils.closeQuietly(customConfigHandler);
        IOUtils.closeQuietly(customStartScriptHandler);
        IOUtils.closeQuietly(customStopScriptHandler);
        customConfigHandler = null;
        customStopScriptHandler = null;
        customStartScriptHandler = null;
    }


    public static void main(String[] args) throws Exception {

        // Usage of the Class to spawn Aerospike Cluster
        // AerospikeSingleNodeCluster a = new AerospikeSingleNodeCluster();
        AerospikeSingleNodeCluster a = new AerospikeSingleNodeCluster("Other", "2G", 3, true);
        a.start();
        AerospikeRunTimeConfig runTime = a.getRunTimeConfiguration();
        System.out.println("Service port : " + runTime.getServicePort());
        System.out.println("fabric port : " + runTime.getFabricPort());
        System.out.println("Info port : " + runTime.getInfoPort());
        System.out.println("Actual Dir : " + runTime.getDirForAerospikeExecutable());
        System.out.println("Base Dir : " + runTime.getBaseDirAerospike());
        System.out.println("NameSpaceName : " + runTime.getNameSpaceName());
        System.out.println("MemorySize : " + runTime.getMemorySize());
        System.out.println("MultiCastPort : " + runTime.getMulticastPort());
        System.out.println("RF : " + runTime.getReplicationfactor());
        // a.stop();
    }
}
