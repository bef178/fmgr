package pd.droidapp.fmgr.fileheader.xml;

public final class XmlReader {

    public final XmlDocument doc;

    public XmlReader(byte[] head) {
        this.doc = new XmlParser(head).parse();
    }

    public String getMimeType() {
        return doc != null && "svg".equals(doc.rootElement) ? "image/svg+xml" : null;
    }
}
