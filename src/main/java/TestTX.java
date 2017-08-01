import java.util.Date;

public class TestTX {

    public static void main(String[] args) throws Exception {
        BitcoinTimestamper bitcoinTimestamper = new BitcoinTimestamper(BitcoinTimestamper.BitcoinNet.TEST, "sjdhfjkdbksd", new Date().getTime());
        bitcoinTimestamper.initialize();

        while (!bitcoinTimestamper.isReady()) {
            Thread.sleep(2000l);
        }

        bitcoinTimestamper.printBalance();

        String attest = bitcoinTimestamper.attest("prefix", "hello gmb".getBytes());
        System.out.println(attest);
        bitcoinTimestamper.stop();
    }
}
