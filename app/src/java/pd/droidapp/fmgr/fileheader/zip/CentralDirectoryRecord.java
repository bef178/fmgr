package pd.droidapp.fmgr.fileheader.zip;

public final class CentralDirectoryRecord {

    // byte [0,3]
    public static final int[] SIGNATURE = new int[] {'P', 'K', 1, 2};

    // [4,5] 创建该条目的版本
    public final int versionCreated;

    // [6,7] 解压所需的最低版本
    public final int minVersionToExtract;

    // [8,9] 通用位标记
    public final int generalPurposeBitFlags;

    // [10,11] 压缩方法
    public final int compressionMethod;

    // [12,13] 最后修改时间
    public final int lastModFileTime;

    // [14,15] 最后修改日期
    public final int lastModFileDate;

    // [16,19] CRC-32
    public final long crc32;

    // [20,23] 压缩后大小
    public final long compressedSize;

    // [24,27] 解压后大小
    public final long uncompressedSize;

    // [28,29] 文件名长度
    public final int fileNameLength;

    // [30,31] 扩展字段长度
    public final int extraFieldLength;

    // [32,33] 文件注释长度
    public final int fileCommentLength;

    // [34,35] 文件的起始盘号
    public final int startDiskNumber;

    // [36,37] 内部文件属性
    public final int internalFileAttributes;

    // [38,41] 外部文件属性
    public final long externalFileAttributes;

    // [42,45] 本地文件头偏移（相对于 `startDiskNumber` 那张盘的开头）
    public final long localFileHeaderOffset;

    public final String fileName;

    public final byte[] extraField;

    public final byte[] fileComment;

    CentralDirectoryRecord(int versionCreated, int minVersionToExtract, int generalPurposeBitFlags,
            int compressionMethod, int lastModFileTime, int lastModFileDate, long crc32,
            long compressedSize, long uncompressedSize, int fileNameLength, int extraFieldLength,
            int fileCommentLength, int startDiskNumber, int internalFileAttributes, long externalFileAttributes,
            long localFileHeaderOffset, String fileName, byte[] extraField, byte[] fileComment) {
        this.versionCreated = versionCreated;
        this.minVersionToExtract = minVersionToExtract;
        this.generalPurposeBitFlags = generalPurposeBitFlags;
        this.compressionMethod = compressionMethod;
        this.lastModFileTime = lastModFileTime;
        this.lastModFileDate = lastModFileDate;
        this.crc32 = crc32;
        this.compressedSize = compressedSize;
        this.uncompressedSize = uncompressedSize;
        this.fileNameLength = fileNameLength;
        this.extraFieldLength = extraFieldLength;
        this.fileCommentLength = fileCommentLength;
        this.startDiskNumber = startDiskNumber;
        this.internalFileAttributes = internalFileAttributes;
        this.externalFileAttributes = externalFileAttributes;
        this.localFileHeaderOffset = localFileHeaderOffset;
        this.fileName = fileName;
        this.extraField = extraField;
        this.fileComment = fileComment;
    }
}
