package pd.droidapp.fmgr.fileheader.xml;

import java.nio.charset.StandardCharsets;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;

public final class XmlParser {

    private final byte[] head;

    public XmlParser(byte[] head) {
        this.head = head;
    }

    public XmlDocument parse() {
        if (head == null) {
            return null;
        }
        int i = eatUtf8Bom();
        while (true) {
            i = eatXmlWhitespace(i);
            int next = eatXmlComment(i);
            if (next < 0) {
                next = eatXmlProcessingInstruction(i);
            }
            if (next < 0) {
                next = eatXmlDocType(i);
            }
            if (next < 0) {
                break;
            }
            i = next;
        }
        String rootElement = readRootElement(i);
        return rootElement != null ? new XmlDocument(rootElement) : null;
    }

    private int eatUtf8Bom() {
        return bytesStartsWith(head, 0, new int[] {0xEF, 0xBB, 0xBF}) ? 3 : 0;
    }

    private int eatXmlWhitespace(int start) {
        int i = start;
        while (i < head.length) {
            int c = head[i] & 0xFF;
            if (c != ' ' && c != '\t' && c != '\r' && c != '\n') {
                break;
            }
            i++;
        }
        return i;
    }

    private int eatXmlComment(int start) {
        if (!bytesStartsWith(head, start, new int[] {'<', '!', '-', '-'})) {
            return -1;
        }
        return indexAfter(start + 4, new int[] {'-', '-', '>'});
    }

    private int eatXmlProcessingInstruction(int start) {
        if (!bytesStartsWith(head, start, new int[] {'<', '?'})) {
            return -1;
        }
        return indexAfter(start + 2, new int[] {'?', '>'});
    }

    private int eatXmlDocType(int start) {
        if (!bytesStartsWith(head, start, new int[] {'<', '!', 'D', 'O', 'C', 'T', 'Y', 'P', 'E'})) {
            return -1;
        }
        // an internal subset may contain brackets and quoted '>' characters
        int depth = 0;
        int quote = 0;
        for (int i = start + 9; i < head.length; i++) {
            int c = head[i] & 0xFF;
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
            } else if (c == '>' && depth <= 0) {
                return i + 1;
            }
        }
        return -1;
    }

    private int indexAfter(int start, int[] pattern) {
        for (int i = start; i + pattern.length <= head.length; i++) {
            if (bytesStartsWith(head, i, pattern)) {
                return i + pattern.length;
            }
        }
        return -1;
    }

    private String readRootElement(int offset) {
        if (offset >= head.length || head[offset] != '<') {
            return null;
        }
        int i = offset + 1;
        if (i >= head.length || !isXmlNameStartChar(head[i] & 0xFF)) {
            return null;
        }
        int start = i;
        while (i < head.length && isXmlNameChar(head[i] & 0xFF)) {
            i++;
        }
        if (i >= head.length || !isXmlTagDelimiter(head[i] & 0xFF)) {
            return null;
        }
        return new String(head, start, i - start, StandardCharsets.UTF_8);
    }

    private static boolean isXmlNameStartChar(int c) {
        return c == ':' || c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isXmlNameChar(int c) {
        return isXmlNameStartChar(c) || (c >= '0' && c <= '9') || c == '-' || c == '.';
    }

    private static boolean isXmlTagDelimiter(int c) {
        return c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '>' || c == '/';
    }
}
