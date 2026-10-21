package pd.droidapp.fmgr.fileheader.png;

// PNG header
public final class PngHeader {

    // byte [0,7]
    public static final int[] SIGNATURE = new int[] {0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    // [8,11] IHDR 块长度
    public final long chunkLength;

    // [12,15] 块类型
    public final String chunkType;

    // [16,19] 宽
    public final long width;

    // [20,23] 高
    public final long height;

    // [24] 位深
    public final int bitDepth;

    // [25] 颜色类型
    public final int colorType;

    // [26] 压缩方法
    public final int compressionMethod;

    // [27] 滤波方法
    public final int filterMethod;

    // [28] 隔行扫描
    public final int interlaceMethod;

    PngHeader(long chunkLength, String chunkType, long width, long height, int bitDepth,
            int colorType, int compressionMethod, int filterMethod, int interlaceMethod) {
        this.chunkLength = chunkLength;
        this.chunkType = chunkType;
        this.width = width;
        this.height = height;
        this.bitDepth = bitDepth;
        this.colorType = colorType;
        this.compressionMethod = compressionMethod;
        this.filterMethod = filterMethod;
        this.interlaceMethod = interlaceMethod;
    }
}
