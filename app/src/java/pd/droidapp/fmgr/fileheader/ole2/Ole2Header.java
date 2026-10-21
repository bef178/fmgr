package pd.droidapp.fmgr.fileheader.ole2;

// OLE2 / Compound File Binary header
public final class Ole2Header {

    // byte [0,7]
    public static final int[] SIGNATURE = new int[] {0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1};

    // [26,27] 主版本：3 或 4
    public final int majorVersion;

    // 由主版本决定：v3 为 512，v4 为 4096
    public final int sectorSize;

    // 扇区数据相对文件开头的偏移，等于 sectorSize
    public final long sectorBase;

    // [44,47] FAT 扇区数
    public final int numFatSectors;

    // [48,51] 目录起始扇区
    public final int firstDirectorySector;

    // [68,71] DIFAT 起始扇区
    public final int firstDifatSector;

    // [72,75] DIFAT 扇区数
    public final int numDifatSectors;

    // [76,511] 头部内的 109 个 FAT 扇区号，超出部分在 DIFAT 链
    final int[] difat;

    Ole2Header(int majorVersion, int sectorSize, long sectorBase, int numFatSectors,
            int firstDirectorySector, int firstDifatSector, int numDifatSectors, int[] difat) {
        this.majorVersion = majorVersion;
        this.sectorSize = sectorSize;
        this.sectorBase = sectorBase;
        this.numFatSectors = numFatSectors;
        this.firstDirectorySector = firstDirectorySector;
        this.firstDifatSector = firstDifatSector;
        this.numDifatSectors = numDifatSectors;
        this.difat = difat;
    }
}
