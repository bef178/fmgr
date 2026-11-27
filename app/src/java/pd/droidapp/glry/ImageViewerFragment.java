package pd.droidapp.glry;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.fileheader.FileMimeUtil;
import pd.droidapp.fmgr.popup.DeletePopup;
import pd.droidapp.fmgr.popup.InfoPopup;
import pd.droidapp.fmgr.repository.ListDirectoryWorker;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ActionBar;
import pd.droidapp.fmgr.view.ButtonState;
import pd.droidapp.fmgr.widget.ButtonScrollView;
import pd.util.FileOps;
import pd.util.PathOps;

import static pd.droidapp.fmgr.util.Util.compareNatural;
import static pd.droidapp.fmgr.util.Util.toFileProperties;
import static pd.droidapp.glry.ImagePagerAdapter.ResetPageEvent;
import static pd.droidapp.glry.ImagePagerAdapter.ShowActualSizeEvent;
import static pd.droidapp.glry.ImagePagerAdapter.State;

public class ImageViewerFragment extends Fragment {

    private static final String STATE_POSITION = "position";
    private static final Comparator<State> STATE_ORDER =
            (a, b) -> compareNatural(a.path, b.path);

    public static boolean mimeSupports(String mime) {
        return ImageManager.singleton.supports(mime);
    }

    private TextView titleView;
    private ButtonScrollView titleScrollView;
    private TextView indexView;
    private TextView indexPlaceholderView;
    private ViewPager2 pagerView;
    private View progressView;
    private ActionBar actionBar;
    private ImagePagerAdapter adapter;
    private List<State> states;
    private boolean[] actualSizeAvailable;
    private ListDirectoryWorker worker;
    private int restoredPosition = -1;
    private int settledPosition;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            restoredPosition = savedInstanceState.getInt(STATE_POSITION, -1);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.image_viewer_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        titleView = view.findViewById(R.id.image_viewer_title);
        titleScrollView = view.findViewById(R.id.image_viewer_title_scroll);
        indexView = view.findViewById(R.id.image_viewer_index);
        indexPlaceholderView = view.findViewById(R.id.image_viewer_index_placeholder);
        pagerView = view.findViewById(R.id.image_viewer_pager);
        progressView = view.findViewById(R.id.image_viewer_progress);
        actionBar = new ActionBar(view.findViewById(R.id.action_bar));
        actionBar.whenButtonClicked(id -> {
            if (!isPagerIdle()) {
                return;
            }
            if (id == R.drawable.ic_zoom0_24) {
                showActualSize();
            } else if (id == R.drawable.ic_info_24) {
                new InfoPopup(pagerView, states.get(pagerView.getCurrentItem()).path).show();
            } else if (id == R.drawable.ia_delete_24) {
                showDeletePopup();
            }
        });
        view.findViewById(R.id.action_back).setOnClickListener(v -> requireActivity().finish());

