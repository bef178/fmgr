package pd.droidapp.fmgr.fileheader.gif;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt16LittleEndian;

public final class GifParser {

    private static final int HEADER_LENGTH = 13;

    private final byte[] head;

    public GifParser(byte[] head) {
        this.head = head;
    }

    public GifHeader parse() {
        if (head == null || head.length < HEADER_LENGTH) {
            return null;
        }
        String version;
        if (bytesStartsWith(head, GifHeader.SIGNATURE_87A)) {
            version = "87a";
        } else if (bytesStartsWith(head, GifHeader.SIGNATURE_89A)) {
            version = "89a";
        } else {
            return null;
        }
        return new GifHeader(version,
                readUnsignedInt16LittleEndian(head, 6),
                readUnsignedInt16LittleEndian(head, 8),
                head[10] & 0xFF,
                head[11] & 0xFF,
                head[12] & 0xFF);
    }
}
