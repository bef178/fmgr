package pd.droidapp.fmgr.fileheader.ole2;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt16LittleEndian;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt32LittleEndian;

public final class Ole2Parser {

    private static final int HEADER_LENGTH = 512;
    private static final int DIFAT_ENTRIES = 109;

    private final byte[] head;

    public Ole2Parser(byte[] head) {
        this.head = head;
    }

    public Ole2Header parse() {
        if (head == null || head.length < HEADER_LENGTH
                || !bytesStartsWith(head, Ole2Header.SIGNATURE)) {
            return null;
        }
        int majorVersion = readUnsignedInt16LittleEndian(head, 26);
        int sectorSize;
        long sectorBase;
        if (majorVersion == 3) {
            sectorSize = 512;
            sectorBase = 512;
        } else if (majorVersion == 4) {
            sectorSize = 4096;
            sectorBase = 4096;
        } else {
            return null;
        }
        long numFatSectors = readUnsignedInt32LittleEndian(head, 44);
        if (numFatSectors <= 0 || numFatSectors > Integer.MAX_VALUE) {
            return null;
        }
        int[] difat = new int[DIFAT_ENTRIES];
        for (int i = 0; i < DIFAT_ENTRIES; i++) {
            difat[i] = (int) readUnsignedInt32LittleEndian(head, 76 + 4 * i);
        }
        return new Ole2Header(majorVersion, sectorSize, sectorBase, (int) numFatSectors,
                (int) readUnsignedInt32LittleEndian(head, 48),
                (int) readUnsignedInt32LittleEndian(head, 68),
                (int) readUnsignedInt32LittleEndian(head, 72),
                difat);
    }
}
