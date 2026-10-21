package pd.droidapp.fmgr.fileheader.riff;

// RIFF header
public final class RiffHeader {

    // byte [0,3]
    public static final int[] SIGNATURE = new int[] {'R', 'I', 'F', 'F'};

    // [4,7] 32 位小端的块长度，等于文件总字节数减 8（从偏移 8 到文件末尾）
    public final long chunkSize;

    // [8,11] 格式类型，如 WEBP/WAVE/AVI
    public final String formType;

    RiffHeader(long chunkSize, String formType) {
        this.chunkSize = chunkSize;
        this.formType = formType;
    }
}
