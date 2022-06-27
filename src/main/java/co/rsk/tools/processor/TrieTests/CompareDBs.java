package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.TrieTests.DataSources.DataSourceWithHeap;
import co.rsk.tools.processor.TrieTests.bahashmaps.AbstractByteArrayHashMap;
import co.rsk.tools.processor.TrieTests.dbutils.FileMapUtil;
import co.rsk.tools.processor.TrieTests.DataSources.DataSourceWithRefHeap;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.util.FastByteComparisons;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class CompareDBs extends Benchmark {
    enum Test {
        readTest,
        writeTest
    }
    enum Database {
        LevelDB,
        MemoryMappedByteArrayRefHeap
    }

    static Test test = Test.readTest;
    boolean keyIsValueHash =true;
    static Database database = Database.MemoryMappedByteArrayRefHeap;

    int maxKeys = 100_000_000; //100_000_000;
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

                System.out.println("Target trie db directory exists. enter 'del' to delete it and continue");

                // Enter data using BufferReader
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(System.in));

                // Reading data using readLine
                String cmd = null;
                try {
                    cmd = reader.readLine();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if ((cmd==null) || (!cmd.equals("del")))
                    throw new RuntimeException("Target trie db directory exists. Remove first");
                deleteIfExists = true;
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

        log("Database class: " + database.toString());

        KeyValueDataSource dsDB =null;
        if (database== Database.LevelDB) {
            //createLevelDBDatabase();
            dsDB = LevelDbDataSource.makeDataSource(trieStorePath);
        } else { // MemoryMappedByteArrayRefHeap
            int maxNodeCount = 32*1000*1000; // 32 Million nodes -> 128 Mbytes of reference cache
            maxNodeCount = maxKeys*2;
            log("beHeap:maxNodeCount: "+maxNodeCount);
            long beHeapCapacity =64L*1000*1000*1000; // 64 GB
            beHeapCapacity =1L*maxNodeCount*valueLength; // 1 MB
            log("beHeap:Capacity: "+beHeapCapacity);
            try {
                dsDB = new DataSourceWithRefHeap(maxNodeCount,beHeapCapacity,trieStorePath.toString(),
                        DataSourceWithHeap.LockType.None,
                        null,false);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }
        }

        return dsDB;
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
        int maxReadKeys = 0; // Filled later
        int dumpInterval = 0;

        if (maxKeys>=100_000_000) {
            // We need to read more than 200k keys to avoid the system cache
            // will show an unrealistic extremely fast read speed.
            maxReadKeys = 200_000; // Things get MUCH slower at this point with LevelDB
            dumpInterval = 5_000;
        } else {
            maxReadKeys = 1_000_000;
            dumpInterval = 10_000;
        }
        // To prevent the cache from caching exactly the keys we want to read
        // we should read staring from some random point
        for (long i = 0; i < maxReadKeys; i++) {

            long x =TestUtils.getPseudoRandom().nextLong(maxKeys);
            pseudoRandom.setSeed(x);

            byte[] key =null;
            if (!keyIsValueHash)
                key = pseudoRandom.randomBytes(keyLength);
            byte[] expectedValue = pseudoRandom.randomBytes(valueLength);
            if (keyIsValueHash)
                key= Keccak256Helper.keccak256(expectedValue);
            byte[] value = db.get(key);
            if (!FastByteComparisons.equalBytes(value,expectedValue)) {
                System.out.println("Wrong value");
                System.exit(1);
            }

            if (i % dumpInterval == 0) {
                dumpProgress(i,maxReadKeys);
                if (database== Database.MemoryMappedByteArrayRefHeap) {

                    List<String> stats = ((DataSourceWithRefHeap) db).getHashtableStats();
                    log("Hashtable: ");
                    logList(" ", stats);
                }
            }

        }
        dumpProgress(maxReadKeys,maxReadKeys);
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
        String dbName = "DB_"+getExactCountLiteral(maxKeys);
        if (keyIsValueHash)
            dbName = dbName +"-vh";
        dbName = dbName + "-vlen_"+valueLength;

        if (database== Database.LevelDB) {

        } else {
            dbName =dbName +"-flat";
        }

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
            // The key is not the hash of the value.
            // This differs from a trie test, where that is true.
            byte key[]=null;
            if (!keyIsValueHash)
                key =pseudoRandom.randomBytes(keyLength);
            byte[] value = pseudoRandom.randomBytes(valueLength);
            if (keyIsValueHash)
                key= Keccak256Helper.keccak256(value);
            db.put(key, value);

            if (i % 100000 == 0) {
                dumpProgress(i,maxKeys);
            }

        }
        dumpProgress(maxKeys,maxKeys);
        stop(true);
        closeDB();

        dumpResults(maxKeys);
        dumpTrieDBFolderSize();
        closeLog();
    }

    public void closeDB() {
        log("Closing db...");
        db.close();
        log("Closed");
    }

    void readFileMapUtil() {
        String fileName="bigFileTest.bin";
        //FileOutputStream out = new FileOutputStream(fileName);
        RandomAccessFile sc
                = null;

        try {
            sc = new RandomAccessFile(fileName, "r");

            FileChannel file = sc.getChannel();
            long offset = 0;
            int intLeft=32*1000*1000;
            int i =0;
            while(intLeft>0) {
                int len = Math.min(intLeft,(1<<24)); // 16M ints = 64 Mbytes Max
                ByteBuffer buf = file.map(FileChannel.MapMode.READ_ONLY, offset,
                        4L * len);
                for (int q=0;q<len;q++) {
                    if (buf.getInt() != i) {
                        System.out.println("" + i);
                        System.exit(1);
                    }
                    i++;
                }
                intLeft -=len;
                offset +=4L*len;
            }
            file.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("file is good");
        System.exit(0);
    }

    void testFileMapUtil() {
        String fileName="bigFileTest.bin";
            //FileOutputStream out = new FileOutputStream(fileName);
        RandomAccessFile sc
                = null;
        int[] table = new int[32*1000*1000]; // 32M elements = 128 Mbytes.
        for(int i=0;i<table.length;i++)
            table[i] =i+3;

        try {
            sc = new RandomAccessFile(fileName, "rw");

            FileChannel file = sc.getChannel();

            // Size cannot exceed Integer.MAX_VALUE !! Horrible thing in file.map().
            // However, we can map in parts.
            ByteBuffer buf = file.map(FileChannel.MapMode.READ_WRITE, 0,
                    4L * 3);
            buf.putInt(0);
            buf.putInt(1);
            buf.putInt(2);
            FileMapUtil.mapAndCopyIntArray(file,4*3,table.length,table);
            file.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    public static void main (String args[]) {

        CompareDBs c = new CompareDBs();
        //c.testFileMapUtil();
        //c.readFileMapUtil();
        if (test==Test.readTest)
            c.reads();
        else
        if (test==Test.writeTest)
            c.writes();

    }
}
