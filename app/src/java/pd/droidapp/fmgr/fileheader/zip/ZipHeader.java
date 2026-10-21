package pd.droidapp.fmgr.fileheader.zip;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ZipHeader {

    public final EndOfCentralDirectory eocd;
    public final EndOfCentralDirectory64Locator eocd64Locator;
    public final EndOfCentralDirectory64 eocd64;
    public final DigitalSignature digitalSignature;
    public final List<CentralDirectoryRecord> centralDirectoryRecords;
    public final ArchiveExtraDataRecord archiveExtraDataRecord;
    public final ArchiveDecryptionHeader archiveDecryptionHeader;
    public final List<LocalFileHeader> localFileHeaders;
    public final SpanningMarker spanningMarker;

    ZipHeader(EndOfCentralDirectory eocd, EndOfCentralDirectory64Locator eocd64Locator,
            EndOfCentralDirectory64 eocd64, DigitalSignature digitalSignature,
            List<CentralDirectoryRecord> centralDirectoryRecords,
            ArchiveExtraDataRecord archiveExtraDataRecord,
            ArchiveDecryptionHeader archiveDecryptionHeader,
            List<LocalFileHeader> localFileHeaders, SpanningMarker spanningMarker) {
        this.eocd = eocd;
        this.eocd64Locator = eocd64Locator;
        this.eocd64 = eocd64;
        this.digitalSignature = digitalSignature;
        this.centralDirectoryRecords = centralDirectoryRecords == null ? null
                : Collections.unmodifiableList(new ArrayList<>(centralDirectoryRecords));
        this.archiveExtraDataRecord = archiveExtraDataRecord;
        this.archiveDecryptionHeader = archiveDecryptionHeader;
        this.localFileHeaders = localFileHeaders == null ? null
                : Collections.unmodifiableList(new ArrayList<>(localFileHeaders));
        this.spanningMarker = spanningMarker;
    }

    public boolean isCentralDirectoryComplete() {
        return centralDirectoryRecords != null;
    }
}
