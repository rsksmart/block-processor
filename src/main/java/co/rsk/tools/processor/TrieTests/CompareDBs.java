package co.rsk.tools.processor.TrieTests;

import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.util.FastByteComparisons;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class CompareDBs extends Benchmark {

    int maxKeys = 100_000_000;
    int keyLength = 32;
    int valueLength = 50;



    public void createLogFile(String basename,String expectedItems) {
        String name = "DBResults/"+basename;
        name = name + "-"+expectedItems;
        name = name +"-Max_"+ getMillions( Runtime.getRuntime().maxMemory());

        Date date = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
        String strDate = dateFormat.format(date);
        name = name + "-"+ strDate;

        plainCreateLogFilename(name);
    }

    public void prepare() {
        showPartialMemConsumed = false;
    }

    void deleteDirectoryRecursion(Path path) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
                for (Path entry : entries) {
                    deleteDirectoryRecursion(entry);
                }
            }
        }
        Files.delete(path);
    }
    private long getFolderSize(File folder) {
        long length = 0;
        File[] files = folder.listFiles();
        if (files==null)
            return 0;

        int count = files.length;

        for (int i = 0; i < count; i++) {
            if (files[i].isFile()) {
                length += files[i].length();
            }
            else {
                length += getFolderSize(files[i]);
            }
        }
        return length;
    }

    public void dumpTrieDBFolderSize() {
        long fs = getFolderSize(new File(trieDBFolder.toString()));
        log("TrieDB size: "+fs+" ("+getMillions(fs)+"M)") ;

        log("valueLength: "+valueLength);
        double entryLen = fs/maxKeys;
        log("db entry length  : " + entryLen);
        int overhead = (int) (entryLen-valueLength);
        log("entry overhead : " + overhead);
        log("entry overhead [%] : " + overhead*100/valueLength+"%");
    }

    KeyValueDataSource db;
    Path trieDBFolder =Path.of("./dbstore");

    public void openDB(boolean deleteIfExists, boolean abortIfExists, String dbName) {
        Path trieDBFolderPlusSize = trieDBFolder;

        if ((dbName!=null) && (dbName.length()>0)) {
            if ((dbName.indexOf("..")>=0) || (dbName.indexOf("/")>=0))
                throw new RuntimeException("sanity check");
            trieDBFolderPlusSize = trieDBFolder.resolve(dbName);
        }
        if (db==null)
            db = buildDB(trieDBFolderPlusSize,deleteIfExists,abortIfExists);
    }
    protected KeyValueDataSource buildDB(Path trieStorePath, boolean deleteIfExists, boolean abortIfExists) {
        int statesCacheSize;

        log("Database: "+trieStorePath.toAbsolutePath());
        if (abortIfExists) {
            if (Files.isDirectory(trieStorePath, LinkOption.NOFOLLOW_LINKS)) {
                throw new RuntimeException("Target trie db directory exists. Remove first");
            }

        }
        if (deleteIfExists) {
            try {
                log("deleting previous trie db");
                deleteDirectoryRecursion(trieStorePath);
                dumpTrieDBFolderSize();
            } catch (IOException e) {
                System.out.println("Could not delete database dir");
            }
        }
        KeyValueDataSource ds = LevelDbDataSource.makeDataSource(trieStorePath);
        return ds;
    }
    PseudoRandom pseudoRandom = new PseudoRandom();

    public void dumpResults(int max) {
        long elapsedTimeMs = (ended - started);
        elapsedTime = elapsedTimeMs / 1000;
        log("Elapsed time [s]: " + elapsedTime);
        if (elapsedTime!=0) {
            log("Nodes/sec: " + (max *1000L/ elapsedTimeMs));
        }
        log("Memory used after test: MB: " + endMbs);
        log("Consumed MBs: " + (endMbs - startMbs));
        showUsedMemory();
    }

    public void reads() {
        setup(true);
        start(false);
        int maxReadKeys = 1000_000;
        int dumpInterval = 10_000;

        if (maxKeys>=100_000_000) {
            maxReadKeys = 50_000; // Things get MUCH slower at this point
            dumpInterval = 5_000;
        }

        for (long i = 0; i < maxReadKeys; i++) {

            long x =TestUtils.getPseudoRandom().nextLong(maxKeys);
            pseudoRandom.setSeed(x);

            byte key[] =pseudoRandom.randomBytes(keyLength);
            byte[] expectedValue = pseudoRandom.randomBytes(valueLength);
            byte[] value = db.get(key);
            if (!FastByteComparisons.equalBytes(value,expectedValue)) {
                System.out.println("Wrong value");
                System.exit(1);
            }

            if (i % dumpInterval == 0) {
                dumpProgress(i,maxReadKeys);
            }

        }
        stop(false);
        dumpResults(maxReadKeys);
        closeLog();
    }

    public void setup(boolean read) {
        String testName;
        if (read)
            testName ="DBReadTest";
        else
            testName ="DBWriteTest";

        String maxStr = ""+ getMillions(maxKeys);
        createLogFile(testName,maxStr);

        prepare();

        String tmpDbNamePrefix = "";
        String dbName = "DB_"+maxKeys;
        dbName = dbName + "-vlen_"+valueLength;

        if (read)
            openDB(false,false,dbName);
        else
        if (tmpDbNamePrefix.length()>0) {
            // Temporary DB. Can delete freely
            openDB(true,false,dbName);
        } else
            openDB(false,true,dbName);

    }

    public void writes() {
        setup(false);

        start(true);
        for (long i = 0; i < maxKeys; i++) {

            pseudoRandom.setSeed(i);

            byte key[] =pseudoRandom.randomBytes(keyLength);
            byte[] value = pseudoRandom.randomBytes(valueLength);
            db.put(key, value);

            if (i % 100000 == 0) {
                dumpProgress(i,maxKeys);
            }

        }
        stop(true);
        dumpResults(maxKeys);
        dumpTrieDBFolderSize();
        closeLog();
    }

    public static void main (String args[]) {

        CompareDBs c = new CompareDBs();
        c.reads();
        //c.writes();
    }
}
