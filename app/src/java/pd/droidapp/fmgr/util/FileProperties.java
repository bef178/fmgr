package pd.droidapp.fmgr.util;

import pd.util.FileOps;
import pd.util.FileStat;

public class FileProperties {

    public final String path;


    public final Long size;
    public String sha256sum;

    public Integer numChildren;

    public FileProperties(String path) {
        this.path = path;

        FileStat stat = FileOps.singleton.stat(path);
        if (stat.isDirectory(true)) {
            size = null;
            numChildren = 0;
            FileOps.singleton.listDirectory(path, 1, true, null, (action, src, dst, succeeded) -> {
                if (action != FileOps.Action.MEET) {
                    return;
                }
                numChildren++;
            });
        } else {
            size = stat.size;
        }
    }

    public FileProperties(String path, long size, String sha256sum) {
        this.path = path;
        this.size = size;
        this.sha256sum = sha256sum;
    }
}
