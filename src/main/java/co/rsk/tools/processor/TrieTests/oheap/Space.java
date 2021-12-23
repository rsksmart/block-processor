package co.rsk.tools.processor.TrieTests.oheap;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.cindex.IndexTrie;
import co.rsk.tools.processor.examples.storage.ObjectIO;
import co.rsk.tools.processor.examples.storage.StorageDumperToFile;

import java.io.*;
import java.util.HashMap;

public class Space {

    public int memTop = 0;
    public byte[] mem;


    public boolean filled;
    public boolean inUse = false;
    public int previousSpaceNum = -1; // unlinked

    public void create(int size) {
        if (mem == null)
            mem = new byte[size];
        memTop = 0;
        inUse = true;
    }

    public void unlink() {
        previousSpaceNum = -1; //
    }

    public void destroy() {
        mem = null;
        filled = false;
    }

    public void softCreate() {
        memTop = 0;
        inUse = true;
    }

    public void softDestroy() {
        // do not remove the memory: this causes the Java garbage colelctor to try to move huge
        // objects around.
        filled = false;
        inUse = false;
    }

    public boolean empty() {
        return (!inUse);
    }

    public int spaceAvail() {
        return mem.length - memTop;
    }

    public boolean spaceAvailFor(int msgLength) {
        return (spaceAvail() >= msgLength);
    }

    public int getUsagePercent() {
        return (int) ((long) memTop * 100 / mem.length);
    }

    public void readFromFile(String fileName) {

        // Now load it all again and put it on a hashmap.
        // We'll see how much time it takes.
        long started = System.currentTimeMillis();

        InputStream in;

        try {

            //in = new BufferedInputStream(new FileInputStream(fileName));
            in = new FileInputStream(fileName);
            System.out.println("Used Before MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024);


            try {
                int avail =in.available();

                //mem = ObjectIO.readNBytes(in,avail);
                if (in.read(mem,0,avail)!=avail) {
                    throw new RuntimeException("not enough data");
                }
                memTop = avail;
            }
            catch (EOFException exc)
            {
                // end of stream
            }
            long currentTime = System.currentTimeMillis();
            System.out.println("Time[s]: "+(currentTime-started)/1000);

          System.out.println("Used After MB: " + (double) (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024);

            //
            in.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        //out.writeObject(s1);


    }

    public void saveToFile(String fileName )  {

        try {
        //out = new BufferedOutputStream(new FileOutputStream(fileName));
        FileOutputStream out = new FileOutputStream(fileName);
        out.write(mem,0,memTop);
        out.flush();
        //closing the stream
        out.close();
        System.out.println("File "+fileName+" written.");
    } catch (FileNotFoundException e) {
        e.printStackTrace();
    } catch (IOException e) {
        e.printStackTrace();
    }
    }


}
