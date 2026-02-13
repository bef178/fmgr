package pd.droidapp.fmgr;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.Stack;

public class BrowseFragment extends Fragment {

    private ActionBar actionBar;
    private PathBar pathBar;
    private FileItemAdapter fileItemAdapter;

    private File currentDirectory;
    private final Stack<File> backStack = new Stack<>();
    private final Stack<File> forwardStack = new Stack<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.browse_fragment, container, false);

        actionBar = new ActionBar(view);
        actionBar.whenBackButtonClicked(v -> navigateBack());
        actionBar.whenForwardButtonClicked(v -> navigateForward());
        actionBar.whenUpButtonClicked(v -> navigateUp());
        actionBar.whenRefreshButtonClicked(v -> fileItemAdapter.invalidate(currentDirectory));

        pathBar = new PathBar(view);

        fileItemAdapter = new FileItemAdapter();
        fileItemAdapter.whenFileClicked(file -> {
            if (file.isDirectory()) {
                navigateTo(file);
            } else {
                // TODO do open file
            }
        });

        RecyclerView fileListView = view.findViewById(R.id.file_list);
        fileListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        fileListView.setAdapter(fileItemAdapter);

        doChangeCurrentDirectory(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));

        return view;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean validateDirectory(File directory) {
        return directory != null && directory.exists();
    }

    private void doChangeCurrentDirectory(File directory) {
        currentDirectory = directory;
        pathBar.invalidate();
        actionBar.invalidate();
        fileItemAdapter.invalidate(directory);
    }

    private void navigateTo(File target) {
        if (!validateDirectory(target)) {
            Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentDirectory != null && !currentDirectory.equals(target)) {
            backStack.push(currentDirectory);
        }
        forwardStack.clear();
        doChangeCurrentDirectory(target);
    }

    private void navigateUp() {
        navigateTo(getParentDirectory(currentDirectory));
    }

    private void navigateBack() {
        if (backStack.isEmpty()) {
            return;
        }

        File target = backStack.peek();
        if (!validateDirectory(target)) {
            Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            return;
        }

        backStack.pop();
        forwardStack.push(currentDirectory);
        doChangeCurrentDirectory(target);
    }

    private void navigateForward() {
        if (forwardStack.isEmpty()) {
            return;
        }

        File target = forwardStack.peek();
        if (!validateDirectory(target)) {
            Toast.makeText(requireContext(), R.string.error_directory_not_accessible, Toast.LENGTH_SHORT).show();
            return;
        }

        backStack.push(currentDirectory);
        forwardStack.pop();
        doChangeCurrentDirectory(target);
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

        private final ImageButton backButton;
        private final ImageButton forwardButton;
        private final ImageButton upButton;
        private final ImageButton refreshButton;

        public ActionBar(View containerView) {
            backButton = containerView.findViewById(R.id.action_back);
            forwardButton = containerView.findViewById(R.id.action_forward);
            upButton = containerView.findViewById(R.id.action_up);
            refreshButton = containerView.findViewById(R.id.action_refresh);

            ImageButton moreButton = containerView.findViewById(R.id.action_more);
            moreButton.setEnabled(false);
        }

        public void whenBackButtonClicked(View.OnClickListener onClickListener) {
            backButton.setOnClickListener(onClickListener);
        }

        public void whenForwardButtonClicked(View.OnClickListener onClickListener) {
            forwardButton.setOnClickListener(onClickListener);
        }

        public void whenUpButtonClicked(View.OnClickListener onClickListener) {
            upButton.setOnClickListener(onClickListener);
        }

        public void whenRefreshButtonClicked(View.OnClickListener onClickListener) {
            refreshButton.setOnClickListener(onClickListener);
        }

        public void invalidate() {
            boolean hasParentDirectory = getParentDirectory(currentDirectory) != null;
            upButton.setEnabled(hasParentDirectory);

            backButton.setEnabled(!backStack.isEmpty());

            forwardButton.setEnabled(!forwardStack.isEmpty());
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
