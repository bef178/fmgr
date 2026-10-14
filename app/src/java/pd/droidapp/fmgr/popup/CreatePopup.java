package pd.droidapp.fmgr.popup;

import android.view.View;
import android.widget.Toast;

import java.util.Collections;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.util.FileProperties;
import pd.util.FileOps;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.checkBasename;

public class CreatePopup extends EditPopup {

    private final String directory;
    private final boolean isDirectory;
    private String createdPath;

    // callbacks
    private PopupOnDismissedListener onPopupDismissed;

    public CreatePopup(View containerView, String directory, boolean isDirectory) {
        super(containerView,
                isDirectory ? R.string.new_directory : R.string.new_file,
                "",
                containerView.getContext().getString(isDirectory ? R.string.directory_name : R.string.file_name));
        this.directory = directory;
        this.isDirectory = isDirectory;
    }

    public void whenPopupDismissed(PopupOnDismissedListener onPopupDismissed) {
        this.onPopupDismissed = onPopupDismissed;
    }

    @Override
    protected void onDismissed() {
        if (onPopupDismissed == null || createdPath == null) {
            return;
        }
        onPopupDismissed.accept(
                Collections.singletonList(new FileProperties(createdPath, isDirectory)),
                Collections.emptyList());
    }

    @Override
    protected boolean canStart(String newName) {
        return !newName.trim().isEmpty();
    }

    @Override
    protected boolean onStart(String name) {
        name = name.trim();
        Integer errorTextId = checkBasename(name);
        if (errorTextId != null) {
            Toast.makeText(context, errorTextId, Toast.LENGTH_SHORT).show();
            return false;
        }

        if (directory == null) {
            Toast.makeText(context, R.string.error_create_failed, Toast.LENGTH_SHORT).show();
            return false;
        }

        String newPath = PathOps.singleton.join(directory, name);
        if (FileOps.singleton.stat(newPath).exists(false)) {
            Toast.makeText(context, R.string.error_already_exists, Toast.LENGTH_SHORT).show();
            return false;
        }

        boolean success;
        if (isDirectory) {
            success = FileOps.singleton.createEmptyDirectory(newPath);
            if (success) {
                Toast.makeText(context, R.string.directory_created, Toast.LENGTH_SHORT).show();
            }
        } else {
            success = FileOps.singleton.save(newPath, new byte[0], false);
            if (success) {
                Toast.makeText(context, R.string.file_created, Toast.LENGTH_SHORT).show();
            }
        }

        if (!success) {
            Toast.makeText(context, R.string.error_create_failed, Toast.LENGTH_SHORT).show();
            return false;
        }

        createdPath = newPath;
        return true;
    }
}
