package pd.droidapp.fmgr.fileheader.bmff;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt32BigEndian;

public final class BmffParser {

    private static final int[] MAGIC_FTYP = {'f', 't', 'y', 'p'};
    private static final int MAX_COMPATIBLE_BRANDS = 4096;

    private static final int BOX_MOOV = 0x6D6F6F76;
    private static final int BOX_TRAK = 0x7472616B;
    private static final int BOX_MDIA = 0x6D646961;
    private static final int BOX_HDLR = 0x68646C72;
    private static final int HANDLER_VIDE = 0x76696465;
    private static final int HANDLER_SOUN = 0x736F756E;

    private final String path;
    private final byte[] head;

    public BmffParser(String path, byte[] head) {
        this.path = path;
        this.head = head;
    }

    public BmffHeader parse() {
        if (!bytesStartsWith(head, 4, MAGIC_FTYP)) {
            return null;
        }
        long boxSize = readUnsignedInt32BigEndian(head, 0);
        if (head.length < 16 || boxSize < 16) {
            return null;
        }
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            if (boxSize > head.length && boxSize > file.length()) {
                return null;
            }
            long declaredBrands = (boxSize - 16) / 4;
            boolean brandsComplete = declaredBrands <= MAX_COMPATIBLE_BRANDS;
            String[] compatibleBrands = brands(file, declaredBrands);
            if (compatibleBrands == null) {
                compatibleBrands = new String[0];
                brandsComplete = false;
            }
            String majorBrand = brand(8);
            int minorVersion = (int) readUnsignedInt32BigEndian(head, 12);
            int trackMask = trackMask(file);
            return new BmffHeader(boxSize, majorBrand, minorVersion, compatibleBrands, brandsComplete,
                    (trackMask & 1) != 0, (trackMask & 2) != 0);
        } catch (IOException ignored) {
            return null;
        }
    }

    private String brand(int offset) {
        return new String(head, offset, 4, StandardCharsets.US_ASCII);
    }

    private String[] brands(RandomAccessFile file, long declaredBrands) {
        int count = (int) Math.min(declaredBrands, MAX_COMPATIBLE_BRANDS);
        String[] brands = new String[count];
        int fromHead = Math.min(count, (head.length - 16) / 4);
        for (int i = 0; i < fromHead; i++) {
            brands[i] = brand(16 + i * 4);
        }
        if (fromHead == count) {
            return brands;
        }
        try {
            byte[] bytes = new byte[4];
            for (int i = fromHead; i < count; i++) {
                file.seek(16L + (long) i * 4);
                file.readFully(bytes);
                brands[i] = new String(bytes, StandardCharsets.US_ASCII);
            }
            return brands;
        } catch (IOException ignored) {
            return null;
        }
    }

    private int trackMask(RandomAccessFile file) {
        try {
            long fileLength = file.length();
            long offset = 0;
            while (offset + 8 <= fileLength) {
                BmffBox box = BmffBox.read(file, offset, fileLength);
                if (box == null) {
                    break;
                }
                if (box.type == BOX_MOOV) {
                    int mask = collectHandlerMask(file, offset + box.headerLength, offset + box.size, 0);
                    if (mask != 0) {
                        return mask;
                    }
                }
                offset += box.size;
            }
        } catch (IOException ignored) {
        }
        return 0;
    }

    private int collectHandlerMask(RandomAccessFile file, long start, long end, int depth) {
        if (depth > 4) {
            return 0;
        }
        int mask = 0;
        long offset = start;
        try {
            while (offset + 8 <= end) {
                BmffBox box = BmffBox.read(file, offset, end);
                if (box == null) {
                    break;
                }
                if (box.type == BOX_HDLR) {
                    long payload = offset + box.headerLength;
                    if (payload + 12 <= offset + box.size) {
                        file.seek(payload + 8);
                        int handler = file.readInt();
                        if (handler == HANDLER_VIDE) {
                            mask |= 1;
                        } else if (handler == HANDLER_SOUN) {
                            mask |= 2;
                        }
                    }
                } else if (box.type == BOX_MOOV || box.type == BOX_TRAK || box.type == BOX_MDIA) {
                    mask |= collectHandlerMask(file, offset + box.headerLength, offset + box.size, depth + 1);
                }
                if ((mask & 3) == 3) {
                    return mask;
                }
                offset += box.size;
            }
        } catch (IOException ignored) {
        }
        return mask;
    }
}
