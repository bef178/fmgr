package pd.droidapp.fmgr.fileheader.ebml;

public final class EbmlHeader {

    // byte [0,3]
    public static final int[] SIGNATURE = new int[] {0x1A, 0x45, 0xDF, 0xA3};

    // 以下字段未出现时为 -1
    public final long ebmlVersion;
    public final long ebmlReadVersion;
    public final long ebmlMaxIdLength;
    public final long ebmlMaxSizeLength;
    public final String docType;
    public final long docTypeVersion;
    public final long docTypeReadVersion;

    EbmlHeader(long ebmlVersion, long ebmlReadVersion, long ebmlMaxIdLength, long ebmlMaxSizeLength,
            String docType, long docTypeVersion, long docTypeReadVersion) {
        this.ebmlVersion = ebmlVersion;
        this.ebmlReadVersion = ebmlReadVersion;
        this.ebmlMaxIdLength = ebmlMaxIdLength;
        this.ebmlMaxSizeLength = ebmlMaxSizeLength;
        this.docType = docType;
        this.docTypeVersion = docTypeVersion;
        this.docTypeReadVersion = docTypeReadVersion;
    }
}
