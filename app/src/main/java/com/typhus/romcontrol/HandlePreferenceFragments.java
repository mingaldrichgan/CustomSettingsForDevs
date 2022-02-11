package com.typhus.romcontrol;

import android.app.Activity;
import android.app.ActivityOptions;
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

    //Gets string for running shell commands, using stericson RootTools lib
    private void runCommandAction(String command) {
        Command c = new Command(0, command);
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
    //Show install Edge Sense Plus dialogue
    private void installEdgeSensePlusWarning() {
        AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
        mSysUIWarnBuilder.setTitle(R.string.attention);
        mSysUIWarnBuilder.setMessage(R.string.install_esp_dialogue);
        mSysUIWarnBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                runCommandAction("pm install /data/adb/modules/AddonFeaturesForPixel/data/appz/EdgeSensePlus/EdgeSensePlus.apk");
            }
        });
        mSysUIWarnBuilder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();

            }
        });
        AlertDialog mSysUIWarn = mSysUIWarnBuilder.create();
        mSysUIWarn.show();
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = c.getTheme();
        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
        Button ok = mSysUIWarn.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setTextColor(typedValue.data);
        mSysUIWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
    }

    //Show uninstall Edge Sense Plus dialogue
    private void uninstallEdgeSensePlusWarning() {
        AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
        mSysUIWarnBuilder.setTitle(R.string.attention);
        mSysUIWarnBuilder.setMessage(R.string.uninstall_esp_dialogue);
        mSysUIWarnBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                runCommandAction("pm uninstall eu.duong.edgesenseplus");
            }
        });
        mSysUIWarnBuilder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();

            }
        });
        AlertDialog mSysUIWarn = mSysUIWarnBuilder.create();
        mSysUIWarn.show();
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = c.getTheme();
        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
        Button ok = mSysUIWarn.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setTextColor(typedValue.data);
        mSysUIWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
    }

    //Show install Tap Tap dialogue
    private void installTapTapWarning() {
        AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
        mSysUIWarnBuilder.setTitle(R.string.attention);
        mSysUIWarnBuilder.setMessage(R.string.install_tap_tap_dialogue);
        mSysUIWarnBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                runCommandAction("pm install /data/adb/modules/AddonFeaturesForPixel/data/appz/TapTap/TapTap-1.0.1.apk");
            }
        });
        mSysUIWarnBuilder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();

            }
        });
        AlertDialog mSysUIWarn = mSysUIWarnBuilder.create();
        mSysUIWarn.show();
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = c.getTheme();
        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
        Button ok = mSysUIWarn.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setTextColor(typedValue.data);
        mSysUIWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
    }

    //Show uninstall Tap Tap dialogue
    private void uninstallTapTapWarning() {
        AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
        mSysUIWarnBuilder.setTitle(R.string.attention);
        mSysUIWarnBuilder.setMessage(R.string.uninstall_tap_tap_dialogue);
        mSysUIWarnBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                runCommandAction("pm uninstall com.kieronquinn.app.taptap");
            }
        });
        mSysUIWarnBuilder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();

            }
        });
        AlertDialog mSysUIWarn = mSysUIWarnBuilder.create();
        mSysUIWarn.show();
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = c.getTheme();
        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
        Button ok = mSysUIWarn.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setTextColor(typedValue.data);
        mSysUIWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
    }

    //Show restart required dialogue
    private void restartRequiredWarning() {
        AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
        mSysUIWarnBuilder.setTitle(R.string.attention);
        mSysUIWarnBuilder.setMessage(R.string.restart_required);
        mSysUIWarnBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                runCommandAction("am start -a android.intent.action.REBOOT");
            }
        });
        mSysUIWarnBuilder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();

            }
        });
        AlertDialog mSysUIWarn = mSysUIWarnBuilder.create();
        mSysUIWarn.show();
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = c.getTheme();
        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
        Button ok = mSysUIWarn.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setTextColor(typedValue.data);
        mSysUIWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
    }

    //Show restart SystemUI required dialogue
    private void restartUiRequiredWarning() {
        AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
        mSysUIWarnBuilder.setTitle(R.string.attention);
        mSysUIWarnBuilder.setMessage(R.string.restartui_required);
        mSysUIWarnBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                runCommandAction("killall com.android.systemui");
            }
        });
        mSysUIWarnBuilder.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();

            }
        });
        AlertDialog mSysUIWarn = mSysUIWarnBuilder.create();
        mSysUIWarn.show();
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = c.getTheme();
        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
        Button ok = mSysUIWarn.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setTextColor(typedValue.data);
        mSysUIWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
    }

    //Show disable color pill dialogue
    private void disableColorPillWarning() {
        AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
        mSysUIWarnBuilder.setTitle(R.string.attention);
        mSysUIWarnBuilder.setMessage(R.string.disable_color_pill_required);
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

    //Show disable hide pill dialogue
    private void disableHidePillWarning() {
        AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
        mSysUIWarnBuilder.setTitle(R.string.attention);
        mSysUIWarnBuilder.setMessage(R.string.disable_hide_pill_required);
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

    //Show 2 or 3 button navigation mode warning
    private void navbarButtonsWarning(){
        AlertDialog.Builder mNoGesturesBuilder = new AlertDialog.Builder(c);
        mNoGesturesBuilder.setTitle(R.string.gesturesareenabled);
        mNoGesturesBuilder.setMessage(R.string.disablegesturessummary);
        mNoGesturesBuilder.setPositiveButton(android.R.string.ok, null);
        mNoGesturesBuilder.create();
        AlertDialog mNoGestures = mNoGesturesBuilder.create();
        mNoGestures.show();
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = c.getTheme();
        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
        Button ok = mNoGestures.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setTextColor(typedValue.data);
        mNoGestures.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
    }

    //Show gestures navigation warning
    private void navbarGesturesWarning(){
        AlertDialog.Builder mNoGesturesBuilder = new AlertDialog.Builder(c);
        mNoGesturesBuilder.setTitle(R.string.requiresgestures);
        mNoGesturesBuilder.setMessage(R.string.requiresgesturessummary);
        mNoGesturesBuilder.setPositiveButton(android.R.string.ok, null);
        mNoGesturesBuilder.create();
        AlertDialog mNoGestures = mNoGesturesBuilder.create();
        mNoGestures.show();
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = c.getTheme();
        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
        Button ok = mNoGestures.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setTextColor(typedValue.data);
        mNoGestures.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
    }

    //Show completely remove navigation bar warning
    private void navbarRemoveWarning(){
        AlertDialog.Builder mNavBarWarnBuilder = new AlertDialog.Builder(c);
        mNavBarWarnBuilder.setTitle(R.string.attention);
        mNavBarWarnBuilder.setMessage(R.string.remove_navbar_warning);
        mNavBarWarnBuilder.setPositiveButton(android.R.string.ok, null);
        mNavBarWarnBuilder.create();
        AlertDialog mNavBarWarn = mNavBarWarnBuilder.create();
        mNavBarWarn.show();
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = c.getTheme();
        theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
        Button ok = mNavBarWarn.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setTextColor(typedValue.data);
        mNavBarWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
    }

    //Show themed icons warning
    private void themedIconsWarning(){
        AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
        mSysUIWarnBuilder.setTitle(R.string.attention);
        mSysUIWarnBuilder.setMessage(R.string.custom_themed_icons_warning);
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

    //Show QS Gradient dialogue
    private void qsGradientWarning() {
        AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
        mSysUIWarnBuilder.setTitle(R.string.attention);
        mSysUIWarnBuilder.setMessage(R.string.qs_gradient_warning);
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

    public void allGroups(Preference p) {
        PreferenceScreen ps = (PreferenceScreen) p;
        ps.setOnPreferenceClickListener(this);

        /*Initiate icon view for preferences with keys that are interpreted as Intent
          For more info see OnPreferenceClick method*/
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
                //Brightness slider thick track options
                if (key.equals("qs_brightness_thick_track")){
                    if (s.isChecked()) {
                        runCommandAction("cmd overlay enable com.android.systemui.bstrack.overlay");
                    } else {
                        runCommandAction("cmd overlay disable com.android.systemui.bstrack.overlay");
                    }
                }
                //Brightness slider padding options
                if (key.equals("qs_brightness_more_padding")){
                    if (s.isChecked()) {
                        runCommandAction("cmd overlay enable com.android.systemui.bspadding.overlay");
                    } else {
                        runCommandAction("cmd overlay disable com.android.systemui.bspadding.overlay");
                    }
                }
                //Lock Screen Fingerprint icon background options
                if (key.equals("disable_lockscreen_fp_icon_bg")){
                    if (s.isChecked()) {
                        runCommandAction("cmd overlay enable com.lsfpicon.nobkg");
                    } else {
                        runCommandAction("cmd overlay disable com.lsfpicon.nobkg");
                    }
                }
                //Lock Screen Double Line Clock options
                if (key.equals("lockscreen_use_double_line_clock")){
                    if (s.isChecked()) {
                        runCommandAction("settings put system lockscreen_small_clock 0");
                    } else {
                        runCommandAction("settings put system lockscreen_small_clock 1");
                    }
                }
                /*Hide Gestures Pill*/
                if (key.equals("gestures_hide_pill")){
                    int colorpillon = Settings.System.getInt(c.getContentResolver(), "gestures_color_pill", 0);
                    if (colorpillon == 1) {
                        s.setEnabled(false);
                        s.setChecked(false);
                        disableColorPillWarning();
                    }
                    else if (colorpillon == 0) {
                        s.setEnabled(true);
                        if (s.isChecked()) {
                            runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.overlay.hidepill");
                            restartUiRequiredWarning();
                        } else {
                            runCommandAction("cmd overlay disable com.android.systemui.overlay.hidepill");
                            restartUiRequiredWarning();
                        }
                    }
                }
                /*Accent color Gestures Pill*/
                if (key.equals("gestures_color_pill")){
                    int hidepillon = Settings.System.getInt(c.getContentResolver(), "gestures_hide_pill", 0);
                    if (hidepillon == 1) {
                        s.setEnabled(false);
                        s.setChecked(false);
                        disableHidePillWarning();
                    }
                    else if (hidepillon == 0) {
                        s.setEnabled(true);
                        if (s.isChecked()) {
                            runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.overlay.colorpill");
                            restartUiRequiredWarning();
                        } else {
                            runCommandAction("cmd overlay disable com.android.systemui.overlay.colorpill");
                            restartUiRequiredWarning();
                        }
                    }
                }
                /*Disable Light QS Header*/
                if (key.equals("disable_light_qs_header")){
                    int gradienton = Settings.System.getInt(c.getContentResolver(), "qs_gradient", 0);
                    if (s.isChecked() & gradienton == 0) {
                        runCommandAction("cmd overlay enable com.android.darkqs && cmd overlay enable com.android.systemui.darkqs");
                    } else if (s.isChecked() & gradienton == 1) {
                        runCommandAction("cmd overlay enable com.android.darkqs && cmd overlay enable com.android.systemui.darkqs && cmd overlay disable com.qsshapegradient.overlay && cmd overlay enable com.qsshapegradient.overlay.dark");
                    } else if (!s.isChecked() & gradienton == 1) {
                        runCommandAction("cmd overlay disable com.android.darkqs && cmd overlay disable com.android.systemui.darkqs && cmd overlay disable com.qsshapegradient.overlay.dark && cmd overlay enable com.qsshapegradient.overlay");
                    } else if (!s.isChecked() & gradienton == 0) {
                        runCommandAction("cmd overlay disable com.android.darkqs && cmd overlay disable com.android.systemui.darkqs");
                    }
                }
                /*QS Tiles Gradient options*/
                if (key.equals("qs_gradient")){
                    int darkqson = Settings.System.getInt(c.getContentResolver(), "disable_light_qs_header", 0);
                    if (s.isChecked() & darkqson == 0) {
                        runCommandAction("cmd overlay enable com.qsshapegradient.overlay");
                        qsGradientWarning();
                    } else if (s.isChecked() & darkqson == 1) {
                        runCommandAction("cmd overlay enable com.qsshapegradient.overlay.dark");
                        qsGradientWarning();
                    } else if (!s.isChecked() & darkqson == 1) {
                        runCommandAction("cmd overlay disable com.qsshapegradient.overlay.dark");
                    } else if (!s.isChecked() & darkqson == 0) {
                        runCommandAction("cmd overlay disable com.qsshapegradient.overlay");
                    }
                }
                //QS Tile vibration options
                if (key.equals("quick_settings_vibrate")){
                    if (s.isChecked()) {
                        runCommandAction("settings put secure quick_settings_vibrate 1");
                    } else {
                        runCommandAction("settings put secure quick_settings_vibrate 0");
                    }
                }
                //Invert navigation bar layout sound on/off
                if (key.equals("nav_bar_inverse")){
                    int gestureson = Settings.Secure.getInt(c.getContentResolver(), "navigation_mode", 0);
                    if (gestureson == 2){
                        navbarButtonsWarning();
                        s.setChecked(false);
                        s.setEnabled(false);
                        runCommandAction("settings put secure sysui_nav_bar_inverse 0");
                    }
                    if (s.isChecked() & gestureson !=2) {
                        runCommandAction("settings put secure sysui_nav_bar_inverse 1");
                    } else {
                        runCommandAction("settings put secure sysui_nav_bar_inverse 0");
                    }
                }
                //Screenshot shutter sound on/off
                if (key.equals("screenshot_sound")){
                    if (s.isChecked()) {
                        runCommandAction("settings put system screenshot_shutter_sound 0");
                    } else {
                        runCommandAction("settings put system screenshot_shutter_sound 1");
                    }
                }
                //Game overlay on/off
                if (key.equals("game_overlay")){
                    restartRequiredWarning();
                    if (s.isChecked()) {
                        runCommandAction("cp /data/adb/modules/AddonFeaturesForPixel/data/productz/etc/sysconfig/game_overlay.xml /data/adb/modules/AddonFeaturesForPixel/system/product/etc/sysconfig/");
                    } else {
                        runCommandAction("rm -rf /data/adb/modules/AddonFeaturesForPixel/system/product/etc/sysconfig/game_overlay.xml");
                    }
                }
                //Google Sans override on/off
                if (key.equals("gsans_override")){
                    restartRequiredWarning();
                    if (s.isChecked()) {
                        runCommandAction("cp /data/adb/modules/AddonFeaturesForPixel/data/fontz/GSans/*.ttf /data/adb/modules/AddonFeaturesForPixel/system/fonts/");
                    } else {
                        runCommandAction("rm -rf /data/adb/modules/AddonFeaturesForPixel/system/fonts/*.ttf");
                    }
                }
                /*Themed custom icons*/
                if (key.equals("themed_icons")){
                    themedIconsWarning();
                    if (s.isChecked()) {
                        runCommandAction("cmd overlay enable com.romcontrolicons.nexuslauncher");
                    } else {
                        runCommandAction("cmd overlay disable com.romcontrolicons.nexuslauncher");
                    }
                }
                /*Enable/Disable combined signal icons*/
                if (key.equals("combine_signal_icons")){
                    if (s.isChecked()) {
                        runCommandAction("cmd overlay enable com.android.systemui.combinesignal");
                        restartUiRequiredWarning();
                    } else {
                        runCommandAction("cmd overlay disable com.android.systemui.combinesignal");
                        restartUiRequiredWarning();
                    }
                }
                /*Enable/Disable show number of unread messages*/
                if (key.equals("show_unread_messages_number")){
                    if (s.isChecked()) {
                        runCommandAction("cmd overlay enable com.android.systemui.shownumber");
                        restartUiRequiredWarning();
                    } else {
                        runCommandAction("cmd overlay disable com.android.systemui.shownumber");
                        restartUiRequiredWarning();
                    }
                }
                /*Enable/Disable dual tone battery meter*/
                if (key.equals("dual_tone_battery")){
                    if (s.isChecked()) {
                        runCommandAction("cmd overlay enable com.android.dualtonebattery");
                    } else {
                        runCommandAction("cmd overlay disable com.android.dualtonebattery");
                    }
                }
                //Enable/Disable Tuner
                if (key.equals("sysui_tuner")){
                    if (s.isChecked()) {
                        runCommandAction("pm enable com.android.systemui/.tuner.TunerActivity");
                    } else {
                        runCommandAction("pm disable com.android.systemui/.tuner.TunerActivity");
                    }
                }
                /*QS Estiamtes warning*/
                if (key.equals("qs_show_battery_estimate")){
                    restartUiRequiredWarning();
                }
                //Adaptive Sound Service on/off
                if (key.equals("adaptive_sound")){
                    if (s.isChecked()) {
                        runCommandAction("device_config put device_personalization_services AdaptiveAudio__enable_adaptive_audio true && sed -re 's/enable_adaptive_audio false/enable_adaptive_audio true/g' /data/adb/modules/AddonFeaturesForPixel/service.sh > /data/adb/modules/AddonFeaturesForPixel/new_service.sh && mv /data/adb/modules/AddonFeaturesForPixel/new_service.sh /data/adb/modules/AddonFeaturesForPixel/service.sh");
                    } else {
                        runCommandAction("device_config put device_personalization_services AdaptiveAudio__enable_adaptive_audio false && sed -re 's/enable_adaptive_audio true/enable_adaptive_audio false/g' /data/adb/modules/AddonFeaturesForPixel/service.sh > /data/adb/modules/AddonFeaturesForPixel/new_service.sh && mv /data/adb/modules/AddonFeaturesForPixel/new_service.sh /data/adb/modules/AddonFeaturesForPixel/service.sh");
                    }
                }
                //Reduce keyboard space on gestures navigation on/off
                if (key.equals("gestures_reduce_keyboard")) {
                    int removenbon = Settings.System.getInt(c.getContentResolver(), "remove_navbar", 0);
                    if (removenbon != 1) {
                        int geston = Settings.Secure.getInt(c.getContentResolver(), "navigation_mode", 0);
                        if (geston != 2) {
                            navbarGesturesWarning();
                            s.setChecked(false);
                            s.setEnabled(false);
                            runCommandAction("cmd overlay disable com.android.overlay.reducekeyboard");
                        }
                        if (s.isChecked() & geston == 2) {
                            runCommandAction("cmd overlay enable com.android.overlay.reducekeyboard");
                        } else {
                            runCommandAction("cmd overlay disable com.android.overlay.reducekeyboard");
                        }
                    } else {
                        s.setChecked(false);
                        s.setEnabled(false);
                    }
                }
                //Lock screen camera shortcut on/off
                if (key.equals("lockscreen_camera_shortcut")) {
                    if (s.isChecked()) {
                        runCommandAction("cmd overlay enable com.android.systemui.showcamera");
                    } else {
                        runCommandAction("cmd overlay disable com.android.systemui.showcamera");
                    }
                }
                //Lock screen left shortcut on/off
                if (key.equals("lockscreen_left_shortcut")) {
                    if (s.isChecked()) {
                        runCommandAction("cmd overlay enable com.android.systemui.showleftshortcut");
                    } else {
                        runCommandAction("cmd overlay disable com.android.systemui.showleftshortcut");
                    }
                }
                //Fix left padding if status bar is reduced on Pixel 4 and 5 devices on/off
                if (key.equals("fix_sb_left_paddding")) {
                    int reduceon = Settings.System.getInt(c.getContentResolver(), "status_bar_height", 0);
                    if (reduceon == 0) {
                        s.setChecked(false);
                        s.setEnabled(false);
                        runCommandAction("cmd overlay disable com.android.systemui.sb_height_small");
                    }
                    if (s.isChecked() & reduceon != 0) {
                        runCommandAction("cmd overlay enable com.android.systemui.sb_height_small");
                    } else {
                        runCommandAction("cmd overlay disable com.android.systemui.sb_height_small");
                    }
                }
                //Remove navigation bar
                if (key.equals("remove_navbar")) {
                    if (s.isChecked()) {
                        navbarRemoveWarning();
                        runCommandAction("sleep 10 && cmd overlay enable com.android.overlay.removenavbar && cmd overlay disable com.android.overlay.reducekeyboard && cmd overlay disable com.android.systemui.overlay.hidepill");
                    } else {
                        runCommandAction("cmd overlay disable com.android.overlay.removenavbar");
                    }
                }
                //Wifi, Cell tiles on/off
                if (key.equals("wifi_cell")) {
                    if (s.isChecked()) {
                        runCommandAction("settings put global settings_provider_model false && settings list secure | grep sysui_qs_tiles > /sdcard/current_qs_tiles.txt && sed 's/wifi,cell,//g' /sdcard/current_qs_tiles.txt > /sdcard/nowificell_qs_tiles.txt && sed 's/=/ /g' /sdcard/nowificell_qs_tiles.txt > /sdcard/new_qs_tiles.txt && sed 's/sysui_qs_tiles/settings put secure sysui_qs_tiles/g' /sdcard/new_qs_tiles.txt > /sdcard/new2_qs_tiles.txt && sed -re 's/\\(/\"\\(/g' /sdcard/new2_qs_tiles.txt > /sdcard/new3_qs_tiles.txt && sed -re 's/\\)/\\)\"/g' /sdcard/new3_qs_tiles.txt > /sdcard/new4_qs_tiles.txt && sed -re 's/internet,/wifi,cell,/g' /sdcard/new4_qs_tiles.txt > /sdcard/final_qs_tiles.txt && sh /sdcard/final_qs_tiles.txt && rm -rf /sdcard/current_qs_tiles.txt && rm -rf /sdcard/new_qs_tiles.txt && rm -rf /sdcard/new2_qs_tiles.txt && rm -rf /sdcard/new3_qs_tiles.txt && rm -rf /sdcard/new4_qs_tiles.txt && rm -rf /sdcard/final_qs_tiles.txt && rm -rf /sdcard/nowificell_qs_tiles.txt");
                    } else {
                        runCommandAction("settings list secure | grep sysui_qs_tiles > /sdcard/current_qs_tiles.txt && sed 's/internet,//g' /sdcard/current_qs_tiles.txt > /sdcard/nointernet_qs_tiles.txt && sed 's/=/ /g' /sdcard/nointernet_qs_tiles.txt > /sdcard/new_qs_tiles.txt && sed 's/sysui_qs_tiles/settings put secure sysui_qs_tiles/g' /sdcard/new_qs_tiles.txt > /sdcard/new2_qs_tiles.txt && sed -re 's/\\(/\"\\(/g' /sdcard/new2_qs_tiles.txt > /sdcard/new3_qs_tiles.txt && sed -re 's/\\)/\\)\"/g' /sdcard/new3_qs_tiles.txt > /sdcard/new4_qs_tiles.txt && sed -re 's/wifi,cell,/internet,/g' /sdcard/new4_qs_tiles.txt > /sdcard/final_qs_tiles.txt && sh /sdcard/final_qs_tiles.txt && rm -rf /sdcard/current_qs_tiles.txt && rm -rf /sdcard/new_qs_tiles.txt && rm -rf /sdcard/new2_qs_tiles.txt && rm -rf /sdcard/new3_qs_tiles.txt && rm -rf /sdcard/new4_qs_tiles.txt && rm -rf /sdcard/final_qs_tiles.txt && rm -rf /sdcard/nointernet_qs_tiles.txt && settings put global settings_provider_model true");
                    }
                }
                //Center clock fix on/off
                if (key.equals("center_clock_fix")) {
                    int reduceon = Settings.System.getInt(c.getContentResolver(), "status_bar_height", 0);
                    int cclockon = Settings.System.getInt(c.getContentResolver(), "statusbar_clock_style", 0);
                    //If status bar height is small or medium, we don't need this fix
                    if (reduceon != 0) {
                        s.setChecked(false);
                        s.setEnabled(false);
                        runCommandAction("cmd overlay disable com.android.systemui.cclock_fix");
                    }
                    if (s.isChecked() & reduceon == 0 & cclockon == 1) {
                        runCommandAction("cmd overlay enable com.android.systemui.cclock_fix");
                    } else {
                        runCommandAction("cmd overlay disable com.android.systemui.cclock_fix");
                    }
                }
                //Center clock fix to the right on/off
                if (key.equals("center_clock_rfix")) {
                    int reduceon = Settings.System.getInt(c.getContentResolver(), "status_bar_height", 0);
                    int cclockon = Settings.System.getInt(c.getContentResolver(), "statusbar_clock_style", 0);
                    //If status bar height is small or medium, we don't need this fix
                    if (reduceon != 0) {
                        s.setChecked(false);
                        s.setEnabled(false);
                        runCommandAction("cmd overlay disable com.android.systemui.cclock_rfix");
                    }
                    if (s.isChecked() & reduceon == 0 & cclockon == 1) {
                        runCommandAction("cmd overlay enable com.android.systemui.cclock_rfix");
                    } else {
                        runCommandAction("cmd overlay disable com.android.systemui.cclock_rfix");
                    }
                }
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
                    //QS battery percentage warning
                    if (key.equals("qs_show_battery_percent")){
                        restartUiRequiredWarning();
                    }
                    //Edge Sense Plus app options
                    if (key.equals("edge_sense_plus_app")){
                        switch(mValueIndex) {
                            case 1:
                                installEdgeSensePlusWarning();
                                break;
                            default:
                                uninstallEdgeSensePlusWarning();
                                break;
                        }
                    }
                    //Tap Tap app options
                    if (key.equals("tap_tap_app")){
                        switch(mValueIndex) {
                            case 1:
                                installTapTapWarning();
                                break;
                            default:
                                uninstallTapTapWarning();
                                break;
                        }
                    }
                    //Dark Theme styles options begin
                    if (key.equals("dt_style")){
                        switch(mValueIndex) {
                            case 0:
                                AlertDialog.Builder mSysUIWarnBuilder0 = new AlertDialog.Builder(c);
                                mSysUIWarnBuilder0.setTitle(R.string.attention);
                                mSysUIWarnBuilder0.setMessage(R.string.restartui_forced_required);
                                mSysUIWarnBuilder0.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.darkgrey && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.pitchblack && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.style && killall com.android.systemui");
                                    }
                                });
                                mSysUIWarnBuilder0.setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.cancel();

                                    }
                                });
                                AlertDialog mSysUIWarn0 = mSysUIWarnBuilder0.create();
                                mSysUIWarn0.show();
                                TypedValue typedValue0 = new TypedValue();
                                Resources.Theme theme0 = c.getTheme();
                                theme0.resolveAttribute(R.attr.colorAccent, typedValue0, true);
                                Button ok0 = mSysUIWarn0.getButton(AlertDialog.BUTTON_POSITIVE);
                                ok0.setTextColor(typedValue0.data);
                                mSysUIWarn0.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                                break;
                            case 1:
                                AlertDialog.Builder mSysUIWarnBuilder1 = new AlertDialog.Builder(c);
                                mSysUIWarnBuilder1.setTitle(R.string.attention);
                                mSysUIWarnBuilder1.setMessage(R.string.dt_implement_method_choice);
                                mSysUIWarnBuilder1.setPositiveButton(R.string.forced_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay enable-exclusive com.android.dark.bakedgreen");
                                    }
                                });
                                mSysUIWarnBuilder1.setNegativeButton(R.string.compatible_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.darkgrey && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.pitchblack && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.style && cmd overlay enable com.android.dark.bakedgreen && killall com.android.systemui");
                                    }
                                });
                                AlertDialog mSysUIWarn1 = mSysUIWarnBuilder1.create();
                                mSysUIWarn1.show();
                                TypedValue typedValue1 = new TypedValue();
                                Resources.Theme theme1 = c.getTheme();
                                theme1.resolveAttribute(R.attr.colorAccent, typedValue1, true);
                                Button ok1 = mSysUIWarn1.getButton(AlertDialog.BUTTON_POSITIVE);
                                ok1.setTextColor(typedValue1.data);
                                mSysUIWarn1.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                                break;
                            case 2:
                                AlertDialog.Builder mSysUIWarnBuilder2 = new AlertDialog.Builder(c);
                                mSysUIWarnBuilder2.setTitle(R.string.attention);
                                mSysUIWarnBuilder2.setMessage(R.string.dt_implement_method_choice);
                                mSysUIWarnBuilder2.setPositiveButton(R.string.forced_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay enable-exclusive com.android.dark.chocox");
                                    }
                                });
                                mSysUIWarnBuilder2.setNegativeButton(R.string.compatible_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.darkgrey && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.pitchblack && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.style && cmd overlay enable com.android.dark.chocox && killall com.android.systemui");
                                    }
                                });
                                AlertDialog mSysUIWarn2 = mSysUIWarnBuilder2.create();
                                mSysUIWarn2.show();
                                TypedValue typedValue2 = new TypedValue();
                                Resources.Theme theme2 = c.getTheme();
                                theme2.resolveAttribute(R.attr.colorAccent, typedValue2, true);
                                Button ok2 = mSysUIWarn2.getButton(AlertDialog.BUTTON_POSITIVE);
                                ok2.setTextColor(typedValue2.data);
                                mSysUIWarn2.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                                break;
                            case 3:
                                AlertDialog.Builder mSysUIWarnBuilder3 = new AlertDialog.Builder(c);
                                mSysUIWarnBuilder3.setTitle(R.string.attention);
                                mSysUIWarnBuilder3.setMessage(R.string.dt_implement_method_choice);
                                mSysUIWarnBuilder3.setPositiveButton(R.string.forced_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay enable-exclusive com.android.dark.darkgrey");
                                    }
                                });
                                mSysUIWarnBuilder3.setNegativeButton(R.string.compatible_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.pitchblack && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.style && cmd overlay enable com.android.dark.darkgrey && killall com.android.systemui");
                                    }
                                });
                                AlertDialog mSysUIWarn3 = mSysUIWarnBuilder3.create();
                                mSysUIWarn3.show();
                                TypedValue typedValue3 = new TypedValue();
                                Resources.Theme theme3 = c.getTheme();
                                theme3.resolveAttribute(R.attr.colorAccent, typedValue3, true);
                                Button ok3 = mSysUIWarn3.getButton(AlertDialog.BUTTON_POSITIVE);
                                ok3.setTextColor(typedValue3.data);
                                mSysUIWarn3.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                                break;
                            case 4:
                                AlertDialog.Builder mSysUIWarnBuilder4 = new AlertDialog.Builder(c);
                                mSysUIWarnBuilder4.setTitle(R.string.attention);
                                mSysUIWarnBuilder4.setMessage(R.string.dt_implement_method_choice);
                                mSysUIWarnBuilder4.setPositiveButton(R.string.forced_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay enable-exclusive com.android.dark.materialocean");
                                    }
                                });
                                mSysUIWarnBuilder4.setNegativeButton(R.string.compatible_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.darkgrey && cmd overlay disable com.android.dark.pitchblack && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.style && cmd overlay enable com.android.dark.materialocean && killall com.android.systemui");
                                    }
                                });
                                AlertDialog mSysUIWarn4 = mSysUIWarnBuilder4.create();
                                mSysUIWarn4.show();
                                TypedValue typedValue4 = new TypedValue();
                                Resources.Theme theme4 = c.getTheme();
                                theme4.resolveAttribute(R.attr.colorAccent, typedValue4, true);
                                Button ok4 = mSysUIWarn4.getButton(AlertDialog.BUTTON_POSITIVE);
                                ok4.setTextColor(typedValue4.data);
                                mSysUIWarn4.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                                break;
                            case 5:
                                AlertDialog.Builder mSysUIWarnBuilder5 = new AlertDialog.Builder(c);
                                mSysUIWarnBuilder5.setTitle(R.string.attention);
                                mSysUIWarnBuilder5.setMessage(R.string.dt_implement_method_choice);
                                mSysUIWarnBuilder5.setPositiveButton(R.string.forced_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay enable-exclusive com.android.dark.pitchblack");
                                    }
                                });
                                mSysUIWarnBuilder5.setNegativeButton(R.string.compatible_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.darkgrey && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.style && cmd overlay enable com.android.dark.pitchblack && killall com.android.systemui");
                                    }
                                });
                                AlertDialog mSysUIWarn5 = mSysUIWarnBuilder5.create();
                                mSysUIWarn5.show();
                                TypedValue typedValue5 = new TypedValue();
                                Resources.Theme theme5 = c.getTheme();
                                theme5.resolveAttribute(R.attr.colorAccent, typedValue5, true);
                                Button ok5 = mSysUIWarn5.getButton(AlertDialog.BUTTON_POSITIVE);
                                ok5.setTextColor(typedValue5.data);
                                mSysUIWarn5.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                                break;
                            case 6:
                                AlertDialog.Builder mSysUIWarnBuilder6 = new AlertDialog.Builder(c);
                                mSysUIWarnBuilder6.setTitle(R.string.attention);
                                mSysUIWarnBuilder6.setMessage(R.string.dt_implement_method_choice);
                                mSysUIWarnBuilder6.setPositiveButton(R.string.forced_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay enable-exclusive com.android.dark.solarizeddark");
                                    }
                                });
                                mSysUIWarnBuilder6.setNegativeButton(R.string.compatible_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.darkgrey && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.pitchblack && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.style && cmd overlay enable com.android.dark.solarizeddark && killall com.android.systemui");
                                    }
                                });
                                AlertDialog mSysUIWarn6 = mSysUIWarnBuilder6.create();
                                mSysUIWarn6.show();
                                TypedValue typedValue6 = new TypedValue();
                                Resources.Theme theme6 = c.getTheme();
                                theme6.resolveAttribute(R.attr.colorAccent, typedValue6, true);
                                Button ok6 = mSysUIWarn6.getButton(AlertDialog.BUTTON_POSITIVE);
                                ok6.setTextColor(typedValue6.data);
                                mSysUIWarn6.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                                break;
							case 7:
                                AlertDialog.Builder mSysUIWarnBuilder7 = new AlertDialog.Builder(c);
                                mSysUIWarnBuilder7.setTitle(R.string.attention);
                                mSysUIWarnBuilder7.setMessage(R.string.dt_implement_method_choice);
                                mSysUIWarnBuilder7.setPositiveButton(R.string.forced_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay enable-exclusive com.android.dark.darkaubergine");
                                    }
                                });
                                mSysUIWarnBuilder7.setNegativeButton(R.string.compatible_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.darkgrey && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.pitchblack && cmd overlay disable com.android.dark.style && cmd overlay enable com.android.dark.darkaubergine && killall com.android.systemui");
                                    }
                                });
                                AlertDialog mSysUIWarn7 = mSysUIWarnBuilder7.create();
                                mSysUIWarn7.show();
                                TypedValue typedValue7 = new TypedValue();
                                Resources.Theme theme7 = c.getTheme();
                                theme7.resolveAttribute(R.attr.colorAccent, typedValue7, true);
                                Button ok7 = mSysUIWarn7.getButton(AlertDialog.BUTTON_POSITIVE);
                                ok7.setTextColor(typedValue7.data);
                                mSysUIWarn7.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                                break;
							case 8:
                                AlertDialog.Builder mSysUIWarnBuilder8 = new AlertDialog.Builder(c);
                                mSysUIWarnBuilder8.setTitle(R.string.attention);
                                mSysUIWarnBuilder8.setMessage(R.string.dt_implement_method_choice);
                                mSysUIWarnBuilder8.setPositiveButton(R.string.forced_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay enable-exclusive com.android.dark.style");
                                    }
                                });
                                mSysUIWarnBuilder8.setNegativeButton(R.string.compatible_method, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        runCommandAction("cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.darkgrey && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.pitchblack && cmd overlay disable com.android.dark.darkaubergine && cmd overlay enable com.android.dark.style && killall com.android.systemui");
                                    }
                                });
                                AlertDialog mSysUIWarn8 = mSysUIWarnBuilder8.create();
                                mSysUIWarn8.show();
                                TypedValue typedValue8 = new TypedValue();
                                Resources.Theme theme8 = c.getTheme();
                                theme8.resolveAttribute(R.attr.colorAccent, typedValue8, true);
                                Button ok8 = mSysUIWarn8.getButton(AlertDialog.BUTTON_POSITIVE);
                                ok8.setTextColor(typedValue8.data);
                                mSysUIWarn8.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                                break;
                            default:
                                runCommandAction("cmd overlay disable com.android.dark.bakedgreen && cmd overlay disable com.android.dark.chocox && cmd overlay disable com.android.dark.darkgrey && cmd overlay disable com.android.dark.materialocean && cmd overlay disable com.android.dark.pitchblack && cmd overlay disable com.android.dark.solarizeddark && cmd overlay disable com.android.dark.darkaubergine && cmd overlay disable com.android.dark.style && killall com.android.systemui");
                                break;
                        }
                    }
                    //Back gestures height warning
                    if (key.equals("back_gesture_height")) {
                        int gest_on = Settings.Secure.getInt(c.getContentResolver(), "navigation_mode", 0);
                        if (gest_on != 2) {
                            navbarGesturesWarning();
                        }
                    }
                    //Nav bar length options
                    if (key.equals("gesture_navbar_length")){
                        switch(mValueIndex) {
                            case 1:
                                runCommandAction("settings put secure gesture_navbar_length_mode 1");
                                break;
                            case 2:
                                runCommandAction("settings put secure gesture_navbar_length_mode 2");
                                break;
                            default:
                                runCommandAction("settings put secure gesture_navbar_length_mode 0");
                                break;
                        }
                    }
                    //Status bar height options
                    if (key.equals("status_bar_height")){
                        switch(mValueIndex) {
                            case 1:
                                runCommandAction("cmd overlay disable com.android.systemui.cclock_fix && cmd overlay disable com.android.systemui.cclock_rfix && cmd overlay enable-exclusive --category com.android.sb_height_small");
                                break;
                            case 2:
                                runCommandAction("cmd overlay disable com.android.systemui.cclock_fix && cmd overlay disable com.android.systemui.cclock_rfix && cmd overlay enable-exclusive --category com.android.sb_height_medium");
                                break;
                            default:
                                runCommandAction("cmd overlay disable com.android.sb_height_small && cmd overlay disable com.android.sb_height_medium && cmd overlay disable com.android.systemui.sb_height_small");
                                break;
                        }
                    }
                    //Quick QS pulldown options begin
                    if (key.equals("status_bar_quick_qs_pulldown")){
                        switch(mValueIndex) {
                            case 1:
                                Settings.Secure.putInt(c.getContentResolver(), "status_bar_quick_qs_pulldown", 1);
                                break;
                            case 2:
                                Settings.Secure.putInt(c.getContentResolver(), "status_bar_quick_qs_pulldown", 2);
                                break;
                            default:
                                Settings.Secure.putInt(c.getContentResolver(), "status_bar_quick_qs_pulldown", 0);
                                break;
                        }
                    }
                    //Network Traffic specific options begin
                    if (key.equals("network_traffic_location")){
                        switch(mValueIndex) {
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
                    //Clock AM PM specific options begin
                    if (key.equals("statusbar_am_pm")){
                        switch(mValueIndex) {
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
                                // Ask the user which time format he wants as default
                                AlertDialog.Builder mSysUIWarnBuilder = new AlertDialog.Builder(c);
                                mSysUIWarnBuilder.setTitle(R.string.attention);
                                mSysUIWarnBuilder.setMessage(R.string.time_format_option);
                                mSysUIWarnBuilder.setPositiveButton(R.string.time_format_option_12, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Settings.System.putInt(c.getContentResolver(), "time_12_24", 12);
                                    }
                                });
                                mSysUIWarnBuilder.setNegativeButton(R.string.time_format_option_24, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Settings.System.putInt(c.getContentResolver(), "time_12_24", 24);
                                    }
                                });
                                AlertDialog mSysUIWarn = mSysUIWarnBuilder.create();
                                mSysUIWarn.show();
                                TypedValue typedValue = new TypedValue();
                                Resources.Theme theme = c.getTheme();
                                theme.resolveAttribute(R.attr.colorAccent, typedValue, true);
                                Button ok = mSysUIWarn.getButton(AlertDialog.BUTTON_POSITIVE);
                                ok.setTextColor(typedValue.data);
                                mSysUIWarn.getWindow().setBackgroundDrawableResource(R.drawable.dialog_bg);
                                break;
                        }
                    }
                    //Clock AM PM specific options end
                    //Clock Date display specific options begin
                    if (key.equals("clock_date_display")){
                        switch(mValueIndex) {
                            case 1:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_date_display", 1);
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.hide.date");
                                break;
                            case 2:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_date_display", 2);
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.hide.date");
                                break;
                            default:
                                Settings.System.putInt(c.getContentResolver(), "statusbar_clock_date_display", 0);
                                runCommandAction("cmd overlay disable com.android.systemui.hide.date");
                                break;
                        }
                    }
                    //Clock Date display specific options end
                    //Clock Date style specific options begin
                    if (key.equals("clock_date_style")){
                        switch(mValueIndex) {
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
					//QS Style options begin
                    if (key.equals("quick_settings_tiles_themes")){
                        switch(mValueIndex) {
                            case 1:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.no34d.overlay");
                                break;
                            case 2:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.circout.overlay");
                                break;
                            case 3:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.dualtone.overlay");
                                break;
                            case 4:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.cookie.overlay");
                                break;
                            case 5:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.o34d.overlay");
                                break;
                            case 6:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.ninja.overlay");
                                break;
                            case 7:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.cosmos.overlay");
                                break;
                            case 8:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.dottedcircle.overlay");
                                break;
                            case 9:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.inkdrop.overlay");
                                break;
                            case 10:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.wavey.overlay");
                                break;
                            case 11:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.attemptmountain.overlay");
                                break;
                            case 12:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.squircle.overlay");
                                break;
                            case 13:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.pokesign.overlay");
                                break;
                            case 14:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.neonlike.overlay");
                                break;
                            case 15:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.squaremedo.overlay");
                                break;
                            case 16:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.divided.overlay");
                                break;
                            case 17:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.triangles.overlay");
                                break;
                            case 18:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.circletrim.overlay");
                                break;
                            case 19:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.circle.overlay");
                                break;
                            case 20:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.liarch.overlay");
                                break;
                            /* These next QS Styles can't be used as agreed with their developer
                            case 21:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.circnout.overlay");
                                break;
                            case 22:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.dnout.overlay");
                                break;
                            case 23:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.dout.overlay");
                                break;
                            case 24:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.no24d.overlay");
                                break;
                            case 25:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.o24d.overlay");
                                break;
                            case 26:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.no24l.overlay");
                                break;
                            case 27:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.o24l.overlay");
                                break;
                            case 28:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.no34l.overlay");
                                break;
                            case 29:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.o34l.overlay");
                                break;
                            case 30:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.no44d.overlay");
                                break;
                            case 31:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.o44d.overlay");
                                break;
                            case 32:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.no44l.overlay");
                                break;
                            case 33:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.o44l.overlay");
                                break;
                             */
                            default:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstile.no34d.overlay && sleep 1 && cmd overlay disable com.android.systemui.qstile.no34d.overlay");
                                break;
                        }
                    }
                    //QS Style options end
                    //Battery bar options begin
                    if (key.equals("statusbar_battery_bar_no_navbar_list")){
                        switch(mValueIndex) {
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
                    //System fonts options begin
                    if (key.equals("font_style")){
                        switch(mValueIndex) {
                            case 1:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.blenderpro");
                                break;
                            case 2:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.euclid");
                                break;
                            case 3:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.harmonysans");
                                break;
                            case 4:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.googlesans.inter");
                                break;
                            case 5:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.linottesource");
                                break;
                            case 6:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.evolvesans");
                                break;
                            case 7:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.manrope");
                                break;
                            case 8:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.milanpro");
                                break;
                            case 9:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.noto_sans");
                                break;
                            case 10:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.notoserifsource");
                                break;
                            case 11:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.opsans");
                                break;
                            case 12:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.oneplusslate");
                                break;
                            case 13:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.poppins");
                                break;
                            case 14:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.recursive_casual");
                                break;
                            case 15:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.recursive_linear");
                                break;
                            case 16:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.roboto");
                                break;
                            case 17:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.rosemarysource");
                                break;
                            case 18:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.source_sans");
                                break;
                            case 19:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.source_serif");
                                break;
                            case 20:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.storopia");
                                break;
                            case 21:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.simpleday");
                                break;
                            case 22:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.lgsmartgothicsource");
                                break;
                            case 23:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.nokiapure");
                                break;
                            case 24:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.bariolsource");
                                break;
                            case 25:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.comfortaa");
                                break;
                            case 26:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.coolstorysource");
                                break;
                            case 27:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.comicsans");
                                break;
                            case 28:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.sonysketchsource");
                                break;
                            case 29:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.amarantesource");
                                break;
                            case 30:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.cocon");
                                break;
                            case 31:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.fucek");
                                break;
                            case 32:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.oduda");
                                break;
                            case 33:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.lemonmilk");
                                break;
                            case 34:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.aclonicasource");
                                break;
                            case 35:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.fifa2018");
                                break;
                            case 36:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.reemkufi");
                                break;
                            case 37:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.exotwo");
                                break;
                            case 38:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.samsungone");
                                break;
                            case 39:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.nunito");
                                break;
                            case 40:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.quando");
                                break;
                            case 41:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.ubuntu");
                                break;
                            case 42:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.antipastopro");
                                break;
                            case 43:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.surfersource");
                                break;
                            case 44:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.cagliostrosource");
                                break;
                            case 45:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.grandhotel");
                                break;
                            case 46:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.font.redressed");
                                break;
                            default:
                                runCommandAction("cmd overlay enable-exclusive --category org.protonaosp.theme.font.blenderpro && sleep 1 && cmd overlay disable org.protonaosp.theme.font.blenderpro");
                                break;
                        }
                    }
                    //System fonts options end
                    //Icons style options begin
                    if (key.equals("icons_style")){
                        switch(mValueIndex) {
                            case 1:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon_pack.acherus.android && cmd overlay enable-exclusive --category com.android.theme.icon_pack.acherus.systemui");
                                break;
                            case 2:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon_pack.circular.android && cmd overlay enable-exclusive --category com.android.theme.icon_pack.circular.systemui && cmd overlay enable-exclusive --category com.android.theme.icon_pack.circular.settings && cmd overlay enable-exclusive --category com.android.theme.icon_pack.circular.launcher && cmd overlay enable-exclusive --category com.android.theme.icon_pack.circular.themepicker");
                                break;
                            case 3:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon_pack.filled.android && cmd overlay enable-exclusive --category com.android.theme.icon_pack.filled.systemui && cmd overlay enable-exclusive --category com.android.theme.icon_pack.filled.settings && cmd overlay enable-exclusive --category com.android.theme.icon_pack.filled.launcher && cmd overlay enable-exclusive --category com.android.theme.icon_pack.filled.themepicker");
                                break;
                            case 4:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon_pack.kai.android && cmd overlay enable-exclusive --category com.android.theme.icon_pack.kai.systemui && cmd overlay enable-exclusive --category com.android.theme.icon_pack.kai.settings && cmd overlay enable-exclusive --category com.android.theme.icon_pack.kai.launcher && cmd overlay enable-exclusive --category com.android.theme.icon_pack.kai.themepicker");
                                break;
                            case 5:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon_pack.oos.android && cmd overlay enable-exclusive --category com.android.theme.icon_pack.oos.systemui && cmd overlay enable-exclusive --category com.android.theme.icon_pack.oos.settings && cmd overlay enable-exclusive --category com.android.theme.icon_pack.oos.launcher && cmd overlay enable-exclusive --category com.android.theme.icon_pack.oos.themepicker");
                                break;
                            case 6:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon_pack.rounded.android && cmd overlay enable-exclusive --category com.android.theme.icon_pack.rounded.systemui && cmd overlay enable-exclusive --category com.android.theme.icon_pack.rounded.settings && cmd overlay enable-exclusive --category com.android.theme.icon_pack.rounded.launcher && cmd overlay enable-exclusive --category com.android.theme.icon_pack.rounded.themepicker");
                                break;
                            case 7:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon_pack.sam.android && cmd overlay enable-exclusive --category com.android.theme.icon_pack.sam.systemui && cmd overlay enable-exclusive --category com.android.theme.icon_pack.sam.settings && cmd overlay enable-exclusive --category com.android.theme.icon_pack.sam.launcher && cmd overlay enable-exclusive --category com.android.theme.icon_pack.sam.themepicker");
                                break;
                            case 8:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon_pack.victor.android && cmd overlay enable-exclusive --category com.android.theme.icon_pack.victor.systemui && cmd overlay enable-exclusive --category com.android.theme.icon_pack.victor.settings && cmd overlay enable-exclusive --category com.android.theme.icon_pack.victor.launcher && cmd overlay enable-exclusive --category com.android.theme.icon_pack.victor.themepicker");
                                break;
                            default:
                                runCommandAction("cmd overlay disable com.android.theme.icon_pack.rounded.android && cmd overlay disable com.android.theme.icon_pack.sam.android && cmd overlay disable com.android.theme.icon_pack.filled.android && cmd overlay disable com.android.theme.icon_pack.kai.android && cmd overlay disable com.android.theme.icon_pack.victor.android && cmd overlay disable com.android.theme.icon_pack.circular.android && cmd overlay disable com.android.theme.icon_pack.rounded.launcher && cmd overlay disable com.android.theme.icon_pack.victor.launcher && cmd overlay disable com.android.theme.icon_pack.kai.launcher && cmd overlay disable com.android.theme.icon_pack.sam.launcher && cmd overlay disable com.android.theme.icon_pack.filled.launcher && cmd overlay disable com.android.theme.icon_pack.circular.launcher && cmd overlay disable com.android.theme.icon_pack.victor.settings && cmd overlay disable com.android.theme.icon_pack.kai.settings && cmd overlay disable com.android.theme.icon_pack.sam.settings && cmd overlay disable com.android.theme.icon_pack.filled.settings && cmd overlay disable com.android.theme.icon_pack.circular.settings && cmd overlay disable com.android.theme.icon_pack.rounded.settings && cmd overlay disable com.android.theme.icon_pack.circular.themepicker && cmd overlay disable com.android.theme.icon_pack.kai.themepicker && cmd overlay disable com.android.theme.icon_pack.rounded.themepicker && cmd overlay disable com.android.theme.icon_pack.filled.themepicker && cmd overlay disable com.android.theme.icon_pack.victor.themepicker && cmd overlay disable com.android.theme.icon_pack.sam.themepicker && cmd overlay disable com.android.theme.icon_pack.rounded.systemui && cmd overlay disable com.android.theme.icon_pack.victor.systemui && cmd overlay disable com.android.theme.icon_pack.kai.systemui && cmd overlay disable com.android.theme.icon_pack.sam.systemui && cmd overlay disable com.android.theme.icon_pack.filled.systemui && cmd overlay disable com.android.theme.icon_pack.circular.systemui && cmd overlay disable com.android.theme.icon_pack.oos.android && cmd overlay disable com.android.theme.icon_pack.oos.launcher && cmd overlay disable com.android.theme.icon_pack.oos.settings && cmd overlay disable com.android.theme.icon_pack.oos.systemui && cmd overlay disable com.android.theme.icon_pack.oos.themepicker && cmd overlay disable com.android.theme.icon_pack.acherus.android && cmd overlay disable com.android.theme.icon_pack.acherus.systemui");
                                break;
                        }
                    }
                    //Icons style options end
                    //Icons shapes options begin
                    if (key.equals("icon_shapes")){
                        switch(mValueIndex) {
                            case 1:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.cylinder");
                                break;
                            case 2:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.leaf");
                                break;
                            case 3:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.heart");
                                break;
                            case 4:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.hexagon");
                                break;
                            case 5:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.mallow");
                                break;
                            case 6:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.pebble");
                                break;
                            case 7:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.roundedhexagon");
                                break;
                            case 8:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.roundedrect");
                                break;
                            case 9:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.square");
                                break;
                            case 10:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.squircle");
                                break;
                            case 11:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.taperedrect");
                                break;
                            case 12:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.teardrop");
                                break;
                            case 13:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.theme.icon.vessel");
                                break;
                            default:
                                runCommandAction("cmd overlay disable com.android.theme.icon.cylinder && cmd overlay disable com.android.theme.icon.heart && cmd overlay disable com.android.theme.icon.hexagon && cmd overlay disable com.android.theme.icon.mallow && cmd overlay disable com.android.theme.icon.pebble && cmd overlay disable com.android.theme.icon.roundedhexagon && cmd overlay disable com.android.theme.icon.roundedrect && cmd overlay disable com.android.theme.icon.square && cmd overlay disable com.android.theme.icon.squircle && cmd overlay disable com.android.theme.icon.taperedrect && cmd overlay disable com.android.theme.icon.teardrop && cmd overlay disable com.android.theme.icon.vessel && cmd overlay disable com.android.theme.icon.leaf");
                                break;

                        }
                    }
                    //Icon shapes options end
                    //Signal Icons theme options begin
                    if (key.equals("signal_icons_theme")){
                        switch(mValueIndex) {
                            case 1:
                                runCommandAction("cmd overlay disable com.tenx.systemui.signalbar_a && cmd overlay disable com.tenx.systemui.wifibar_a && cmd overlay disable com.tenx.systemui.signalbar_b && cmd overlay disable com.tenx.systemui.wifibar_b && cmd overlay disable com.tenx.systemui.signalbar_c && cmd overlay disable com.tenx.systemui.wifibar_c && cmd overlay disable com.tenx.systemui.signalbar_d && cmd overlay disable com.tenx.systemui.wifibar_d && cmd overlay disable com.tenx.systemui.signalbar_e && cmd overlay disable com.tenx.systemui.wifibar_e && cmd overlay disable com.tenx.systemui.signalbar_f && cmd overlay disable com.tenx.systemui.wifibar_f && cmd overlay disable com.tenx.systemui.signalbar_g && cmd overlay disable com.tenx.systemui.wifibar_g && cmd overlay disable com.tenx.systemui.signalbar_h && cmd overlay disable com.tenx.systemui.wifibar_h && cmd overlay enable com.tenx.systemui.signalbar_a && cmd overlay enable com.tenx.systemui.wifibar_a && killall com.typhus.romcontrol && sleep 1 && am start -n \"com.typhus.romcontrol/com.typhus.romcontrol.MainViewActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER");
                                break;
                            case 2:
                                runCommandAction("cmd overlay disable com.tenx.systemui.signalbar_a && cmd overlay disable com.tenx.systemui.wifibar_a && cmd overlay disable com.tenx.systemui.signalbar_b && cmd overlay disable com.tenx.systemui.wifibar_b && cmd overlay disable com.tenx.systemui.signalbar_c && cmd overlay disable com.tenx.systemui.wifibar_c && cmd overlay disable com.tenx.systemui.signalbar_d && cmd overlay disable com.tenx.systemui.wifibar_d && cmd overlay disable com.tenx.systemui.signalbar_e && cmd overlay disable com.tenx.systemui.wifibar_e && cmd overlay disable com.tenx.systemui.signalbar_f && cmd overlay disable com.tenx.systemui.wifibar_f && cmd overlay disable com.tenx.systemui.signalbar_g && cmd overlay disable com.tenx.systemui.wifibar_g && cmd overlay disable com.tenx.systemui.signalbar_h && cmd overlay disable com.tenx.systemui.wifibar_h && cmd overlay enable com.tenx.systemui.signalbar_b && cmd overlay enable com.tenx.systemui.wifibar_b && killall com.typhus.romcontrol && sleep 1 && am start -n \"com.typhus.romcontrol/com.typhus.romcontrol.MainViewActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER");
                                break;
                            case 3:
                                runCommandAction("cmd overlay disable com.tenx.systemui.signalbar_a && cmd overlay disable com.tenx.systemui.wifibar_a && cmd overlay disable com.tenx.systemui.signalbar_b && cmd overlay disable com.tenx.systemui.wifibar_b && cmd overlay disable com.tenx.systemui.signalbar_c && cmd overlay disable com.tenx.systemui.wifibar_c && cmd overlay disable com.tenx.systemui.signalbar_d && cmd overlay disable com.tenx.systemui.wifibar_d && cmd overlay disable com.tenx.systemui.signalbar_e && cmd overlay disable com.tenx.systemui.wifibar_e && cmd overlay disable com.tenx.systemui.signalbar_f && cmd overlay disable com.tenx.systemui.wifibar_f && cmd overlay disable com.tenx.systemui.signalbar_g && cmd overlay disable com.tenx.systemui.wifibar_g && cmd overlay disable com.tenx.systemui.signalbar_h && cmd overlay disable com.tenx.systemui.wifibar_h && cmd overlay enable com.tenx.systemui.signalbar_c && cmd overlay enable com.tenx.systemui.wifibar_c && killall com.typhus.romcontrol && sleep 1 && am start -n \"com.typhus.romcontrol/com.typhus.romcontrol.MainViewActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER");
                            case 4:
                                runCommandAction("cmd overlay disable com.tenx.systemui.signalbar_a && cmd overlay disable com.tenx.systemui.wifibar_a && cmd overlay disable com.tenx.systemui.signalbar_b && cmd overlay disable com.tenx.systemui.wifibar_b && cmd overlay disable com.tenx.systemui.signalbar_c && cmd overlay disable com.tenx.systemui.wifibar_c && cmd overlay disable com.tenx.systemui.signalbar_d && cmd overlay disable com.tenx.systemui.wifibar_d && cmd overlay disable com.tenx.systemui.signalbar_e && cmd overlay disable com.tenx.systemui.wifibar_e && cmd overlay disable com.tenx.systemui.signalbar_f && cmd overlay disable com.tenx.systemui.wifibar_f && cmd overlay disable com.tenx.systemui.signalbar_g && cmd overlay disable com.tenx.systemui.wifibar_g && cmd overlay disable com.tenx.systemui.signalbar_h && cmd overlay disable com.tenx.systemui.wifibar_h && cmd overlay enable com.tenx.systemui.signalbar_d && cmd overlay enable com.tenx.systemui.wifibar_d && killall com.typhus.romcontrol && sleep 1 && am start -n \"com.typhus.romcontrol/com.typhus.romcontrol.MainViewActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER");
                                break;
                            case 5:
                                runCommandAction("cmd overlay disable com.tenx.systemui.signalbar_a && cmd overlay disable com.tenx.systemui.wifibar_a && cmd overlay disable com.tenx.systemui.signalbar_b && cmd overlay disable com.tenx.systemui.wifibar_b && cmd overlay disable com.tenx.systemui.signalbar_c && cmd overlay disable com.tenx.systemui.wifibar_c && cmd overlay disable com.tenx.systemui.signalbar_d && cmd overlay disable com.tenx.systemui.wifibar_d && cmd overlay disable com.tenx.systemui.signalbar_e && cmd overlay disable com.tenx.systemui.wifibar_e && cmd overlay disable com.tenx.systemui.signalbar_f && cmd overlay disable com.tenx.systemui.wifibar_f && cmd overlay disable com.tenx.systemui.signalbar_g && cmd overlay disable com.tenx.systemui.wifibar_g && cmd overlay disable com.tenx.systemui.signalbar_h && cmd overlay disable com.tenx.systemui.wifibar_h && cmd overlay enable com.tenx.systemui.signalbar_e && cmd overlay enable com.tenx.systemui.wifibar_e && killall com.typhus.romcontrol && sleep 1 && am start -n \"com.typhus.romcontrol/com.typhus.romcontrol.MainViewActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER");
                                break;
                            case 6:
                                runCommandAction("cmd overlay disable com.tenx.systemui.signalbar_a && cmd overlay disable com.tenx.systemui.wifibar_a && cmd overlay disable com.tenx.systemui.signalbar_b && cmd overlay disable com.tenx.systemui.wifibar_b && cmd overlay disable com.tenx.systemui.signalbar_c && cmd overlay disable com.tenx.systemui.wifibar_c && cmd overlay disable com.tenx.systemui.signalbar_d && cmd overlay disable com.tenx.systemui.wifibar_d && cmd overlay disable com.tenx.systemui.signalbar_e && cmd overlay disable com.tenx.systemui.wifibar_e && cmd overlay disable com.tenx.systemui.signalbar_f && cmd overlay disable com.tenx.systemui.wifibar_f && cmd overlay disable com.tenx.systemui.signalbar_g && cmd overlay disable com.tenx.systemui.wifibar_g && cmd overlay disable com.tenx.systemui.signalbar_h && cmd overlay disable com.tenx.systemui.wifibar_h && cmd overlay enable com.tenx.systemui.signalbar_f && cmd overlay enable com.tenx.systemui.wifibar_f && killall com.typhus.romcontrol && sleep 1 && am start -n \"com.typhus.romcontrol/com.typhus.romcontrol.MainViewActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER");
                            case 7:
                                runCommandAction("cmd overlay disable com.tenx.systemui.signalbar_a && cmd overlay disable com.tenx.systemui.wifibar_a && cmd overlay disable com.tenx.systemui.signalbar_b && cmd overlay disable com.tenx.systemui.wifibar_b && cmd overlay disable com.tenx.systemui.signalbar_c && cmd overlay disable com.tenx.systemui.wifibar_c && cmd overlay disable com.tenx.systemui.signalbar_d && cmd overlay disable com.tenx.systemui.wifibar_d && cmd overlay disable com.tenx.systemui.signalbar_e && cmd overlay disable com.tenx.systemui.wifibar_e && cmd overlay disable com.tenx.systemui.signalbar_f && cmd overlay disable com.tenx.systemui.wifibar_f && cmd overlay disable com.tenx.systemui.signalbar_g && cmd overlay disable com.tenx.systemui.wifibar_g && cmd overlay disable com.tenx.systemui.signalbar_h && cmd overlay disable com.tenx.systemui.wifibar_h && cmd overlay enable com.tenx.systemui.signalbar_g && cmd overlay enable com.tenx.systemui.wifibar_g && killall com.typhus.romcontrol && sleep 1 && am start -n \"com.typhus.romcontrol/com.typhus.romcontrol.MainViewActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER");
                                break;
                            case 8:
                                runCommandAction("cmd overlay disable com.tenx.systemui.signalbar_a && cmd overlay disable com.tenx.systemui.wifibar_a && cmd overlay disable com.tenx.systemui.signalbar_b && cmd overlay disable com.tenx.systemui.wifibar_b && cmd overlay disable com.tenx.systemui.signalbar_c && cmd overlay disable com.tenx.systemui.wifibar_c && cmd overlay disable com.tenx.systemui.signalbar_d && cmd overlay disable com.tenx.systemui.wifibar_d && cmd overlay disable com.tenx.systemui.signalbar_e && cmd overlay disable com.tenx.systemui.wifibar_e && cmd overlay disable com.tenx.systemui.signalbar_f && cmd overlay disable com.tenx.systemui.wifibar_f && cmd overlay disable com.tenx.systemui.signalbar_g && cmd overlay disable com.tenx.systemui.wifibar_g && cmd overlay disable com.tenx.systemui.signalbar_h && cmd overlay disable com.tenx.systemui.wifibar_h && cmd overlay enable com.tenx.systemui.signalbar_h && cmd overlay enable com.tenx.systemui.wifibar_h && killall com.typhus.romcontrol && sleep 1 && am start -n \"com.typhus.romcontrol/com.typhus.romcontrol.MainViewActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER");
                                break;
                            default:
                                runCommandAction("cmd overlay disable com.tenx.systemui.signalbar_a && cmd overlay disable com.tenx.systemui.wifibar_a && cmd overlay disable com.tenx.systemui.signalbar_b && cmd overlay disable com.tenx.systemui.wifibar_b && cmd overlay disable com.tenx.systemui.signalbar_c && cmd overlay disable com.tenx.systemui.wifibar_c && cmd overlay disable com.tenx.systemui.signalbar_d && cmd overlay disable com.tenx.systemui.wifibar_d && cmd overlay disable com.tenx.systemui.signalbar_e && cmd overlay disable com.tenx.systemui.wifibar_e && cmd overlay disable com.tenx.systemui.signalbar_f && cmd overlay disable com.tenx.systemui.wifibar_f && cmd overlay disable com.tenx.systemui.signalbar_g && cmd overlay disable com.tenx.systemui.wifibar_g && cmd overlay disable com.tenx.systemui.signalbar_h && cmd overlay disable com.tenx.systemui.wifibar_h && killall com.typhus.romcontrol && sleep 1 && am start -n \"com.typhus.romcontrol/com.typhus.romcontrol.MainViewActivity\" -a android.intent.action.MAIN -c android.intent.category.LAUNCHER");
                        }
                    }
                    //Signal Icons theme options end
                    //QS Rows options
                    if (key.equals("qs_rows_number")){
                        switch(mValueIndex) {
                            case 1:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstiles2r");
                                break;
                            case 2:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstiles3r");
                                break;
                            case 3:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstiles4r");
                                break;
                            case 4:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstiles5r");
                                break;
                            default:
                                runCommandAction("cmd overlay enable-exclusive --category com.android.systemui.qstiles2r && cmd overlay disable com.android.systemui.qstiles2r");
                                break;
                        }
                    }
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
