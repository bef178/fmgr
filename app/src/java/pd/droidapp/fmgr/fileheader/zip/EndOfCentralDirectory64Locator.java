package pd.droidapp.fmgr.fileheader.zip;

public final class EndOfCentralDirectory64Locator {

    // byte [0,3]
    public static final int[] SIGNATURE = new int[] {'P', 'K', 6, 7};

    // [4,7] 存放 EOCD64 记录的盘号
    public final int eocd64DiskNumber;

    // [8,15] EOCD64 记录相对该盘开头的偏移
    public final long eocd64Offset;

    // [16,19] 总盘数
    public final int totalDisks;

    EndOfCentralDirectory64Locator(int eocd64DiskNumber, long eocd64Offset, int totalDisks) {
        this.eocd64DiskNumber = eocd64DiskNumber;
        this.eocd64Offset = eocd64Offset;
        this.totalDisks = totalDisks;
    }
}
