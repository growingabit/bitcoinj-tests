import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;

public final class BitcoinTimestamper {

    private static final File POSIX_TEMP_DIRECTORY = new File("tmp");
    private static final int SHA256_LENGTH = 32;
    private static final int MAX_PREFIX_LENGTH = 8;
    private static final byte NULL_BYTE = (byte) '\0';

    public static void main(String[] args) {
        new BitcoinTimestamper(BitcoinNet.TEST, "hello blockchain", new Date().getTime());
    }

    public enum BitcoinNet {
        MAIN,
        TEST
    };

    private WalletAppKit walletAppKit;

    // This is simply to get a sha256 implementation
    private static MessageDigest SHA_256 = null;
    static {
        try {
            SHA_256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException("Programmer error.", nsae);
        }
    }

    public BitcoinTimestamper(BitcoinNet bitcoinNet, String seedWords, long seedCreationTime, File tempDirectory) {

        // Configure network parameters for the appropriate net
        NetworkParameters params;
        if (bitcoinNet.equals(BitcoinNet.TEST)) {
            params = TestNet3Params.get();
        } else if (bitcoinNet.equals(BitcoinNet.MAIN)) {
            params = MainNetParams.get();
        } else {
            throw new RuntimeException("Programmer error.");
        }

        // Generate a wallet from the given seed values
        DeterministicSeed seed = null;
        try {
            seed = new DeterministicSeed(seedWords, null, "", seedCreationTime);
        } catch (UnreadableWalletException uwe) {
            throw new RuntimeException(uwe);
        }
        walletAppKit = new WalletAppKit(params, tempDirectory, ".spv");
        walletAppKit.restoreWalletFromSeed(seed);
    }

    public BitcoinTimestamper(BitcoinNet bitcoinNet, String seedWords, long seedCreationTime) {
        this(bitcoinNet, seedWords, seedCreationTime, POSIX_TEMP_DIRECTORY);
    }

    public void initialize() {
        walletAppKit.setBlockingStartup(true);
        walletAppKit.setAutoSave(true);
        walletAppKit.startAsync();
    }

    public void stop() {
        walletAppKit.stopAsync();
    }

    public boolean isReady() {
        return walletAppKit.isRunning();
    }

    public void waitUntilReady() {
        walletAppKit.awaitRunning();
    }

    public void printBalance() throws IOException {
        System.out.println(walletAppKit.wallet().toString());
        System.out.println("balance: " + walletAppKit.wallet().getBalance().getValue());
        System.out.println("Send money to: " + walletAppKit.wallet().currentReceiveAddress().toString());
    }

    public String attest(String prefix, byte[] subject) throws EmptyBitcoinAccountException {

        final Wallet wallet = walletAppKit.wallet();

        // Take the actual sha256 to embed
        byte[] hash = SHA_256.digest(subject);

        // ASCII encode the prefix
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.US_ASCII);
        if (MAX_PREFIX_LENGTH < prefix.length()) {
            throw new IllegalArgumentException("OP_RETURN prefix is too long: " + prefix);
        }

        // Construct the OP_RETURN data
        byte[] opReturnValue = new byte[40];
        Arrays.fill(opReturnValue, NULL_BYTE);
        System.arraycopy(prefixBytes, 0, opReturnValue, 0, prefixBytes.length);
        System.arraycopy(hash, 0, opReturnValue, MAX_PREFIX_LENGTH, SHA256_LENGTH);

        // Construct a OP_RETURN transaction
        Transaction transaction = new Transaction(wallet.getParams());
        transaction.addOutput(Coin.ZERO, ScriptBuilder.createOpReturnScript(hash));

        SendRequest sendRequest = SendRequest.forTx(transaction);

        // Fill-in the missing details for our wallet, eg. fees.
        try {
            wallet.completeTx(sendRequest);
        } catch (InsufficientMoneyException e) {
            throw new EmptyBitcoinAccountException();
        }

        // Broadcast and commit transaction
        walletAppKit.peerGroup().broadcastTransaction(transaction);
        wallet.commitTx(transaction);

        System.out.println("[SUCCESS] Transaction committed");
        System.out.println(transaction.toString());

        // Return a reference to the caller
        return transaction.getHashAsString();
    }

    @Override
    public void finalize() throws Throwable {
        this.stop();
        super.finalize();
    }

    public class EmptyBitcoinAccountException extends Exception {
    }
}
