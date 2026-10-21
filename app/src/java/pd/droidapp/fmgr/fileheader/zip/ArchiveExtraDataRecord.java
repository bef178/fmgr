package pd.droidapp.fmgr.fileheader.zip;

// archive extra data record
public final class ArchiveExtraDataRecord {

    // byte [0,3]
    public static final int[] SIGNATURE = new int[] {'P', 'K', 6, 8};

    public final byte[] data;

    ArchiveExtraDataRecord(byte[] data) {
        this.data = data;
    }
}
