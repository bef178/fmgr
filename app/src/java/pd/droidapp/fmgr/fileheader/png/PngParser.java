package pd.droidapp.fmgr.fileheader.png;

import java.nio.charset.StandardCharsets;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt32BigEndian;

public final class PngParser {

    private static final int HEADER_LENGTH = 29;

    private final byte[] head;

    public PngParser(byte[] head) {
        this.head = head;
    }

    public PngHeader parse() {
        if (head == null || head.length < HEADER_LENGTH
                || !bytesStartsWith(head, PngHeader.SIGNATURE)) {
            return null;
        }
        long chunkLength = readUnsignedInt32BigEndian(head, 8);
        String chunkType = new String(head, 12, 4, StandardCharsets.US_ASCII);
        if (chunkLength != 13 || !"IHDR".equals(chunkType)) {
            return null;
        }
        return new PngHeader(
                chunkLength,
                chunkType,
                readUnsignedInt32BigEndian(head, 16),
                readUnsignedInt32BigEndian(head, 20),
                head[24] & 0xFF,
                head[25] & 0xFF,
                head[26] & 0xFF,
                head[27] & 0xFF,
                head[28] & 0xFF);
    }
}
