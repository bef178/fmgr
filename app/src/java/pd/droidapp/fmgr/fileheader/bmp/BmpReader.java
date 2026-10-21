package pd.droidapp.fmgr.fileheader.bmp;

public final class BmpReader {

    public final BmpHeader header;

    public BmpReader(byte[] head) {
        this.header = new BmpParser(head).parse();
    }

    public String getMimeType() {
        return header != null ? "image/bmp" : null;
    }
}
