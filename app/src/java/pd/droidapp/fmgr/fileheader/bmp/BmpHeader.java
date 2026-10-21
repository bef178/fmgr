package pd.droidapp.fmgr.fileheader.bmp;

public final class BmpHeader {

    // byte [0,1]
    public static final int[] SIGNATURE = new int[] {'B', 'M'};

    // [2,5] 文件大小
    public final long fileSize;

    // [10,13] 像素数据偏移
    public final long pixelOffset;

    // [14,17] DIB 头大小
    public final long dibSize;

    // 宽；DIB 大小 12 时取 [18,19] 的 16 位值，否则取 [18,21] 的 32 位值
    public final int width;

    // 高；DIB 大小 12 时取 [20,21] 的 16 位值，否则取 [22,25] 的 32 位值
    public final int height;

    BmpHeader(long fileSize, long pixelOffset, long dibSize, int width, int height) {
        this.fileSize = fileSize;
        this.pixelOffset = pixelOffset;
        this.dibSize = dibSize;
        this.width = width;
        this.height = height;
    }
}
