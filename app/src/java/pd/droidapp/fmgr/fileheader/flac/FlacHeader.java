package pd.droidapp.fmgr.fileheader.flac;

// FLAC header
public final class FlacHeader {

    // byte [0,3]
    public static final int[] SIGNATURE = new int[] {'f', 'L', 'a', 'C'};

    // [4] 1 位 last-metadata-block 标志 + 7 位块类型
    public final boolean lastBlock;
    public final int blockType;

    // [5,7] 元数据块长度
    public final int blockLength;

    // STREAMINFO 数据从偏移 8 开始

    // [8,9] 最小编码块大小
    public final int minBlockSize;

    // [10,11] 最大编码块大小
    public final int maxBlockSize;

    // [12,14] 最小帧大小
    public final int minFrameSize;

    // [15,17] 最大帧大小
    public final int maxFrameSize;

    // [18,25] 采样率（20 位）、声道数（3 位）、位深（5 位）、总样本数（36 位）
    public final int sampleRate;
    public final int channels;
    public final int bitsPerSample;
    public final long totalSamples;

    FlacHeader(boolean lastBlock, int blockType, int blockLength,
            int minBlockSize, int maxBlockSize, int minFrameSize, int maxFrameSize,
            int sampleRate, int channels, int bitsPerSample, long totalSamples) {
        this.lastBlock = lastBlock;
        this.blockType = blockType;
        this.blockLength = blockLength;
        this.minBlockSize = minBlockSize;
        this.maxBlockSize = maxBlockSize;
        this.minFrameSize = minFrameSize;
        this.maxFrameSize = maxFrameSize;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.bitsPerSample = bitsPerSample;
        this.totalSamples = totalSamples;
    }
}
