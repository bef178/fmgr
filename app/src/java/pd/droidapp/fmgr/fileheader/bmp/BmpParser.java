package pd.droidapp.fmgr.fileheader.bmp;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt16LittleEndian;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt32LittleEndian;

public final class BmpParser {

    private static final int HEADER_LENGTH = 18;

    private final byte[] head;

    public BmpParser(byte[] head) {
        this.head = head;
    }

    public BmpHeader parse() {
        if (head == null || head.length < HEADER_LENGTH
                || !bytesStartsWith(head, BmpHeader.SIGNATURE)) {
            return null;
        }
        long dibSize = readUnsignedInt32LittleEndian(head, 14);
        if (dibSize != 12 && dibSize != 40 && dibSize != 52 && dibSize != 56
                && dibSize != 64 && dibSize != 108 && dibSize != 124) {
            return null;
        }
        int width = 0;
        int height = 0;
        if (dibSize == 12) {
            if (head.length >= 22) {
                width = readUnsignedInt16LittleEndian(head, 18);
                height = readUnsignedInt16LittleEndian(head, 20);
            }
        } else if (head.length >= 26) {
            width = (int) readUnsignedInt32LittleEndian(head, 18);
            height = (int) readUnsignedInt32LittleEndian(head, 22);
        }
        return new BmpHeader(
                readUnsignedInt32LittleEndian(head, 2),
                readUnsignedInt32LittleEndian(head, 10),
                dibSize, width, height);
    }
}
