package co.rsk.tools.processor.examples.storage;

import co.rsk.crypto.Keccak256;
import co.rsk.tools.processor.RskBlockProcessor;
import co.rsk.tools.processor.RskProvider;
import co.rsk.trie.NodeReference;
import co.rsk.trie.Trie;
import java.io.*;
import java.util.*;

import static co.rsk.tools.processor.examples.storage.RskNetworkUpgrades.papyrus200;

//
// This class processes one or more RSK blocks and dumps the whole
// trie state to a file
//
public class StorageDumperToFile extends RskBlockProcessor {

    private void processReference(NodeReference reference, byte side,FastTrieKeySlice prePath) {
        if (!reference.isEmpty()) {
            Optional<Trie> node = reference.getNode();

            if (node.isPresent()) {

                //if (!reference.isEmbeddable()) {
                    FastTrieKeySlice path = null;
                    if (trackPath) { path = prePath.appendBit(side); }
                    Trie childTrie = node.get();
                    processTrie(childTrie, reference.isEmbeddable(),path);
                // }
            }
        }
    }

    private static final byte LEFT_CHILD_IMPLICIT_KEY = (byte) 0x00;
    private static final byte RIGHT_CHILD_IMPLICIT_KEY = (byte) 0x01;

    long prevTime;
    long prevNodes;
    long virtualNodes;
    long started;

    public static class TrieItem  {
        public byte[] hash;
        public byte[] value;

        public TrieItem(byte[] hash, byte[] value) {
            this.hash = hash;
            this.value = value;
        }

        public TrieItem(InputStream in) throws IOException {
            readObject(in);
        }

        private void readObject(InputStream stream)
                throws IOException {

            this.hash =ObjectIO.readNBytes(stream,32);
            int valueLength = ObjectIO.readInt(stream);
            if ((valueLength<0) || (valueLength>10_000_000)) {
                //
                System.out.println(valueLength);
                throw  new RuntimeException("invalid stream");
            }

            this.value = stream.readNBytes(valueLength);

        }
        private void writeObject(OutputStream out)
                throws IOException {
            if (hash.length!=32) {
                throw new RuntimeException("Invalid hash");
            }
            out.write(hash);
            int length = value.length;
            if (length<0)
                throw new RuntimeException("Invalid length");
            ObjectIO.writeInt(out,length);
            out.write(value);
        }
    }
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
    //TreeMap<Keccak256,byte[]> map = new TreeMap<>();
    static byte[] akey =hexStringToByteArray("391df4bc67ae9a4c235ee373a183027f60451e19b7ac680a7b5e0d28447194f0");
    static byte[] aPath =hexStringToByteArray("00a1c67f69a80032a500252acc95758f8b5f583470ba265eb685a8f45fc9d580");

    static boolean trackPath = false;
    HashMap<Keccak256,Integer> map ;
    int duplicatedSpace = 0;

