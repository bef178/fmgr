package pd.droidapp.fmgr.fileheader.palm;

import java.io.IOException;
import java.io.RandomAccessFile;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt16LittleEndian;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt32LittleEndian;

public final class PalmDatabaseReader {

    private static final int MIN_RECORDS = 1;
    private static final int MAX_RECORDS = 8192;
    private static final int RECORD_LIST_OFFSET = 78;
    private static final int[] MOBI_MAGIC = new int[] {'M', 'O', 'B', 'I'};

    private final String path;
    public final PalmDatabaseHeader header;

    public PalmDatabaseReader(String path, byte[] head) {
        this.path = path;
        this.header = new PalmDatabaseParser(head).parse();
    }

    public String getMimeType() {
        if (header == null || header.numRecords < MIN_RECORDS || header.numRecords > MAX_RECORDS) {
            return null;
        }
        boolean mobi = "BOOK".equals(header.type) && "MOBI".equals(header.creator);
        boolean palmDoc = "TEXt".equals(header.type) && "REAd".equals(header.creator);
        if ((!mobi && !palmDoc) || !hasValidFirstRecord(mobi)) {
            return null;
        }
        return mobi ? "application/x-mobipocket-ebook" : "application/vnd.palm";
    }

    private boolean hasValidFirstRecord(boolean mobi) {
        int recordHeaderLength = mobi ? 20 : 16;
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            long fileLength = file.length();
            file.seek(RECORD_LIST_OFFSET);
            byte[] offsetBytes = new byte[4];
            file.readFully(offsetBytes);
            long recordOffset = readUnsignedInt32LittleEndian(offsetBytes, 0);
            long recordListEnd = RECORD_LIST_OFFSET + (long) header.numRecords * 8;
            if (recordOffset < recordListEnd || recordOffset > fileLength - recordHeaderLength) {
                return false;
            }
            file.seek(recordOffset);
            byte[] record = new byte[recordHeaderLength];
            file.readFully(record);
            int compression = readUnsignedInt16LittleEndian(record, 0);
            if (compression != 1 && compression != 2 && compression != 17480) {
                return false;
            }
            return !mobi || bytesStartsWith(record, 16, MOBI_MAGIC);
        } catch (IOException ignored) {
            return false;
        }
    }
}
