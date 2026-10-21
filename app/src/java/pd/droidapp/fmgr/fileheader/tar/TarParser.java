package pd.droidapp.fmgr.fileheader.tar;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readNulTerminatedString;

public final class TarParser {

    private static final int HEADER_LENGTH = 512;
    private static final int CHECKSUM_OFFSET = 148;
    private static final int CHECKSUM_LENGTH = 8;

    private final byte[] head;

    public TarParser(byte[] head) {
        this.head = head;
    }

    public TarHeader parse() {
        if (head == null || head.length < HEADER_LENGTH
                || !bytesStartsWith(head, 257, TarHeader.MAGIC)) {
            return null;
        }
        long checksum = parseOctal(head, CHECKSUM_OFFSET, CHECKSUM_LENGTH);
        if (checksum < 0) {
            return null;
        }
        long unsignedSum = 0;
        long signedSum = 0;
        for (int i = 0; i < HEADER_LENGTH; i++) {
            if (i >= CHECKSUM_OFFSET && i < CHECKSUM_OFFSET + CHECKSUM_LENGTH) {
                unsignedSum += 0x20;
                signedSum += 0x20;
            } else {
                unsignedSum += head[i] & 0xFF;
                signedSum += head[i];
            }
        }
        if (checksum != unsignedSum && checksum != signedSum) {
            return null;
        }
        return new TarHeader(
                readNulTerminatedString(head, 0, 100),
                parseOctal(head, 100, 8),
                parseOctal(head, 108, 8),
                parseOctal(head, 116, 8),
                parseOctal(head, 124, 12),
                parseOctal(head, 136, 12),
                (int) checksum,
                head[156] & 0xFF,
                readNulTerminatedString(head, 157, 257),
                readNulTerminatedString(head, 265, 297),
                readNulTerminatedString(head, 297, 329),
                readNulTerminatedString(head, 345, 500));
    }

    private static long parseOctal(byte[] bytes, int offset, int length) {
        long value = 0;
        int end = offset + length;
        int i = offset;
        while (i < end && (bytes[i] == 0 || bytes[i] == ' ')) {
            i++;
        }
        for (; i < end; i++) {
            int b = bytes[i] & 0xFF;
            if (b == 0 || b == ' ') {
                break;
            }
            if (b < '0' || b > '7') {
                return -1;
            }
            value = value * 8 + (b - '0');
        }
        return value;
    }

}
