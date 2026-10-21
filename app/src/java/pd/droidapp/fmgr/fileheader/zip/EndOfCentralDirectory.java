package pd.droidapp.fmgr.fileheader.zip;

public final class EndOfCentralDirectory {

    // byte [0,3]
    public static final int[] SIGNATURE = new int[] {'P', 'K', 5, 6};

    // [4,5] 当前盘号。在不分卷的普通 ZIP 中，该值固定为 0
    public final int diskNumber;

    // [6,7] 中央目录开始的盘号。在不分卷的普通 ZIP 中，该值固定为 0
    public final int centralDirectoryDiskNumber;

    // [8,9] 本盘上的中央目录记录数；对应条目的本地头可以在别的盘上
    public final int numCentralDirectoryRecordsOnDisk;

    // [10,11] 中央目录记录总数。整个 ZIP 压缩包内包含的文件和文件夹总数
    public final int numCentralDirectoryRecords;

    // [12,15] 中央目录的总大小（字节数），含目录内的其他记录；加密目录时为加密后的长度
    public final long centralDirectoryLength;

    // [16,19] 中央目录的起始偏移，相对中央目录所在盘的开头。ZIP64 时为 0xFFFFFFFF，真实值在 EOCD64；加密目录时可能指向解密头
    public final long centralDirectoryOffset;

    // [20,21] 注释的字节数
    public final int commentLength;

    public final byte[] comment;

    EndOfCentralDirectory(int diskNumber, int centralDirectoryDiskNumber,
            int numCentralDirectoryRecordsOnDisk, int numCentralDirectoryRecords,
            long centralDirectoryLength, long centralDirectoryOffset, int commentLength, byte[] comment) {
        this.diskNumber = diskNumber;
        this.centralDirectoryDiskNumber = centralDirectoryDiskNumber;
        this.numCentralDirectoryRecordsOnDisk = numCentralDirectoryRecordsOnDisk;
        this.numCentralDirectoryRecords = numCentralDirectoryRecords;
        this.centralDirectoryLength = centralDirectoryLength;
        this.centralDirectoryOffset = centralDirectoryOffset;
        this.commentLength = commentLength;
        this.comment = comment;
    }
}
