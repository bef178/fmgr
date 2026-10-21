package pd.droidapp.fmgr.fileheader.tar;

public final class TarHeader {

    // [257,261]
    public static final int[] MAGIC = new int[] {'u', 's', 't', 'a', 'r'};

    // [0,99] 文件名
    public final String name;

    // [100,107] 权限
    public final long mode;

    // [108,115] 用户 ID
    public final long uid;

    // [116,123] 组 ID
    public final long gid;

    // [124,135] 文件大小
    public final long size;

    // [136,147] 修改时间（Unix 秒）
    public final long modifiedTime;

    // [148,155] 头部校验和
    public final int checksum;

    // [156] 类型标志
    public final int typeFlag;

    // [157,256] 链接目标
    public final String linkName;

    // [265,296] 用户名
    public final String uname;

    // [297,328] 组名
    public final String gname;

    // [345,499] 路径前缀
    public final String prefix;

    TarHeader(String name, long mode, long uid, long gid, long size, long modifiedTime,
            int checksum, int typeFlag, String linkName, String uname, String gname, String prefix) {
        this.name = name;
        this.mode = mode;
        this.uid = uid;
        this.gid = gid;
        this.size = size;
        this.modifiedTime = modifiedTime;
        this.checksum = checksum;
        this.typeFlag = typeFlag;
        this.linkName = linkName;
        this.uname = uname;
        this.gname = gname;
        this.prefix = prefix;
    }
}
