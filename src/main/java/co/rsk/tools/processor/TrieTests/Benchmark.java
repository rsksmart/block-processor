package co.rsk.tools.processor.TrieTests;

import co.rsk.tools.processor.TrieTests.Unitrie.EncodedObjectStore;
import co.rsk.tools.processor.TrieTests.Unitrie.GlobalEncodedObjectStore;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class Benchmark {
    long startMbs;
    long started;
    long elapsedTime;

    long ended;
    long endMbs;
    String logName;
    FileWriter myWriter;


    public void start(boolean showMem) {
        startMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;

        if (showMem) {
            log("-- Java Total Memory [MB]: " + Runtime.getRuntime().totalMemory() / 1024 / 1024);
            log("-- Java Free Memory [MB]: " + Runtime.getRuntime().freeMemory() / 1024 / 1024);
            log("-- Java Max Memory [MB]: " + Runtime.getRuntime().maxMemory() / 1024 / 1024);
            log("Used Before MB: " + startMbs);
        }

        started = System.currentTimeMillis();
        log("Starting...");
    }

    public void stop(boolean showmem) {
        ended = System.currentTimeMillis();
        System.out.println("Stopped.");
        if (showmem) {
            System.out.println("Forced system garbage collection");
            System.gc();
            System.out.println("Forced store garbage collection");
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
        elapsedTime = (ended - started) / 1000;
        log("-- Partial Elapsed time [s]: " + elapsedTime);
        if (elapsedTime>0)
            log("-- Added nodes/sec: "+(i/elapsedTime)); // 18K
        endMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        //System.gc();
        log("-- Jave Mem Used[MB]: " + endMbs);
        log("-- Jave Mem Comsumed [MB]: " + (endMbs - startMbs));

    }

    public void dumpMemProgress(long i,long amax) {
        log("item " + i + " (" + (i * 100 / amax) + "%)");

        long ended = System.currentTimeMillis();
        elapsedTime = (ended - started) / 1000;
        log("-- Partial Elapsed time [s]: " + elapsedTime);
        if (elapsedTime>0)
            log("-- Added nodes/sec: "+(i/elapsedTime)); // 18K
        endMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        log("-- Jave Mem Used[MB]: " + endMbs);
        log("-- Jave Mem Comsumed [MB]: " + (endMbs - startMbs));

    }
    public void plainCreateLogFilename(String name) {
        logName = name;

        try {
            File myObj = new File(name + ".txt");
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

    public void log(String s) {
        long stime = System.currentTimeMillis()-startMillis;
        long sec = stime /1000;
        long mil = stime % 1000;
        String strDate =""+sec+"."+mil+": ";

        System.out.println(strDate+": "+s);
        if (myWriter==null) return;

        try {
            myWriter.write(s+"\n");
        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }

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
