package io.happytobi.arttv;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A static art print and a small, native-focus navigation row; no frame loop or recommendation fetches. */
final class ArtHomeView extends FrameLayout {
    static final int PAPER = 0xfff3efe8, INK = 0xff30312f;
    private static final long NAVIGATION_IDLE_MS = 20_000;
    private final Activity activity;
    private final FrameLayout mount;
    private final ImageView picture;
    private final ImageView blurred;
    private final View menuVeil;
    private final LinearLayout navigation;
    private final TextView[] buttons = new TextView[6];
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final SonyArtChannels sonyChannels = new SonyArtChannels();
    private volatile boolean destroyed, active;
    private boolean loading, channelBusy;
    private volatile int channelRequest;
    private String shown;
    private boolean navigationVisible, wakeTouch;
    private float mouseX = Float.NaN, mouseY = Float.NaN;
    private int navigationFocus, wakeKey = KeyEvent.KEYCODE_UNKNOWN;
    private int blurGeneration;
    private final Runnable idleNavigation = this::hideNavigation;

    ArtHomeView(Activity activity) {
        super(activity);
        this.activity = activity;
        setBackgroundColor(PAPER);
        setClipChildren(false);
        setFocusableInTouchMode(true);
        setDefaultFocusHighlightEnabled(false);
        setContentDescription("Art TV");
        setOnClickListener(v -> navigationInteraction());
        mount = new FrameLayout(activity);
        addView(mount);
        picture = new ImageView(activity);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setCropToPadding(true);
        picture.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        picture.setBackgroundColor(0xfffffbf5);
        mount.addView(picture, new FrameLayout.LayoutParams(-1, -1));
        picture.addOnLayoutChangeListener((v, l, t, r, b, oldl, oldt, oldr, oldb) -> {
            loadArtwork();
            if (l != oldl || t != oldt || r != oldr || b != oldb) prepareBlur();
        });
        blurred = new ImageView(activity);
        blurred.setScaleType(ImageView.ScaleType.FIT_XY);
        blurred.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mount.addView(blurred);
        menuVeil = new View(activity);
        menuVeil.setBackground(new MenuVeil());
        menuVeil.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mount.addView(menuVeil);
        navigation = new LinearLayout(activity);
        navigation.setGravity(Gravity.CENTER);
        navigation.setClipChildren(false);
        navigation.setClipToPadding(false);
        addView(navigation);
        String[] labels = {"Das Erste", "ZDF", "KiKA", Ui.tr("Live TV"), "Apple TV", Ui.tr("Settings")};
        for (int i = 0; i < buttons.length; i++) {
            final int item = i;
            TextView button = new TextView(activity);
            button.setId(View.generateViewId());
            button.setText(labels[i]);
            button.setTextColor(Color.BLACK);
            button.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            button.setSingleLine(true);
            button.setGravity(Gravity.CENTER);
            button.setFocusable(true);
            button.setFocusableInTouchMode(true);
            button.setClickable(true);
            button.setDefaultFocusHighlightEnabled(false);
            button.setOutlineAmbientShadowColor(0x40000000);
            button.setOutlineSpotShadowColor(0x50000000);
            button.setOnFocusChangeListener((v, focused) -> {
                float scale = navigation.getHeight() / 112f;
                float zoom = focused ? 1.05f : 1;
                v.animate().scaleX(zoom).scaleY(zoom)
                        .translationY(focused ? -4 * scale : 0)
                        .translationZ(focused ? 8 * scale : 0).setDuration(200).start();
            });
            button.setOnClickListener(v -> activate(item));
            button.setContentDescription(i < 3 ? labels[i] + ", " + Ui.tr("Live TV")
                    : i == 4 ? labels[i] + ", HDMI" : labels[i]);
            buttons[i] = button;
            navigation.addView(button);
            if (i == 3 || i == 4) {
                View divider = new View(activity);
                divider.setBackgroundColor(0xffd5cfc6);
                navigation.addView(divider, new LinearLayout.LayoutParams(1, 50));
            }
        }
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setNextFocusLeftId(buttons[Math.max(0, i - 1)].getId());
            buttons[i].setNextFocusRightId(buttons[Math.min(buttons.length - 1, i + 1)].getId());
            buttons[i].setNextFocusUpId(buttons[i].getId());
            buttons[i].setNextFocusDownId(buttons[i].getId());
        }
        hideNavigation();
    }

    private void hideNavigation() {
        removeCallbacks(idleNavigation);
        for (int i = 0; i < buttons.length; i++) if (buttons[i].hasFocus()) navigationFocus = i;
        navigationVisible = false;
        navigation.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        navigation.setVisibility(INVISIBLE);
        blurred.setVisibility(INVISIBLE);
        menuVeil.setVisibility(INVISIBLE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        requestFocus();
    }

    private boolean navigationInteraction() {
        boolean waking = !navigationVisible;
        if (waking) {
            navigationVisible = true;
            navigation.setVisibility(VISIBLE);
            blurred.setVisibility(VISIBLE);
            menuVeil.setVisibility(VISIBLE);
            navigation.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            buttons[navigationFocus].requestFocus();
        }
        removeCallbacks(idleNavigation);
        if (active) postDelayed(idleNavigation, NAVIGATION_IDLE_MS);
        return waking;
    }

    boolean onKey(KeyEvent event) {
        int key = event.getKeyCode();
        if (wakeKey != KeyEvent.KEYCODE_UNKNOWN && key == wakeKey) {
            if (event.getAction() == KeyEvent.ACTION_UP) wakeKey = KeyEvent.KEYCODE_UNKNOWN;
            else if (event.getAction() == KeyEvent.ACTION_DOWN) navigationInteraction();
            return true;
        }
        boolean navigationKey = key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT
                || key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN
                || key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER
                || key == KeyEvent.KEYCODE_NUMPAD_ENTER || key == KeyEvent.KEYCODE_SPACE
                || key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_MENU;
        if (event.getAction() == KeyEvent.ACTION_DOWN && (navigationKey || navigationVisible)) {
            boolean waking = navigationInteraction();
            if (waking && navigationKey) {
                wakeKey = key;
                return true;
            }
        }
        return false;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) wakeTouch = navigationInteraction();
        else if (action == MotionEvent.ACTION_MOVE) navigationInteraction();
        if (wakeTouch) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) wakeTouch = false;
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override public boolean dispatchGenericMotionEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_SCROLL) {
            boolean waking = navigationInteraction();
            if (waking) return true;
        } else if (action == MotionEvent.ACTION_HOVER_ENTER || action == MotionEvent.ACTION_HOVER_MOVE) {
            if (event.getX() != mouseX || event.getY() != mouseY) {
                mouseX = event.getX();
                mouseY = event.getY();
                navigationInteraction();
            }
        } else if (action == MotionEvent.ACTION_HOVER_EXIT) {
            mouseX = mouseY = Float.NaN;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float scale = Math.min(w / 1920f, h / 1080f);
        mount.setLayoutParams(new FrameLayout.LayoutParams(w, h));
        int mat = Math.round(64 * scale), edge = Math.max(1, Math.round(2 * scale));
        GradientDrawable paper = new GradientDrawable();
        paper.setColor(0xfffffbf5);
        paper.setStroke(edge, 0xffd6cbbd);
        mount.setBackground(paper);
        mount.setPadding(mat, mat, mat, mat);
        int bevel = Math.max(1, Math.round(8 * scale));
        picture.setBackground(new Bevel(bevel));
        picture.setPadding(bevel, bevel, bevel, bevel);
        for (View layer : new View[] {blurred, menuVeil}) {
            FrameLayout.LayoutParams aperture = new FrameLayout.LayoutParams(-1, -1);
            aperture.setMargins(bevel, bevel, bevel, bevel);
            layer.setLayoutParams(aperture);
        }
        FrameLayout.LayoutParams nav = new FrameLayout.LayoutParams(Math.round(1728 * scale), Math.round(112 * scale),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        nav.bottomMargin = Math.round(86 * scale);
        navigation.setLayoutParams(nav);
        for (int i = 0; i < buttons.length; i++) {
            TextView button = buttons[i];
            LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, -1, 1);
            size.leftMargin = size.rightMargin = Math.round(9 * scale);
            button.setLayoutParams(size);
            int horizontal = Math.round(4 * scale);
            button.setPadding(horizontal, 0, horizontal, 0);
            button.setTextSize(TypedValue.COMPLEX_UNIT_PX, 34 * scale);
            button.setBackground(focusBackground(scale));
            button.animate().cancel();
            float zoom = button.isFocused() ? 1.05f : 1;
            button.setScaleX(zoom);
            button.setScaleY(zoom);
            button.setTranslationY(button.isFocused() ? -4 * scale : 0);
            button.setTranslationZ(button.isFocused() ? 8 * scale : 0);
        }
        for (int i = 0; i < navigation.getChildCount(); i++) {
            View child = navigation.getChildAt(i);
            if (child instanceof TextView) continue;
            child.setLayoutParams(new LinearLayout.LayoutParams(Math.max(1, Math.round(scale)), Math.round(72 * scale)));
        }
        loadArtwork();
    }

    private static final class Bevel extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path[] sides = {new Path(), new Path(), new Path(), new Path()};
        private final int[] colors = {0xffb9ab98, 0xffe8dece, 0xffded2c2, 0xffc6b8a5};
        private final int width;
        private int alpha = 255;

        Bevel(int width) { this.width = width; }

        @Override protected void onBoundsChange(android.graphics.Rect bounds) {
            float l = bounds.left, t = bounds.top, r = bounds.right, b = bounds.bottom, d = width;
            side(0, l, t, r, t, r - d, t + d, l + d, t + d);
            side(1, r, t, r, b, r - d, b - d, r - d, t + d);
            side(2, r, b, l, b, l + d, b - d, r - d, b - d);
            side(3, l, b, l, t, l + d, t + d, l + d, b - d);
        }

        private void side(int i, float x1, float y1, float x2, float y2,
                          float x3, float y3, float x4, float y4) {
            Path path = sides[i];
            path.reset();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            path.lineTo(x3, y3);
            path.lineTo(x4, y4);
            path.close();
        }

        @Override public void draw(Canvas canvas) {
            for (int i = 0; i < sides.length; i++) {
                paint.setColor(colors[i]);
                paint.setAlpha(alpha);
                canvas.drawPath(sides[i], paint);
            }
        }

        @Override public void setAlpha(int value) { alpha = value; invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    static StateListDrawable focusBackground(float scale) {
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.WHITE);
        focused.setCornerRadius(24 * scale);
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[] {android.R.attr.state_pressed}, focused);
        states.addState(new int[0], new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return states;
    }

    void resume() {
        active = true;
        wakeKey = KeyEvent.KEYCODE_UNKNOWN;
        wakeTouch = false;
        hideNavigation();
        loadArtwork();
    }

    void pause() {
        active = false;
        removeCallbacks(idleNavigation);
        channelRequest++;
        channelBusy = false;
    }

    void reset() {
        navigationFocus = 0;
        if (navigationVisible) buttons[0].requestFocus();
    }

    void settings() { activity.startActivity(new Intent(activity, ArtSettingsActivity.class)); }

    void destroy() {
        destroyed = true;
        blurGeneration++;
        removeCallbacks(idleNavigation);
        io.shutdownNow();
        picture.setImageDrawable(null);
        blurred.setImageDrawable(null);
    }

    private static LinearGradient menuFade(int height, int color) {
        return new LinearGradient(0, height * .65f, 0, height * .88f,
                color & 0x00ffffff, color, Shader.TileMode.CLAMP);
    }

    private static final class MenuVeil extends Drawable {
        private final Paint paint = new Paint(Paint.DITHER_FLAG);

        @Override protected void onBoundsChange(android.graphics.Rect bounds) {
            paint.setShader(menuFade(bounds.height(), 0x99fffbf5));
        }

        @Override public void draw(Canvas canvas) { canvas.drawRect(getBounds(), paint); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
        @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private void prepareBlur() {
        int width = picture.getWidth() - picture.getPaddingLeft() - picture.getPaddingRight();
        int height = picture.getHeight() - picture.getPaddingTop() - picture.getPaddingBottom();
        if (destroyed || picture.getDrawable() == null || width <= 0 || height <= 0) return;
        final int generation = ++blurGeneration;
        Bitmap small = Bitmap.createBitmap(192, 108, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(small);
        canvas.scale(192f / width, 108f / height);
        canvas.translate(-picture.getPaddingLeft(), -picture.getPaddingTop());
        picture.draw(canvas);
        blurred.setImageDrawable(null);
        io.execute(() -> {
            int[] pixels = new int[192 * 108];
            small.getPixels(pixels, 0, 192, 0, 0, 192, 108);
            ScreenBlur.blur(pixels, 192, 108, 2, 2);
            small.setPixels(pixels, 0, 192, 0, 0, 192, 108);
            Paint fade = new Paint();
            fade.setShader(menuFade(108, Color.BLACK));
            fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
            new Canvas(small).drawRect(0, 0, 192, 108, fade);
            post(() -> {
                if (destroyed || generation != blurGeneration) {
                    small.recycle();
                    return;
                }
                blurred.setImageBitmap(small);
            });
        });
    }

    private void loadArtwork() {
        String selected = Prefs.artBackground();
        if (destroyed || loading || picture.getWidth() == 0 || picture.getHeight() == 0 || selected.equals(shown)) return;
        int width = mount.getWidth() - mount.getPaddingLeft() - mount.getPaddingRight();
        int height = mount.getHeight() - mount.getPaddingTop() - mount.getPaddingBottom();
        if (width <= 0 || height <= 0) return;
        loading = true;
        io.execute(() -> {
            try {
                List<Artworks.Entry> entries = Artworks.list(activity);
                Artworks.Entry chosen = entries.get(0);
                boolean found = selected.isEmpty();
                for (Artworks.Entry entry : entries) if (entry.id.equals(selected)) {
                    chosen = entry;
                    found = true;
                }
                Bitmap image = Artworks.decode(activity, chosen, width, height);
                boolean missing = !found;
                post(() -> {
                    loading = false;
                    if (destroyed) return;
                    picture.setImageBitmap(image);
                    shown = selected;
                    prepareBlur();
                    if (missing) error(Ui.tr("Background image"));
                    loadArtwork();
                });
            } catch (IOException e) {
                AppLog.w("art background: " + e.getMessage());
                post(() -> { loading = false; if (!destroyed) error(Ui.tr("Background image")); });
            }
        });
    }

    private void activate(int item) {
        if (item < 3) {
            openChannel(new String[] {"ard", "zdf", "kika"}[item]);
        } else if (item == 3) {
            if (!SonyTv.openLive(activity)) error(Ui.tr("Live TV"));
        } else {
            Intent settings = new Intent(activity, ArtSettingsActivity.class);
            if (item == 4) settings.putExtra(ArtSettingsActivity.INPUT, true);
            activity.startActivity(settings);
        }
    }

    private void openChannel(String channel) {
        if (channelBusy || destroyed || !active) return;
        if (!SonyArtChannels.configured()) {
            AppLog.w("Channel shortcuts need sony_tv_url in config.properties and Sony pairing");
            error(channel);
            return;
        }
        int request = ++channelRequest;
        channelBusy = true;
        io.execute(() -> {
            try {
                sonyChannels.tune(channel, () -> !destroyed && active && request == channelRequest);
                post(() -> {
                    if (!destroyed && request == channelRequest) channelBusy = false;
                });
            } catch (IOException | org.json.JSONException | RuntimeException e) {
                AppLog.w("Sony art channel " + channel + ": " + e.getMessage());
                post(() -> {
                    if (destroyed || !active || request != channelRequest) return;
                    channelBusy = false;
                    error(channel);
                });
            }
        });
    }

    private void error(String name) {
        Toast.makeText(activity, Ui.trf("Couldn't open {app}.", "app", name), Toast.LENGTH_LONG).show();
    }
}
