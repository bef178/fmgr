package pd.droidapp.fmgr.fileheader.palm;

import java.nio.charset.StandardCharsets;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readNulTerminatedString;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt16LittleEndian;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt32LittleEndian;

public final class PalmDatabaseParser {

    private static final int HEADER_LENGTH = 78;

    private final byte[] head;

    public PalmDatabaseParser(byte[] head) {
        this.head = head;
    }

    public PalmDatabaseHeader parse() {
        if (head == null || head.length < HEADER_LENGTH) {
            return null;
        }
        return new PalmDatabaseHeader(
                readNulTerminatedString(head, 0, 32),
                readUnsignedInt16LittleEndian(head, 32),
                readUnsignedInt16LittleEndian(head, 34),
                readUnsignedInt32LittleEndian(head, 36),
                readUnsignedInt32LittleEndian(head, 40),
                readUnsignedInt32LittleEndian(head, 44),
                readUnsignedInt32LittleEndian(head, 48),
                readUnsignedInt32LittleEndian(head, 52),
                readUnsignedInt32LittleEndian(head, 56),
                new String(head, 60, 4, StandardCharsets.US_ASCII),
                new String(head, 64, 4, StandardCharsets.US_ASCII),
                readUnsignedInt32LittleEndian(head, 68),
                readUnsignedInt32LittleEndian(head, 72),
                readUnsignedInt16LittleEndian(head, 76));
    }

}
