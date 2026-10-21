package pd.droidapp.fmgr.fileheader;

import java.nio.charset.StandardCharsets;

import pd.util.Int8ArrayExtension;

public final class BytesReadUtil {

    public static boolean bytesContains(byte[] bytes, int[] magic) {
        for (int i = 0; i + magic.length <= bytes.length; i++) {
            if (bytesStartsWith(bytes, i, magic)) {
                return true;
            }
        }
        return false;
    }

    public static boolean bytesStartsWith(byte[] bytes, int[] magic) {
        return bytesStartsWith(bytes, 0, magic);
    }

    public static boolean bytesStartsWith(byte[] bytes, int offset, int[] magic) {
        if (magic == null || offset < 0 || offset + magic.length > bytes.length) {
            return false;
        }
        return Int8ArrayExtension.startsWith(bytes, offset, bytes.length, magic, 0, magic.length);
    }

    public static int readUnsignedInt16BigEndian(byte[] bytes, int offset) {
        if (offset < 0 || offset + 2 > bytes.length) {
            return -1;
        }
        return Int8ArrayExtension.readUnsignedInt16BigEndian(bytes, offset);
    }

    public static int readUnsignedInt16LittleEndian(byte[] bytes, int offset) {
        if (offset < 0 || offset + 2 > bytes.length) {
            return -1;
        }
        return Int8ArrayExtension.readUnsignedInt16LittleEndian(bytes, offset);
    }

    public static long readUnsignedInt32BigEndian(byte[] bytes, int offset) {
        if (offset < 0 || offset + 4 > bytes.length) {
            return -1;
        }
        return Int8ArrayExtension.readUnsignedInt32BigEndian(bytes, offset);
    }

    public static long readUnsignedInt32LittleEndian(byte[] bytes, int offset) {
        if (offset < 0 || offset + 4 > bytes.length) {
            return -1;
        }
        return Int8ArrayExtension.readUnsignedInt32LittleEndian(bytes, offset);
    }

    public static long readUnsignedInt63LittleEndian(byte[] bytes, int offset) {
        if (offset < 0 || offset + 8 > bytes.length) {
            return -1;
        }
        return Int8ArrayExtension.readUnsignedInt63LittleEndian(bytes, offset);
    }

    public static String readNulTerminatedString(byte[] bytes, int startIndex, int endIndex) {
        if (bytes == null || startIndex < 0 || endIndex > bytes.length || startIndex > endIndex) {
            return null;
        }
        int i = startIndex;
        while (i < endIndex && bytes[i] != 0) {
            i++;
        }
        return new String(bytes, startIndex, i - startIndex, StandardCharsets.UTF_8);
    }
}
