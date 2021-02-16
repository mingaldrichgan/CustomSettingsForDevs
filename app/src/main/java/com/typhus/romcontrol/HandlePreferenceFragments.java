package com.typhus.romcontrol;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.Toast;

import com.stericson.RootShell.exceptions.RootDeniedException;
import com.stericson.RootShell.execution.Command;
import com.stericson.RootTools.RootTools;
import com.typhus.romcontrol.prefs.ColorPickerPreference;
import com.typhus.romcontrol.prefs.FilePreference;
import com.typhus.romcontrol.prefs.IntentDialogPreference;
import com.typhus.romcontrol.prefs.MyEditTextPreference;
import com.typhus.romcontrol.prefs.MyListPreference;
import com.typhus.romcontrol.prefs.SeekBarPreference;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.concurrent.TimeoutException;


/*      Created by Roberto Mariani and Anna Berkovitch, 21/06/15
        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <http://www.gnu.org/licenses/>.*/
public class HandlePreferenceFragments implements SharedPreferences.OnSharedPreferenceChangeListener, Preference.OnPreferenceClickListener {
    PreferenceFragment pf;
    Context c;
    PreferenceManager pm;
    String spName;
    SharedPreferences prefs;
    SharedPreferences.Editor ed;
    ContentResolver cr;
    ListAdapter adapter;
    boolean isOutOfBounds;


    /*Main constructor, manages what we need to do in the onCreate of each PreferenceFragment. We instantiate
    * this class in the onCreate method of each fragment and setting: shared preference file name (String spName),
    * as well is adding preferences from resource, by using spName in getIdentifier.
    * Basically, the shared preference name and the preference xml file will have the same name.
    * In addition, all the class variables are set here*/
    public HandlePreferenceFragments(Context context, PreferenceFragment pf, String spName) {
        this.pf = pf;
        this.c = context;
        this.spName = spName;
        pm = pf.getPreferenceManager();
        pm.setSharedPreferencesName(spName);
        prefs = pm.getSharedPreferences();
        ed = prefs.edit();
        cr = c.getContentResolver();
        int id = c.getResources().getIdentifier(spName, "xml", c.getPackageName());
        pf.addPreferencesFromResource(id);
    }

    /*Called from onResume method in PreferenceFragment. This method will set all the preferences upon resuming fragment,
    * by integrating the defaultValue (must be set in xml for each "valuable" preference item) and data retrived using
    * ContentResolver from Settings.System. Here we also register the OnSharedPreferenceChangeListener, which we will later
    * unregister in onPauseFragment.
    *
    * OnPreferenceClickListener is also initiated here, so our preferences are ready to go.*/
    public void onResumeFragment() {
        prefs.registerOnSharedPreferenceChangeListener(this);
        initAllKeys();
        getAllPrefs();
    }

    private void getAllPrefs() {
        //Get all preferences in the main preference screen
        adapter = pf.getPreferenceScreen().getRootAdapter();
        for (int i = 0; i < adapter.getCount(); i++) {
            Preference p = (Preference) adapter.getItem(i);
            if (p instanceof PreferenceScreen) {
                //Call allGroups method to retrieve all preferences in the nested Preference Screens
                allGroups(p);

            }
        }
    }

    public void allGroups(Preference p) {
        PreferenceScreen ps = (PreferenceScreen) p;
        ps.setOnPreferenceClickListener(this);

            /*Initiate icon view for preferences with keys that are interpreted as Intent
            *For more info see OnPreferenceClick method*/
        if (ps.getKey() != null) {
            if (ps.getKey().contains(".")) {
                int lastDot = ps.getKey().lastIndexOf(".");
                String pkgName = ps.getKey().substring(0, lastDot);
                try {
                    //if application package exists, we will set the icon successfully
                    Drawable icon = c.getPackageManager().getApplicationIcon(pkgName);
                    ps.setIcon(icon);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                    /*In case of exception, icon will not be set and we will remove the preference to avoid crashes on clicks
                    *To find the parent for each preference screen we use HashMap to buil the parent tree*/
                    Map<Preference, PreferenceScreen> preferenceParentTree = buildPreferenceParentTree();
                    PreferenceScreen preferenceParent = preferenceParentTree.get(ps);
                    preferenceParent.removePreference(ps);

                }
            }
        }

        for (int i = 0; i < ps.getPreferenceCount(); i++) {
            Preference p1 = ps.getPreference(i);
            if (p1 instanceof PreferenceScreen) {
                /*As we descend further on a preference tree, if we meet another PreferenceScreen, we repeat the allGroups method.
                *This method will loop untill we don't have nested screeens anymore.*/
                allGroups(p1);

            }
        }
    }

    //Returns a map of preference tree
    public Map<Preference, PreferenceScreen> buildPreferenceParentTree() {
        final Map<Preference, PreferenceScreen> result = new HashMap<>();
        final Stack<PreferenceScreen> curParents = new Stack<>();
        curParents.add(pf.getPreferenceScreen());
        while (!curParents.isEmpty()) {
            final PreferenceScreen parent = curParents.pop();
            final int childCount = parent.getPreferenceCount();
            for (int i = 0; i < childCount; ++i) {
                final Preference child = parent.getPreference(i);
                result.put(child, parent);
                if (child instanceof PreferenceScreen)
                    curParents.push((PreferenceScreen) child);
            }
        }
        return result;
    }

    /*Main onResume method.
    * Here we create a map of all the keys in existence in each SharedPreference
    * Here: keys are all the keys in preferences
    *       entry.getValue is an object (? in map) for the entry: boolean, int, string and so on
    * We  work through all the entries and sort them by instances of their objects.
    * Knowing that our preferences return different objects in preferences (Checkbox/boolean... etc),
    * we can set specific values and even find specific preferences, as we loop through the map*/

    private void initAllKeys() {
        Map<String, ?> keys = pm.getSharedPreferences().getAll();

        for (Map.Entry<String, ?> entry : keys.entrySet()) {

            String key = entry.getKey();
            Preference p = pf.findPreference(key);

            if (entry.getValue() instanceof Boolean) {
                if (p instanceof FilePreference) {
                } else {
                    int prefInt;
                    int actualInt = 0;
                    boolean actualBoolean;
                    boolean boolValue = prefs.getBoolean(key, true);

                    prefInt = (boolValue) ? 1 : 0;

                    try {
                        actualInt = Settings.System.getInt(cr, key);
                    } catch (Settings.SettingNotFoundException e) {
                        Settings.System.putInt(cr, key, prefInt);
                        actualInt = prefInt;
                    }

                    actualBoolean = (actualInt == 0) ? false : true;
                    if (!String.valueOf(boolValue).equals(String.valueOf(actualBoolean))) {
                        ed.putBoolean(key, actualBoolean).commit();
                    }
                }
            } else if (entry.getValue() instanceof Integer) {
                int prefInt = prefs.getInt(key, 0);
                int actualInt = 0;
                try {
                    actualInt = Settings.System.getInt(cr, key);
                } catch (Settings.SettingNotFoundException e) {
                    Settings.System.putInt(cr, key, prefInt);
                    actualInt = prefInt;
                }
                if (prefInt != actualInt) {
                    ed.putInt(key, actualInt).commit();
                }

            } else if (entry.getValue() instanceof String) {
                String prefString = prefs.getString(key, "");
                String actualString = Settings.System.getString(cr, key);
                String t = (actualString == null) ? prefString : actualString;
                /*Big fix for the annoying and elusive IndexOutOfBoundsException on first install
                * Although the error never came back afterwards, it included copied out of bounds values to db
                * I had to catch exception and set boolean value accordingly to use it later on
                * That implies that on first install the summary for the first screen list preference will not be set
                * After that it will be just fine. No biggie for a great cause of not wiping my device anymore to try and catch the bastard*/
                try {
                    if (p instanceof MyListPreference) {
                        MyListPreference mlp = (MyListPreference) pf.findPreference(key);
                        CharSequence[] entries = mlp.getEntries();
                        //we specifically create string using the index. If it's out of bounds the string will be null
                        //And we have exception on index out of bounds
                        //Boolean isOutOfBounds returns false if "try" succeeded
                        String s = (String) entries[mlp.findIndexOfValue(t)];
                        Log.d("listview index", s);
                        isOutOfBounds = false;
                    }

                } catch (IndexOutOfBoundsException e) {
                    Log.d("listview index", "exception");
                    //boolean isOutOfBounds returns tru if exception was caught
                    isOutOfBounds = true;

                }
                if (p instanceof MyListPreference) {
                    //Any action on the rouge list preference will be performed only if there was no exception
                    if (!isOutOfBounds) {
                        if (actualString == null) {
                            Settings.System.putString(cr, key, prefString);
                        }
                        if (!prefString.equals(t)) {
                            Toast.makeText(c, t + "/" + prefString, Toast.LENGTH_SHORT).show();

                            ed.putString(key, t).commit();
                        }


                        MyListPreference l = (MyListPreference) pf.findPreference(key);
                        CharSequence[] mEntries = l.getEntries();
                        int mValueIndex = l.findIndexOfValue(t);
                        l.setSummary(mEntries[mValueIndex]);
                    }
                }
                if (p instanceof MyEditTextPreference) {
                    if (actualString == null) {
                        Settings.System.putString(cr, key, prefString);
                    }
                    if (!prefString.equals(t)) {
                        Toast.makeText(c, t + "/" + prefString, Toast.LENGTH_SHORT).show();

                        ed.putString(key, t).commit();
                    }
                    MyEditTextPreference et = (MyEditTextPreference) pf.findPreference(key);
                    et.setSummary(t);
                }
            }
        }

    }