    private void processTrie(Trie trie, boolean isEmbedded, FastTrieKeySlice prePath) {
        virtualNodes++;
        if (virtualNodes % 1000 == 0) {
            System.out.println("Nodes processed: " + virtualNodes);
            long currentTime = System.currentTimeMillis();
            long nodesXsec = (virtualNodes) * 1000 / (currentTime - started);
            long nodesXsecNow = (virtualNodes - prevNodes) * 1000 / (currentTime - prevTime);
            System.out.println(" Nodes/sec total: " + nodesXsec);
            System.out.println(" Nodes/sec total: " + nodesXsec + " nodes/sec now: " + nodesXsecNow);
            prevNodes = virtualNodes;
            prevTime = currentTime;
        }
        FastTrieKeySlice path = null;
        if (trackPath) {
            path = prePath.append(trie.getSharedPath());
            if ((path.length() == aPath.length * 8) && (Arrays.compare(path.encode(), aPath) == 0)) {
                System.out.println("Key Found: " + trie.getValueHash().toHexString());
            }
        }

        //map.put(trie.getHash(),trie.toMessage());
        try {
            Keccak256 hash =trie.getHash();

            Integer prevMsgLen =map.get(hash);
            if (prevMsgLen!=null) {
                duplicatedSpace +=prevMsgLen;
                if (trie.hasLongValue()) {
                    Keccak256 hash2 = trie.getValueHash();
                    duplicatedSpace +=map.get(hash2);
                }
                return;
            }

            TrieItem ti = new TrieItem(trie.getHash().getBytes(), trie.toMessage());
            ti.writeObject(out);
            map.put(hash,ti.value.length);

            if (isEmbedded) {
            }

            if (trie.hasLongValue()) {
                //map.put(trie.getValueHash(),trie.getValue());
                byte[] key = trie.getValueHash().getBytes();
                if (trackPath) {
                    if (Arrays.compare(key, akey) == 0) {
                        System.out.println("Found: " + trie.getValueHash().toHexString());
                    }
                }
                Keccak256 hash2 = trie.getValueHash();
                Integer prevLongMsgLen =map.get(hash2);
                if (prevLongMsgLen!=null) {
                    duplicatedSpace +=prevLongMsgLen;
                } else {
                    TrieItem ti2 = new TrieItem(hash2.getBytes(), trie.getValue());
                    ti2.writeObject(out);
                    map.put(hash2,ti.value.length);
                }

            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        if (isEmbedded)
            return;
        NodeReference leftReference = trie.getLeft();
        NodeReference rightReference = trie.getRight();
        processReference(leftReference, LEFT_CHILD_IMPLICIT_KEY,path);
        processReference(rightReference, RIGHT_CHILD_IMPLICIT_KEY,path);
    }

    @Override
    public boolean processBlock() {
        try {
            createFile();
            prevNodes = 0;
            prevTime = 0;
            started = System.currentTimeMillis();
            Trie aTrie = getTrieAtCurrentBlock();
            //Trie aTrinode = this.trie.find(aPath);
            //System.out.println(aTrinode.getValueLength());
            map = new HashMap<>();
            duplicatedSpace = 0;
            processTrie(getTrieAtCurrentBlock(), false,FastTrieKeySlice.emptyWithCapacity());
            long ended = System.currentTimeMillis();
            System.out.println("Elapsed time [s]: " + (ended - started) / 1000);
            System.out.println("DuplicatedSpace [Kb]: " + (duplicatedSpace / 1000));
            closeFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

       OutputStream out;

    public void closeFile() throws IOException {
        out.flush();
        //closing the stream
        out.close();
        System.out.println("File "+fileName+" written.");
    }


    String fileName ="unset";

    final static  int maxBlockchainBlock = 3_210_000; // 3219985

    // Testnet "difficult" blocks: 1672391 to 167313
    static final int testnetDifficultBlock =  1672391;
    final static int ioblockNumber = maxBlockchainBlock-1;

    public void createFile() throws IOException {

        out = new BufferedOutputStream(new FileOutputStream(fileName));
    }



    public static void main (String args[]) {

        StorageDumperToFile sl = new StorageDumperToFile();
        sl.DumpTrie(2_999_999,"3M",args);
        System.exit(0);

        int defaultRange = 100;


        //executeCenter(bahamas,"bahamas",defaultRange, args);
        //executeCenter(afterBridgeSync,"afterBridgeSync",defaultRange, args);
        //executeCenter(orchid,"orchid",defaultRange, args);
        //executeCenter(orchid060,"orchid060",defaultRange , args);
        //executeCenter(wasabi100,"wasabi100",defaultRange , args);
        //executeCenter(twoToThree,"twoToThree",defaultRange , args);
        executeCenter(papyrus200,"papyrus200",defaultRange , args);

    }

    static public void DumpTrie(int bn,String dumpName,String args[]) {
        System.out.println("dumping :"+dumpName);
        StorageDumperToFile storageDumperToFile = new StorageDumperToFile();
        storageDumperToFile.writeTrie(args,bn);
    }

    static public void executeCenter(int bn,String dumpName,int range,String args[]) {

        System.out.println("dumping :"+dumpName);
        StorageDumperToFile storageDumperToFile = new StorageDumperToFile();
        storageDumperToFile.writeTrie(args,bn-range-1);
    }




    public void writeTrie(String args[],int aSingleBlockNumber) {
        int minBlock = aSingleBlockNumber;
        int maxBlock = aSingleBlockNumber+1;

        int step = 800_000;
        RskProvider provider = new RskProvider(args,minBlock,maxBlock,step);
        fileName = provider.getNetworkName()+"-" + aSingleBlockNumber + ".bin";
        System.out.println("Output filename: "+fileName);
        provider.processBlockchain(this);
        //provider.getContext().getBlockStore().flush();

    }

    public void writeTrie(String args[]) {
        int minBlock = ioblockNumber;///1_600_001; //2_400_001;
        int maxBlock = minBlock+1; // maxBlockchainBlock;
        int step = 800_000;
        RskProvider provider = new RskProvider(args,minBlock,maxBlock,step);
        fileName = provider.getNetworkName()+"-" + ioblockNumber + ".bin";
        StorageDumperToFile loader = new StorageDumperToFile();
        provider.processBlockchain(loader);
    }
}
