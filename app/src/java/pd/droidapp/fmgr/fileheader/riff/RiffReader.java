package pd.droidapp.fmgr.fileheader.riff;

public final class RiffReader {

    public final RiffHeader header;

    public RiffReader(byte[] head) {
        this.header = new RiffParser(head).parse();
    }

    public String getMimeType() {
        if (header == null) {
            return null;
        }
        String formType = header.formType;
        if ("WEBP".equals(formType)) {
            return "image/webp";
        }
        if ("WAVE".equals(formType)) {
            return "audio/wav";
        }
        if ("AVI ".equals(formType)) {
            return "video/x-msvideo";
        }
        return null;
    }
}
