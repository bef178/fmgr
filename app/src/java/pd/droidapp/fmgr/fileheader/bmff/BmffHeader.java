package pd.droidapp.fmgr.fileheader.bmff;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BmffHeader {

    public final long boxSize;
    public final String majorBrand;
    public final int minorVersion;
    public final List<String> compatibleBrands;
    public final boolean compatibleBrandsComplete;
    public final boolean hasVideoTrack;
    public final boolean hasAudioTrack;

    BmffHeader(long boxSize, String majorBrand, int minorVersion, String[] compatibleBrands,
            boolean compatibleBrandsComplete, boolean hasVideoTrack, boolean hasAudioTrack) {
        this.boxSize = boxSize;
        this.majorBrand = majorBrand;
        this.minorVersion = minorVersion;
        this.compatibleBrands = Collections.unmodifiableList(Arrays.asList(compatibleBrands.clone()));
        this.compatibleBrandsComplete = compatibleBrandsComplete;
        this.hasVideoTrack = hasVideoTrack;
        this.hasAudioTrack = hasAudioTrack;
    }
}
