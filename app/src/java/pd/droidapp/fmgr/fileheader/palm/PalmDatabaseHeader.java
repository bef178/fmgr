package pd.droidapp.fmgr.fileheader.palm;

// Palm Database (PDB) header
public final class PalmDatabaseHeader {

    // [0,31] 数据库名，NUL 结尾
    public final String name;

    // [32,33] 属性
    public final int attributes;

    // [34,35] 版本
    public final int version;

    // [36,39] 创建时间（Palm epoch）
    public final long creationTime;

    // [40,43] 修改时间
    public final long modificationTime;

    // [44,47] 备份时间
    public final long backupTime;

    // [48,51] 修改序号
    public final long modificationNumber;

    // [52,55] app info ID
    public final long appInfoId;

    // [56,59] sort info ID
    public final long sortInfoId;

    // [60,63] 类型
    public final String type;

    // [64,67] 创建者
    public final String creator;

    // [68,71] unique ID seed
    public final long uniqueIdSeed;

    // [72,75] next record list ID
    public final long nextRecordListId;

    // [76,77] 记录数
    public final int numRecords;

    PalmDatabaseHeader(String name, int attributes, int version, long creationTime,
            long modificationTime, long backupTime, long modificationNumber, long appInfoId,
            long sortInfoId, String type, String creator, long uniqueIdSeed, long nextRecordListId,
            int numRecords) {
        this.name = name;
        this.attributes = attributes;
        this.version = version;
        this.creationTime = creationTime;
        this.modificationTime = modificationTime;
        this.backupTime = backupTime;
        this.modificationNumber = modificationNumber;
        this.appInfoId = appInfoId;
        this.sortInfoId = sortInfoId;
        this.type = type;
        this.creator = creator;
        this.uniqueIdSeed = uniqueIdSeed;
        this.nextRecordListId = nextRecordListId;
        this.numRecords = numRecords;
    }
}
