package pd.droidapp.fmgr.fileheader.ogg;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesContains;

public final class OggReader {

    private static final int[] THEORA = new int[] {0x80, 't', 'h', 'e', 'o', 'r', 'a'};

    private final byte[] head;
    public final OggHeader header;

    public OggReader(byte[] head) {
        this.head = head;
        this.header = new OggParser(head).parse();
    }

    public String getMimeType() {
        if (header == null) {
            return null;
        }
        return bytesContains(head, THEORA) ? "video/ogg" : "audio/ogg";
    }
}
