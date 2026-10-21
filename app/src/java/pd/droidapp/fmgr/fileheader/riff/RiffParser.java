package pd.droidapp.fmgr.fileheader.riff;

import java.nio.charset.StandardCharsets;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt32LittleEndian;

public final class RiffParser {

    private static final int HEADER_LENGTH = 12;

    private final byte[] head;

    public RiffParser(byte[] head) {
        this.head = head;
    }

    public RiffHeader parse() {
        if (head == null || head.length < HEADER_LENGTH
                || !bytesStartsWith(head, RiffHeader.SIGNATURE)) {
            return null;
        }
        long chunkSize = readUnsignedInt32LittleEndian(head, 4);
        String formType = new String(head, 8, 4, StandardCharsets.US_ASCII);
        return new RiffHeader(chunkSize, formType);
    }
}
