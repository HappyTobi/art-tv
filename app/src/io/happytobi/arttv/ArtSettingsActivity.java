package io.happytobi.arttv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Art selection changes only this app's preferences, never the TV's picture or home-role settings. */
public final class ArtSettingsActivity extends Activity {
    static final String INPUT = "artInput";
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private LinearLayout gallery;
    private TextView backgroundButton, closeButton;
    private View artworkFocus;
    private final List<TextView> actions = new ArrayList<>();
    private final List<View> cards = new ArrayList<>();
    private boolean destroyed, active;
    private float scale;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Ui.init(this);
        DisplayMetrics display = getResources().getDisplayMetrics();
        scale = Math.min(display.widthPixels / 1920f, display.heightPixels / 1080f);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xffe6e0d7);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(40), px(28), px(40), px(32));
        page.setBackground(shape(0xfff8f5ef, 32));
        FrameLayout.LayoutParams window = new FrameLayout.LayoutParams(-1, -1);
        window.setMargins(px(56), px(56), px(56), px(56));
        root.addView(page, window);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("Art TV", 21);
        brand.setTextColor(0xff746e64);
        titles.addView(brand);
        TextView heading = text(Ui.tr("Settings"), 46);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        titles.addView(heading);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        closeButton = button(Ui.tr("Cancel"), this::finish);
        closeButton.setGravity(Gravity.CENTER);
        closeButton.setSingleLine(true);
        header.addView(closeButton, new LinearLayout.LayoutParams(px(184), px(72)));
        page.addView(header, new LinearLayout.LayoutParams(-1, px(110)));
        View rule = new View(this);
        rule.setBackgroundColor(0xffded8ce);
        page.addView(rule, new LinearLayout.LayoutParams(-1, px(1)));

        LinearLayout body = new LinearLayout(this);
        body.setClipChildren(false);
        LinearLayout.LayoutParams bodySize = new LinearLayout.LayoutParams(-1, 0, 1);
        bodySize.topMargin = px(22);
        page.addView(body, bodySize);
        ScrollView sidebar = new ScrollView(this);
        sidebar.setBackground(shape(0xffefeae2, 24));
        sidebar.setVerticalScrollBarEnabled(false);
        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(px(18), px(18), px(18), px(18));
        sidebar.addView(menu);
        body.addView(sidebar, new LinearLayout.LayoutParams(px(400), -1));
        backgroundButton = action(menu, Ui.tr("Background image"), () -> {
            if (artworkFocus != null) artworkFocus.requestFocus();
        });
        action(menu, "Apple TV · " + Ui.tr("HDMI Inputs"), () -> inputs(false));
        action(menu, Ui.tr("Live TV"), () -> {
            if (!SonyTv.openLive(this)) error(Ui.tr("Live TV"));
        });
        action(menu, "Sony · " + Ui.tr("Home Screen"), this::originalHome);
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).setNextFocusUpId(i == 0 ? closeButton.getId() : actions.get(i - 1).getId());
            actions.get(i).setNextFocusDownId(actions.get(Math.min(i + 1, actions.size() - 1)).getId());
            actions.get(i).setNextFocusLeftId(actions.get(i).getId());
        }

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams contentSize = new LinearLayout.LayoutParams(0, -1, 1);
        contentSize.leftMargin = px(40);
        body.addView(content, contentSize);
        TextView section = text(Ui.tr("Background image"), 32);
        section.setTypeface(Typeface.DEFAULT_BOLD);
        section.setPadding(px(10), 0, 0, px(14));
        content.addView(section);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        gallery = new LinearLayout(this);
        gallery.setOrientation(LinearLayout.VERTICAL);
        gallery.setPadding(0, 0, 0, px(12));
        gallery.setClipChildren(false);
        scroll.addView(gallery);
        setContentView(root);
        backgroundButton.requestFocus();
        loadGallery();
        if (getIntent().getBooleanExtra(INPUT, false)) inputs(true);
    }

    private void loadGallery() {
        io.execute(() -> {
            try {
                List<Artworks.Entry> entries = Artworks.list(this);
                runOnUiThread(() -> {
                    if (destroyed) return;
                    for (int start = 0; start < entries.size(); start += 3) {
                        LinearLayout row = new LinearLayout(this);
                        row.setClipChildren(false);
                        gallery.addView(row);
                        for (int i = start; i < Math.min(start + 3, entries.size()); i++) addArtwork(row, entries.get(i), i == 0);
                        for (int i = entries.size(); i < start + 3; i++) {
                            LinearLayout.LayoutParams empty = new LinearLayout.LayoutParams(0, 1, 1);
                            empty.setMargins(px(10), 0, px(10), 0);
                            row.addView(new View(this), empty);
                        }
                    }
                    for (int i = 0; i < cards.size(); i++) {
                        View card = cards.get(i);
                        card.setNextFocusLeftId(i % 3 == 0 ? backgroundButton.getId() : cards.get(i - 1).getId());
                        card.setNextFocusRightId(i % 3 < 2 && i + 1 < cards.size() ? cards.get(i + 1).getId() : card.getId());
                        card.setNextFocusUpId(i >= 3 ? cards.get(i - 3).getId() : closeButton.getId());
                        card.setNextFocusDownId(i + 3 < cards.size() ? cards.get(i + 3).getId() : card.getId());
                    }
                    if (artworkFocus != null) {
                        for (TextView action : actions) action.setNextFocusRightId(artworkFocus.getId());
                        closeButton.setNextFocusDownId(artworkFocus.getId());
                        closeButton.setNextFocusLeftId(backgroundButton.getId());
                    }
                });
            } catch (IOException e) {
                AppLog.w("art gallery: " + e.getMessage());
                runOnUiThread(() -> { if (!destroyed) error(Ui.tr("Background image")); });
            }
        });
    }

    private void addArtwork(LinearLayout row, Artworks.Entry entry, boolean first) {
        LinearLayout card = new LinearLayout(this);
        card.setId(View.generateViewId());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(px(14), px(14), px(14), px(14));
        card.setFocusable(true);
        card.setClickable(true);
        focusStyle(card, 0xffeee8de);
        card.setSelected(entry.id.equals(Prefs.artBackground()) || first && Prefs.artBackground().isEmpty());
        ImageView thumbnail = new ImageView(this);
        thumbnail.setScaleType(ImageView.ScaleType.FIT_CENTER);
        thumbnail.setBackgroundColor(0xfffffbf5);
        thumbnail.setPadding(px(8), px(8), px(8), px(8));
        card.addView(thumbnail, new LinearLayout.LayoutParams(-1, px(168)));
        TextView title = text(entry.title, 24);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(0, px(8), 0, 0);
        card.addView(title, new LinearLayout.LayoutParams(-1, px(70)));
        TextView selected = text(Ui.tr("On"), 18);
        selected.setPadding(px(12), px(2), px(12), px(2));
        selected.setBackground(shape(0xffe4ddd2, 12));
        selected.setVisibility(card.isSelected() ? View.VISIBLE : View.INVISIBLE);
        LinearLayout.LayoutParams badgeSize = new LinearLayout.LayoutParams(-2, -2);
        badgeSize.topMargin = px(4);
        card.addView(selected, badgeSize);
        card.setOnClickListener(v -> {
            Prefs.setArtBackground(entry.id);
            finish();
        });
        card.setContentDescription(entry.title + (card.isSelected() ? ", " + Ui.tr("On") : ""));
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, px(304), 1);
        size.setMargins(px(10), 0, px(10), px(18));
        row.addView(card, size);
        cards.add(card);
        if (first || card.isSelected()) artworkFocus = card;
        io.execute(() -> {
            try {
                Bitmap image = Artworks.decode(this, entry, 440, 208);
                runOnUiThread(() -> { if (!destroyed) thumbnail.setImageBitmap(image); });
            } catch (IOException e) {
                AppLog.w("art thumbnail: " + e.getMessage());
                runOnUiThread(() -> {
                    if (!destroyed) {
                        card.setEnabled(false);
                        error(entry.title);
                    }
                });
            }
        });
    }

    private void inputs(boolean open) {
        io.execute(() -> {
            final List<SonyTv.Input> ports;
            try {
                ports = SonyTv.inputs(this);
            } catch (RuntimeException failure) {
                AppLog.w("Cannot list Sony HDMI inputs: " + failure.getMessage());
                runOnUiThread(() -> { if (!destroyed && active) error(Ui.tr("HDMI Inputs")); });
                return;
            }
            runOnUiThread(() -> {
                if (destroyed || !active) return;
                if (ports.isEmpty()) {
                    Toast.makeText(this, Ui.tr("No inputs"), Toast.LENGTH_LONG).show();
                    return;
                }
                int selected = -1;
                String[] labels = new String[ports.size()];
                for (int i = 0; i < ports.size(); i++) {
                    SonyTv.Input port = ports.get(i);
                    labels[i] = "HDMI " + port.number + " · " + port.label;
                    if (port.inputId.equals(Prefs.artInput())) selected = i;
                }
                if (open && selected >= 0) {
                    openInput(ports.get(selected));
                    return;
                }
                showInputs(ports, labels, selected, open);
            });
        });
    }

    private void showInputs(List<SonyTv.Input> ports, String[] labels, int selected, boolean open) {
        AlertDialog dialog = new AlertDialog.Builder(this).create();
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(px(36), px(32), px(36), px(32));
        sheet.setBackground(shape(0xfff8f5ef, 20));
        TextView heading = text("Apple TV · " + Ui.tr("HDMI Inputs"), 36);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        heading.setPadding(0, 0, 0, px(18));
        sheet.addView(heading);
        ScrollView scroll = new ScrollView(this);
        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(choices);
        sheet.addView(scroll, new LinearLayout.LayoutParams(-1, px(Math.min(ports.size() * 84, 420))));
        View first = null;
        for (int i = 0; i < ports.size(); i++) {
            SonyTv.Input port = ports.get(i);
            TextView choice = button(labels[i] + (i == selected ? " · " + Ui.tr("On") : ""), () -> {
                Prefs.setArtInput(port.inputId);
                dialog.dismiss();
                if (open) openInput(port);
            });
            choices.addView(choice, new LinearLayout.LayoutParams(-1, -2));
            if (first == null || i == selected) first = choice;
        }
        sheet.addView(button(Ui.tr("Cancel"), () -> {
            dialog.dismiss();
            if (open) finish();
        }));
        dialog.setView(sheet, 0, 0, 0, 0);
        dialog.setOnCancelListener(ignored -> { if (open) finish(); });
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(shape(0xfff8f5ef, 20));
        dialog.getWindow().setLayout(px(960),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (first != null) first.requestFocus();
    }

    private void originalHome() {
        Intent original = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .setPackage("com.google.android.tvlauncher")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        original.setComponent(new ComponentName("com.google.android.tvlauncher", "com.google.android.tvlauncher.MainActivity"));
        try {
            startActivity(original);
        } catch (ActivityNotFoundException | SecurityException e) {
            AppLog.w("art original Sony home: " + e.getMessage());
            error("Sony · " + Ui.tr("Home Screen"));
        }
    }

    private void openInput(SonyTv.Input port) {
        if (SonyTv.openInput(this, port)) finish();
        else error(port.label);
    }

    private TextView button(String title, Runnable action) {
        TextView button = text(title, 28);
        button.setId(View.generateViewId());
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setPadding(px(20), px(12), px(20), px(12));
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinHeight(px(84));
        button.setFocusable(true);
        button.setClickable(true);
        focusStyle(button, Color.TRANSPARENT);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private TextView action(LinearLayout menu, String title, Runnable action) {
        TextView item = button(title, action);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2);
        size.bottomMargin = px(10);
        menu.addView(item, size);
        actions.add(item);
        return item;
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(px(radius));
        return shape;
    }

    private void focusStyle(View view, int normal) {
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[] {android.R.attr.state_focused}, shape(Color.WHITE, 20));
        states.addState(new int[] {android.R.attr.state_pressed}, shape(Color.WHITE, 20));
        states.addState(new int[0], shape(normal, 20));
        view.setBackground(states);
        view.setDefaultFocusHighlightEnabled(false);
    }

    private TextView text(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * scale);
        text.setTextColor(ArtHomeView.INK);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        return text;
    }

    private int px(int value) { return Math.max(1, Math.round(value * scale)); }

    private void error(String name) {
        Toast.makeText(this, Ui.trf("Couldn't open {app}.", "app", name), Toast.LENGTH_LONG).show();
    }

    @Override protected void onStart() {
        super.onStart();
        ArtAudioFocus.enter(this);
    }

    @Override protected void onStop() {
        ArtAudioFocus.leave();
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
    }

    @Override protected void onPause() {
        active = false;
        super.onPause();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        io.shutdownNow();
        super.onDestroy();
    }
}
