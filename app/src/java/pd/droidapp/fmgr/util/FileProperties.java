package pd.droidapp.fmgr.util;

public class FileProperties {

    /**
     * normalized absolute path without trailing '/'
     */
    public final String path;
    public final boolean isDirectory;

    public volatile boolean computed;

    public Long size;
    public String sha256sum;

    public Long numChildren;

    public FileProperties(String path, boolean isDirectory) {
        this.path = path;
        this.isDirectory = isDirectory;
    }
}
