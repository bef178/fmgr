package pd.droidapp.fmgr.fileheader.ebml;

public final class EbmlReader {

    public final EbmlHeader header;

    public EbmlReader(byte[] head) {
        this.header = new EbmlParser(head).parse();
    }

    public String getMimeType() {
        if (header == null) {
            return null;
        }
        return "webm".equals(header.docType) ? "video/webm" : "video/x-matroska";
    }
}
