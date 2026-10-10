package pd.droidapp.fmgr.popup;

import android.view.View;
import android.widget.Toast;

import java.util.Collections;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.FileProperties;
import pd.util.FileOps;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.checkBasename;

public class RenamePopup extends EditPopup {

    private final String path;
    private String newPath;

    // callbacks
    private PopupOnDismissedListener onPopupDismissed;

    public RenamePopup(View containerView, String path) {
        super(containerView,
                R.string.rename,
                R.string.rename,
                suggestNewName(PathOps.singleton.basename(path)),
                PathOps.singleton.basename(path));
        this.path = path;
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onDismissed() {
        if (onPopupDismissed == null || newPath == null) {
            return;
        }
        boolean isDirectory = FileOps.singleton.stat(newPath).isDirectory(false);
        onPopupDismissed.accept(
                Collections.singletonList(new FileProperties(newPath, isDirectory)),
                Collections.singletonList(new FileProperties(path, isDirectory)));
    }

    @Override
    protected boolean canStart(String newName) {
        return !newName.trim().isEmpty();
    }

    @Override
    protected boolean onStart(String newName) {
        newName = newName.trim();
        if (newName.equals(PathOps.singleton.basename(path))) {
            Toast.makeText(context, R.string.error_unchanged_name, Toast.LENGTH_SHORT).show();
            return false;
        }

        Integer errorTextId = checkBasename(newName);
        if (errorTextId != null) {
            Toast.makeText(context, errorTextId, Toast.LENGTH_SHORT).show();
            return false;
        }

        String targetPath = PathOps.singleton.resolve(PathOps.singleton.dirname(path), newName);
        if (FileOps.singleton.stat(targetPath).exists(true)) {
            Toast.makeText(context, R.string.error_already_exists, Toast.LENGTH_SHORT).show();
            return false;
        }

        if (!FileOps.singleton.move(path, targetPath, null)) {
            Toast.makeText(context, R.string.error_rename_failed, Toast.LENGTH_SHORT).show();
            return false;
        }

        Toast.makeText(context, R.string.renamed, Toast.LENGTH_SHORT).show();
        newPath = targetPath;
        return true;
    }

    private static String suggestNewName(String name) {
        {
            String name1 = name.trim();
            if (name1.isEmpty()) {
                return name;
            }
            name = name1;
        }

        String extension = PathOps.singleton.extname(name);
        String core = PathOps.singleton.basename(name, extension);

        {
            String core1 = core.replaceAll("(.+?)(\\s*\\(\\d+\\))*\\s*$", "$1");
            if (!core1.isEmpty()) {
                core = core1;
            }
        }

        if (!extension.isEmpty()) {
            String ext1 = extension
                    .substring(1)
                    .replaceAll("(.+?)(\\s*\\(\\d+\\))*\\s*$", "$1")
                    .trim();
            if (!ext1.isEmpty()) {
                extension = "." + ext1;
            }
        }
        return core + extension;
    }
}
