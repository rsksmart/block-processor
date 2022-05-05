package co.rsk.tools.processor.TrieTests;


import java.io.*;
import java.nio.Buffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

public class CompareDisk extends Benchmark {

    public void createLogFile(String basename, String expectedItems) {
        String name = "DiskResults/" + basename;
        name = name + "-" + expectedItems;
        //name = name + "-Max_" + getMillions(Runtime.getRuntime().maxMemory());

        Date date = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
        String strDate = dateFormat.format(date);
        name = name + "-" + strDate;

        plainCreateLogFilename(name);

    }

    static final int BUFFER_SIZE = 4096; // 4KB
    static final int nodeSize = 128;
    static final int nodesPerChunk = BUFFER_SIZE / nodeSize; // = 32

    long gigas = 1_000_000_000L;
    long fileSize = 8 * gigas;
    int chunks = (int) (fileSize / BUFFER_SIZE);
    String maxStr = getMillions(fileSize);
    String inputFile;
    String outputFile;
    Path trieDBFolder = Path.of("./bigfiles");
    Path filePath = trieDBFolder;

    void setFilenames() {
        String dbName = "file-" + maxStr;


        if ((dbName != null) && (dbName.length() > 0)) {
            if ((dbName.indexOf("..") >= 0) || (dbName.indexOf("/") >= 0))
                throw new RuntimeException("sanity check");
            filePath = trieDBFolder.resolve(dbName);
        }
        inputFile = filePath.toString();
        outputFile = filePath.toString();
    }

    public void writeTest() {
        String testName = "writeTest";
        setFilenames();
        createLogFile(testName, maxStr);
        log("nodeSize: " + nodeSize);
        log("nodesPerChunk: " + nodesPerChunk);
        log("chunks: " + chunks);
        if (Files.exists(filePath)) {
            log("destination file exists");
            System.exit(1);
        }
        try (
                OutputStream outputStream = new FileOutputStream(outputFile);
        ) {
            byte[] buffer = new byte[BUFFER_SIZE];

            start(false);
            for (int i = 0; i < chunks; i++) {
                if (i % 100_000 == 0) {
                    dumpShortProgress(i, chunks);
                }
                fillBuffer(i, buffer);
                outputStream.write(buffer, 0, BUFFER_SIZE);
            }
            stop(false);
            dumpSpeedResults(chunks);
            closeLog();
        } catch (
                IOException ex) {
            ex.printStackTrace();
        }
    }

    PseudoRandom pseudoRandom = new PseudoRandom();

    void fillNodeBuffer(int i, byte[] buffer,int ofs) {
            pseudoRandom.setSeed(i);
            pseudoRandom.fillRandomBytes(buffer, ofs, nodeSize);

    }

    void fillBuffer(int i, byte[] buffer) {
        for (int p = 0; p < nodesPerChunk; p++) {
            fillNodeBuffer(i * nodesPerChunk + p,buffer,nodeSize * p);
        }
    }

    void checkBuffer(int i, byte[] buffer) {
        byte[] expectedBuffer = new byte[BUFFER_SIZE];
        fillBuffer(i, expectedBuffer);
        if (!Arrays.equals(expectedBuffer, buffer)) {
            throw new RuntimeException("File error");
        }
    }

    void checkNode(int i, byte[] buffer) {
        byte[] expectedBuffer = new byte[nodeSize];
        fillNodeBuffer(i, expectedBuffer,0);
        if (!Arrays.equals(expectedBuffer, buffer)) {
            throw new RuntimeException("File error");
        }
    }

    public void sequentialReadTest() {

        String testName = "seqReadTest";
        setFilenames();
        createLogFile(testName, maxStr);
        try (
                InputStream inputStream = new FileInputStream(inputFile);
        ) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead = -1;
            int i = 0;

            start(false);
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (i % 100_000 == 0) {
                    dumpShortProgress(i, chunks);
                }
                checkBuffer(i, buffer);
                i++;
            }
            System.out.println("Buffers read: " + i);
            stop(false);
            dumpSpeedResults(chunks);
            closeLog();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void scatteredReadTest() {

        String testName = "scatReadTest";
        setFilenames();
        createLogFile(testName, maxStr);
        Random r = new Random();
        int nodes = nodesPerChunk * chunks; // ~2^25
        try (
                RandomAccessFile raf = new RandomAccessFile(inputFile, "r");
        ) {
            byte[] buffer = new byte[nodeSize];
            int bytesRead = -1;

            start(false);
            int maxNodesRead = chunks/5; // 20% node per chunk average
            for (int i = 0; i < maxNodesRead; i++) {
                int seekNode = r.nextInt(nodes);
                long seekPos = (long) seekNode * nodeSize;
                raf.seek(seekPos);
                bytesRead = raf.read(buffer);
                if (bytesRead == -1) {
                    break;
                }
                if (i % 100_000 == 0) {
                    dumpShortProgress(i, maxNodesRead);
                }
                checkNode(seekNode, buffer);
                i++;
            }
            System.out.println("Nodes read: " + maxNodesRead);
            stop(false);
            dumpSpeedResults(maxNodesRead);
            closeLog();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    public static void main(String args[]) {
        CompareDisk c = new CompareDisk();
        //c.writeTest();
        //c.sequentialReadTest();
        c.scatteredReadTest();
    }
}
