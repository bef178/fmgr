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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import pd.droidapp.fmgr.R;
import pd.droidapp.fmgr.fileheader.FileMimeUtil;
import pd.droidapp.fmgr.repository.ListDirectoryWorker;
import pd.droidapp.fmgr.repository.WorkerPool;
import pd.droidapp.fmgr.util.FileProperties;
import pd.droidapp.fmgr.view.ButtonState;
import pd.util.FileOps;
import pd.util.FileStat;
import pd.util.PathOps;

import static java.util.AbstractMap.SimpleEntry;
import static pd.droidapp.fmgr.util.Util.getDisplayPath;
import static pd.droidapp.fmgr.util.Util.getSizeString;
import static pd.droidapp.fmgr.util.Util.renderButtonStates;
import static pd.droidapp.glry.ImagePagerAdapter.ResetPageEvent;
import static pd.droidapp.glry.ImagePagerAdapter.ShowActualSizeEvent;
import static pd.droidapp.glry.ImagePagerAdapter.State;

public class ImageFragment extends Fragment {

    private static final String STATE_POSITION = "position";
    private static final DateTimeFormatter INFO_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);

    public static boolean mimeSupports(String mime) {
        return ImageManager.singleton.supports(mime);
    }

    private TextView titleView;
    private TextView indexView;
    private TextView indexPlaceholderView;
    private ViewPager2 pagerView;
    private View progressView;
    private ViewGroup actionButtons;
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
        indexView = view.findViewById(R.id.image_viewer_index);
        indexPlaceholderView = view.findViewById(R.id.image_viewer_index_placeholder);
        pagerView = view.findViewById(R.id.image_viewer_pager);
        progressView = view.findViewById(R.id.image_viewer_progress);
        actionButtons = view.findViewById(R.id.action_buttons);
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
        int position = indexOf(states, currentPath);
        if (position < 0) {
            String mimeType = getArguments() != null
                    ? getArguments().getString(GlryActivity.EXTRA_MIME)
                    : null;
            states.add(new State(currentPath, mimeType));
            states.sort((a, b) -> PathOps.singleton.compare(a.path, b.path));
            position = indexOf(states, currentPath);
        }
        if (restoredPosition >= 0 && restoredPosition < states.size()) {
            position = restoredPosition;
        }

        progressView.setVisibility(View.GONE);
        pagerView.setOffscreenPageLimit(1);
        this.states = states;
        actualSizeAvailable = new boolean[states.size()];
        adapter = new ImagePagerAdapter();
        adapter.whenPageLoaded(this::onPageLoaded);
        adapter.render(states);
        pagerView.setAdapter(adapter);
        pagerView.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int pagePosition) {
                renderTitle(states, pagePosition);
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
        List<ButtonState> buttonStates = new ArrayList<>();
        buttonStates.add(new ButtonState(R.drawable.ic_zoom0_24, actualSizeAvailable[position]));
        buttonStates.add(new ButtonState(R.drawable.ic_info_24));
        renderButtonStates(buttonStates, actionButtons, R.layout.action_button, id -> {
            if (!isPagerIdle()) {
                return;
            }
            if (id == R.drawable.ic_zoom0_24) {
                showActualSize();
            } else if (id == R.drawable.ic_info_24) {
                showInfo(pagerView.getCurrentItem());
            }
        });
    }

    private boolean isPagerIdle() {
        return adapter != null && pagerView.getScrollState() == ViewPager2.SCROLL_STATE_IDLE;
    }

    private void showInfo(int position) {
        State state = states.get(position);
        String path = state.path;
        String mimeType = state.mimeType;
        View pager = pagerView;
        WorkerPool.post(() -> {
            FileStat stat = FileOps.singleton.stat(path);
            Long size = stat.size;
            Long mtime = stat.mtime;
            int[] sourceResolution = ImageManager.singleton.readImageResolution(path, mimeType);
            pager.post(() -> {
                if (!isAdded() || getView() == null) {
                    return;
                }
                showInfoPopup(path, size, mtime, mimeType, sourceResolution);
            });
        });
    }

    private void showInfoPopup(String path, Long size, Long mtime, String mimeType, int[] sourceResolution) {
        List<Map.Entry<String, String>> rows = new ArrayList<>();
        rows.add(new SimpleEntry<>(getString(R.string.info_path), getDisplayPath(path)));
        rows.add(new SimpleEntry<>(getString(R.string.info_size),
                size != null ? getSizeString(size) : getString(R.string.error)));
        if (sourceResolution != null) {
            rows.add(new SimpleEntry<>(getString(R.string.info_resolution),
                    sourceResolution[0] + " x " + sourceResolution[1]));
        }
        if (mtime != null) {
            rows.add(new SimpleEntry<>(getString(R.string.info_modified),
                    INFO_DATE_FORMAT.format(Instant.ofEpochMilli(mtime))));
        }
        if (mimeType != null) {
            rows.add(new SimpleEntry<>(getString(R.string.info_type), mimeType));
        }
        new InfoPopup(pagerView, rows).show();
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
        actionButtons = null;
        titleView = null;
        indexView = null;
        indexPlaceholderView = null;
    }
}
