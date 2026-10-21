package pd.droidapp.fmgr.fileheader.ebml;

import java.nio.charset.StandardCharsets;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;

public final class EbmlParser {

    private static final int ID_EBML_VERSION = 0x4286;
    private static final int ID_EBML_READ_VERSION = 0x42F7;
    private static final int ID_EBML_MAX_ID_LENGTH = 0x42F2;
    private static final int ID_EBML_MAX_SIZE_LENGTH = 0x42F3;
    private static final int ID_DOC_TYPE = 0x4282;
    private static final int ID_DOC_TYPE_VERSION = 0x4287;
    private static final int ID_DOC_TYPE_READ_VERSION = 0x4285;

    private final byte[] head;

    public EbmlParser(byte[] head) {
        this.head = head;
    }

    public EbmlHeader parse() {
        if (head == null || head.length < 8 || !bytesStartsWith(head, EbmlHeader.SIGNATURE)) {
            return null;
        }
        Vint headerSize = readSize(head, 4);
        if (headerSize == null) {
            return null;
        }
        int offset = 4 + headerSize.length;
        long limit = Math.min(offset + headerSize.value, head.length);
        long ebmlVersion = -1;
        long ebmlReadVersion = -1;
        long ebmlMaxIdLength = -1;
        long ebmlMaxSizeLength = -1;
        String docType = null;
        long docTypeVersion = -1;
        long docTypeReadVersion = -1;
        while (offset < limit) {
            Vint id = readId(head, offset);
            if (id == null) {
                break;
            }
            offset += id.length;
            Vint size = readSize(head, offset);
            if (size == null) {
                break;
            }
            offset += size.length;
            long payloadEnd = offset + size.value;
            if (payloadEnd > limit) {
                break;
            }
            int length = (int) size.value;
            switch ((int) id.value) {
                case ID_EBML_VERSION:
                    ebmlVersion = readUnsigned(head, offset, length);
                    break;
                case ID_EBML_READ_VERSION:
                    ebmlReadVersion = readUnsigned(head, offset, length);
                    break;
                case ID_EBML_MAX_ID_LENGTH:
                    ebmlMaxIdLength = readUnsigned(head, offset, length);
                    break;
                case ID_EBML_MAX_SIZE_LENGTH:
                    ebmlMaxSizeLength = readUnsigned(head, offset, length);
                    break;
                case ID_DOC_TYPE:
                    docType = new String(head, offset, length, StandardCharsets.US_ASCII);
                    break;
                case ID_DOC_TYPE_VERSION:
                    docTypeVersion = readUnsigned(head, offset, length);
                    break;
                case ID_DOC_TYPE_READ_VERSION:
                    docTypeReadVersion = readUnsigned(head, offset, length);
                    break;
            }
            offset = (int) payloadEnd;
        }
        return new EbmlHeader(ebmlVersion, ebmlReadVersion, ebmlMaxIdLength, ebmlMaxSizeLength,
                docType, docTypeVersion, docTypeReadVersion);
    }

    private static Vint readId(byte[] bytes, int offset) {
        if (offset >= bytes.length) {
            return null;
        }
        int length = vintLength(bytes[offset]);
        if (length < 0 || length > 4 || offset + length > bytes.length) {
            return null;
        }
        long value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFF);
        }
        return new Vint(length, value);
    }

    private static Vint readSize(byte[] bytes, int offset) {
        if (offset >= bytes.length) {
            return null;
        }
        int length = vintLength(bytes[offset]);
        if (length < 0 || offset + length > bytes.length) {
            return null;
        }
        long value = bytes[offset] & (0xFF >> length);
        for (int i = 1; i < length; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFF);
        }
        if (value == (1L << (7 * length)) - 1) {
            return null;    // 未知长度
        }
        return new Vint(length, value);
    }

    private static int vintLength(byte first) {
        int mask = 0x80;
        for (int length = 1; length <= 8; length++) {
            if ((first & mask) != 0) {
                return length;
            }
            mask >>= 1;
        }
        return -1;
    }

    private static long readUnsigned(byte[] bytes, int offset, int length) {
        if (length > 8 || offset < 0 || offset + length > bytes.length) {
            return -1;
        }
        long value = 0;
        for (int i = 0; i < length; i++) {
            value = (value << 8) | (bytes[offset + i] & 0xFF);
        }
        return value;
    }

    private static final class Vint {
        final int length;
        final long value;

        Vint(int length, long value) {
            this.length = length;
            this.value = value;
        }
    }
}
