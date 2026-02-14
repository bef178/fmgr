package pd.droidapp.fmgr;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
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

        pathBar = new PathBar(view);

        fileItemAdapter = new FileItemAdapter();
        fileItemAdapter.whenFileClicked(file -> {
            if (file.isDirectory()) {
                navigateToDirectory(file);
            } else if (file.isFile()) {
                openFile(file);
            } else {
                Toast.makeText(requireContext(), R.string.error_failed_to_handle, Toast.LENGTH_SHORT).show();
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

    private void navigateToDirectory(File target) {
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

    private void navigateToHome() {
        MainActivity mainActivity = (MainActivity) requireActivity();
        mainActivity.navigateToHome();
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

    private void openFile(File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(requireContext(), R.string.error_file_not_exist, Toast.LENGTH_SHORT).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(
                requireContext(),
                requireContext().getPackageName() + ".file_provider",
                file);

        String mimeType = requireContext().getContentResolver().getType(uri);
        if (mimeType == null) {
            mimeType = "*/*";
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.error_no_app_to_open, Toast.LENGTH_SHORT).show();
        }
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

        public ActionBar(View containerView) {
            ImageButton homeButton = containerView.findViewById(R.id.action_home);
            homeButton.setOnClickListener(v -> navigateToHome());

            backButton = containerView.findViewById(R.id.action_back);
            backButton.setOnClickListener(v -> navigateBack());

            forwardButton = containerView.findViewById(R.id.action_forward);
            forwardButton.setOnClickListener(v -> navigateForward());

            upButton = containerView.findViewById(R.id.action_up);
            upButton.setOnClickListener(v -> navigateToDirectory(getParentDirectory(currentDirectory)));

            ImageButton refreshButton = containerView.findViewById(R.id.action_refresh);
            refreshButton.setOnClickListener(v -> fileItemAdapter.invalidate(currentDirectory));

            ImageButton moreButton = containerView.findViewById(R.id.action_more);
            moreButton.setEnabled(false);
        }

        public void invalidate() {
            boolean hasParentDirectory = getParentDirectory(currentDirectory) != null;
            upButton.setEnabled(hasParentDirectory);

            backButton.setEnabled(!backStack.isEmpty());

            forwardButton.setEnabled(!forwardStack.isEmpty());
        }
    }

    public class PathBar {

        private final LinearLayout breadcrumbLayout;

        public PathBar(View containerView) {
            breadcrumbLayout = containerView.findViewById(R.id.breadcrumb_layout);
        }

        public void invalidate() {
            breadcrumbLayout.removeAllViews();

            Context context = requireContext();
            LayoutInflater inflater = LayoutInflater.from(context);

            File f = currentDirectory;
            while (f != null) {
                if (f.getName().isEmpty()) {
                    // the root directory deserves a breadcrumb
                    breadcrumbLayout.addView(createBreadcrumbItemView(inflater, f), 0);
                } else {
                    if (breadcrumbLayout.getChildCount() > 0) {
                        breadcrumbLayout.addView(createSeparatorTextView(context), 0);
                    }
                    breadcrumbLayout.addView(createBreadcrumbItemView(inflater, f), 0);
                }
                f = f.getParentFile();
            }
        }

        private TextView createBreadcrumbItemView(LayoutInflater inflater, File f) {
            TextView textView = (TextView) inflater.inflate(R.layout.breadcrumb_item, breadcrumbLayout, false);
            textView.setText(f.getName().isEmpty() ? "/" : f.getName());
            textView.setOnClickListener(v -> navigateToDirectory(f));
            return textView;
        }

        private TextView createSeparatorTextView(Context context) {
            TextView textView = new TextView(context);
            textView.setText("/");
            textView.setTextSize(12);
            textView.setTextColor(context.getColor(android.R.color.darker_gray));
            return textView;
        }
    }
}
