package pd.droidapp.fmgr.fileheader.zip;

// ZIP64 end of central directory record
public final class EndOfCentralDirectory64 {

    // byte [0,3]
    public static final int[] SIGNATURE = new int[] {'P', 'K', 6, 6};

    // [4,11] 本记录余下部分的长度，不含签名与本字段
    public final long recordLength;

    // [12,13] 创建该条目的版本
    public final int versionCreated;

    // [14,15] 解压所需的最低版本
    public final int minVersionToExtract;

    // [16,19] 当前盘号。不分卷时固定为 0
    public final int diskNumber;

    // [20,23] 中央目录开始的盘号。不分卷时固定为 0
    public final int centralDirectoryDiskNumber;

    // [24,31] 本盘上的中央目录记录总数
    public final long numCentralDirectoryRecordsOnDisk;

    // [32,39] 中央目录记录总数
    public final long numCentralDirectoryRecords;

    // [40,47] 中央目录的总大小
    public final long centralDirectoryLength;

    // [48,55] 中央目录的起始偏移
    public final long centralDirectoryOffset;

    // [56,56+recordLength-44) 扩展数据；为零长度时为空数组，过大未读取时为 null
    public final byte[] extensibleData;

    EndOfCentralDirectory64(long recordLength, int versionCreated, int minVersionToExtract,
            int diskNumber, int centralDirectoryDiskNumber, long numCentralDirectoryRecordsOnDisk,
            long numCentralDirectoryRecords, long centralDirectoryLength, long centralDirectoryOffset,
            byte[] extensibleData) {
        this.recordLength = recordLength;
        this.versionCreated = versionCreated;
        this.minVersionToExtract = minVersionToExtract;
        this.diskNumber = diskNumber;
        this.centralDirectoryDiskNumber = centralDirectoryDiskNumber;
        this.numCentralDirectoryRecordsOnDisk = numCentralDirectoryRecordsOnDisk;
        this.numCentralDirectoryRecords = numCentralDirectoryRecords;
        this.centralDirectoryLength = centralDirectoryLength;
        this.centralDirectoryOffset = centralDirectoryOffset;
        this.extensibleData = extensibleData;
    }
}
