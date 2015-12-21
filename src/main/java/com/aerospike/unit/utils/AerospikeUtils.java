package com.aerospike.unit.utils;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Language;
import com.aerospike.client.cluster.Node;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.task.RegisterTask;


public class AerospikeUtils {

    public static final Logger LOG = LoggerFactory.getLogger(AerospikeUtils.class);

    public static void printNodes(AerospikeClient client) {
        if (client == null) {
            LOG.warn("null client has been sent returning");
            return;
        }
        for (Node node : client.getNodes()) {
            LOG.info("node name is " + node.getAddress().getHostString());
        }
    }


    public static void registerUdf(AerospikeClient client, Policy policy, String clientPath, String serverPath,
            Language language) {
        RegisterTask task = client.register(policy, clientPath, serverPath, language);
        task.waitTillComplete();
    }


    /*
     * DeleteDir deletes directories on linuxFileSystem recursively
     * 
     * @param path of the directories
     * 
     * @return boolean true:deletion successful false:deletion unsuccessful
     */

    public static boolean deleteDir(File dir) {
        if (!dir.exists()) {
            LOG.info(dir.toString() + " directory dosent exists so cant be deleted.");
            return true;
        }
        if (dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
        }
        LOG.info(dir.toString() + "  directory is being deleted.");
        return dir.delete();
    }

    /*
     * Function to mimic Wget command
     * 
     * @param urlOfFile URL of the hosted file(tar,jar,file)
     * 
     * @param dirToDownload directory where the file will be downloaded
     * 
     * @fileNameAfterDownload fileName of the downloaded file
     * 
     * @return boolean true : file download successful
     */

    public static void wget(String urlOfFile, String dirToDownload, String fileNameAfterDownload)
            throws MalformedURLException, IOException {
        int bufferSize = 512000;
        InputStream httpIn = null;
        OutputStream fileOutput = null;
        OutputStream bufferedOut = null;
        try {
            // check the http connection before we do anything to the fs
            httpIn = new BufferedInputStream(new URL(urlOfFile).openStream());
            File f = new File(dirToDownload);
            f.mkdirs();

            LOG.info("File will be downloaded at:  " + dirToDownload);

            // prep saving the file
            fileOutput = new FileOutputStream(dirToDownload + File.separatorChar + fileNameAfterDownload);
            bufferedOut = new BufferedOutputStream(fileOutput, 1024);
            byte data[] = new byte[bufferSize];
            boolean fileComplete = false;
            int count = 0;
            while (!fileComplete) {
                count = httpIn.read(data, 0, bufferSize);
                if (count <= 0) {
                    fileComplete = true;
                } else {
                    bufferedOut.write(data, 0, count);
                }
            }
        } finally {
            IOUtils.closeQuietly(bufferedOut);
            IOUtils.closeQuietly(fileOutput);
            IOUtils.closeQuietly(httpIn);
        }
        LOG.info("File download success  at " + dirToDownload);
    }

    /*
     * Function to mimic tar -xvf command
     * 
     * @param inputTarGzFilePath input path of the tar.gz file
     * 
     * @param outputDirPathToUntar Directory where the tar.gz file will be extracted to
     * 
     * @return boolean true extraction of file from tar.gz is successful
     */

    public static void untarGzFiles(String inputTarGzFilePath, String outputDirPathToUntar) throws IOException {
        int buffer = 2048;
        FileInputStream fin = null;
        BufferedInputStream in = null;
        GzipCompressorInputStream gzIn = null;
        TarArchiveInputStream tarIn = null;
        TarArchiveEntry entry = null;
        try {
            fin = new FileInputStream(new File(inputTarGzFilePath));
            in = new BufferedInputStream(fin);
            gzIn = new GzipCompressorInputStream(in);
            tarIn = new TarArchiveInputStream(gzIn);
            LOG.info("File to untar is present at  " + inputTarGzFilePath);
            while ((entry = tarIn.getNextTarEntry()) != null) {
                String dynamicFile = outputDirPathToUntar + File.separatorChar + entry.getName();
                if (entry.isDirectory()) {
                    File f = new File(dynamicFile);
                    f.mkdirs();
                    // f.setExecutable(true, true);

                } else {
                    int count;
                    byte data[] = new byte[buffer];
                    FileOutputStream fos = new FileOutputStream(dynamicFile);
                    BufferedOutputStream dest = new BufferedOutputStream(fos, buffer);
                    while ((count = tarIn.read(data, 0, buffer)) != -1) {
                        dest.write(data, 0, count);
                    }
                    File f = new File(dynamicFile);
                    f.setExecutable(true, true);
                    f.setWritable(true, true);

                    IOUtils.closeQuietly(dest);
                    IOUtils.closeQuietly(fos);
                }
            }
        } finally {

            IOUtils.closeQuietly(tarIn);
            IOUtils.closeQuietly(gzIn);
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(fin);
        }
        LOG.info("File has been extracted successfuly at   " + outputDirPathToUntar);
    }

}
