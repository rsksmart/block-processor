package co.rsk.tools.processor.examples;

import co.rsk.tools.processor.RskBlockProcessor;
import co.rsk.tools.processor.RskProvider;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/*************************************************************************
 * This class analyzes the block time and the uncle rate
 * By SDL.
 *************************************************************************/
public class AverageBlockTimeAnalyzer extends RskBlockProcessor  {

    RskProvider provider;

    File file;
    FileWriter fileWriter;

    public void begin() {
        createOutputFile();
    }

    public void createOutputFile() {
        try {
            file = new File("blocktime.csv");
            if (file.createNewFile()) {
                System.out.println("File created: " + file.getName());
            } else {
                System.out.println("File already exists.");
            }
            fileWriter = new FileWriter(file);
            // Average time between blocks, average uncles
            fileWriter.write("BlockNumber,UnixTime,dtime,uncles\n");

        } catch (IOException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
    }

    public AverageBlockTimeAnalyzer(RskProvider provider) {
        this.provider = provider;
    }

    long dayStartTimeStamp =0;

    long dayInSeconds = 60*60*24;
    long acumUncles  =0;
    long lastBlockNumber =0;

    @Override
    public boolean processBlock() {

        if (dayStartTimeStamp==0) {
            // First time initialization
            dayStartTimeStamp = currentBlock.getTimestamp();
            lastBlockNumber = currentBlock.getNumber();
        }
        int u = currentBlock.getUncleList().size();
        acumUncles +=u;

        if (currentBlock.getTimestamp()>dayStartTimeStamp+dayInSeconds) {
            long dtime = currentBlock.getTimestamp()-dayStartTimeStamp;
            long dblocks = currentBlock.getNumber()-lastBlockNumber;
            double avgBlockTime = dtime*1.0/dblocks;
            double avgUncles = acumUncles*1.0/dblocks;
            String line = "" + currentBlock.getNumber() + "," +
                    currentBlock.getTimestamp() + "," +
                    avgBlockTime + "," +
                    avgUncles;
            dayStartTimeStamp = currentBlock.getTimestamp();
            lastBlockNumber = currentBlock.getNumber();
            System.out.println(line);
            acumUncles = 0;
            try {
                fileWriter.write(line+"\n");
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }

    public void end() {
        close();
    }
    public void close() {
        try {
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main (String args[]) {
        int maxBlockchainBlock = 3_220_000; // 3219985
        int minBlock = 1;
        int maxBlock = maxBlockchainBlock; //3200_000;
        int step = 1; // must go trought all txs to count
        RskProvider provider = new RskProvider(args,minBlock,maxBlock,step);
        AverageBlockTimeAnalyzer analyzer = new AverageBlockTimeAnalyzer(provider);
        analyzer.setLoadTrieForEachBlock(false);
        provider.processBlockchain(analyzer);
    }
}
