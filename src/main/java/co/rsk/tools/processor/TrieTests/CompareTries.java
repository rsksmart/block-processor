package co.rsk.tools.processor.TrieTests;

//import co.rsk.tools.processor.TrieTests.sepAttempt.InMemTrie;

import co.rsk.core.Coin;
import org.ethereum.core.Account;
import org.ethereum.core.AccountState;
import org.ethereum.core.Denomination;

import java.math.BigInteger;

public class CompareTries {

    public static void main (String args[]) {
       // in satoshis
        // 0.1 bitcoin

        AccountState a = new AccountState(BigInteger.valueOf(100),
                new Coin(
                    Denomination.satoshisToWeis(10_1000_1000)));

        int accountSize = a.getEncoded().length;
        System.out.println("Average account size: "+accountSize);
        // accountSize = 12
        accountSize = 12;

        Trie t = new Trie();
        int max = 10_000_000;

        // A cell address contains 10+20 account bytes, plus 10+32.
        // + 1 domain
        // + 1 intermediate (always fixed byte, we can skip for these tests)
        int valueSize = accountSize;
        int keySize = 10+20;//+10+32;
        System.out.println("keysize: "+keySize);
        System.out.println("valueSize: "+valueSize);

        long startMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;

        System.out.println("Used Before MB: " + startMbs);

        long started = System.currentTimeMillis();
        System.out.println("Filling...");
        byte[] v1 = new byte[]{1};
        byte[] v2 = new byte[]{2};
        byte[] v3 = new byte[]{3};

        //v1= TestUtils.randomBytes(32);
        //v2= TestUtils.randomBytes(32);
        /*
        t = t.put("1",v1);
        t = t.put("2",v2);
        t = t.put("3",v3);
        */
        long remapTime =0;
        long remapTimeBelow50 =0;
        long remapTimeOver50 =0;

        InMemStore ms =InMemStore.get();
        for(int i=0;i<max;i++) {


            byte[] key = TestUtils.randomBytes(keySize);
            byte[] value = TestUtils.randomBytes(valueSize);
            t = t.put(key,value);
            if (i % 100000==0) {
                System.out.println("item " + i + " (" + (i * 100 / max) + "%)");
                System.out.println("--InMemStore usage[%]: "+ms.getUsagePercent());
                System.out.println("--InMemStore usage[Mb]: "+ms.getMemUsed()/1000/1000);
                long ended = System.currentTimeMillis();
                long  elapsedTime = (ended - started) / 1000;
                System.out.println("--Partial Elapsed time [s]: " + elapsedTime);
                if (elapsedTime>0)
                    System.out.println("--Added nodes/sec: "+(i/elapsedTime)); // 18K
            }

            if (ms.almostFull()) {
                System.out.println(":::::::::::::::::::::::::::::::::::::");
                System.out.println("::Remapping from: "+ms.getUsagePercent()+"%");
                long rstarted = System.currentTimeMillis();
                long rstartMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;

                System.out.println("::Remap Used Before MB: " + rstartMbs);
                ms.beginRemap();
                t.compressTree();
                long rended = System.currentTimeMillis();

                long  relapsedTime = (rended - rstarted) ;

                if (ms.getUsagePercent()<50)
                    remapTimeBelow50 +=relapsedTime;
                else
                    remapTimeOver50 +=relapsedTime;
                ms.endRemap();
                System.out.println("::Remapping   to: "+ms.getUsagePercent()+"%");
                remapTime +=relapsedTime;
                System.out.println("::Remap Elapsed time [msec]: " + relapsedTime);
                System.gc();
                long rendMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
                System.out.println("::Remap Used After MB: " + rendMbs );
                System.out.println("::Remap Freed MBs: " + (rstartMbs-rendMbs));
                System.out.println(":::::::::::::::::::::::::::::::::::::");
            }
        }

        long ended = System.currentTimeMillis();
        long  elapsedTime = (ended - started) / 1000;
        System.out.println("Elapsed time [s]: " + elapsedTime);
        System.out.println("Added nodes/sec: "+(max/elapsedTime));
        System.gc();
        long endMbs = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024/ 1024;
        System.out.println("Used After MB: " + endMbs );
        System.out.println("Comsumed MBs: " + (endMbs-startMbs) );

        System.out.println("InMemStore usage[%]: "+ms.getUsagePercent());
        System.out.println("InMemStore usage[Mb]: "+ms.getMemUsed()/1000/1000);
        System.out.println("total remap time[s]: "+remapTime/1000);
        System.out.println("remap time below 50% [s]: "+remapTimeBelow50/1000);
        System.out.println("remap time above 50% [s]: "+remapTimeOver50/1000);

        System.out.println("remap time per insertion[msec]: "+(remapTime*1.0/max));

        started = System.currentTimeMillis();
        // now we count something
        System.out.println("Counting...");
        //System.out.println("nodes: "+t.countNodes());
        System.out.println("leaf nodes: "+t.countLeafNodes());

        ended = System.currentTimeMillis();
        elapsedTime = (ended - started) / 1000;
        System.out.println("Elapsed time [s]: " + elapsedTime);
        System.out.println("Scanned leaf nodes/sec: "+(max/elapsedTime)); // 18K

        System.out.println("Finished.");

    }
}
