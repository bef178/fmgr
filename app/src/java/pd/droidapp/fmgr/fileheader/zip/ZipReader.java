package pd.droidapp.fmgr.fileheader.zip;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

public final class ZipReader {

    private static final int MAX_MIMETYPE_ENTRY_SIZE = 4096;

    private final String path;
    public final ZipHeader header;

    public ZipReader(String path) {
        this(path, null);
    }

    public ZipReader(String path, byte[] head) {
        this.path = path;
        this.header = new ZipParser(path, head).parse();
    }

    public int indexOf(String entryName) {
        if (header == null || header.centralDirectoryRecords == null || entryName == null) {
            return -1;
        }
        for (int i = 0; i < header.centralDirectoryRecords.size(); i++) {
            if (entryName.equals(header.centralDirectoryRecords.get(i).fileName)) {
                return i;
            }
        }
        return -1;
    }

    public boolean hasEntry(String entryName) {
        return indexOf(entryName) >= 0;
    }

    public boolean hasEntryPrefix(String prefix) {
        if (header == null || header.centralDirectoryRecords == null || prefix == null) {
            return false;
        }
        for (CentralDirectoryRecord record : header.centralDirectoryRecords) {
            if (record.fileName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public byte[] readEntry(String entryName) {
        int index = indexOf(entryName);
        if (index < 0) {
            return null;
        }
        return readEntry(index);
    }

    /**
     * Returns the decompressed content or `null` if encrypted, decompression failed or size exceeds `Integer.MAX_VALUE`, etc
     */
    public byte[] readEntry(int index) {
        if (header == null || header.centralDirectoryRecords == null || header.localFileHeaders == null
                || index < 0 || index >= header.centralDirectoryRecords.size()
                || index >= header.localFileHeaders.size()) {
            return null;
        }
        CentralDirectoryRecord record = header.centralDirectoryRecords.get(index);
        if ((record.generalPurposeBitFlags & 0x0001) != 0
                || (record.generalPurposeBitFlags & 0x0040) != 0) {
            return null;    // 已加密
        }
        LocalFileHeader localFileHeader = header.localFileHeaders.get(index);
        if (localFileHeader == null) {
            return null;
        }
        int method = record.compressionMethod;
        long compressedSize = record.compressedSize;
        long uncompressedSize = record.uncompressedSize;
        if ((method != 0 && method != 8)
                || compressedSize < 0 || uncompressedSize < 0
                || compressedSize > Integer.MAX_VALUE || uncompressedSize > Integer.MAX_VALUE) {
            return null;
        }
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            long dataOffset = record.localFileHeaderOffset + localFileHeader.recordLength();
            byte[] compressed = new byte[(int) compressedSize];
            file.seek(dataOffset);
            file.readFully(compressed);
            if (method == 0) {
                return compressed;
            }
            byte[] content = new byte[(int) uncompressedSize];
            Inflater inflater = new Inflater(true);
            try {
                inflater.setInput(compressed);
                int filled = 0;
                while (filled < content.length && !inflater.finished()) {
                    int n = inflater.inflate(content, filled, content.length - filled);
                    if (n <= 0) {
                        break;
                    }
                    filled += n;
                }
                return filled == content.length ? content : null;
            } finally {
                inflater.end();
            }
        } catch (IOException | DataFormatException ignored) {
            return null;
        }
    }

    public String readEntryContentString(String entryName) {
        byte[] data = readEntry(entryName);
        return data != null ? new String(data, StandardCharsets.UTF_8) : null;
    }

    private String readEntryContentString(String entryName, int maxSize) {
        int index = indexOf(entryName);
        if (index < 0) {
            return null;
        }
        CentralDirectoryRecord record = header.centralDirectoryRecords.get(index);
        if (record.compressedSize < 0 || record.compressedSize > maxSize
                || record.uncompressedSize < 0 || record.uncompressedSize > maxSize) {
            return null;
        }
        byte[] data = readEntry(index);
        return data != null ? new String(data, StandardCharsets.UTF_8) : null;
    }

    public String getMimeType() {
        if (header == null) {
            return null;
        }
        if (header.centralDirectoryRecords == null) {
            return "application/zip";
        }
        String mimetype = readEntryContentString("mimetype", MAX_MIMETYPE_ENTRY_SIZE);
        if (Objects.equals(mimetype, "application/epub+zip")) {
            return mimetype;
        }
        if (hasEntry("AndroidManifest.xml")) {
            return hasEntry("classes.jar")
                    && !hasEntry("classes.dex")
                    && !hasEntry("resources.arsc")
                    ? "application/vnd.android.aar" : "application/vnd.android.package-archive";
        }
        if (hasEntry("base/manifest/AndroidManifest.xml")) {
            return "application/vnd.android.bundle";
        }
        if (hasEntry("[Content_Types].xml")) {
            if (hasEntryPrefix("word/")) {
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            } else if (hasEntryPrefix("xl/")) {
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            } else if (hasEntryPrefix("ppt/")) {
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            }
        }
        if (hasEntry("META-INF/MANIFEST.MF")) {
            return "application/java-archive";
        }
        return "application/zip";
    }
}
