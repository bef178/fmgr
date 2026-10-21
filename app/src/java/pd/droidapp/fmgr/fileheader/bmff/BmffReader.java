package pd.droidapp.fmgr.fileheader.bmff;

public final class BmffReader {

    private static final String[] HEIF_BRANDS = {
            "heic", "heix", "hevc", "hevx", "heim", "heis", "mif1", "msf1"};
    private static final String[] AVIF_BRANDS = {"avif", "avis"};
    private static final String[] PREFIX_AUDIO_BRANDS = {"M4A", "M4B", "M4P", "M4R", "F4A", "F4B"};
    private static final String PREFIX_3GP = "3gp";
    private static final String QUICKTIME_BRAND = "qt  ";

    private static boolean isHeifBrand(String brand) {
        for (String heifBrand : HEIF_BRANDS) {
            if (heifBrand.equals(brand)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAvifBrand(String brand) {
        for (String avifBrand : AVIF_BRANDS) {
            if (avifBrand.equals(brand)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAudioBrand(String brand) {
        for (String prefix : PREFIX_AUDIO_BRANDS) {
            if (brand.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean is3gpBrand(String brand) {
        return brand.startsWith(PREFIX_3GP);
    }

    public final BmffHeader header;

    public BmffReader(String path, byte[] head) {
        this.header = new BmffParser(path, head).parse();
    }

    public String getMimeType() {
        if (header == null) {
            return null;
        }
        if (hasAvifBrand()) {
            return "image/avif";
        }
        if (hasHeifBrand()) {
            return "image/heif";
        }
        if (QUICKTIME_BRAND.equals(header.majorBrand)) {
            return "video/quicktime";
        }
        if (hasAudioBrand()) {
            return "audio/mp4";
        }
        if (header.hasAudioTrack && !header.hasVideoTrack) {
            return "audio/mp4";
        }
        if (has3gpBrand()) {
            return "video/3gpp";
        }
        return "video/mp4";
    }

    private boolean hasHeifBrand() {
        if (isHeifBrand(header.majorBrand)) {
            return true;
        }
        for (String brand : header.compatibleBrands) {
            if (isHeifBrand(brand)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAvifBrand() {
        if (isAvifBrand(header.majorBrand)) {
            return true;
        }
        for (String brand : header.compatibleBrands) {
            if (isAvifBrand(brand)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAudioBrand() {
        if (isAudioBrand(header.majorBrand)) {
            return true;
        }
        for (String brand : header.compatibleBrands) {
            if (isAudioBrand(brand)) {
                return true;
            }
        }
        return false;
    }

    private boolean has3gpBrand() {
        if (is3gpBrand(header.majorBrand)) {
            return true;
        }
        for (String brand : header.compatibleBrands) {
            if (is3gpBrand(brand)) {
                return true;
            }
        }
        return false;
    }
}
