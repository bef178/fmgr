package pd.droidapp.fmgr.fileheader;

import java.io.IOException;
import java.io.RandomAccessFile;

import pd.droidapp.fmgr.fileheader.bmff.BmffReader;
import pd.droidapp.fmgr.fileheader.bmp.BmpReader;
import pd.droidapp.fmgr.fileheader.ebml.EbmlReader;
import pd.droidapp.fmgr.fileheader.flac.FlacReader;
import pd.droidapp.fmgr.fileheader.gif.GifReader;
import pd.droidapp.fmgr.fileheader.ogg.OggReader;
import pd.droidapp.fmgr.fileheader.ole2.Ole2Reader;
import pd.droidapp.fmgr.fileheader.palm.PalmDatabaseReader;
import pd.droidapp.fmgr.fileheader.png.PngReader;
import pd.droidapp.fmgr.fileheader.riff.RiffReader;
import pd.droidapp.fmgr.fileheader.tar.TarReader;
import pd.droidapp.fmgr.fileheader.zip.ZipReader;

import static pd.droidapp.fmgr.fileheader.BytesReadUtil.bytesStartsWith;
import static pd.droidapp.fmgr.fileheader.BytesReadUtil.readUnsignedInt16LittleEndian;
import static pd.droidapp.fmgr.util.Util.readHeadBytes;

public final class FileMimeDetector {

    private static final int HEAD_LENGTH = 1024;
    private static final int PDF_OBJECT_LENGTH = 65536;
    private static final int PDF_TAIL_LENGTH = 65536;

    private static final Signature[] SIGNATURES = new Signature[] {
            // standalone magic
            new Signature("application/gzip", new int[] {0x1F, 0x8B, 0x08}),
            new Signature("application/vnd.apple.mpegurl", new int[] {'#', 'E', 'X', 'T', 'M', '3', 'U'}),
            new Signature("application/vnd.ms-htmlhelp", new int[] {'I', 'T', 'S', 'F', 0x02}),
            new Signature("application/vnd.ms-htmlhelp", new int[] {'I', 'T', 'S', 'F', 0x03}),
            new Signature("application/vnd.rar", new int[] {'R', 'a', 'r', '!'}),
            new Signature("application/x-7z-compressed", new int[] {'7', 'z', 0xBC, 0xAF, 0x27, 0x1C}),
            new Signature("application/x-xz", new int[] {0xFD, '7', 'z', 'X', 'Z', 0x00}),
            new Signature("image/jpeg", new int[] {0xFF, 0xD8, 0xFF}),
            new Signature("image/tiff", new int[] {'I', 'I', '*', 0x00}),
            new Signature("image/tiff", new int[] {'M', 'M', 0x00, '*'}),
    };

    private static final class Signature {
        private final String mime;
        private final int[] magic;

        private Signature(String mime, int[] magic) {
            this.mime = mime;
            this.magic = magic;
        }
    }

    public static String mimeByContent(String path) {
        byte[] head = readHeadBytes(path, HEAD_LENGTH);
        if (head == null) {
            return null;
        }

        String mime = detectSignature(head);
        if (mime != null) {
            return mime;
        }

        mime = new BmffReader(path, head).getMimeType();
        if (mime != null) {
            return mime;
        }

        mime = new FlacReader(head).getMimeType();
        if (mime != null) {
            return mime;
        }

        mime = new GifReader(head).getMimeType();
        if (mime != null) {
            return mime;
        }

        mime = new PngReader(head).getMimeType();
        if (mime != null) {
            return mime;
        }

        mime = new RiffReader(head).getMimeType();
        if (mime != null) {
            return mime;
        }

        mime = new OggReader(head).getMimeType();
        if (mime != null) {
            return mime;
        }

        mime = detectMpeg(head);
        if (mime != null) {
            return mime;
        }

        mime = detectMpegAudio(head);
        if (mime != null) {
            return mime;
        }

        mime = new EbmlReader(head).getMimeType();
        if (mime != null) {
            return mime;
        }

        mime = detectRealMedia(head);
        if (mime != null) {
            return mime;
        }

        mime = detectApe(head);
        if (mime != null) {
            return mime;
        }

        mime = new BmpReader(head).getMimeType();
        if (mime != null) {
            return mime;
        }

        mime = new PalmDatabaseReader(path, head).getMimeType();
        if (mime != null) {
            return mime;
        }

        mime = detectBzip2(head);
        if (mime != null) {
            return mime;
        }

        mime = new Ole2Reader(path, head).getMimeType();
        if (mime != null) {
            return mime;
        }

        mime = new TarReader(head).getMimeType();
        if (mime != null) {
            return mime;
        }

        mime = new ZipReader(path, head).getMimeType();
        if (mime != null) {
            return mime;
        }

        return detectPdf(path, head);
    }

    private static String detectSignature(byte[] head) {
        for (Signature signature : SIGNATURES) {
            if (bytesStartsWith(head, signature.magic)) {
                return signature.mime;
            }
        }
        return null;
    }

