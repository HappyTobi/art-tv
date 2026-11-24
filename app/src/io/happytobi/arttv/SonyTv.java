package io.happytobi.arttv;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SonyTv {
    static final class Input {
        final String inputId, label;
        final int number;

        Input(String id, String label, int number) {
            this.inputId = id;
            this.label = label;
            this.number = number;
        }
    }

    static List<Input> inputs(Context context) {
        TvInputManager manager = context.getSystemService(TvInputManager.class);
        if (manager == null) throw new IllegalStateException("TV input service unavailable");
        List<TvInputInfo> ports = new ArrayList<>();
        Map<String, String> devices = new HashMap<>();
        for (TvInputInfo info : manager.getTvInputList()) {
            if (info.getType() != TvInputInfo.TYPE_HDMI) continue;
            if (info.getParentId() == null) ports.add(info);
            else devices.put(info.getParentId(), String.valueOf(info.loadLabel(context)));
        }
        ports.sort(Comparator.comparing(TvInputInfo::getId));
        List<Input> result = new ArrayList<>();
        Pattern digits = Pattern.compile("(\\d+)");
        for (int i = 0; i < ports.size(); i++) {
            TvInputInfo port = ports.get(i);
            String label = String.valueOf(port.loadLabel(context));
            Matcher match = digits.matcher(label);
            int number = match.find() ? Integer.parseInt(match.group(1)) : i + 1;
            CharSequence custom = port.loadCustomLabel(context);
            if (custom != null && custom.length() > 0) label = custom.toString();
            else if (devices.containsKey(port.getId())) label = devices.get(port.getId());
            result.add(new Input(port.getId(), label, number));
        }
        result.sort(Comparator.comparingInt(port -> port.number));
        return result;
    }

    static boolean openInput(Activity activity, Input input) {
        return open(activity, new Intent(Intent.ACTION_VIEW,
                TvContract.buildChannelUriForPassthroughInput(input.inputId)));
    }

    static boolean openLive(Activity activity) {
        Intent intent = activity.getPackageManager().getLeanbackLaunchIntentForPackage("com.sony.dtv.tvx");
        if (intent == null) intent = activity.getPackageManager().getLaunchIntentForPackage("com.sony.dtv.tvx");
        if (intent == null) {
            AppLog.w("Sony TV app is unavailable");
            return false;
        }
        return open(activity, intent);
    }

    private static boolean open(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException error) {
            AppLog.w("Cannot open Sony TV: " + error.getMessage());
            return false;
        }
    }
}
