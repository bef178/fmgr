package pd.droidapp.fmgr.fileheader.gif;

public final class GifReader {

    public final GifHeader header;

    public GifReader(byte[] head) {
        this.header = new GifParser(head).parse();
    }

    public String getMimeType() {
        return header != null ? "image/gif" : null;
    }
}
