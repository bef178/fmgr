package pd.droidapp.fmgr.fileheader.zip;

// central directory digital signature
public final class DigitalSignature {

    // byte [0,3]
    public static final int[] SIGNATURE = new int[] {'P', 'K', 5, 5};

    public final byte[] data;

    DigitalSignature(byte[] data) {
        this.data = data;
    }
}
