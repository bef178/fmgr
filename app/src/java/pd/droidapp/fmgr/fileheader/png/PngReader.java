package pd.droidapp.fmgr.fileheader.png;

public final class PngReader {

    public final PngHeader header;

    public PngReader(byte[] head) {
        this.header = new PngParser(head).parse();
    }

    public String getMimeType() {
        return header != null ? "image/png" : null;
    }
}
