package pd.droidapp.fmgr.fileheader.zip;

// 分卷首卷开头的 spanning marker；固定 4 字节，后面紧跟本地文件头
public final class SpanningMarker {

    // byte [0,3]
    public static final int[] SIGNATURE = new int[] {'P', 'K', 7, 8};

    // 只用了一段时的临时标记
    public static final int[] TEMPORARY_SIGNATURE = new int[] {'P', 'K', '0', '0'};

    public final boolean temporary;

    SpanningMarker(boolean temporary) {
        this.temporary = temporary;
    }
}