        String path = getArguments() != null ? getArguments().getString(GlryActivity.EXTRA_PATH) : null;
        if (path == null) {
            requireActivity().finish();
            return;
        }
        titleView.setText(PathOps.singleton.basename(path));
        loadImagePaths(path);
    }

    private void loadImagePaths(String currentPath) {
        String directory = PathOps.singleton.dirname(currentPath);
        List<State> images = new ArrayList<>();
        View progress = progressView;

        ListDirectoryWorker current = new ListDirectoryWorker();
        current.whenUpdated(listed -> {
            for (FileProperties item : listed) {
                if (item.isDirectory) {
                    continue;
                }
                String mimeType = FileMimeUtil.mimeByContent(item.path);
                if (mimeSupports(mimeType)) {
                    images.add(new State(item.path, mimeType));
                }
            }
        });
        current.whenStopped(reason -> progress.post(() -> {
            if (worker != current) {
                return;
            }
            showImages(images, currentPath);
        }));
        worker = current;
        current.start(directory);
    }

    private void showImages(List<State> states, String currentPath) {
        if (indexOf(states, currentPath) < 0 && FileOps.singleton.stat(currentPath).exists(true)) {
            String mimeType = getArguments() != null
                    ? getArguments().getString(GlryActivity.EXTRA_MIME)
                    : null;
            states.add(new State(currentPath, mimeType));
        }
        states.sort(STATE_ORDER);

        this.states = states;
        actualSizeAvailable = new boolean[states.size()];
        if (states.isEmpty()) {
            showEmptyImages();
            return;
        }
        int position = indexOf(states, currentPath);
        if (position < 0) {
            position = 0;
        }
        if (restoredPosition >= 0 && restoredPosition < states.size()) {
            position = restoredPosition;
        }

        progressView.setVisibility(View.GONE);
        pagerView.setOffscreenPageLimit(1);
        adapter = new ImagePagerAdapter();
        adapter.whenPageLoaded(this::onPageLoaded);
        adapter.render(states);
        pagerView.setAdapter(adapter);
        pagerView.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int pagePosition) {
                List<State> currentStates = ImageViewerFragment.this.states;
                if (currentStates == null || pagePosition < 0 || pagePosition >= currentStates.size()) {
                    return;
                }
                renderTitle(currentStates, pagePosition);
                renderActionButtons(pagePosition);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (state != ViewPager2.SCROLL_STATE_IDLE) {
                    return;
                }
                int current = pagerView.getCurrentItem();
                if (current == settledPosition) {
                    return;
                }
                int previous = settledPosition;
                settledPosition = current;
                adapter.render(new ResetPageEvent(previous));
            }
        });
        settledPosition = position;
        pagerView.setCurrentItem(position, false);
        renderTitle(states, position);
        renderActionButtons(position);
    }

    private void showEmptyImages() {
        progressView.setVisibility(View.GONE);
        adapter = new ImagePagerAdapter();
        pagerView.setAdapter(adapter);
        settledPosition = 0;
        titleView.setText(R.string.no_images);
        titleScrollView.scrollTo(0, 0);
        String emptyIndex = getString(R.string.x_of_y, 0, 0);
        indexView.setText(emptyIndex);
        indexPlaceholderView.setText(emptyIndex);
        actionBar.render(new ActionBar.State(new ButtonState[0]));
    }

    private static int indexOf(List<State> states, String path) {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).path.equals(path)) {
                return i;
            }
        }
        return -1;
    }

    private void onPageLoaded(int position, boolean downsampled) {
        if (position < 0 || position >= actualSizeAvailable.length) {
            return;
        }
        actualSizeAvailable[position] = downsampled;
        if (pagerView != null && position == pagerView.getCurrentItem()) {
            renderActionButtons(position);
        }
    }

    private void renderTitle(List<State> states, int position) {
        titleView.setText(PathOps.singleton.basename(states.get(position).path));
        titleScrollView.scrollTo(0, 0);
        int total = states.size();
        indexView.setText(getString(R.string.x_of_y, position + 1, total));

        int indexPlaceholder = 1;
        for (int i = 0; i < String.valueOf(total).length(); i++) {
            indexPlaceholder *= 10;
        }
        indexPlaceholder--;
        indexPlaceholderView.setText(getString(R.string.x_of_y, indexPlaceholder, indexPlaceholder));
    }

    private void showActualSize() {
        int position = pagerView.getCurrentItem();
        actualSizeAvailable[position] = false;
        renderActionButtons(position);
        adapter.render(new ShowActualSizeEvent(position));
    }

    private void renderActionButtons(int position) {
        actionBar.render(new ActionBar.State(
                new ButtonState[]{
                        new ButtonState(R.drawable.ic_zoom0_24, actualSizeAvailable[position]),
                        new ButtonState(R.drawable.ic_info_24)
                },
                new ButtonState(R.drawable.ia_delete_24)));
    }

    private void showDeletePopup() {
        View view = getView();
        if (view == null) {
            return;
        }
        String path = states.get(pagerView.getCurrentItem()).path;
        DeletePopup deletePopup = new DeletePopup(view, PathOps.singleton.dirname(path),
                Collections.singletonList(toFileProperties(path)), false);
        deletePopup.whenPopupDismissed((added, removed) -> {
            if (!isAdded() || adapter == null || pagerView == null) {
                return;
            }
            if (removed.isEmpty()) {
                return;
            }
            onFileDeleted(path);
        });
        deletePopup.show();
    }

    private void onFileDeleted(String path) {
        int index = indexOf(states, path);
        if (index < 0) {
            return;
        }

        states.remove(index);

        int size = states.size();
        boolean[] newActualSizeAvailable = new boolean[size];
        System.arraycopy(actualSizeAvailable, 0, newActualSizeAvailable, 0, index);
        System.arraycopy(actualSizeAvailable, index + 1, newActualSizeAvailable, index,
                size - index);
        actualSizeAvailable = newActualSizeAvailable;

        if (states.isEmpty()) {
            showEmptyImages();
            return;
        }

        int newCurrent = Math.min(index, size - 1);
        adapter.render(states);
        pagerView.setCurrentItem(newCurrent, false);
        settledPosition = newCurrent;
        renderTitle(states, newCurrent);
        renderActionButtons(newCurrent);
    }

    private boolean isPagerIdle() {
        return adapter != null && pagerView.getScrollState() == ViewPager2.SCROLL_STATE_IDLE;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pagerView != null && pagerView.getAdapter() != null) {
            outState.putInt(STATE_POSITION, pagerView.getCurrentItem());
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (worker != null) {
            worker.cancel();
            worker = null;
        }
        adapter = null;
        pagerView = null;
        progressView = null;
        actionBar = null;
        titleView = null;
        titleScrollView = null;
        indexView = null;
        indexPlaceholderView = null;
    }
}
