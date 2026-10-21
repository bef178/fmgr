package pd.droidapp.fmgr.fileheader.ogg;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt32LittleEndian;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt63LittleEndian;

public final class OggParser {

    private static final int HEADER_LENGTH = 27;

    private final byte[] head;

    public OggParser(byte[] head) {
        this.head = head;
    }

    public OggHeader parse() {
        if (head == null || head.length < HEADER_LENGTH
                || !bytesStartsWith(head, OggHeader.SIGNATURE)) {
            return null;
        }
        return new OggHeader(
                head[4] & 0xFF,
                head[5] & 0xFF,
                readUnsignedInt63LittleEndian(head, 6),
                readUnsignedInt32LittleEndian(head, 14),
                readUnsignedInt32LittleEndian(head, 18),
                readUnsignedInt32LittleEndian(head, 22),
                head[26] & 0xFF);
    }
}
