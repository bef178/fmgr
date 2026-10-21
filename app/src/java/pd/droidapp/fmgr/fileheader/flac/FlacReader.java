package pd.droidapp.fmgr.fileheader.flac;

public final class FlacReader {

    public final FlacHeader header;

    public FlacReader(byte[] head) {
        this.header = new FlacParser(head).parse();
    }

    public String getMimeType() {
        return header != null ? "audio/flac" : null;
    }
}
