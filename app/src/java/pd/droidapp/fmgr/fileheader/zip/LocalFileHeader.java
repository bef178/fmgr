package pd.droidapp.fmgr.fileheader.zip;

public final class LocalFileHeader {

    // byte [0,3]
    public static final int[] SIGNATURE = new int[] {'P', 'K', 3, 4};

    // [4,5] 解压所需的最低版本
    public final int minVersionToExtract;

    // [6,7] 通用位标记
    public final int generalPurposeBitFlags;

    // [8,9] 压缩方法
    public final int compressionMethod;

    // [10,11] 最后修改时间
    public final int lastModFileTime;

    // [12,13] 最后修改日期
    public final int lastModFileDate;

    // [14,17] CRC-32
    public final long crc32;

    // [18,21] 压缩后大小
    public final long compressedSize;

    // [22,25] 解压后大小
    public final long uncompressedSize;

    // [26,27] 文件名长度
    public final int fileNameLength;

    // [28,29] 扩展字段长度
    public final int extraFieldLength;

    public final String fileName;

    public final byte[] extraField;

    public final DataDescriptor dataDescriptor;

    LocalFileHeader(int minVersionToExtract, int generalPurposeBitFlags, int compressionMethod,
            int lastModFileTime, int lastModFileDate, long crc32, long compressedSize,
            long uncompressedSize, int fileNameLength, int extraFieldLength, String fileName,
            byte[] extraField, DataDescriptor dataDescriptor) {
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
        this.fileName = fileName;
        this.extraField = extraField;
        this.dataDescriptor = dataDescriptor;
    }

    public int recordLength() {
        return 30 + fileNameLength + extraFieldLength;
    }
}
