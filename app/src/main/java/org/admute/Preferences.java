package org.admute;

import android.content.Context;
import android.content.SharedPreferences;

final class Preferences {
    static SharedPreferences get(Context context) { return context.getSharedPreferences("comfort", Context.MODE_PRIVATE); }
    static boolean enabled(Context context, String key) { return get(context).getBoolean(key, true); }
}
