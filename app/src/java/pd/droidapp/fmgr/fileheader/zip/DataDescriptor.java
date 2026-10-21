package pd.droidapp.fmgr.fileheader.zip;

// 通用位标记 bit 3 置位时跟在条目数据之后；签名可省略
public final class DataDescriptor {

    // 可选的 4 字节签名，不出现时其后的字段依次前移
    public static final int[] SIGNATURE = new int[] {'P', 'K', 7, 8};

    public final boolean hasSignature;

    // [0,3] 或 [4,7] CRC-32
    public final long crc32;

    // 压缩后大小
    public final long compressedSize;

    // 解压后大小
    public final long uncompressedSize;

    DataDescriptor(boolean hasSignature, long crc32, long compressedSize, long uncompressedSize) {
        this.hasSignature = hasSignature;
        this.crc32 = crc32;
        this.compressedSize = compressedSize;
        this.uncompressedSize = uncompressedSize;
    }
}
