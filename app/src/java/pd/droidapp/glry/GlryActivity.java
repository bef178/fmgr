package pd.droidapp.glry;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.fileheader.FileMimeUtil;
import pd.util.FileOps;

public class GlryActivity extends AppCompatActivity {

    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_MIME = "mime";

    public static boolean supports(String mime) {
        return ImageFragment.mimeSupports(mime);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            return;
        }
        String path = getIntent().getStringExtra(EXTRA_PATH);
        if (path == null || !FileOps.singleton.stat(path).exists(true)) {
            Toast.makeText(this, R.string.error_file_not_exist, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        String mimeType = FileMimeUtil.mimeByContent(path);
        if (!ImageFragment.mimeSupports(mimeType)) {
            Toast.makeText(this, R.string.error_failed_to_load_image, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Fragment fragment = createFragment(path, mimeType);
        if (fragment == null) {
            finish();
            return;
        }
        getSupportFragmentManager().beginTransaction()
                .replace(android.R.id.content, fragment)
                .commit();
    }

    private Fragment createFragment(String path, String mimeType) {
        if (!ImageFragment.mimeSupports(mimeType)) {
            return null;
        }
        Fragment fragment = new ImageFragment();
        Bundle arguments = new Bundle();
        arguments.putString(EXTRA_PATH, path);
        arguments.putString(EXTRA_MIME, mimeType);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ImageManager.singleton.clearCache();
    }
}
