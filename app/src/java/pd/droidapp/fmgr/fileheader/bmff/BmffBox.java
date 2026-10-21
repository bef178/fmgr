package pd.droidapp.fmgr.fileheader.bmff;

import java.io.IOException;
import java.io.RandomAccessFile;

public final class BmffBox {

    public final long size;
    public final int type;
    public final long headerLength;

    private BmffBox(long size, int type, long headerLength) {
        this.size = size;
        this.type = type;
        this.headerLength = headerLength;
    }

    public static BmffBox read(RandomAccessFile file, long offset, long limit) throws IOException {
        file.seek(offset);
        long size = file.readInt() & 0xFFFFFFFFL;
        int type = file.readInt();
        long headerLength = 8;
        if (size == 1) {
            if (offset + 16 > limit) {
                return null;
            }
            size = file.readLong();
            headerLength = 16;
        } else if (size == 0) {
            size = limit - offset;
        }
        if (size < headerLength || size > limit - offset) {
            return null;
        }
        return new BmffBox(size, type, headerLength);
    }
}