    private static String detectMpeg(byte[] head) {
        if (bytesStartsWith(head, 0, new int[] {0x00, 0x00, 0x01, 0xBA})    // program stream
                || bytesStartsWith(head, 0, new int[] {0x00, 0x00, 0x01, 0xB3})) {    // elementary stream sequence header
            return "video/mpeg";
        }
        if (bytesStartsWith(head, 4, new int[] {0x47}) && bytesStartsWith(head, 196, new int[] {0x47})
                && bytesStartsWith(head, 388, new int[] {0x47})
                && hasBinaryPayload(head, 8, 388)) {    // 192-byte packet transport stream
            return "video/mp2t";
        }
        if (bytesStartsWith(head, 0, new int[] {0x47}) && bytesStartsWith(head, 188, new int[] {0x47})
                && bytesStartsWith(head, 376, new int[] {0x47})
                && hasBinaryPayload(head, 4, 376)) {    // mpeg transport stream
            return "video/mp2t";
        }
        return null;
    }

    // a transport stream packet holds muxed binary data; every byte being printable text means it is not one
    private static boolean hasBinaryPayload(byte[] head, int from, int to) {
        for (int i = from; i < to; i++) {
            int b = head[i] & 0xFF;
            if (b < 0x09 || (b > 0x0D && b < 0x20) || b > 0x7E) {
                return true;
            }
        }
        return false;
    }

    private static String detectMpegAudio(byte[] head) {
        if (head.length >= 4) {
            if (bytesStartsWith(head, 0, new int[] {'I', 'D', '3'}) && head[3] >= 2 && head[3] <= 4) {    // id3v2.2/2.3/2.4
                return "audio/mpeg";
            }
            if ((head[0] & 0xFF) == 0xFF && (head[1] & 0xE0) == 0xE0 && head[1] != (byte) 0xFE) {    // 0xFFFE is the utf-16 byte order mark, not a frame
                if ((head[1] & 0x06) == 0) {    // layer bits 00: an adts aac frame, not a mp3 frame
                    if (((head[2] & 0x3C) >> 2) != 15) {    // 15 is a forbidden sample rate index
                        return "audio/aac";
                    }
                } else if ((head[1] & 0x18) != 0x08         // version bits 01 are reserved
                        && (head[2] & 0xF0) != 0xF0         // bitrate index 1111 is reserved
                        && (head[2] & 0x0C) != 0x0C) {      // sample rate index 11 is reserved
                    return "audio/mpeg";
                }
            }
        }
        return null;
    }

    private static String detectRealMedia(byte[] head) {
        if (head.length >= 8 && bytesStartsWith(head, 0, new int[] {'.', 'R', 'M', 'F'})
                && (head[4] == 0 || head[5] == 0 || head[6] == 0 || head[7] == 0)) {    // rm and rmvb share the container; the version field is binary
            return "application/vnd.rn-realmedia-vbr";
        }
        return null;
    }

    private static String detectApe(byte[] head) {
        int version = readUnsignedInt16LittleEndian(head, 4);
        if (head.length >= 8 && bytesStartsWith(head, 0, new int[] {'M', 'A', 'C', ' '})
                && version >= 1000 && version <= 5000
                && readUnsignedInt16LittleEndian(head, 6) == 0) {   // the zero padding after the version guards against text starting with "MAC "
            return "audio/x-ape";
        }
        return null;
    }

    private static String detectBzip2(byte[] head) {
        if (head.length >= 4 && bytesStartsWith(head, 0, new int[] {'B', 'Z', 'h'}) && head[3] >= '1' && head[3] <= '9'
                && (bytesStartsWith(head, 4, new int[] {'1', 'A', 'Y', '&'})    // block magic
                || bytesStartsWith(head, 4, new int[] {0x17, 'r', 'E', '8'}))) {    // or the end of stream magic
            return "application/x-bzip2";
        }
        return null;
    }

    private static String detectPdf(String path, byte[] head) {
        int[] magic1 = new int[] {'%', 'P', 'D', 'F', '-', '1', '.'};
        int[] magic2 = new int[] {'%', 'P', 'D', 'F', '-', '2', '.'};
        int magicStart = -1;
        for (int i = 0; i + 5 <= head.length; i++) {
            if (bytesStartsWith(head, i, magic1) || bytesStartsWith(head, i, magic2)) {
                magicStart = i;
                break;
            }
        }
        if (magicStart >= 0) {
            byte[] pdfPrefix = readHeadBytes(path, PDF_OBJECT_LENGTH);
            if (pdfPrefix != null) {
                boolean result = false;
                for (int i = 1; i + 4 <= pdfPrefix.length; i++) {
                    if (bytesStartsWith(pdfPrefix, i, new int[] {'o', 'b', 'j'})
                            && isPdfDelimiter(pdfPrefix[i - 1]) && isPdfDelimiter(pdfPrefix[i + 3])) {
                        result = true;
                        break;
                    }
                }
                if (result && hasPdfEof(path)) {
                    return "application/pdf";
                }
            }
        }
        return null;
    }

    private static boolean isPdfDelimiter(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r'
                || b == '<' || b == '>' || b == '[' || b == ']';
    }

    // the end marker may be followed by trailing bytes
    private static boolean hasPdfEof(String path) {
        try (RandomAccessFile file = new RandomAccessFile(path, "r")) {
            long fileLength = file.length();
            if (fileLength < 5) {
                return false;
            }
            int tailLength = (int) Math.min(fileLength, PDF_TAIL_LENGTH);
            byte[] tail = new byte[tailLength];
            file.seek(fileLength - tailLength);
            if (file.read(tail) != tailLength) {
                return false;
            }
            for (int i = tailLength - 5; i >= 0; i--) {
                if (bytesStartsWith(tail, i, new int[] {'%', '%', 'E', 'O', 'F'})) {
                    return true;
                }
            }
            return false;
        } catch (IOException ignored) {
            return false;
        }
    }
}
