package pd.droidapp.fmgr;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;

public class BrowseFragment extends Fragment {

    private ActionBar actionBar;
    private PathBar pathBar;
    private FileItemAdapter fileItemAdapter;

    private File currentDirectory;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.browse_fragment, container, false);

        actionBar = new ActionBar(view);
        actionBar.whenRefreshButtonClicked(v -> fileItemAdapter.invalidate(currentDirectory));
        actionBar.whenParentButtonClicked(v -> navigateToDirectory(getParentDirectory(currentDirectory)));

        pathBar = new PathBar(view);

        fileItemAdapter = new FileItemAdapter();
        fileItemAdapter.whenFileClicked(file -> {
            if (file.isDirectory()) {
                navigateToDirectory(file);
            } else {
                // TODO do open file
            }
        });

        RecyclerView fileListView = view.findViewById(R.id.file_list);
        fileListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        fileListView.setAdapter(fileItemAdapter);

        navigateToDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));

        return view;
    }

    private void navigateToDirectory(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            return;
        }

        currentDirectory = directory;

        pathBar.invalidate();
        actionBar.invalidate();
        fileItemAdapter.invalidate(directory);
    }

    public File getParentDirectory(File directory) {
        if (directory != null) {
            File parent = directory.getParentFile();
            if (parent != null && parent.exists()) {
                return parent;
            }
        }
        return null;
    }

    public class ActionBar {

        private final ImageButton parentButton;

        private final ImageButton refreshButton;

        public ActionBar(View containerView) {
            parentButton = containerView.findViewById(R.id.action_parent);
            refreshButton = containerView.findViewById(R.id.action_refresh);

            ImageButton moreButton = containerView.findViewById(R.id.action_more);
            moreButton.setEnabled(false);
        }

        public void whenRefreshButtonClicked(View.OnClickListener onClickListener) {
            refreshButton.setOnClickListener(onClickListener);
        }

        public void whenParentButtonClicked(View.OnClickListener onClickListener) {
            parentButton.setOnClickListener(onClickListener);
        }

        public void invalidate() {
            boolean hasParentDirectory = getParentDirectory(currentDirectory) != null;
            parentButton.setEnabled(hasParentDirectory);
        }
    }

    public class PathBar {

        private final TextView currentDirectoryTextView;

        public PathBar(View containerView) {
            currentDirectoryTextView = containerView.findViewById(R.id.current_directory);
        }


        public void invalidate() {
            String path = currentDirectory.getAbsolutePath();
            currentDirectoryTextView.setText(path);
        }
    }
}
