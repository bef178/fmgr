package pd.droidapp.fmgr.fileheader.tar;

public final class TarReader {

    public final TarHeader header;

    public TarReader(byte[] head) {
        this.header = new TarParser(head).parse();
    }

    public String getMimeType() {
        return header != null ? "application/x-tar" : null;
    }
}
