package pd.droidapp.fmgr.fileheader.flac;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt16BigEndian;

public final class FlacParser {

    private static final int HEADER_LENGTH = 42;
    private static final int STREAMINFO_LENGTH = 34;

    private final byte[] head;

    public FlacParser(byte[] head) {
        this.head = head;
    }

    public FlacHeader parse() {
        if (head == null || head.length < HEADER_LENGTH
                || !bytesStartsWith(head, FlacHeader.SIGNATURE)) {
            return null;
        }
        boolean lastBlock = (head[4] & 0x80) != 0;
        int blockType = head[4] & 0x7F;
        int blockLength = ((head[5] & 0xFF) << 16) | ((head[6] & 0xFF) << 8) | (head[7] & 0xFF);
        if (blockType != 0 || blockLength != STREAMINFO_LENGTH) {
            return null;
        }
        int minBlockSize = readUnsignedInt16BigEndian(head, 8);
        int maxBlockSize = readUnsignedInt16BigEndian(head, 10);
        int minFrameSize = readUnsignedInt24BigEndian(head, 12);
        int maxFrameSize = readUnsignedInt24BigEndian(head, 15);
        int b20 = head[20] & 0xFF;
        int b21 = head[21] & 0xFF;
        int sampleRate = ((head[18] & 0xFF) << 12) | ((head[19] & 0xFF) << 4) | (b20 >> 4);
        int channels = ((b20 >> 1) & 0x07) + 1;
        int bitsPerSample = (((b20 & 0x01) << 4) | (b21 >> 4)) + 1;
        long totalSamples = ((long) (b21 & 0x0F) << 32)
                | ((long) (head[22] & 0xFF) << 24)
                | ((long) (head[23] & 0xFF) << 16)
                | ((long) (head[24] & 0xFF) << 8)
                | (head[25] & 0xFF);
        return new FlacHeader(lastBlock, blockType, blockLength,
                minBlockSize, maxBlockSize, minFrameSize, maxFrameSize,
                sampleRate, channels, bitsPerSample, totalSamples);
    }

    private static int readUnsignedInt24BigEndian(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 16)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | (bytes[offset + 2] & 0xFF);
    }
}
