package pd.droidapp.fmgr.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

import pd.droidapp.fmgr.view.ButtonState;
import pd.util.PathOps;

public class Util {

    public static String getSizeString(long size) {
        if (size < 0) {
            throw new IllegalArgumentException("E: `size` must not be negative");
        }
        if (size < 1024) {
            return size + " B";
        }
        int exp = (int) (Math.log(size) / Math.log(1024));
        if (exp > 6) {
            exp = 6;
        }
        char unit = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.getDefault(), "%.1f %sB", size / Math.pow(1024, exp), unit);
    }

    // ClickableViewAccessibility: the listener never consumes events (returns false);
    @SuppressLint("ClickableViewAccessibility")
    public static void forwardViewActionsTo(View view, View itemView) {
        view.setOnClickListener(v -> itemView.performClick());
        view.setOnLongClickListener(v -> itemView.performLongClick());
        view.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    itemView.setPressed(true);
                    break;
                case MotionEvent.ACTION_UP:
                    // ViewGroup#dispatchSetPressed always propagates pressed=false to children,
                    // which would clear this view's pressed flag and swallow its pending click
                    itemView.post(() -> itemView.setPressed(false));
                    break;
                default:
                    itemView.setPressed(false);
                    break;
            }
            return false;
        });
    }

    public static float getGaussianValue(double mu, double sigma, float amplitude, float fraction) {
        // f(x) = A * exp(-(x-μ)² / (2σ²))
        double exponent = -Math.pow(fraction - mu, 2) / (2 * sigma * sigma);
        return (float) (amplitude * Math.exp(exponent));
    }

    public static void animateCollapsed(ImageView triangleView, View contentView, boolean collapsed) {
        // rotate the triangle
        float targetRotation = collapsed ? -90f : 0f;
        ValueAnimator animator = ValueAnimator.ofFloat(triangleView.getRotation(), targetRotation);
        animator.setDuration(200);
        animator.addUpdateListener(animation ->
                triangleView.setRotation((float) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                contentView.setVisibility(collapsed ? View.GONE : View.VISIBLE);
            }
        });
        animator.start();
    }

    /**
     * display path for UI
     * `/storage/emulated/0` => `/`
     * `/storage/emulated/0/xxx` => `/xxx`
     */
    public static String getDisplayPath(String path) {
        String rootPath = Environment.getExternalStorageDirectory().getPath();
        if (path.equals(rootPath)) {
            return "/";
        }
        if (path.startsWith(rootPath + "/")) {
            return path.substring(rootPath.length());
        }
        return path;
    }

    public static FileProperties toFileProperties(String path) {
        return new FileProperties(PathOps.singleton.normalize(path), path.endsWith("/"));
    }

    public static List<FileProperties> toFileProperties(Collection<String> paths) {
        List<FileProperties> items = new LinkedList<>();
        for (String path : paths) {
            items.add(toFileProperties(path));
        }
        return items;
    }

    public static int bitCeil(int n) {
        if (n <= 0) {
            return 1;
        }
        return 1 << (32 - Integer.numberOfLeadingZeros(n - 1));
    }

    public static List<byte[]> encode(String s, String[] charsets) {
        return Arrays.stream(charsets)
                .map(charset -> encode(s, charset))
                .filter(Objects::nonNull)
                .map(ByteBuffer::wrap)
                .distinct()
                .map(ByteBuffer::array)
                .collect(Collectors.toList());
    }

    public static byte[] encode(String s, String charset) {
        try {
            ByteBuffer encoded = Charset.forName(charset).newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(s));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return bytes;
        } catch (CharacterCodingException | IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void scrollToIndex(RecyclerView itemsView, int index) {
        LinearLayoutManager layoutManager = (LinearLayoutManager) itemsView.getLayoutManager();
        if (layoutManager == null) {
            return;
        }
        int firstEntireVisibleIndex = layoutManager.findFirstCompletelyVisibleItemPosition();
        int lastEntireVisibleIndex = layoutManager.findLastCompletelyVisibleItemPosition();
        View firstEntireVisibleView = layoutManager.findViewByPosition(firstEntireVisibleIndex);
        View lastEntireVisibleView = layoutManager.findViewByPosition(lastEntireVisibleIndex);
        if (firstEntireVisibleView == null || lastEntireVisibleView == null) {
            return;
        }
        int viewportTop = itemsView.getPaddingTop();
        int viewportBottom = itemsView.getHeight() - itemsView.getPaddingBottom();
        int dy;
        if (index > lastEntireVisibleIndex) {
            dy = (index - lastEntireVisibleIndex) * lastEntireVisibleView.getHeight()
                    - (viewportBottom - lastEntireVisibleView.getBottom());
        } else if (index < firstEntireVisibleIndex) {
            dy = (index - firstEntireVisibleIndex) * firstEntireVisibleView.getHeight()
                    + (firstEntireVisibleView.getTop() - viewportTop);
        } else {
            return;
        }
        final int SCROLL_ANIMATION_MILLISECONDS = 250;
        if (Math.abs(dy) > itemsView.getHeight() - itemsView.getPaddingTop() - itemsView.getPaddingBottom()) {
            itemsView.scrollBy(0, dy);
        } else {
            itemsView.smoothScrollBy(0, dy, new LinearInterpolator(), SCROLL_ANIMATION_MILLISECONDS);
        }
    }

    public static boolean isSameAsOrDescendantOfAny(String path, Collection<FileProperties> items) {
        for (FileProperties item : items) {
            if (path.equals(item.path) || path.startsWith(item.path + "/")) {
                return true;
            }
        }
        return false;
    }

    public static <T> List<T> listOf(T[] elements) {
        return elements == null || elements.length == 0
                ? Collections.emptyList()
                : Collections.unmodifiableList(Arrays.asList(elements));
    }

    public static void renderButtonStates(List<ButtonState> buttonStates, ViewGroup containerView, @LayoutRes int layoutId, IntConsumer onButtonClicked) {
        // remove unused
        for (int i = containerView.getChildCount() - 1; i >= 0; i--) {
            int id = containerView.getChildAt(i).getId();
            boolean contains = false;
            for (ButtonState buttonState : buttonStates) {
                if (buttonState.id == id) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                containerView.removeViewAt(i);
            }
        }

        // add new and set status
        for (ButtonState buttonState : buttonStates) {
            View button = containerView.findViewById(buttonState.id);
            if (button == null) {
                button = LayoutInflater.from(containerView.getContext())
                        .inflate(layoutId, containerView, false);
                button.setId(buttonState.id);
                if (button instanceof TextView) {
                    ((TextView) button).setText(buttonState.text);
                } else {
                    ((ImageButton) button).setImageResource(buttonState.drawableId);
                }
                if (onButtonClicked != null) {
                    button.setOnClickListener(v -> onButtonClicked.accept(buttonState.id));
                }
                containerView.addView(button);
            }
            button.setVisibility(buttonState.visible ? View.VISIBLE : View.GONE);
            button.setEnabled(buttonState.enabled);
        }

        // sort without detach
        for (ButtonState buttonState : buttonStates) {
            containerView.bringChildToFront(containerView.findViewById(buttonState.id));
        }
    }
}
