package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectStore;
import co.rsk.tools.processor.TrieTests.Unitrie.GlobalEncodedObjectStore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Benchmark {
    public long startMbs;
    public long started;
    public long elapsedTime;

    public long lastDumpTime;
    public long  lastDumpIndex;


    public long ended;
    public long endMbs;
    String logName;
    FileWriter myWriter;
    boolean showPartialMemConsumed = true;

    public String getMillions(long i) {
        String maxStr = ""+ (i/1000/1000)+"M";
        return maxStr;
    }

    public void showUsedMemory() {
        garbageCollector();
        long usedMemoryMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        log("Memory used now [MB]: " + usedMemoryMbs);
    }

    public void startMem(boolean showMem) {
        long usedMemoryMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        startMbs = usedMemoryMbs;
        if (showMem) {
            log("-- Java Total Memory [MB]: " + Runtime.getRuntime().totalMemory() / 1024 / 1024);
            log("-- Java Free Memory [MB]: " + Runtime.getRuntime().freeMemory() / 1024 / 1024);
            log("-- Java Max Memory [MB]: " + Runtime.getRuntime().maxMemory() / 1024 / 1024);
            log("Used before start [MB]: " + startMbs);
        }

    }

    public void start(boolean showMem) {
        if (showMem)
            startMem(true);

        started = System.currentTimeMillis();
        lastDumpTime = started;
        lastDumpIndex = 0;
        log("Starting...");
    }


    public void garbageCollector() {
        log("Forced system garbage collection");
        System.gc();
    }

    public void stop(boolean showmem) {
        ended = System.currentTimeMillis();
        log("Stopped.");
        if (showmem) {
            garbageCollector();
            userGarbageCollector();
        }
        endMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
    }

    public void userGarbageCollector() {

    }
    public void printMemStats(String s) {

    }
    public void dumpProgress(long i,long amax) {
        log("item " + i + " (" + (i * 100 / amax) + "%)");
        printMemStats("--");
        long ended = System.currentTimeMillis();
        long elapsedMillis = (ended - started);
        elapsedTime = elapsedMillis/ 1000;
        log("-- Partial Elapsed time [s]: " + elapsedTime);
        if (elapsedMillis>0) {
            log("-- Average nodes/sec: " + (i *1000/ elapsedMillis));
        }
        long elapseMillisSinceLastDump = ended-lastDumpTime;
        if (elapseMillisSinceLastDump>0) {
            long nodesProcessed = (i-lastDumpIndex);
            log("-- Nodes processed delta: "+nodesProcessed);
            log("-- Current nodes/sec: " + ( nodesProcessed*1000/ elapseMillisSinceLastDump));
            lastDumpIndex = i;
            lastDumpTime = ended;
        }
        lastDumpTime = ended;

        endMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        if (showPartialMemConsumed) {
            log("-- Jave Mem Used[MB]: " + endMbs);
            log("-- Jave Mem Comsumed [MB]: " + (endMbs - startMbs));
        }
    }

    public void dumpShortProgress(long i,long amax) {
        log("item " + i + " (" + (i * 100 / amax) + "%)");

        long ended = System.currentTimeMillis();
        elapsedTime = (ended - started) / 1000;
        log("-- Partial Elapsed time [s]: " + elapsedTime);
        if (elapsedTime>0)
            log("-- Nodes/sec: "+(i/elapsedTime)); // 18K
    }

    public void dumpMemProgress(long i,long amax) {
        dumpShortProgress(i,amax);
        endMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        log("-- Jave Mem Used[MB]: " + endMbs);
        log("-- Jave Mem Comsumed [MB]: " + (endMbs - startMbs));

    }
    public void plainCreateLogFilename(String name) {
        logName = name;
        String logNameExt = name + ".txt";
        System.out.println("Creating: " + logNameExt);

        try {
            File myObj = new File(logNameExt);
            if (myObj.createNewFile()) {
                System.out.println("File created: " + myObj.getName());
            } else {
                System.out.println("File already exists.");
            }
            System.out.println("File path: " + myObj.getAbsolutePath());

            myWriter = new FileWriter(myObj);
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public void closeLog() {

        try {
            myWriter.close();
            System.out.println("Successfully wrote to the file.");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

    }

    long startMillis = System.currentTimeMillis();
    String newSection = ">>> ";
    String endSection = "<<< ";

    public void logEndSection(String s) {
        log(endSection + s);
        log("",false);
    }

    public void logNewSection(String s) {
        log("",false);
        log(newSection+s);
    }

    public void log(String s,boolean addDate) {
        String strDate;
        if (addDate) {
            long stime = System.currentTimeMillis() - startMillis;
            long sec = stime / 1000;
            long mil = stime % 1000;
            strDate = "" + sec + "." + mil + ": ";
            s = strDate + s;
        } else
            strDate = "";

        System.out.println(s);
        if (myWriter==null) return;

        try {
            myWriter.write(s+"\n");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public void log(String s) {
        log(s,true);
    }

    public void dumpSpeedResults(int max) {
        long elapsedTimeMs = (ended - started);
        elapsedTime =  elapsedTimeMs/ 1000;
        log("Elapsed time [s]: " + elapsedTime);
        if (elapsedTimeMs!=0) {
            log("Rate nodes/sec: " + (max*1000L / elapsedTimeMs));
        }
    }

    public void dumpMemResults(int max) {
        dumpSpeedResults(max);
        log("Memory used after test: MB: " + endMbs);
        log("Consumed MBs: " + (endMbs - startMbs));

    }

}
