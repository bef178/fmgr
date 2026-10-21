package pd.droidapp.fmgr.fileheader.gif;

// GIF header
public final class GifHeader {

    // byte [0,5]
    public static final int[] SIGNATURE_87A = new int[] {'G', 'I', 'F', '8', '7', 'a'};
    public static final int[] SIGNATURE_89A = new int[] {'G', 'I', 'F', '8', '9', 'a'};

    public final String version;

    // [6,7] 宽
    public final int width;

    // [8,9] 高
    public final int height;

    // [10] packed
    public final int packed;

    // [11] 背景色索引
    public final int backgroundColorIndex;

    // [12] 像素宽高比
    public final int pixelAspectRatio;

    GifHeader(String version, int width, int height, int packed, int backgroundColorIndex,
            int pixelAspectRatio) {
        this.version = version;
        this.width = width;
        this.height = height;
        this.packed = packed;
        this.backgroundColorIndex = backgroundColorIndex;
        this.pixelAspectRatio = pixelAspectRatio;
    }
}
