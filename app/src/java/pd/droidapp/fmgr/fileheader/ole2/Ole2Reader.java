package pd.droidapp.fmgr.fileheader.ole2;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt16LittleEndian;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt32LittleEndian;

public final class Ole2Reader {

    // 4MB 上限，超出按非 OLE2 处理，避免超大 CFB 的 FAT 目录号数组
    private static final int MAX_FAT_SECTORS = 1 << 20;

    private final String path;
    public final Ole2Header header;

    public Ole2Reader(String path, byte[] head) {
        this.path = path;
        this.header = new Ole2Parser(head).parse();
    }

    public String getMimeType() {
        if (header == null) {
            return null;
        }
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            int sectorSize = header.sectorSize;
            long sectorBase = header.sectorBase;
            int numFatSectors = header.numFatSectors;
            long fileLength = file.length();
            if (numFatSectors <= 0 || numFatSectors > MAX_FAT_SECTORS
                    || (long) numFatSectors * sectorSize > fileLength) {
                return null;
            }
            int entriesPerSector = sectorSize / 4;
            int[] fatSectors = new int[numFatSectors];
            int fromHeader = Math.min(numFatSectors, header.difat.length);
            System.arraycopy(header.difat, 0, fatSectors, 0, fromHeader);
            int numDifatSectors = header.numDifatSectors;
            int difatSector = header.firstDifatSector;
            byte[] sector = new byte[sectorSize];
            for (int i = fromHeader; i < numFatSectors; i += entriesPerSector - 1) {
                if (numDifatSectors-- <= 0 || difatSector < 0) {
                    return null;
                }
                readSector(file, sectorBase, sectorSize, difatSector, sector);
                for (int j = 0; j < entriesPerSector - 1 && i + j < numFatSectors; j++) {
                    fatSectors[i + j] = (int) readUnsignedInt32LittleEndian(sector, 4 * j);
                }
                difatSector = (int) readUnsignedInt32LittleEndian(sector, (entriesPerSector - 1) * 4);
            }
            int dirSector = header.firstDirectorySector;
            for (int step = 0; step < 1024; step++) {
                if (dirSector < 0 || (long) dirSector >= (long) numFatSectors * entriesPerSector) {
                    return null;
                }
                readSector(file, sectorBase, sectorSize, dirSector, sector);
                for (int entry = 0; entry + 128 <= sectorSize; entry += 128) {
                    int nameLength = readUnsignedInt16LittleEndian(sector, entry + 64);
                    if (nameLength < 2 || nameLength > 64) {
                        continue;
                    }
                    String name = new String(sector, entry, nameLength - 2, StandardCharsets.UTF_16LE);
                    if ("WordDocument".equals(name)) {
                        return "application/msword";
                    }
                    if ("Workbook".equals(name) || "Book".equals(name)) {
                        return "application/vnd.ms-excel";
                    }
                    if ("PowerPoint Document".equals(name)) {
                        return "application/vnd.ms-powerpoint";
                    }
                }
                // 按需读 FAT：目录链只需要沿链查找，避免按文件大小分配整张 FAT
                dirSector = readFatEntry(file, sectorBase, sectorSize, fatSectors, numFatSectors,
                        dirSector, sector);
            }
            return null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static int readFatEntry(RandomAccessFile file,
            long sectorBase, int sectorSize, int[] fatSectors, int numFatSectors,
            int sectorNumber, byte[] outBuffer) throws IOException {
        int entriesPerSector = sectorSize / 4;
        if (sectorNumber < 0 || (long) sectorNumber >= (long) numFatSectors * entriesPerSector) {
            return -1;
        }
        int fatSector = fatSectors[sectorNumber / entriesPerSector];
        if (fatSector < 0) {
            return -1;
        }
        readSector(file, sectorBase, sectorSize, fatSector, outBuffer);
        return (int) readUnsignedInt32LittleEndian(outBuffer, (sectorNumber % entriesPerSector) * 4);
    }

    private static void readSector(RandomAccessFile file,
            long sectorBase, int sectorSize, int sectorNumber,
            byte[] outBuffer) throws IOException {
        file.seek(sectorBase + (long) sectorNumber * sectorSize);
        file.readFully(outBuffer);
    }
}
