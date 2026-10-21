package pd.droidapp.fmgr.fileheader.zip;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt16LittleEndian;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt32LittleEndian;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt63LittleEndian;

final class ZipParser {

    private static final int MAX_EXTENSIBLE_DATA = 1 << 20;
    private static final int MAX_EXTRA_DATA_SIZE = 1 << 20;
    private static final int SHORT_TAIL_LENGTH = 4096;
    private static final int MAX_TAIL_LENGTH = 65577;

    private final String path;
    private final byte[] head;

    ZipParser(String path) {
        this(path, null);
    }

    ZipParser(String path, byte[] head) {
        this.path = path;
        this.head = head;
    }

    ZipHeader parse() {
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            long fileLength = file.length();
            byte[] fileHead = head;
            if (fileHead == null || fileHead.length < 4) {
                fileHead = new byte[(int) Math.min(fileLength, 4)];
                file.readFully(fileHead);
            }
            boolean temporarySpanning = bytesStartsWith(fileHead, SpanningMarker.TEMPORARY_SIGNATURE);
            SpanningMarker spanningMarker = bytesStartsWith(fileHead, SpanningMarker.SIGNATURE)
                    || temporarySpanning ? new SpanningMarker(temporarySpanning) : null;
            boolean zipHead = bytesStartsWith(fileHead, LocalFileHeader.SIGNATURE)
                    || bytesStartsWith(fileHead, EndOfCentralDirectory.SIGNATURE)
                    || spanningMarker != null;
            if (fileLength < 22) {
                return null;
            }
            byte[] tail = readTail(file, fileLength, SHORT_TAIL_LENGTH);
            if (tail == null) {
                return null;
            }
            int eocdOffset = findEocd(tail);
            EndOfCentralDirectory eocd = eocdOffset >= 0 ? readEocd(tail, eocdOffset) : null;
            if (fileLength > SHORT_TAIL_LENGTH
                    && (eocd == null || eocdOffset < 20
                    || eocdOffset + 22 + eocd.commentLength != tail.length)) {
                tail = readTail(file, fileLength, MAX_TAIL_LENGTH);
                if (tail == null) {
                    return null;
                }
                eocdOffset = findEocd(tail);
                eocd = eocdOffset >= 0 ? readEocd(tail, eocdOffset) : null;
            }
            if (eocdOffset < 0 || eocd == null) {
                return null;
            }
            if (!zipHead && eocdOffset + 22 + eocd.commentLength != tail.length) {
                return null;    // 文件头不是 ZIP 且 EOCD 不在尾部，按非压缩包处理
            }
            boolean hasEocdLocator = eocdOffset >= 20
                    && bytesStartsWith(tail, eocdOffset - 20, EndOfCentralDirectory64Locator.SIGNATURE);
            EndOfCentralDirectory64Locator eocdLocator = hasEocdLocator
                    ? readEocdLocator(tail, eocdOffset)
                    : null;
            EndOfCentralDirectory64 eocd64 = eocdLocator != null
                    ? readEocd64(file, eocdLocator, fileLength)
                    : null;
            if (hasEocdLocator && (eocdLocator == null || eocd64 == null)) {
                return null;
            }
            int diskNumber = eocd64 != null ? eocd64.diskNumber : eocd.diskNumber;
            int centralDirectoryDisk = eocd64 != null
                    ? eocd64.centralDirectoryDiskNumber : eocd.centralDirectoryDiskNumber;
            long centralDirectoryOffset = eocd64 != null
                    ? eocd64.centralDirectoryOffset : eocd.centralDirectoryOffset;
            long centralDirectoryLength = eocd64 != null
                    ? eocd64.centralDirectoryLength : eocd.centralDirectoryLength;
            long numCentralDirectoryRecords = eocd64 != null
                    ? eocd64.numCentralDirectoryRecords : eocd.numCentralDirectoryRecords;
            if (centralDirectoryDisk != diskNumber
                    || (eocd64 == null && centralDirectoryOffset == 0xFFFFFFFFL)
                    || centralDirectoryOffset >= fileLength
                    || centralDirectoryLength < 0
                    || numCentralDirectoryRecords < 0
                    || numCentralDirectoryRecords > Integer.MAX_VALUE) {
                return incomplete(eocd, eocd64, eocdLocator, spanningMarker, null, null);
            }
            long centralDirectoryStart = centralDirectoryOffset;
            ArchiveExtraDataRecord archiveExtraDataRecord = null;
            ArchiveDecryptionHeader archiveDecryptionHeader = null;
            if (startsWith(file, centralDirectoryStart, ArchiveExtraDataRecord.SIGNATURE)) {
                archiveExtraDataRecord = readArchiveExtraDataRecord(file, centralDirectoryStart, fileLength);
                if (archiveExtraDataRecord != null) {
                    centralDirectoryStart += 8 + archiveExtraDataRecord.data.length;
                }
            } else if (!startsWith(file, centralDirectoryStart, CentralDirectoryRecord.SIGNATURE)) {
                archiveDecryptionHeader = readArchiveDecryptionHeader(file, centralDirectoryStart, fileLength);
                if (archiveDecryptionHeader != null) {
                    archiveExtraDataRecord = readArchiveExtraDataRecord(file,
                            centralDirectoryStart + archiveDecryptionHeader.data.length, fileLength);
                    return incomplete(eocd, eocd64, eocdLocator, spanningMarker,
                            archiveExtraDataRecord, archiveDecryptionHeader);
                }
            }
            return readCentralDirectory(file, centralDirectoryStart, (int) numCentralDirectoryRecords,
                    eocd, eocd64, eocdLocator, diskNumber, spanningMarker,
                    archiveExtraDataRecord, archiveDecryptionHeader);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static int findEocd(byte[] tail) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = tail.length - 22; i >= 0; i--) {
            if (!bytesStartsWith(tail, i, EndOfCentralDirectory.SIGNATURE)) {
                continue;
            }
            int commentLength = readUnsignedInt16LittleEndian(tail, i + 20);
            if (commentLength < 0 || i + 22 + commentLength > tail.length) {
                continue;
            }
            candidates.add(i);
        }
        int chosen = -1;
        int maxEnd = -1;
        for (int k = candidates.size() - 1; k >= 0; k--) {
            int i = candidates.get(k);
            if (i < maxEnd) {
                continue;    // 落在更早候选的注释里，不是 EOCD
            }
            int end = i + 22 + readUnsignedInt16LittleEndian(tail, i + 20);
            maxEnd = Math.max(maxEnd, end);
            chosen = i;
            if (end == tail.length) {
                return i;    // 结束于文件末尾
            }
        }
        return chosen;
    }

    private static EndOfCentralDirectory readEocd(byte[] tail, int offset) {
        int diskNumber = readUnsignedInt16LittleEndian(tail, offset + 4);
        int centralDirectoryDisk = readUnsignedInt16LittleEndian(tail, offset + 6);
        int entriesOnDisk = readUnsignedInt16LittleEndian(tail, offset + 8);
        int totalEntries = readUnsignedInt16LittleEndian(tail, offset + 10);
        long centralDirectorySize = readUnsignedInt32LittleEndian(tail, offset + 12);
        long centralDirectoryOffset = readUnsignedInt32LittleEndian(tail, offset + 16);
        int commentLength = readUnsignedInt16LittleEndian(tail, offset + 20);
        if (diskNumber < 0 || centralDirectoryDisk < 0 || entriesOnDisk < 0 || totalEntries < 0
                || centralDirectorySize < 0 || centralDirectoryOffset < 0 || commentLength < 0) {
            return null;
        }
        if (offset + 22 + commentLength > tail.length) {
            return null;
        }
        byte[] comment = commentLength == 0 ? new byte[0]
                : Arrays.copyOfRange(tail, offset + 22, offset + 22 + commentLength);
        return new EndOfCentralDirectory(diskNumber, centralDirectoryDisk, entriesOnDisk, totalEntries,
                centralDirectorySize, centralDirectoryOffset, commentLength, comment);
    }

    private static EndOfCentralDirectory64Locator readEocdLocator(byte[] tail, int eocdOffset) {
        int locatorOffset = eocdOffset - 20;
        int diskWithEocd64 = (int) readUnsignedInt32LittleEndian(tail, locatorOffset + 4);
        long eocd64Offset = readUnsignedInt63LittleEndian(tail, locatorOffset + 8);
        int totalDisks = (int) readUnsignedInt32LittleEndian(tail, locatorOffset + 16);
        if (diskWithEocd64 < 0 || totalDisks < 1 || eocd64Offset < 0) {
            return null;
        }
        return new EndOfCentralDirectory64Locator(diskWithEocd64, eocd64Offset, totalDisks);
    }

    private static EndOfCentralDirectory64 readEocd64(RandomAccessFile file,
            EndOfCentralDirectory64Locator locator, long fileLength) {
        long eocd64Offset = locator.eocd64Offset;
        if (eocd64Offset > fileLength - 56) {
            return null;
        }
        try {
            file.seek(eocd64Offset);
            byte[] record = new byte[56];
            if (file.read(record) != record.length
                    || !bytesStartsWith(record, 0, EndOfCentralDirectory64.SIGNATURE)) {
                return null;
            }
            long recordLength = readUnsignedInt63LittleEndian(record, 4);
            int diskNumber = (int) readUnsignedInt32LittleEndian(record, 16);
            int centralDirectoryDisk = (int) readUnsignedInt32LittleEndian(record, 20);
            long entriesOnDisk = readUnsignedInt63LittleEndian(record, 24);
            long totalEntries = readUnsignedInt63LittleEndian(record, 32);
            long centralDirectoryLength = readUnsignedInt63LittleEndian(record, 40);
            long centralDirectoryOffset = readUnsignedInt63LittleEndian(record, 48);
            if (recordLength < 44 || diskNumber < 0 || diskNumber != locator.eocd64DiskNumber
                    || centralDirectoryDisk < 0
                    || entriesOnDisk < 0 || totalEntries < 0
                    || centralDirectoryLength < 0 || centralDirectoryOffset < 0) {
                return null;
            }
            long extensibleLength = recordLength - 44;
            byte[] extensibleData = new byte[0];
            if (extensibleLength > 0) {
                if (extensibleLength <= MAX_EXTENSIBLE_DATA
                        && extensibleLength <= fileLength - eocd64Offset - 56) {
                    byte[] data = new byte[(int) extensibleLength];
                    file.seek(eocd64Offset + 56);
                    if (file.read(data) != data.length) {
                        return null;
                    }
                    extensibleData = data;
                } else {
                    extensibleData = null;
                }
            }
            return new EndOfCentralDirectory64(recordLength,
                    readUnsignedInt16LittleEndian(record, 12),
                    readUnsignedInt16LittleEndian(record, 14),
                    diskNumber, centralDirectoryDisk, entriesOnDisk, totalEntries,
                    centralDirectoryLength, centralDirectoryOffset, extensibleData);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static Zip64Values resolveZip64(byte[] extraField, long uncompressedSize, long compressedSize,
            long localFileHeaderOffset, int startDiskNumber) {
        Zip64Values values = new Zip64Values(uncompressedSize, compressedSize, localFileHeaderOffset,
                startDiskNumber);
        if (uncompressedSize != 0xFFFFFFFFL && compressedSize != 0xFFFFFFFFL
                && localFileHeaderOffset != 0xFFFFFFFFL && startDiskNumber != 0xFFFF) {
            return values;    // 固定字段都不是哨兵值，没有 ZIP64 extra field
        }
        for (int offset = 0; offset + 4 <= extraField.length; ) {
            int headerId = readUnsignedInt16LittleEndian(extraField, offset);
            int dataSize = readUnsignedInt16LittleEndian(extraField, offset + 2);
            int dataOffset = offset + 4;
            if (dataOffset + dataSize > extraField.length) {
                break;
            }
            if (headerId == 0x0001) {
                int fieldOffset = dataOffset;
                if (values.uncompressedSize == 0xFFFFFFFFL && fieldOffset + 8 <= dataOffset + dataSize) {
                    values.uncompressedSize = readUnsignedInt63LittleEndian(extraField, fieldOffset);
                    fieldOffset += 8;
                }
                if (values.compressedSize == 0xFFFFFFFFL && fieldOffset + 8 <= dataOffset + dataSize) {
                    values.compressedSize = readUnsignedInt63LittleEndian(extraField, fieldOffset);
                    fieldOffset += 8;
                }
                if (values.localFileHeaderOffset == 0xFFFFFFFFL && fieldOffset + 8 <= dataOffset + dataSize) {
                    values.localFileHeaderOffset = readUnsignedInt63LittleEndian(extraField, fieldOffset);
                    fieldOffset += 8;
                }
                if (values.startDiskNumber == 0xFFFF && fieldOffset + 4 <= dataOffset + dataSize) {
                    values.startDiskNumber = (int) readUnsignedInt32LittleEndian(extraField, fieldOffset);
                }
                break;
            }
            offset = dataOffset + dataSize;
        }
        return values;
    }

    private static boolean hasZip64ExtraField(byte[] extraField) {
        for (int offset = 0; offset + 4 <= extraField.length; ) {
            int headerId = readUnsignedInt16LittleEndian(extraField, offset);
            int dataSize = readUnsignedInt16LittleEndian(extraField, offset + 2);
            if (headerId == 0x0001) {
                return true;
            }
            if (offset + 4 + dataSize > extraField.length) {
                break;
            }
            offset += 4 + dataSize;
        }
        return false;
    }

    private static final class Zip64Values {
        long uncompressedSize;
        long compressedSize;
        long localFileHeaderOffset;
        int startDiskNumber;

        Zip64Values(long uncompressedSize, long compressedSize, long localFileHeaderOffset,
                int startDiskNumber) {
            this.uncompressedSize = uncompressedSize;
            this.compressedSize = compressedSize;
            this.localFileHeaderOffset = localFileHeaderOffset;
            this.startDiskNumber = startDiskNumber;
        }
    }

    private ZipHeader readCentralDirectory(RandomAccessFile file, long centralDirectoryOffset,
            int numCentralDirectoryRecords, EndOfCentralDirectory eocd, EndOfCentralDirectory64 eocd64,
            EndOfCentralDirectory64Locator eocdLocator, int diskNumber, SpanningMarker spanningMarker,
            ArchiveExtraDataRecord archiveExtraDataRecord,
            ArchiveDecryptionHeader archiveDecryptionHeader) throws IOException {
        List<CentralDirectoryRecord> centralDirectory = new ArrayList<>();
        List<LocalFileHeader> localFileHeaders = new ArrayList<>();
        boolean complete = true;
        file.seek(centralDirectoryOffset);
        byte[] entryHeader = new byte[46];
        for (int i = 0; i < numCentralDirectoryRecords; i++) {
            if (file.read(entryHeader) != entryHeader.length) {
                complete = false;
                break;
            }
            if (!bytesStartsWith(entryHeader, 0, CentralDirectoryRecord.SIGNATURE)) {
                complete = false;
                break;
            }
            int fileNameLength = readUnsignedInt16LittleEndian(entryHeader, 28);
            int extraFieldLength = readUnsignedInt16LittleEndian(entryHeader, 30);
            int fileCommentLength = readUnsignedInt16LittleEndian(entryHeader, 32);
            if (fileNameLength < 0 || extraFieldLength < 0 || fileCommentLength < 0) {
                complete = false;
                break;
            }
            long nextEntry = file.getFilePointer() + fileNameLength + extraFieldLength + fileCommentLength;
            if (fileNameLength == 0) {
                complete = false;
                file.seek(nextEntry);
                continue;
            }
            byte[] fileNameBytes = new byte[fileNameLength];
            if (file.read(fileNameBytes) != fileNameLength) {
                complete = false;
                break;
            }
            byte[] extraField = new byte[extraFieldLength];
            if (file.read(extraField) != extraFieldLength) {
                complete = false;
                break;
            }
            byte[] fileComment = new byte[fileCommentLength];
            if (file.read(fileComment) != fileCommentLength) {
                complete = false;
                break;
            }
            String fileName = new String(fileNameBytes, StandardCharsets.UTF_8);
            Zip64Values zip64 = resolveZip64(extraField,
                    readUnsignedInt32LittleEndian(entryHeader, 24),
                    readUnsignedInt32LittleEndian(entryHeader, 20),
                    readUnsignedInt32LittleEndian(entryHeader, 42),
                    readUnsignedInt16LittleEndian(entryHeader, 34));
            long compressedSize = zip64.compressedSize;
            long uncompressedSize = zip64.uncompressedSize;
            long localFileHeaderOffset = zip64.localFileHeaderOffset;
            int startDiskNumber = zip64.startDiskNumber;
            LocalFileHeader localFileHeader = startDiskNumber == diskNumber
                    && localFileHeaderOffset >= 0
                    ? readLocalFileHeader(file, localFileHeaderOffset, compressedSize, uncompressedSize)
                    : null;
            centralDirectory.add(new CentralDirectoryRecord(
                    readUnsignedInt16LittleEndian(entryHeader, 4),
                    readUnsignedInt16LittleEndian(entryHeader, 6),
                    readUnsignedInt16LittleEndian(entryHeader, 8),
                    readUnsignedInt16LittleEndian(entryHeader, 10),
                    readUnsignedInt16LittleEndian(entryHeader, 12),
                    readUnsignedInt16LittleEndian(entryHeader, 14),
                    readUnsignedInt32LittleEndian(entryHeader, 16),
                    compressedSize,
                    uncompressedSize,
                    fileNameLength, extraFieldLength, fileCommentLength,
                    startDiskNumber,
                    readUnsignedInt16LittleEndian(entryHeader, 36),
                    readUnsignedInt32LittleEndian(entryHeader, 38),
                    localFileHeaderOffset, fileName, extraField, fileComment));
            localFileHeaders.add(localFileHeader);
            file.seek(nextEntry);
        }
        DigitalSignature digitalSignature = complete
                ? readDigitalSignature(file, file.getFilePointer(), file.length())
                : null;
        return new ZipHeader(eocd, eocdLocator, eocd64, digitalSignature,
                complete ? centralDirectory : null,
                archiveExtraDataRecord, archiveDecryptionHeader,
                complete ? localFileHeaders : null, spanningMarker);
    }

    private static LocalFileHeader readLocalFileHeader(RandomAccessFile file, long offset,
            long compressedSize, long uncompressedSize) {
        try {
            file.seek(offset);
            byte[] header = new byte[30];
            if (file.read(header) != header.length) {
                return null;
            }
            if (!bytesStartsWith(header, 0, LocalFileHeader.SIGNATURE)) {
                return null;
            }
            int fileNameLength = readUnsignedInt16LittleEndian(header, 26);
            int extraFieldLength = readUnsignedInt16LittleEndian(header, 28);
            if (fileNameLength < 0 || extraFieldLength < 0) {
                return null;
            }
            byte[] fileNameBytes = new byte[fileNameLength];
            if (file.read(fileNameBytes) != fileNameLength) {
                return null;
            }
            byte[] extraField = new byte[extraFieldLength];
            if (file.read(extraField) != extraFieldLength) {
                return null;
            }
            String fileName = new String(fileNameBytes, 0, fileNameLength, StandardCharsets.UTF_8);
            long rawUncompressedSize = readUnsignedInt32LittleEndian(header, 22);
            long rawCompressedSize = readUnsignedInt32LittleEndian(header, 18);
            Zip64Values lfhZip64 = resolveZip64(extraField,
                    rawUncompressedSize, rawCompressedSize, -1, 0);
            int generalPurposeBitFlags = readUnsignedInt16LittleEndian(header, 6);
            long dataOffset = offset + 30 + fileNameLength + extraFieldLength;
            DataDescriptor dataDescriptor = null;
            if ((generalPurposeBitFlags & 0x0008) != 0 && compressedSize >= 0
                    && compressedSize != 0xFFFFFFFFL) {
                // 描述符宽度按条目是否为 ZIP64 决定：forced-zip64 的小条目也写 8 字节
                boolean zip64Descriptor = compressedSize > 0xFFFFFFFFL || uncompressedSize > 0xFFFFFFFFL
                        || rawCompressedSize == 0xFFFFFFFFL || rawUncompressedSize == 0xFFFFFFFFL
                        || hasZip64ExtraField(extraField);
                dataDescriptor = readDataDescriptor(file, dataOffset + compressedSize, zip64Descriptor);
            }
            return new LocalFileHeader(
                    readUnsignedInt16LittleEndian(header, 4),
                    generalPurposeBitFlags,
                    readUnsignedInt16LittleEndian(header, 8),
                    readUnsignedInt16LittleEndian(header, 10),
                    readUnsignedInt16LittleEndian(header, 12),
                    readUnsignedInt32LittleEndian(header, 14),
                    lfhZip64.compressedSize,
                    lfhZip64.uncompressedSize,
                    fileNameLength, extraFieldLength, fileName, extraField, dataDescriptor);
        } catch (IOException ignored) {
            return null;
        }
    }

    private static DataDescriptor readDataDescriptor(RandomAccessFile file, long offset, boolean zip64) {
        try {
            file.seek(offset);
            byte[] bytes = new byte[zip64 ? 24 : 16];
            int nRead = file.read(bytes);
            int start = nRead >= 4 && bytesStartsWith(bytes, 0, DataDescriptor.SIGNATURE) ? 4 : 0;
            int sizeLength = zip64 ? 8 : 4;
            if (nRead < start + 4 + sizeLength * 2) {
                return null;
            }
            return new DataDescriptor(start != 0,
                    readUnsignedInt32LittleEndian(bytes, start),
                    zip64 ? readUnsignedInt63LittleEndian(bytes, start + 4)
                            : readUnsignedInt32LittleEndian(bytes, start + 4),
                    zip64 ? readUnsignedInt63LittleEndian(bytes, start + 12)
                            : readUnsignedInt32LittleEndian(bytes, start + 8));
        } catch (IOException ignored) {
            return null;
        }
    }

    private static DigitalSignature readDigitalSignature(RandomAccessFile file, long offset,
            long fileLength) {
        if (offset < 0 || offset + 6 > fileLength) {
            return null;
        }
        try {
            file.seek(offset);
            byte[] header = new byte[6];
            if (file.read(header) != 6 || !bytesStartsWith(header, 0, DigitalSignature.SIGNATURE)) {
                return null;
            }
            int length = readUnsignedInt16LittleEndian(header, 4);
            if (length < 0 || offset + 6 + length > fileLength) {
                return null;
            }
            byte[] data = new byte[length];
            return file.read(data) == data.length ? new DigitalSignature(data) : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static ArchiveExtraDataRecord readArchiveExtraDataRecord(RandomAccessFile file,
            long offset, long fileLength) {
        if (offset < 0 || offset + 8 > fileLength
                || !startsWith(file, offset, ArchiveExtraDataRecord.SIGNATURE)) {
            return null;
        }
        long length = readUnsignedInt32(file, offset + 4, fileLength);
        if (length < 0 || length > MAX_EXTRA_DATA_SIZE || length > fileLength - offset - 8) {
            return null;
        }
        try {
            file.seek(offset + 8);
            byte[] data = new byte[(int) length];
            return file.read(data) == data.length ? new ArchiveExtraDataRecord(data) : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static ArchiveDecryptionHeader readArchiveDecryptionHeader(RandomAccessFile file,
            long offset, long fileLength) {
        if (offset < 0 || offset + 6 > fileLength) {
            return null;
        }
        try {
            file.seek(offset);
            byte[] bytes = new byte[2];
            if (file.read(bytes) != 2) {
                return null;
            }
            int ivSize = readUnsignedInt16LittleEndian(bytes, 0);
            if (ivSize < 0 || offset + 6 + ivSize > fileLength) {
                return null;
            }
            long size = readUnsignedInt32(file, offset + 2 + ivSize, fileLength);
            if (size < 2 || size > fileLength - offset - 6 - ivSize) {
                return null;
            }
            long length = 6 + ivSize + size;
            if (length > MAX_EXTRA_DATA_SIZE) {
                return null;
            }
            long formatOffset = offset + 6 + ivSize;
            file.seek(formatOffset);
            byte[] format = new byte[2];
            if (file.read(format) != 2 || readUnsignedInt16LittleEndian(format, 0) != 3) {
                return null;
            }
            byte[] data = new byte[(int) length];
            file.seek(offset);
            return file.read(data) == data.length ? new ArchiveDecryptionHeader(data) : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static byte[] readTail(RandomAccessFile file, long fileLength, int maxLength)
            throws IOException {
        int tailLength = (int) Math.min(fileLength, maxLength);
        byte[] tail = new byte[tailLength];
        file.seek(fileLength - tailLength);
        return file.read(tail) == tailLength ? tail : null;
    }

    private static long readUnsignedInt32(RandomAccessFile file, long offset, long fileLength) {
        if (offset < 0 || offset + 4 > fileLength) {
            return -1;
        }
        try {
            file.seek(offset);
            byte[] bytes = new byte[4];
            return file.read(bytes) == 4 ? readUnsignedInt32LittleEndian(bytes, 0) : -1;
        } catch (IOException ignored) {
            return -1;
        }
    }

    private static boolean startsWith(RandomAccessFile file, long offset, int[] magic) {
        if (offset < 0) {
            return false;
        }
        try {
            byte[] bytes = new byte[magic.length];
            file.seek(offset);
            return file.read(bytes) == bytes.length && bytesStartsWith(bytes, 0, magic);
        } catch (IOException ignored) {
            return false;
        }
    }

    private ZipHeader incomplete(EndOfCentralDirectory eocd, EndOfCentralDirectory64 eocd64,
            EndOfCentralDirectory64Locator eocdLocator, SpanningMarker spanningMarker,
            ArchiveExtraDataRecord archiveExtraDataRecord,
            ArchiveDecryptionHeader archiveDecryptionHeader) {
        return new ZipHeader(eocd, eocdLocator, eocd64, null, null,
                archiveExtraDataRecord, archiveDecryptionHeader, null, spanningMarker);
    }
}
