package pd.droidapp.fmgr.fileheader.zip;

// 中央目录加密时的归档解密头；没有签名
public final class ArchiveDecryptionHeader {

    public final byte[] data;

    ArchiveDecryptionHeader(byte[] data) {
        this.data = data;
    }
}