    /*Method is called from OnPause of the preference fragment and it's main function is
    *to unregister the OnSharedPreferenceChangeListener*/
    public void onPauseFragment() {
        prefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    /*We sort through all the possibilities of changes preferences
    * A key is provided as param for the method so we use it to specify a preference
    * as well as retrieve a value from sharedpreferences or database*/
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference p = pf.findPreference(key);
        switch (p.getClass().getSimpleName()) {
            case "SwitchPreference":
                SwitchPreference s = (SwitchPreference) pf.findPreference(key);
                s.setChecked(sharedPreferences.getBoolean(key, true));
                //Blur on/off Android 11
                if (key.equals("qs_blur")){
                    if (s.isChecked()) {
                        Command c0 = new Command(0, "resetprop ro.surface_flinger.supports_background_blur 1 && sed -re 's/supports_background_blur=0/supports_background_blur=1/g' /data/adb/modules/PixelForAndroid11/system.prop > /data/adb/modules/PixelForAndroid11/new_system.prop && mv /data/adb/modules/PixelForAndroid11/new_system.prop /data/adb/modules/PixelForAndroid11/system.prop && sed -re 's/supports_background_blur=0/supports_background_blur=1/g' /data/adb/modules/MiA3ModsA11/system.prop > /data/adb/modules/MiA3ModsA11/new_system.prop && mv /data/adb/modules/MiA3ModsA11/new_system.prop /data/adb/modules/MiA3ModsA11/system.prop");
                        try {
                            RootTools.getShell(true).add(c0);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Command c1 = new Command(1, "resetprop ro.surface_flinger.supports_background_blur 0 && sed -re 's/supports_background_blur=1/supports_background_blur=0/g' /data/adb/modules/PixelForAndroid11/system.prop > /data/adb/modules/PixelForAndroid11/new_system.prop && mv /data/adb/modules/PixelForAndroid11/new_system.prop /data/adb/modules/PixelForAndroid11/system.prop && sed -re 's/supports_background_blur=1/supports_background_blur=0/g' /data/adb/modules/MiA3ModsA11/system.prop > /data/adb/modules/MiA3ModsA11/new_system.prop && mv /data/adb/modules/MiA3ModsA11/new_system.prop /data/adb/modules/MiA3ModsA11/system.prop");
                        try {
                            RootTools.getShell(true).add(c1);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                //Enable/Disable Tuner
                if (key.equals("sysui_tuner")){
                    if (s.isChecked()) {
                        Command c0 = new Command(0, "pm enable com.android.systemui/.tuner.TunerActivity");
                        try {
                            RootTools.getShell(true).add(c0);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Command c1 = new Command(1, "pm disable com.android.systemui/.tuner.TunerActivity");
                        try {
                            RootTools.getShell(true).add(c1);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                //Screen record on/off Android 11
                if (key.equals("qs_screenrecord")){
                    if (s.isChecked()) {
                        Command c0 = new Command(0, "settings list secure | grep sysui_qs_tiles > /sdcard/current_qs_tiles.txt && sed 's/,screenrecord//g' /sdcard/current_qs_tiles.txt > /sdcard/noscreen_qs_tiles.txt && sed 's/=/ /g' /sdcard/noscreen_qs_tiles.txt > /sdcard/new_qs_tiles.txt && sed 's/sysui_qs_tiles/settings put secure sysui_qs_tiles/g' /sdcard/new_qs_tiles.txt > /sdcard/new2_qs_tiles.txt && sed -re 's/\\(/\"(\"/g' /sdcard/new2_qs_tiles.txt > /sdcard/new3_qs_tiles.txt && sed -re 's/\\)/\")\"/g' /sdcard/new3_qs_tiles.txt > /sdcard/new4_qs_tiles.txt && echo -n \",screenrecord\" >> /sdcard/new5_qs_tiles.txt && paste -d \"\" /sdcard/new4_qs_tiles.txt /sdcard/new5_qs_tiles.txt > /sdcard/final_qs_tiles.txt && sh /sdcard/final_qs_tiles.txt && rm -rf /sdcard/current_qs_tiles.txt && rm -rf /sdcard/new_qs_tiles.txt && rm -rf /sdcard/new2_qs_tiles.txt && rm -rf /sdcard/new3_qs_tiles.txt && rm -rf /sdcard/new4_qs_tiles.txt && rm -rf /sdcard/new5_qs_tiles.txt && rm -rf /sdcard/final_qs_tiles.txt && rm -rf /sdcard/noscreen_qs_tiles.txt");
                        try {
                            RootTools.getShell(true).add(c0);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Command c1 = new Command(1, "settings list secure | grep sysui_qs_tiles > /sdcard/current_qs_tiles.txt && sed 's/,screenrecord//g' /sdcard/current_qs_tiles.txt > /sdcard/noscreen_qs_tiles.txt && sed 's/=/ /g' /sdcard/noscreen_qs_tiles.txt > /sdcard/new_qs_tiles.txt && sed 's/sysui_qs_tiles/settings put secure sysui_qs_tiles/g' /sdcard/new_qs_tiles.txt > /sdcard/new2_qs_tiles.txt && sed -re 's/\\(/\"(\"/g' /sdcard/new2_qs_tiles.txt > /sdcard/new3_qs_tiles.txt && sed -re 's/\\)/\")\"/g' /sdcard/new3_qs_tiles.txt > /sdcard/new4_qs_tiles.txt && echo -n \"\" >> /sdcard/new5_qs_tiles.txt && paste -d \"\" /sdcard/new4_qs_tiles.txt /sdcard/new5_qs_tiles.txt > /sdcard/final_qs_tiles.txt && sh /sdcard/final_qs_tiles.txt && rm -rf /sdcard/current_qs_tiles.txt && rm -rf /sdcard/new_qs_tiles.txt && rm -rf /sdcard/new2_qs_tiles.txt && rm -rf /sdcard/new3_qs_tiles.txt && rm -rf /sdcard/new4_qs_tiles.txt && rm -rf /sdcard/new5_qs_tiles.txt && rm -rf /sdcard/final_qs_tiles.txt && rm -rf /sdcard/noscreen_qs_tiles.txt");
                        try {
                            RootTools.getShell(true).add(c1);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                //FOD animation on/off
                if (key.equals("fod_recognizing_animation")){
                    if (s.isChecked()) {
                        Settings.System.putInt(c.getContentResolver(), "fod_animation_type", 1);
                    } else {
                        Settings.System.putInt(c.getContentResolver(), "fod_animation_type", 4);
                    }
                }
                //FOD vibration on/off
                if (key.equals("fod_vibrate")){
                    if (s.isChecked()) {
                        Settings.System.putInt(c.getContentResolver(), "haptic_feedback_intensity", 1);
                    } else {
                        AlertDialog.Builder mVibWarnBuilder = new AlertDialog.Builder(c);
                        mVibWarnBuilder.setTitle(R.string.attention);
                        mVibWarnBuilder.setMessage(R.string.novibrate);
                        mVibWarnBuilder.setPositiveButton(android.R.string.ok,null);
                        mVibWarnBuilder.create();
                        AlertDialog mVibWarn = mVibWarnBuilder.create();
                        mVibWarn.show();
                        TypedValue typedValue = new TypedValue();
                        Resources.Theme theme = c.getTheme();
                        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
                        Button ok = mVibWarn.getButton(AlertDialog.BUTTON_POSITIVE);
                        ok.setTextColor(typedValue.data);
                        mVibWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                        Settings.System.putInt(c.getContentResolver(), "haptic_feedback_intensity", 0);
                    }
                }
                /*Blur switch warning*/
                if (key.equals("qs_blur")){
                    AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
                    mSysUIWarnBuilder.setTitle(R.string.attention);
                    mSysUIWarnBuilder.setMessage(R.string.restart_required);
                    mSysUIWarnBuilder.setPositiveButton(android.R.string.ok,null);
                    AlertDialog mSysUIWarn = mSysUIWarnBuilder.create();
                    mSysUIWarn.show();
                    TypedValue typedValue = new TypedValue();
                    Resources.Theme theme = c.getTheme();
                    theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
                    Button ok = mSysUIWarn.getButton(AlertDialog.BUTTON_POSITIVE);
                    ok.setTextColor(typedValue.data);
                    mSysUIWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                }
                /*QS New Tile colors warning*/
                if (key.equals("qs_panel_bg_use_new_tint")){
                    AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
                    mSysUIWarnBuilder.setTitle(R.string.attention);
                    mSysUIWarnBuilder.setMessage(R.string.restartui_required);
                    mSysUIWarnBuilder.setPositiveButton(android.R.string.ok,null);
                    AlertDialog mSysUIWarn = mSysUIWarnBuilder.create();
                    mSysUIWarn.show();
                    TypedValue typedValue = new TypedValue();
                    Resources.Theme theme = c.getTheme();
                    theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
                    Button ok = mSysUIWarn.getButton(AlertDialog.BUTTON_POSITIVE);
                    ok.setTextColor(typedValue.data);
                    mSysUIWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                }
                /*Lock screen clock date and alarm info warning*/
                if (key.equals("clock_show_status_area")){
                    AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
                    mSysUIWarnBuilder.setTitle(R.string.attention);
                    mSysUIWarnBuilder.setMessage(R.string.restartui_required);
                    mSysUIWarnBuilder.setPositiveButton(android.R.string.ok,null);
                    AlertDialog mSysUIWarn = mSysUIWarnBuilder.create();
                    mSysUIWarn.show();
                    TypedValue typedValue = new TypedValue();
                    Resources.Theme theme = c.getTheme();
                    theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
                    Button ok = mSysUIWarn.getButton(AlertDialog.BUTTON_POSITIVE);
                    ok.setTextColor(typedValue.data);
                    mSysUIWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                }
                //Hide gestures navigation pill on/off
                if (key.equals("navbar_hide_pill")){
                    int geston = Settings.Secure.getInt(c.getContentResolver(), "navigation_mode", 0);
                    if(geston!=2){
                        AlertDialog.Builder mNoGesturesBuilder = new AlertDialog.Builder(c);
                        mNoGesturesBuilder.setTitle(R.string.requiresgestures);
                        mNoGesturesBuilder.setMessage(R.string.requiresgesturessummary);
                        mNoGesturesBuilder.setPositiveButton(android.R.string.ok,null);
                        mNoGesturesBuilder.create();
                        AlertDialog mNoGestures = mNoGesturesBuilder.create();
                        mNoGestures.show();
                        TypedValue typedValue = new TypedValue();
                        Resources.Theme theme = c.getTheme();
                        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
                        Button ok = mNoGestures.getButton(AlertDialog.BUTTON_POSITIVE);
                        ok.setTextColor(typedValue.data);
                        mNoGestures.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                        s.setChecked(false);
                        s.setEnabled(false);
                        Command c0 = new Command(0, "cmd overlay disable com.android.systemui.overlay.hidepill");
                        try {
                            RootTools.getShell(true).add(c0);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (s.isChecked() & geston==2) {
                        Command c1 = new Command(1, "cmd overlay enable com.android.systemui.overlay.hidepill");
                        try {
                            RootTools.getShell(true).add(c1);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Command c2 = new Command(2, "cmd overlay disable com.android.systemui.overlay.hidepill");
                        try {
                            RootTools.getShell(true).add(c2);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                //Reduce keyboard space on gestures navigation on/off
                if (key.equals("gestures_reduce_keyboard")){
                    int geston = Settings.Secure.getInt(c.getContentResolver(), "navigation_mode", 0);
                    if(geston!=2){
                        AlertDialog.Builder mNoGesturesBuilder = new AlertDialog.Builder(c);
                        mNoGesturesBuilder.setTitle(R.string.requiresgestures);
                        mNoGesturesBuilder.setMessage(R.string.requiresgesturessummary);
                        mNoGesturesBuilder.setPositiveButton(android.R.string.ok,null);
                        mNoGesturesBuilder.create();
                        AlertDialog mNoGestures = mNoGesturesBuilder.create();
                        mNoGestures.show();
                        TypedValue typedValue = new TypedValue();
                        Resources.Theme theme = c.getTheme();
                        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
                        Button ok = mNoGestures.getButton(AlertDialog.BUTTON_POSITIVE);
                        ok.setTextColor(typedValue.data);
                        mNoGestures.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                        s.setChecked(false);
                        s.setEnabled(false);
                        Command c0 = new Command(0, "cmd overlay disable com.android.overlay.reducekeyboard");
                        try {
                            RootTools.getShell(true).add(c0);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    }
                    if (s.isChecked() & geston==2) {
                        Command c1 = new Command(1, "cmd overlay enable com.android.overlay.reducekeyboard");
                        try {
                            RootTools.getShell(true).add(c1);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    } else {
                        Command c2 = new Command(2, "cmd overlay disable com.android.overlay.reducekeyboard");
                        try {
                            RootTools.getShell(true).add(c2);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                //Ambient visualizer on/off
                /* REMOVED ON ANDROID 11
                if (key.equals("lockscreen_visualizer_enabled")){
                    if (s.isChecked()) {
                        Settings.System.putInt(c.getContentResolver(), "ambient_visualizer_enabled", 1);
                    } else {
                        Settings.System.putInt(c.getContentResolver(), "ambient_visualizer_enabled", 0);
                    }
                }*/
                break;
            case "CheckBoxPreference":
                CheckBoxPreference cbp = (CheckBoxPreference) pf.findPreference(key);
                cbp.setChecked(sharedPreferences.getBoolean(key, true));
                break;
            case "MyListPreference":
                MyListPreference l = (MyListPreference) pf.findPreference(key);
                String lValue = sharedPreferences.getString(key, "");
                //Any action on the rouge list preference will be performed only if there was no exception
                if (!isOutOfBounds) {
                    CharSequence[] mEntries = l.getEntries();
                    int mValueIndex = l.findIndexOfValue(lValue);
                    l.setSummary(mEntries[mValueIndex]);
                    l.setSummary(mEntries[l.findIndexOfValue(lValue)]);
                    //Network Traffic specific options begin
                    if (key.equals("network_traffic_location")){
                        switch(mValueIndex) {
                            case 0:
                                Settings.System.putInt(c.getContentResolver(), "network_traffic_state", 0);
                                Settings.System.putInt(c.getContentResolver(), "network_traffic_view_location", 0);
                                break;
                            case 1:
                                Settings.System.putInt(c.getContentResolver(), "network_traffic_state", 1);
                                Settings.System.putInt(c.getContentResolver(), "network_traffic_view_location", 0);
                                break;
                            case 2:
                                Settings.System.putInt(c.getContentResolver(), "network_traffic_state", 1);
                                Settings.System.putInt(c.getContentResolver(), "network_traffic_view_location", 1);
                                break;
                            default:
                                Settings.System.putInt(c.getContentResolver(), "network_traffic_state", 0);
                                Settings.System.putInt(c.getContentResolver(), "network_traffic_view_location", 0);
                                break;
                        }
                    }
                    //Network Traffic specific options end
                    //Battery bar specific options begin
                    if (key.equals("statusbar_battery_bar_no_navbar_list")){
                        switch(mValueIndex) {
                            case 0:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_battery_bar", 0);
                                break;
                            case 1:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_battery_bar", 1);
                                break;
                            case 2:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_battery_bar", 2);
                                break;
                            default:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_battery_bar", 0);
                                break;
                        }
                    }
                    //Battery bar specific options end
                    //Clock AM PM specific options begin
                    if (key.equals("statusbar_am_pm")){
                        switch(mValueIndex) {
                            case 0:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_am_pm_style", 0);
                                Settings.System.putInt(c.getContentResolver(), "time_12_24", 24);
                                break;
                            case 1:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_am_pm_style", 1);
                                Settings.System.putInt(c.getContentResolver(), "time_12_24", 12);
                                break;
                            case 2:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_am_pm_style", 2);
                                Settings.System.putInt(c.getContentResolver(), "time_12_24", 12);
                                break;
                            default:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_am_pm_style", 0);
                                Settings.System.putInt(c.getContentResolver(), "time_12_24", 24);
                                break;
                        }
                    }
                    //Clock AM PM specific options end
                    //Clock Date display specific options begin
                    if (key.equals("clock_date_display")){
                        switch(mValueIndex) {
                            case 0:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_date_display", 0);
                                break;
                            case 1:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_date_display", 1);
                                break;
                            case 2:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_date_display", 2);
                                break;
                            default:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_date_display", 0);
                                break;
                        }
                    }
                    //Clock Date display specific options end
                    //Clock Date style specific options begin
                    if (key.equals("clock_date_style")){
                        switch(mValueIndex) {
                            case 0:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_date_style", 0);
                                break;
                            case 1:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_date_style", 1);
                                break;
                            case 2:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_date_style", 2);
                                break;
                            default:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_date_style", 0);
                                break;
                        }
                    }
                    //Clock Date style specific options end
                    //Lock Screen Clock style specific options begin
                    if (key.equals("lockscreen_clock_style")){
                        switch(mValueIndex) {
                            case 0:
                                Command c0 = new Command(0, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.DefaultClockController\"");
                                try {
                                    RootTools.getShell(true).add(c0);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 1:
                                Command c1 = new Command(1, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.AnalogClockController\"");
                                try {
                                    RootTools.getShell(true).add(c1);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 2:
                                Command c2 = new Command(2, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.BinaryClockController\"");
                                try {
                                    RootTools.getShell(true).add(c2);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 3:
                                Command c3 = new Command(3, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.BubbleClockController\"");
                                try {
                                    RootTools.getShell(true).add(c3);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 4:
                                Command c4 = new Command(4, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.DividedLinesClockController\"");
                                try {
                                    RootTools.getShell(true).add(c4);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 5:
                                Command c5 = new Command(5, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.MNMLBoxClockController\"");
                                try {
                                    RootTools.getShell(true).add(c5);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 6:
                                Command c6 = new Command(6, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.MNMLMinimalClockController\"");
                                try {
                                    RootTools.getShell(true).add(c6);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 7:
                                Command c7 = new Command(7, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.SpectrumClockController\"");
                                try {
                                    RootTools.getShell(true).add(c7);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 8:
                                Command c8 = new Command(8, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.SfunyClockController\"");
                                try {
                                    RootTools.getShell(true).add(c8);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 9:
                                Command c9 = new Command(9, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.TypeClockController\"");
                                try {
                                    RootTools.getShell(true).add(c9);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 10:
                                Command c10 = new Command(10, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.TypeClockAltController\"");
                                try {
                                    RootTools.getShell(true).add(c10);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 11:
                                Command c11 = new Command(11, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.SamsungClockController\"");
                                try {
                                    RootTools.getShell(true).add(c11);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 12:
                                Command c12 = new Command(12, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.SamsungHighlightClockController\"");
                                try {
                                    RootTools.getShell(true).add(c12);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            default:
                                Command cd = new Command(13, "settings put secure lock_screen_custom_clock_face \"com.android.keyguard.clock.DefaultClockController\"");
                                try {
                                    RootTools.getShell(true).add(cd);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                    //Lock Screen Clock style specific options end
                    //QS Tiles Styles options begin
                    if (key.equals("qs_tile_style")){
                        switch(mValueIndex) {
                            case 0:
                                Command c0 = new Command(0, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop");
                                try {
                                    RootTools.getShell(true).add(c0);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 1:
                                Command c1 = new Command(1, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.android.systemui.qstile.circletrim");
                                try {
                                    RootTools.getShell(true).add(c1);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 2:
                                Command c2 = new Command(2, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.android.systemui.qstile.dualtonecircletrim");
                                try {
                                    RootTools.getShell(true).add(c2);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 3:
                                Command c3 = new Command(3, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.android.systemui.qstile.squircletrim");
                                try {
                                    RootTools.getShell(true).add(c3);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 4:
                                Command c4 = new Command(4, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.android.systemui.qstile.wavey");
                                try {
                                    RootTools.getShell(true).add(c4);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 5:
                                Command c5 = new Command(5, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.android.systemui.qstile.pokesign");
                                try {
                                    RootTools.getShell(true).add(c5);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 6:
                                Command c6 = new Command(6, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.android.systemui.qstile.ninja");
                                try {
                                    RootTools.getShell(true).add(c6);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 7:
                                Command c7 = new Command(7, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.android.systemui.qstile.dottedcircle");
                                try {
                                    RootTools.getShell(true).add(c7);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 8:
                                Command c8 = new Command(8, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.android.systemui.qstile.attemptmountain");
                                try {
                                    RootTools.getShell(true).add(c8);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 9:
                                Command c9 = new Command(9, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.android.systemui.qstile.squaremedo");
                                try {
                                    RootTools.getShell(true).add(c9);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 10:
                                Command c10 = new Command(10, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.android.systemui.qstile.inkdrop");
                                try {
                                    RootTools.getShell(true).add(c10);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 11:
                                Command c11 = new Command(11, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.android.systemui.qstile.cookie");
                                try {
                                    RootTools.getShell(true).add(c11);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 12:
                                Command c12 = new Command(12, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.android.systemui.qstile.circleoutline");
                                try {
                                    RootTools.getShell(true).add(c12);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 13:
                                Command c13 = new Command(13, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.bootleggers.qstile.cosmos");
                                try {
                                    RootTools.getShell(true).add(c13);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 14:
                                Command c14 = new Command(14, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.bootleggers.qstile.divided");
                                try {
                                    RootTools.getShell(true).add(c14);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 15:
                                Command c15 = new Command(15, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.bootleggers.qstile.neonlike");
                                try {
                                    RootTools.getShell(true).add(c15);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 16:
                                Command c16 = new Command(16, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop && cmd overlay enable com.bootleggers.qstile.triangles");
                                try {
                                    RootTools.getShell(true).add(c16);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            default:
                                Command cd = new Command(17, "cmd overlay disable com.android.systemui.qstile.cookie && cmd overlay disable com.bootleggers.qstile.triangles && cmd overlay disable com.bootleggers.qstile.divided && cmd overlay disable com.android.systemui.qstile.attemptmountain && cmd overlay disable com.android.systemui.qstile.squircletrim && cmd overlay disable com.android.systemui.qstile.ninja && cmd overlay disable com.android.systemui.qstile.wavey && cmd overlay disable com.android.systemui.qstile.squaremedo && cmd overlay disable com.android.systemui.qstile.dualtonecircletrim && cmd overlay disable com.android.systemui.qstile.circleoutline && cmd overlay disable com.android.systemui.qstile.pokesign && cmd overlay disable com.bootleggers.qstile.cosmos && cmd overlay disable com.bootleggers.qstile.neonlike && cmd overlay disable com.android.systemui.qstile.circletrim && cmd overlay disable com.android.systemui.qstile.dottedcircle && cmd overlay disable com.android.systemui.qstile.inkdrop");
                                try {
                                    RootTools.getShell(true).add(cd);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                    //QS Tiles Styles options end
                    //QS battery percentage warning
                    if (key.equals("qs_show_battery_percent")){
                        AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
                        mSysUIWarnBuilder.setTitle(R.string.attention);
                        mSysUIWarnBuilder.setMessage(R.string.restartui_required);
                        mSysUIWarnBuilder.setPositiveButton(android.R.string.ok,null);
                        AlertDialog mSysUIWarn = mSysUIWarnBuilder.create();
                        mSysUIWarn.show();
                        TypedValue typedValue = new TypedValue();
                        Resources.Theme theme = c.getTheme();
                        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
                        Button ok = mSysUIWarn.getButton(AlertDialog.BUTTON_POSITIVE);
                        ok.setTextColor(typedValue.data);
                        mSysUIWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                        switch(mValueIndex) {
                            case 0:
                                Settings.System.putInt(c.getContentResolver(), "qs_show_battery_estimate", 0);
                                break;
                            case 1:
                                Settings.System.putInt(c.getContentResolver(), "qs_show_battery_estimate", 0);
                                break;
                            case 2:
                                Settings.System.putInt(c.getContentResolver(), "qs_show_battery_estimate", 0);
                                break;
                            default:
                                Settings.System.putInt(c.getContentResolver(), "qs_show_battery_estimate", 0);
                                break;
                        }
                    }
                    //FOD Animations options begin
                    if (key.equals("fod_anim")){
                        switch(mValueIndex) {
                            case 0:
                                Command c0 = new Command(0, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke");
                                try {
                                    RootTools.getShell(true).add(c0);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 1:
                                Command c1 = new Command(1, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.miuinormal");
                                try {
                                    RootTools.getShell(true).add(c1);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 2:
                                Command c2 = new Command(2, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.miuiblue");
                                try {
                                    RootTools.getShell(true).add(c2);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 3:
                                Command c3 = new Command(3, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.aurora1");
                                try {
                                    RootTools.getShell(true).add(c3);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 4:
                                Command c4 = new Command(4, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.aurora2");
                                try {
                                    RootTools.getShell(true).add(c4);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 5:
                                Command c5 = new Command(5, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.miuilight");
                                try {
                                    RootTools.getShell(true).add(c5);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 6:
                                Command c6 = new Command(6, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.pop");
                                try {
                                    RootTools.getShell(true).add(c6);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 7:
                                Command c7 = new Command(7, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.pulse");
                                try {
                                    RootTools.getShell(true).add(c7);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 8:
                                Command c8 = new Command(8, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.whitepulse");
                                try {
                                    RootTools.getShell(true).add(c8);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 9:
                                Command c9 = new Command(9, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.rhythm");
                                try {
                                    RootTools.getShell(true).add(c9);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 10:
                                Command c10 = new Command(10, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.miuistar");
                                try {
                                    RootTools.getShell(true).add(c10);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 11:
                                Command c11 = new Command(11, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.cosmos");
                                try {
                                    RootTools.getShell(true).add(c11);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 12:
                                Command c12 = new Command(12, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.energy");
                                try {
                                    RootTools.getShell(true).add(c12);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 13:
                                Command c13 = new Command(13, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.mclaren");
                                try {
                                    RootTools.getShell(true).add(c13);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 14:
                                Command c14 = new Command(14, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.ripple");
                                try {
                                    RootTools.getShell(true).add(c14);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 15:
                                Command c15 = new Command(15, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.stripe");
                                try {
                                    RootTools.getShell(true).add(c15);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 16:
                                Command c16 = new Command(16, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.wave");
                                try {
                                    RootTools.getShell(true).add(c16);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 17:
                                Command c17 = new Command(17, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.dna");
                                try {
                                    RootTools.getShell(true).add(c17);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 18:
                                Command c18 = new Command(18, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.future");
                                try {
                                    RootTools.getShell(true).add(c18);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 19:
                                Command c19 = new Command(19, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.haloring");
                                try {
                                    RootTools.getShell(true).add(c19);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 20:
                                Command c20 = new Command(20, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.molecular");
                                try {
                                    RootTools.getShell(true).add(c20);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 21:
                                Command c21 = new Command(21, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.firework");
                                try {
                                    RootTools.getShell(true).add(c21);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 22:
                                Command c22 = new Command(22, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.coloros1");
                                try {
                                    RootTools.getShell(true).add(c22);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 23:
                                Command c23 = new Command(23, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.coloros2");
                                try {
                                    RootTools.getShell(true).add(c23);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 24:
                                Command c24 = new Command(24, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.fantasy");
                                try {
                                    RootTools.getShell(true).add(c24);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 25:
                                Command c25 = new Command(25, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.smog");
                                try {
                                    RootTools.getShell(true).add(c25);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 26:
                                Command c26 = new Command(26, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.fusion");
                                try {
                                    RootTools.getShell(true).add(c26);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 27:
                                Command c27 = new Command(27, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.round");
                                try {
                                    RootTools.getShell(true).add(c27);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 28:
                                Command c28 = new Command(28, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.blueaurora");
                                try {
                                    RootTools.getShell(true).add(c28);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 29:
                                Command c29 = new Command(29, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.bluediamond");
                                try {
                                    RootTools.getShell(true).add(c29);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 30:
                                Command c30 = new Command(30, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.bounce");
                                try {
                                    RootTools.getShell(true).add(c30);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 31:
                                Command c31 = new Command(31, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.circuit");
                                try {
                                    RootTools.getShell(true).add(c31);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 32:
                                Command c32 = new Command(32, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.colourful");
                                try {
                                    RootTools.getShell(true).add(c32);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 33:
                                Command c33 = new Command(33, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.icontransform");
                                try {
                                    RootTools.getShell(true).add(c33);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 34:
                                Command c34 = new Command(34, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.purpleblueaura");
                                try {
                                    RootTools.getShell(true).add(c34);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 35:
                                Command c35 = new Command(35, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.purplesmoke");
                                try {
                                    RootTools.getShell(true).add(c35);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 36:
                                Command c36 = new Command(36, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.ripplelines");
                                try {
                                    RootTools.getShell(true).add(c36);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 37:
                                Command c37 = new Command(37, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.shortcircuit");
                                try {
                                    RootTools.getShell(true).add(c37);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 38:
                                Command c38 = new Command(38, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke && cmd overlay enable com.android.theme.fod.starexplosion");
                                try {
                                    RootTools.getShell(true).add(c38);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            default:
                                Command cd = new Command(39, "cmd overlay disable com.android.theme.fod.miuilight && cmd overlay disable com.android.theme.fod.coloros1 && cmd overlay disable com.android.theme.fod.coloros2 && cmd overlay disable com.android.theme.fod.firework && cmd overlay disable com.android.theme.fod.haloring && cmd overlay disable com.android.theme.fod.cosmos && cmd overlay disable com.android.theme.fod.energy && cmd overlay disable com.android.theme.fod.fusion && cmd overlay disable com.android.theme.fod.future && cmd overlay disable com.android.theme.fod.rhythm && cmd overlay disable com.android.theme.fod.ripple && cmd overlay disable com.android.theme.fod.stripe && cmd overlay disable com.android.theme.fod.mclaren && cmd overlay disable com.android.theme.fod.miuinormal && cmd overlay disable com.android.theme.fod.pulse && cmd overlay disable com.android.theme.fod.round && cmd overlay disable com.android.theme.fod.molecular && cmd overlay disable com.android.theme.fod.whitepulse && cmd overlay disable com.android.theme.fod.fantasy && cmd overlay disable com.android.theme.fod.smog && cmd overlay disable com.android.theme.fod.wave && cmd overlay disable com.android.theme.fod.miuiblue && cmd overlay disable com.android.theme.fod.miuistar && cmd overlay disable com.android.theme.fod.dna && cmd overlay disable com.android.theme.fod.pop && cmd overlay disable com.android.theme.fod.aurora1 && cmd overlay disable com.android.theme.fod.aurora2 && cmd overlay disable com.android.theme.fod.bounce && cmd overlay disable com.android.theme.fod.circuit && cmd overlay disable com.android.theme.fod.icontransform && cmd overlay disable com.android.theme.fod.ripplelines && cmd overlay disable com.android.theme.fod.blueaurora && cmd overlay disable com.android.theme.fod.starexplosion && cmd overlay disable com.android.theme.fod.purpleblueaura && cmd overlay disable com.android.theme.fod.colourful && cmd overlay disable com.android.theme.fod.bluediamond && cmd overlay disable com.android.theme.fod.shortcircuit && cmd overlay disable com.android.theme.fod.purplesmoke");
                                try {
                                    RootTools.getShell(true).add(cd);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                    //FOD Animations options end
                    //FOD Icon picker options begin
                    if (key.equals("fod_icon_picker")){
                        switch(mValueIndex) {
                            case 0:
                                Command c0 = new Command(0, "cmd overlay disable com.android.theme.fodicon.miui && cmd overlay disable com.android.theme.fodicon.g && cmd overlay disable com.android.theme.fodicon.op && cmd overlay disable com.android.theme.fodicon.ar && cmd overlay disable com.android.theme.fodicon.rb && cmd overlay disable com.android.theme.fodicon.cl && cmd overlay disable com.android.theme.fodicon.mb && cmd overlay disable com.android.theme.fodicon.op2 && cmd overlay disable com.android.theme.fodicon.miui3 && cmd overlay disable com.android.theme.fodicon.ro");
                                try {
                                    RootTools.getShell(true).add(c0);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 1:
                                Command c1 = new Command(1, "cmd overlay disable com.android.theme.fodicon.miui && cmd overlay disable com.android.theme.fodicon.g && cmd overlay disable com.android.theme.fodicon.op && cmd overlay disable com.android.theme.fodicon.ar && cmd overlay disable com.android.theme.fodicon.rb && cmd overlay disable com.android.theme.fodicon.cl && cmd overlay disable com.android.theme.fodicon.mb && cmd overlay disable com.android.theme.fodicon.op2 && cmd overlay disable com.android.theme.fodicon.miui3 && cmd overlay disable com.android.theme.fodicon.ro && cmd overlay enable com.android.theme.fodicon.miui");
                                try {
                                    RootTools.getShell(true).add(c1);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 2:
                                Command c2 = new Command(2, "cmd overlay disable com.android.theme.fodicon.miui && cmd overlay disable com.android.theme.fodicon.g && cmd overlay disable com.android.theme.fodicon.op && cmd overlay disable com.android.theme.fodicon.ar && cmd overlay disable com.android.theme.fodicon.rb && cmd overlay disable com.android.theme.fodicon.cl && cmd overlay disable com.android.theme.fodicon.mb && cmd overlay disable com.android.theme.fodicon.op2 && cmd overlay disable com.android.theme.fodicon.miui3 && cmd overlay disable com.android.theme.fodicon.ro && cmd overlay enable com.android.theme.fodicon.g");
                                try {
                                    RootTools.getShell(true).add(c2);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 3:
                                Command c3 = new Command(3, "cmd overlay disable com.android.theme.fodicon.miui && cmd overlay disable com.android.theme.fodicon.g && cmd overlay disable com.android.theme.fodicon.op && cmd overlay disable com.android.theme.fodicon.ar && cmd overlay disable com.android.theme.fodicon.rb && cmd overlay disable com.android.theme.fodicon.cl && cmd overlay disable com.android.theme.fodicon.mb && cmd overlay disable com.android.theme.fodicon.op2 && cmd overlay disable com.android.theme.fodicon.miui3 && cmd overlay disable com.android.theme.fodicon.ro && cmd overlay enable com.android.theme.fodicon.op");
                                try {
                                    RootTools.getShell(true).add(c3);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 4:
                                Command c4 = new Command(4, "cmd overlay disable com.android.theme.fodicon.miui && cmd overlay disable com.android.theme.fodicon.g && cmd overlay disable com.android.theme.fodicon.op && cmd overlay disable com.android.theme.fodicon.ar && cmd overlay disable com.android.theme.fodicon.rb && cmd overlay disable com.android.theme.fodicon.cl && cmd overlay disable com.android.theme.fodicon.mb && cmd overlay disable com.android.theme.fodicon.op2 && cmd overlay disable com.android.theme.fodicon.miui3 && cmd overlay disable com.android.theme.fodicon.ro && cmd overlay enable com.android.theme.fodicon.ar");
                                try {
                                    RootTools.getShell(true).add(c4);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 5:
                                Command c5 = new Command(5, "cmd overlay disable com.android.theme.fodicon.miui && cmd overlay disable com.android.theme.fodicon.g && cmd overlay disable com.android.theme.fodicon.op && cmd overlay disable com.android.theme.fodicon.ar && cmd overlay disable com.android.theme.fodicon.rb && cmd overlay disable com.android.theme.fodicon.cl && cmd overlay disable com.android.theme.fodicon.mb && cmd overlay disable com.android.theme.fodicon.op2 && cmd overlay disable com.android.theme.fodicon.miui3 && cmd overlay disable com.android.theme.fodicon.ro && cmd overlay enable com.android.theme.fodicon.rb");
                                try {
                                    RootTools.getShell(true).add(c5);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 6:
                                Command c6 = new Command(6, "cmd overlay disable com.android.theme.fodicon.miui && cmd overlay disable com.android.theme.fodicon.g && cmd overlay disable com.android.theme.fodicon.op && cmd overlay disable com.android.theme.fodicon.ar && cmd overlay disable com.android.theme.fodicon.rb && cmd overlay disable com.android.theme.fodicon.cl && cmd overlay disable com.android.theme.fodicon.mb && cmd overlay disable com.android.theme.fodicon.op2 && cmd overlay disable com.android.theme.fodicon.miui3 && cmd overlay disable com.android.theme.fodicon.ro && cmd overlay enable com.android.theme.fodicon.cl");
                                try {
                                    RootTools.getShell(true).add(c6);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 7:
                                Command c7 = new Command(7, "cmd overlay disable com.android.theme.fodicon.miui && cmd overlay disable com.android.theme.fodicon.g && cmd overlay disable com.android.theme.fodicon.op && cmd overlay disable com.android.theme.fodicon.ar && cmd overlay disable com.android.theme.fodicon.rb && cmd overlay disable com.android.theme.fodicon.cl && cmd overlay disable com.android.theme.fodicon.mb && cmd overlay disable com.android.theme.fodicon.op2 && cmd overlay disable com.android.theme.fodicon.miui3 && cmd overlay disable com.android.theme.fodicon.ro && cmd overlay enable com.android.theme.fodicon.mb");
                                try {
                                    RootTools.getShell(true).add(c7);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 8:
                                Command c8 = new Command(8, "cmd overlay disable com.android.theme.fodicon.miui && cmd overlay disable com.android.theme.fodicon.g && cmd overlay disable com.android.theme.fodicon.op && cmd overlay disable com.android.theme.fodicon.ar && cmd overlay disable com.android.theme.fodicon.rb && cmd overlay disable com.android.theme.fodicon.cl && cmd overlay disable com.android.theme.fodicon.mb && cmd overlay disable com.android.theme.fodicon.op2 && cmd overlay disable com.android.theme.fodicon.miui3 && cmd overlay disable com.android.theme.fodicon.ro && cmd overlay enable com.android.theme.fodicon.op2");
                                try {
                                    RootTools.getShell(true).add(c8);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 9:
                                Command c9 = new Command(9, "cmd overlay disable com.android.theme.fodicon.miui && cmd overlay disable com.android.theme.fodicon.g && cmd overlay disable com.android.theme.fodicon.op && cmd overlay disable com.android.theme.fodicon.ar && cmd overlay disable com.android.theme.fodicon.rb && cmd overlay disable com.android.theme.fodicon.cl && cmd overlay disable com.android.theme.fodicon.mb && cmd overlay disable com.android.theme.fodicon.op2 && cmd overlay disable com.android.theme.fodicon.miui3 && cmd overlay disable com.android.theme.fodicon.ro && cmd overlay enable com.android.theme.fodicon.miui3");
                                try {
                                    RootTools.getShell(true).add(c9);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
							case 10:
                                Command c10 = new Command(10, "cmd overlay disable com.android.theme.fodicon.miui && cmd overlay disable com.android.theme.fodicon.g && cmd overlay disable com.android.theme.fodicon.op && cmd overlay disable com.android.theme.fodicon.ar && cmd overlay disable com.android.theme.fodicon.rb && cmd overlay disable com.android.theme.fodicon.cl && cmd overlay disable com.android.theme.fodicon.mb && cmd overlay disable com.android.theme.fodicon.op2 && cmd overlay disable com.android.theme.fodicon.miui3 && cmd overlay disable com.android.theme.fodicon.ro && cmd overlay enable com.android.theme.fodicon.ro");
                                try {
                                    RootTools.getShell(true).add(c10);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            default:
                                Command cd = new Command(11, "cmd overlay disable com.android.theme.fodicon.miui && cmd overlay disable com.android.theme.fodicon.g && cmd overlay disable com.android.theme.fodicon.op && cmd overlay disable com.android.theme.fodicon.ar && cmd overlay disable com.android.theme.fodicon.rb && cmd overlay disable com.android.theme.fodicon.cl && cmd overlay disable com.android.theme.fodicon.mb && cmd overlay disable com.android.theme.fodicon.op2 && cmd overlay disable com.android.theme.fodicon.miui3 && cmd overlay disable com.android.theme.fodicon.ro");
                                try {
                                    RootTools.getShell(true).add(cd);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                    //FOD Icon picker options end
                    //FOD Icon pressed options begin
                    if (key.equals("fod_icon_pressed")){
                        AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
                        mSysUIWarnBuilder.setTitle(R.string.attention);
                        mSysUIWarnBuilder.setMessage(R.string.restartui_required);
                        mSysUIWarnBuilder.setPositiveButton(android.R.string.ok,null);
                        AlertDialog mSysUIWarn = mSysUIWarnBuilder.create();
                        mSysUIWarn.show();
                        TypedValue typedValue = new TypedValue();
                        Resources.Theme theme = c.getTheme();
                        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
                        Button ok = mSysUIWarn.getButton(AlertDialog.BUTTON_POSITIVE);
                        ok.setTextColor(typedValue.data);
                        mSysUIWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                        switch(mValueIndex) {
                            case 0:
                                Command c0 = new Command(0, "cmd overlay disable com.android.theme.fodiconp.g && cmd overlay disable com.android.theme.fodiconp.w && cmd overlay disable com.android.theme.fodiconp.y");
                                try {
                                    RootTools.getShell(true).add(c0);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 1:
                                Command c1 = new Command(1, "cmd overlay disable com.android.theme.fodiconp.g && cmd overlay disable com.android.theme.fodiconp.w && cmd overlay disable com.android.theme.fodiconp.y && cmd overlay enable com.android.theme.fodiconp.g");
                                try {
                                    RootTools.getShell(true).add(c1);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 2:
                                Command c2 = new Command(2, "cmd overlay disable com.android.theme.fodiconp.g && cmd overlay disable com.android.theme.fodiconp.w && cmd overlay disable com.android.theme.fodiconp.y && cmd overlay enable com.android.theme.fodiconp.y");
                                try {
                                    RootTools.getShell(true).add(c2);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 3:
                                Command c3 = new Command(3, "cmd overlay disable com.android.theme.fodiconp.g && cmd overlay disable com.android.theme.fodiconp.w && cmd overlay disable com.android.theme.fodiconp.y && cmd overlay enable com.android.theme.fodiconp.w");
                                try {
                                    RootTools.getShell(true).add(c3);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            default:
                                Command cd = new Command(4, "cmd overlay disable com.android.theme.fodiconp.g && cmd overlay disable com.android.theme.fodiconp.w && cmd overlay disable com.android.theme.fodiconp.y");
                                try {
                                    RootTools.getShell(true).add(cd);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                    //FOD Icon pressed options end
                    //Rounded corners options begin
                    if (key.equals("rounded_corners")){
                        switch(mValueIndex) {
                            case 0:
                                Command c0 = new Command(0, "cmd overlay disable com.android.theme.uirs && cmd overlay disable com.android.theme.uirsExt && cmd overlay disable com.android.theme.uirb && cmd overlay disable com.android.theme.uirbExt");
                                try {
                                    RootTools.getShell(true).add(c0);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 1:
                                Command c1 = new Command(1, "cmd overlay disable com.android.theme.uirs && cmd overlay disable com.android.theme.uirsExt && cmd overlay disable com.android.theme.uirb && cmd overlay disable com.android.theme.uirbExt && cmd overlay enable com.android.theme.uirs && cmd overlay enable com.android.theme.uirsExt");
                                try {
                                    RootTools.getShell(true).add(c1);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 2:
                                Command c2 = new Command(2, "cmd overlay disable com.android.theme.uirs && cmd overlay disable com.android.theme.uirsExt && cmd overlay disable com.android.theme.uirb && cmd overlay disable com.android.theme.uirbExt && cmd overlay enable com.android.theme.uirb && cmd overlay enable com.android.theme.uirbExt");
                                try {
                                    RootTools.getShell(true).add(c2);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            default:
                                Command cd = new Command(3, "cmd overlay disable com.android.theme.uirs && cmd overlay disable com.android.theme.uirsExt && cmd overlay disable com.android.theme.uirb && cmd overlay disable com.android.theme.uirbExt");
                                try {
                                    RootTools.getShell(true).add(cd);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                    //Rounded corners options end
                    //QS Header styles options begin
                    if (key.equals("qs_header_style")){
                        switch(mValueIndex) {
                            case 0:
                                Command c0 = new Command(0, "cmd overlay disable com.android.systemui.qsheader.grey && cmd overlay disable com.android.systemui.qsheader.lightgrey && cmd overlay disable com.android.systemui.qsheader.accent && cmd overlay disable com.android.systemui.qsheader.followdark");
                                try {
                                    RootTools.getShell(true).add(c0);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 1:
                                Command c1 = new Command(1, "cmd overlay disable com.android.systemui.qsheader.grey && cmd overlay disable com.android.systemui.qsheader.lightgrey && cmd overlay disable com.android.systemui.qsheader.accent && cmd overlay disable com.android.systemui.qsheader.followdark && cmd overlay enable com.android.systemui.qsheader.grey");
                                try {
                                    RootTools.getShell(true).add(c1);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 2:
                                Command c2 = new Command(2, "cmd overlay disable com.android.systemui.qsheader.grey && cmd overlay disable com.android.systemui.qsheader.lightgrey && cmd overlay disable com.android.systemui.qsheader.accent && cmd overlay disable com.android.systemui.qsheader.followdark && cmd overlay enable com.android.systemui.qsheader.lightgrey");
                                try {
                                    RootTools.getShell(true).add(c2);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 3:
                                Command c3 = new Command(3, "cmd overlay disable com.android.systemui.qsheader.grey && cmd overlay disable com.android.systemui.qsheader.lightgrey && cmd overlay disable com.android.systemui.qsheader.accent && cmd overlay disable com.android.systemui.qsheader.followdark && cmd overlay enable com.android.systemui.qsheader.accent");
                                try {
                                    RootTools.getShell(true).add(c3);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 4:
                                Command c4 = new Command(4, "cmd overlay disable com.android.systemui.qsheader.grey && cmd overlay disable com.android.systemui.qsheader.lightgrey && cmd overlay disable com.android.systemui.qsheader.accent && cmd overlay disable com.android.systemui.qsheader.followdark && cmd overlay enable com.android.systemui.qsheader.followdark");
                                try {
                                    RootTools.getShell(true).add(c4);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                                /*Not needed anymore since now we control the opacity
                                case 5:
                                Command c5 = new Command(5, "cmd overlay disable com.android.systemui.qsheader.grey && cmd overlay disable com.android.systemui.qsheader.lightgrey && cmd overlay disable com.android.systemui.qsheader.accent && cmd overlay disable com.android.systemui.qsheader.transparent && cmd overlay disable com.android.systemui.qsheader.followdark && cmd overlay enable com.android.systemui.qsheader.transparent");
                                try {
                                    RootTools.getShell(true).add(c5);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;*/
                            default:
                                Command cd = new Command(6, "cmd overlay disable com.android.systemui.qsheader.grey && cmd overlay disable com.android.systemui.qsheader.lightgrey && cmd overlay disable com.android.systemui.qsheader.accent && cmd overlay disable com.android.systemui.qsheader.followdark");
                                try {
                                    RootTools.getShell(true).add(cd);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                    //QS Header styles options end
                    //Dark Theme styles options begin
                    if (key.equals("dt_style")){
                        switch(mValueIndex) {
                            case 0:
                                Command c0 = new Command(0, "cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.bakedgreenExt && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.chocoxExt && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.darkaubergineExt && cmd overlay disable com.android.dark.style && cmd overlay disable com.android.dark.styleExt && cmd overlay disable com.android.dark.darkgray && cmd overlay disable com.android.dark.darkgrayExt && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.materialoceanExt && cmd overlay disable com.android.dark.night && cmd overlay disable com.android.dark.nightExt && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.solarizeddarkExt && cmd overlay disable com.android.dark.clearspring && cmd overlay disable com.android.dark.clearspringExt");
                                try {
                                    RootTools.getShell(true).add(c0);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 1:
                                Command c1 = new Command(1, "cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.chocoxExt && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.darkaubergineExt && cmd overlay disable com.android.dark.darkgray && cmd overlay disable com.android.dark.darkgrayExt && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.materialoceanExt && cmd overlay disable com.android.dark.night && cmd overlay disable com.android.dark.nightExt && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.solarizeddarkExt && cmd overlay disable com.android.dark.style && cmd overlay disable com.android.dark.styleExt && cmd overlay disable com.android.dark.clearspring && cmd overlay disable com.android.dark.clearspringExt && cmd overlay enable com.android.dark.bakedgreen && cmd overlay enable com.android.dark.bakedgreenExt");
                                try {
                                    RootTools.getShell(true).add(c1);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 2:
                                Command c2 = new Command(2, "cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.bakedgreenExt && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.darkaubergineExt && cmd overlay disable com.android.dark.darkgray && cmd overlay disable com.android.dark.darkgrayExt && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.materialoceanExt && cmd overlay disable com.android.dark.night && cmd overlay disable com.android.dark.nightExt && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.solarizeddarkExt && cmd overlay disable com.android.dark.style && cmd overlay disable com.android.dark.styleExt && cmd overlay disable com.android.dark.clearspring && cmd overlay disable com.android.dark.clearspringExt && cmd overlay enable com.android.dark.chocox && cmd overlay enable com.android.dark.chocoxExt");
                                try {
                                    RootTools.getShell(true).add(c2);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 3:
                                Command c3 = new Command(3, "cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.bakedgreenExt && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.chocoxExt && cmd overlay disable com.android.dark.darkgray && cmd overlay disable com.android.dark.darkgrayExt && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.materialoceanExt && cmd overlay disable com.android.dark.night && cmd overlay disable com.android.dark.nightExt && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.solarizeddarkExt && cmd overlay disable com.android.dark.style && cmd overlay disable com.android.dark.styleExt && cmd overlay disable com.android.dark.clearspring && cmd overlay disable com.android.dark.clearspringExt && cmd overlay enable com.android.dark.darkaubergine && cmd overlay enable com.android.dark.darkaubergineExt");
                                try {
                                    RootTools.getShell(true).add(c3);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 4:
                                Command c4 = new Command(4, "cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.bakedgreenExt && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.chocoxExt && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.darkaubergineExt && cmd overlay disable com.android.dark.darkgray && cmd overlay disable com.android.dark.darkgrayExt && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.materialoceanExt && cmd overlay disable com.android.dark.night && cmd overlay disable com.android.dark.nightExt && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.solarizeddarkExt && cmd overlay disable com.android.dark.clearspring && cmd overlay disable com.android.dark.clearspringExt && cmd overlay enable com.android.dark.style && cmd overlay enable com.android.dark.styleExt");
                                try {
                                    RootTools.getShell(true).add(c4);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 5:
                                Command c5 = new Command(5, "cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.bakedgreenExt && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.chocoxExt && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.darkaubergineExt && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.materialoceanExt && cmd overlay disable com.android.dark.night && cmd overlay disable com.android.dark.nightExt && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.solarizeddarkExt && cmd overlay disable com.android.dark.style && cmd overlay disable com.android.dark.styleExt && cmd overlay disable com.android.dark.clearspring && cmd overlay disable com.android.dark.clearspringExt && cmd overlay enable com.android.dark.darkgray && cmd overlay enable com.android.dark.darkgrayExt");
                                try {
                                    RootTools.getShell(true).add(c5);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 6:
                                Command c6 = new Command(6, "cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.bakedgreenExt && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.chocoxExt && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.darkaubergineExt && cmd overlay disable com.android.dark.darkgray && cmd overlay disable com.android.dark.darkgrayExt && cmd overlay disable com.android.dark.night && cmd overlay disable com.android.dark.nightExt && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.solarizeddarkExt && cmd overlay disable com.android.dark.style && cmd overlay disable com.android.dark.styleExt && cmd overlay disable com.android.dark.clearspring && cmd overlay disable com.android.dark.clearspringExt && cmd overlay enable com.android.dark.materialocean && cmd overlay enable com.android.dark.materialoceanExt");
                                try {
                                    RootTools.getShell(true).add(c6);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 7:
                                Command c7 = new Command(7, "cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.bakedgreenExt && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.chocoxExt && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.darkaubergineExt && cmd overlay disable com.android.dark.darkgray && cmd overlay disable com.android.dark.darkgrayExt && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.materialoceanExt && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.solarizeddarkExt && cmd overlay disable com.android.dark.style && cmd overlay disable com.android.dark.styleExt && cmd overlay disable com.android.dark.clearspring && cmd overlay disable com.android.dark.clearspringExt && cmd overlay enable com.android.dark.night && cmd overlay enable com.android.dark.nightExt");
                                try {
                                    RootTools.getShell(true).add(c7);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 8:
                                Command c8 = new Command(8, "cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.bakedgreenExt && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.chocoxExt && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.darkaubergineExt && cmd overlay disable com.android.dark.darkgray && cmd overlay disable com.android.dark.darkgrayExt && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.materialoceanExt && cmd overlay disable com.android.dark.night && cmd overlay disable com.android.dark.nightExt && cmd overlay disable com.android.dark.style && cmd overlay disable com.android.dark.styleExt && cmd overlay disable com.android.dark.clearspring && cmd overlay disable com.android.dark.clearspringExt && cmd overlay enable com.android.dark.solarizeddark && cmd overlay enable com.android.dark.solarizeddarkExt");
                                try {
                                    RootTools.getShell(true).add(c8);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 9:
                                Command c9 = new Command(9, "cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.bakedgreenExt && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.chocoxExt && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.darkaubergineExt && cmd overlay disable com.android.dark.style && cmd overlay disable com.android.dark.styleExt && cmd overlay disable com.android.dark.darkgray && cmd overlay disable com.android.dark.darkgrayExt && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.materialoceanExt && cmd overlay disable com.android.dark.night && cmd overlay disable com.android.dark.nightExt && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.solarizeddarkExt && cmd overlay enable com.android.dark.clearspring && cmd overlay enable com.android.dark.clearspringExt");
                                try {
                                    RootTools.getShell(true).add(c9);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            default:
                                Command cd = new Command(10, "cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.bakedgreenExt && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.chocoxExt && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.darkaubergineExt && cmd overlay disable com.android.dark.style && cmd overlay disable com.android.dark.styleExt && cmd overlay disable com.android.dark.darkgray && cmd overlay disable com.android.dark.darkgrayExt && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.materialoceanExt && cmd overlay disable com.android.dark.night && cmd overlay disable com.android.dark.nightExt && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.solarizeddarkExt && cmd overlay disable com.android.dark.clearspring && cmd overlay disable com.android.dark.clearspringExt");
                                try {
                                    RootTools.getShell(true).add(cd);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                    //Dark Theme styles options end
                    //System fonts options begin
                    if (key.equals("font_style")){
                        switch(mValueIndex) {
                            case 0:
                                Command c0 = new Command(0, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor");
                                try {
                                    RootTools.getShell(true).add(c0);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 1:
                                Command c1 = new Command(1, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.aclonicasource");
                                try {
                                    RootTools.getShell(true).add(c1);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 2:
                                Command c2 = new Command(2, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.aileron");
                                try {
                                    RootTools.getShell(true).add(c2);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 3:
                                Command c3 = new Command(3, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.amarantesource");
                                try {
                                    RootTools.getShell(true).add(c3);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 4:
                                Command c4 = new Command(4, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.anaheim");
                                try {
                                    RootTools.getShell(true).add(c4);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 5:
                                Command c5 = new Command(5, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.arbutussource");
                                try {
                                    RootTools.getShell(true).add(c5);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 6:
                                Command c6 = new Command(6, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.arvolato");
                                try {
                                    RootTools.getShell(true).add(c6);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 7:
                                Command c7 = new Command(7, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.bariolsource");
                                try {
                                    RootTools.getShell(true).add(c7);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 8:
                                Command c8 = new Command(8, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.cagliostrosource");
                                try {
                                    RootTools.getShell(true).add(c8);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 9:
                                Command c9 = new Command(9, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.circularstd");
                                try {
                                    RootTools.getShell(true).add(c9);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 10:
                                Command c10 = new Command(10, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.comicsans");
                                try {
                                    RootTools.getShell(true).add(c10);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 11:
                                Command c11 = new Command(11, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.coolstorysource");
                                try {
                                    RootTools.getShell(true).add(c11);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 12:
                                Command c12 = new Command(12, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.googlesans");
                                try {
                                    RootTools.getShell(true).add(c12);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 13:
                                Command c13 = new Command(13, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.inter");
                                try {
                                    RootTools.getShell(true).add(c13);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 14:
                                Command c14 = new Command(14, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.kai");
                                try {
                                    RootTools.getShell(true).add(c14);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 15:
                                Command c15 = new Command(15, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.lgsmartgothicsource");
                                try {
                                    RootTools.getShell(true).add(c15);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 16:
                                Command c16 = new Command(16, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.linotte");
                                try {
                                    RootTools.getShell(true).add(c16);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 17:
                                Command c17 = new Command(17, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.notoserifsource");
                                try {
                                    RootTools.getShell(true).add(c17);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 18:
                                Command c18 = new Command(18, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.rosemarysource");
                                try {
                                    RootTools.getShell(true).add(c18);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 19:
                                Command c19 = new Command(19, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.rubikrubik");
                                try {
                                    RootTools.getShell(true).add(c19);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 20:
                                Command c20 = new Command(20, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.sam");
                                try {
                                    RootTools.getShell(true).add(c20);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 21:
                                Command c21 = new Command(21, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.samsungone");
                                try {
                                    RootTools.getShell(true).add(c21);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 22:
                                Command c22 = new Command(22, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.slateforoneplus");
                                try {
                                    RootTools.getShell(true).add(c22);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 23:
                                Command c23 = new Command(23, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.sonysketchsource");
                                try {
                                    RootTools.getShell(true).add(c23);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 24:
                                Command c24 = new Command(24, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.surfersource");
                                try {
                                    RootTools.getShell(true).add(c24);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 25:
                                Command c25 = new Command(25, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.tinkerbell");
                                try {
                                    RootTools.getShell(true).add(c25);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 26:
                                Command c26 = new Command(26, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.urbanist");
                                try {
                                    RootTools.getShell(true).add(c26);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            case 27:
                                Command c27 = new Command(27, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor && cmd overlay enable com.android.theme.font.victor");
                                try {
                                    RootTools.getShell(true).add(c27);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                            default:
                                Command cd = new Command(28, "cmd overlay disable com.android.theme.font.aclonicasource && cmd overlay disable com.android.theme.font.aileron && cmd overlay disable com.android.theme.font.amarantesource && cmd overlay disable com.android.theme.font.anaheim && cmd overlay disable com.android.theme.font.arbutussource && cmd overlay disable com.android.theme.font.arvolato && cmd overlay disable com.android.theme.font.bariolsource && cmd overlay disable com.android.theme.font.cagliostrosource && cmd overlay disable com.android.theme.font.circularstd && cmd overlay disable com.android.theme.font.comicsans && cmd overlay disable com.android.theme.font.coolstorysource && cmd overlay disable com.android.theme.font.googlesans && cmd overlay disable com.android.theme.font.inter && cmd overlay disable com.android.theme.font.kai && cmd overlay disable com.android.theme.font.lgsmartgothicsource && cmd overlay disable com.android.theme.font.linotte && cmd overlay disable com.android.theme.font.notoserifsource && cmd overlay disable com.android.theme.font.rosemarysource && cmd overlay disable com.android.theme.font.rubikrubik && cmd overlay disable com.android.theme.font.sam && cmd overlay disable com.android.theme.font.samsungone && cmd overlay disable com.android.theme.font.slateforoneplus && cmd overlay disable com.android.theme.font.sonysketchsource && cmd overlay disable com.android.theme.font.surfersource && cmd overlay disable com.android.theme.font.tinkerbell && cmd overlay disable com.android.theme.font.urbanist && cmd overlay disable com.android.theme.font.victor");
                                try {
                                    RootTools.getShell(true).add(cd);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                } catch (TimeoutException e) {
                                    e.printStackTrace();
                                } catch (RootDeniedException e) {
                                    e.printStackTrace();
                                }
                                break;
                        }
                    }
                    //System fonts options end
                } else {
                    l.setSummary("");
                }
                break;
            case "MyEditTextPreference":
                MyEditTextPreference et = (MyEditTextPreference) pf.findPreference(key);
                String etValue = sharedPreferences.getString(key, "");
                if (etValue != null) {
                    et.setSummary(sharedPreferences.getString(key, ""));
                }
                break;
            case "ColorPickerPreference":
                ColorPickerPreference cpp = (ColorPickerPreference) pf.findPreference(key);
                cpp.setColor(sharedPreferences.getInt(key, Color.WHITE));
                break;
        }
        /*Calling main method to handle updating database based on preference changes*/
        if (p instanceof FilePreference) {
        } else {
            updateDatabase(key, p, sharedPreferences);
        }
    }

    private void updateDatabase(String key, Object o, SharedPreferences sp) {
        boolean isEnabled;
        int dbInt;
        String value = "";

        if (o instanceof SwitchPreference || o instanceof CheckBoxPreference) {
            isEnabled = sp.getBoolean(key, true);
            dbInt = (isEnabled) ? 1 : 0;
            Settings.System.putInt(cr, key, dbInt);
        } else if (o instanceof MyEditTextPreference || o instanceof MyListPreference || o instanceof IntentDialogPreference) {
            value = sp.getString(key, "");
            Settings.System.putString(cr, key, value);
        } else if (o instanceof ColorPickerPreference) {
            dbInt = sp.getInt(key, Color.WHITE);
            Settings.System.putInt(cr, key, dbInt);
        } else if (o instanceof SeekBarPreference) {
            dbInt = sp.getInt(key, 0);
            Settings.System.putInt(cr, key, dbInt);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        if (preference.getKey() != null && preference.getKey().contains("script#")) {
            /*We use a special char sequence (script#) to specify preference items that need to run shell script
            * Upon click, the key is broken down to the specifier and what comes after the hash - which is script name
            * Scripts are being copied from assets to the file dir of our app in onCreate of main activity
            * If the script is found on it's intended path, it's checked for being executable.
            * Although we chmod 755 all the files upon copying them in main activity,
            * We need to make sure, so we check and set it executable if it's not
            * Permission 700 (set by this method (setExecutable(true)) is sufficient for executing scripts)*/
            String scriptName = preference.getKey().substring(preference.getKey().lastIndexOf("#") + 1) + ".sh";
            String pathToScript = c.getFilesDir() + File.separator + "scripts" + File.separator + scriptName;
            File script = new File(pathToScript);
            if (script.exists()) {
                boolean isChmoded = script.canExecute() ? true : false;
                if (!isChmoded) {
                    script.setExecutable(true);
                }
                Command command = new Command(0, pathToScript) {
                    @Override
                    public void commandCompleted(int id, int exitcode) {
                        super.commandCompleted(id, exitcode);
                        if (exitcode != 0) {
                            Toast.makeText(c, String.valueOf(exitcode), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(c, "Executed Successfully", Toast.LENGTH_SHORT).show();

                        }
                    }
                };
                try {
                    RootTools.getShell(true).add(command);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (TimeoutException e) {
                    e.printStackTrace();
                } catch (RootDeniedException e) {
                    e.printStackTrace();
                }
            }
        /*If preference key contains a dot ".", we assume the dev meant to create an intent to another app
        * As per instructions, devs are required to enter full path to the main activity they wish to open in the intended app.
        * In the following condition the key is broken down to package name and class name (full key)
        * and we attempt to build intent.
        * We know from the allGroups() method that if the intent is not valid, the preference will not show at all.
        * Nevertheless. as precaution we catch an exception and show a toast that the app is not installed.*/
        } else if (preference.getKey() != null && preference.getKey().contains(".")) {
            String cls = preference.getKey();
            String pkg = cls.substring(0, cls.lastIndexOf("."));
            Intent intent = new Intent(Intent.ACTION_MAIN).setClassName(pkg,
                    cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .setComponent(new ComponentName(pkg,
                            cls));
            try {
                c.startActivity(intent);
            } catch (ActivityNotFoundException anf) {
                Toast.makeText(c, "App not installed or intent not valid", Toast.LENGTH_SHORT).show();
            }

        } else if (preference.getKey() == null) {
//            setToolbarForNested(preference);
        }
        return true;
    }

    //    private void setToolbarForNested(Preference p) {
//        PreferenceScreen ps = (PreferenceScreen) p;
//        Dialog d = ps.getDialog();
//        android.support.v7.widget.Toolbar tb;
//        LinearLayout ll = (LinearLayout) d.findViewById(android.R.id.list).getParent();
//        tb = (android.support.v7.widget.Toolbar) LayoutInflater.from(c).inflate(R.layout.toolbar_default, ll, false);
//        ll.addView(tb, 0);
//
//    }

    /**
     * This method can be useful for devs that wish to inform their users
     * that reboot of an app is required fpr the changes to take effect.
     * Some apps reboot quietly, but f.e SystemUI will reboot visibly.
     * To inform user, you can call this method, and a popup, informing of what reboot is required, will be shown.
     * Upon clicking ok button, the specified app will be killed.*/
    public void appRebootRequired(final String pckgName) {
        PackageManager pm = c.getPackageManager();
        String appName = "";
        Drawable appIcon = null;
        try {
            appName = pm.getApplicationInfo(pckgName, 0).loadLabel(pm).toString();
            appIcon = pm.getApplicationInfo(pckgName, 0).loadIcon(pm);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        AlertDialog.Builder b = new AlertDialog.Builder(c);
        b
                .setTitle(c.getString(R.string.app_reboot_required_title).toUpperCase())
                .setIcon(-1).setIcon(appIcon)
                .setMessage(String.format(c.getString(R.string.app_reboot_required_message), appName))
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                })
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Command c = new Command(0, "pkill " + pckgName);
                        try {
                            RootTools.getShell(true).add(c);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (TimeoutException e) {
                            e.printStackTrace();
                        } catch (RootDeniedException e) {
                            e.printStackTrace();
                        }
                    }
                });
        AlertDialog d = b.create();
        d.show();
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = c.getTheme();
        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
        Button cancel = d.getButton(AlertDialog.BUTTON_NEGATIVE);
        Button ok = d.getButton(AlertDialog.BUTTON_POSITIVE);
        cancel.setTextColor(typedValue.data);
        ok.setTextColor(typedValue.data);
        d.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);

    }

}