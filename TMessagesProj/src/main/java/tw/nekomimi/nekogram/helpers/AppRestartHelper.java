package tw.nekomimi.nekogram.helpers;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

public final class AppRestartHelper {

    public static void triggerRebirth(Context context, Intent... nextIntents) {
        if (context == null || nextIntents == null || nextIntents.length == 0) {
            return;
        }
        Intent intent = nextIntents[0];
        intent.addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        if (context instanceof Activity) {
            ((Activity) context).finishAffinity();
        }
    }
}

