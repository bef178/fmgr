package pd.droidapp.fmgr.fileheader.ogg;

// Ogg page header
public final class OggHeader {

    // byte [0,3]
    public static final int[] SIGNATURE = new int[] {'O', 'g', 'g', 'S'};

    // [4] 版本
    public final int version;

    // [5] 头部类型标志
    public final int headerTypeFlags;

    // [6,13] granule position
    public final long granulePosition;

    // [14,17] 串号
    public final long serialNumber;

    // [18,21] 页序号
    public final long sequenceNumber;

    // [22,25] CRC
    public final long checksum;

    // [26] 页段数，段表从偏移 27 开始
    public final int pageSegments;

    OggHeader(int version, int headerTypeFlags, long granulePosition, long serialNumber,
            long sequenceNumber, long checksum, int pageSegments) {
        this.version = version;
        this.headerTypeFlags = headerTypeFlags;
        this.granulePosition = granulePosition;
        this.serialNumber = serialNumber;
        this.sequenceNumber = sequenceNumber;
        this.checksum = checksum;
        this.pageSegments = pageSegments;
    }
}
