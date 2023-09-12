package com.threethan.launcher.adapter;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import com.threethan.launcher.R;
import com.threethan.launcher.helper.App;
import com.threethan.launcher.helper.Compat;
import com.threethan.launcher.helper.Dialog;
import com.threethan.launcher.helper.Icon;
import com.threethan.launcher.helper.Launch;
import com.threethan.launcher.helper.Settings;
import com.threethan.launcher.launcher.LauncherActivity;
import com.threethan.launcher.lib.ImageLib;
import com.threethan.launcher.support.SettingsManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@SuppressLint("UseSwitchCompatOrMaterialCode")
public class AppsAdapter extends BaseAdapter{
    private static Drawable iconDrawable;
    private static File iconFile;
    private static String packageName;
    private final LauncherActivity launcherActivity;
    private final List<ApplicationInfo> appList;
    private final boolean isEditMode;
    private final boolean showTextLabels;
    public AppsAdapter(LauncherActivity context, boolean editMode, boolean names, List<ApplicationInfo> myApps) {
        launcherActivity = context;
        isEditMode = editMode;
        showTextLabels = names;
        SettingsManager settingsManager = SettingsManager.getInstance(launcherActivity);

        ArrayList<String> sortedSelectedGroups = settingsManager.getAppGroupsSorted(true);
        appList = settingsManager.getInstalledApps(context, sortedSelectedGroups, myApps);
    }

    private static class ViewHolder {
        View view;
        ImageView imageView;
        ImageView imageViewBg;
        TextView textView;
        Button moreButton;
        Button killButton;
        ApplicationInfo app;
    }

    public int getCount() { return appList.size(); }

    public Object getItem(int position) {
        return appList.get(position);
    }

    public long getItemId(int position) {
        return position;
    }

    /** @noinspection deprecation*/
    @SuppressWarnings("unchecked")
    public View getView(int position, View convertView, ViewGroup parent) {

        ViewHolder holder;

        final ApplicationInfo currentApp = appList.get(position);
        LayoutInflater layoutInflater = (LayoutInflater) launcherActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        if (convertView == null) {
            // Create a new ViewHolder and inflate the view
            int layout = App.isBanner(currentApp, launcherActivity) ? R.layout.lv_app_wide : R.layout.lv_app_icon;

            convertView = layoutInflater.inflate(layout, parent, false);
            if (currentApp.packageName == null) return convertView;

            holder = new ViewHolder();
            holder.view = convertView;
            holder.imageView = convertView.findViewById(R.id.imageLabel);
            holder.textView = convertView.findViewById(R.id.textLabel);
            holder.moreButton = convertView.findViewById(R.id.moreButton);
            holder.killButton = convertView.findViewById(R.id.killButton);
            holder.app = currentApp;

            // Set clipToOutline to true on imageView
            convertView.findViewById(R.id.clip).setClipToOutline(true);
            convertView.setTag(holder);
            if (position == 0) launcherActivity.updateGridViewHeights();

        } else holder = (ViewHolder) convertView.getTag();

        // set value into textview
        String name = SettingsManager.getAppLabel(currentApp);
        holder.textView.setVisibility(showTextLabels ? View.VISIBLE : View.INVISIBLE);
        if (showTextLabels) {
            holder.textView.setText(name);
            holder.textView.setTextColor(Color.parseColor(launcherActivity.darkMode ? "#FFFFFF" : "#000000"));
            holder.textView.setShadowLayer(6, 0, 0, Color.parseColor(launcherActivity.darkMode ? "#000000" : "#20FFFFFF"));
        }


        new LoadIconTask().execute(this, currentApp, launcherActivity, holder.imageView, holder.imageViewBg);
        holder.view.post(() -> updateView(holder));

        return convertView;
    }

    private void updateView(ViewHolder holder) {
        if (isEditMode) {
            holder.view.setOnClickListener(view -> {
                boolean selected = launcherActivity.selectApp(holder.app.packageName);
                ValueAnimator an = android.animation.ObjectAnimator.ofFloat(holder.view, "alpha", selected? 0.5F : 1.0F);
                an.setDuration(150);
                an.start();
            });
            holder.view.setOnLongClickListener(view -> {
                showAppDetails(holder.app);
                return true;
            });

        } else {
            holder.view.setOnClickListener(view -> {
                if (Launch.launchApp(launcherActivity, holder.app)) animateOpen(holder);

                boolean isWeb = App.isWebsite(holder.app);
                if (isWeb) holder.killButton.setVisibility(View.VISIBLE);
                shouldAnimateClose = isWeb;
            });
            holder.view.setOnLongClickListener(view -> {
                launcherActivity.setEditMode(true);
                launcherActivity.selectApp(holder.app.packageName);
                return true;
            });
        }

        Runnable periodicUpdate = new Runnable() {
            @Override
            public void run() {
                holder.moreButton.setVisibility(holder.view.isHovered() || holder.moreButton.isHovered() ? View.VISIBLE : View.INVISIBLE);
                boolean selected = launcherActivity.isSelected(holder.app.packageName);
                if (selected != holder.view.getAlpha() < 0.9) {
                    ValueAnimator an = android.animation.ObjectAnimator.ofFloat(holder.view, "alpha", selected ? 0.5F : 1.0F);
                    an.setDuration(150);
                    an.start();
                }

                boolean hovered = holder.view.isHovered() || holder.moreButton.isHovered() || holder.killButton.isHovered();
                holder.moreButton.setVisibility(hovered ? View.VISIBLE : View.INVISIBLE);
                holder.killButton.setBackgroundResource(hovered ? R.drawable.ic_circ_running_stop : R.drawable.ic_running);

                holder.view.postDelayed(this, 250);
            }
        };
        periodicUpdate.run();

        holder.moreButton.setOnClickListener(view -> showAppDetails(holder.app));

        holder.killButton.setVisibility(SettingsManager.getRunning(holder.app.packageName) ? View.VISIBLE : View.GONE);
        holder.killButton.setOnClickListener(view -> {
            SettingsManager.stopRunning(holder.app.packageName);
            view.setVisibility(View.GONE);
        });
    }

    public void onImageSelected(String path, ImageView selectedImageView) {
        Compat.clearIcons(launcherActivity);
        if (path != null) {
            Bitmap bitmap = ImageLib.bitmapFromFile(launcherActivity, new File(path));
            if (bitmap == null) return;
            bitmap = ImageLib.getResizedBitmap(bitmap, 450);
            ImageLib.saveBitmap(bitmap, iconFile);
            selectedImageView.setImageBitmap(bitmap);
        } else {
            selectedImageView.setImageDrawable(iconDrawable);
            Icon.updateIcon(iconFile, packageName, null);
            //No longer sets icon here but that should be fine
        }
    }
    private void showAppDetails(ApplicationInfo currentApp) {
        // set view
        AlertDialog dialog = Dialog.build(launcherActivity, R.layout.dialog_app_details);
        // package name
        ((TextView) dialog.findViewById(R.id.packageName)).setText(currentApp.packageName);
        // info action
        dialog.findViewById(R.id.info).setOnClickListener(view -> App.openInfo(launcherActivity, currentApp.packageName));
        dialog.findViewById(R.id.uninstall).setOnClickListener(view -> {
            App.uninstall(launcherActivity, currentApp.packageName); dialog.dismiss();});

        // toggle launch mode
        final boolean[] launchOut = {SettingsManager.getAppLaunchOut(currentApp.packageName)};
        final Switch launchModeSwitch = dialog.findViewById(R.id.launchModeSwitch);
        final View launchOutButton = dialog.findViewById(R.id.launchOut);
        final View launchModeSection = dialog.findViewById(R.id.launchModeSection);
        final View refreshIconButton = dialog.findViewById(R.id.refreshIconButton);
        final boolean isVr = App.isVirtualReality(currentApp, launcherActivity);
        final boolean isWeb = App.isWebsite(currentApp);

        // load icon
        PackageManager packageManager = launcherActivity.getPackageManager();
        ImageView iconImageView = dialog.findViewById(R.id.appIcon);
        iconImageView.setImageDrawable(Icon.loadIcon(launcherActivity, currentApp, null));

        iconImageView.setClipToOutline(true);
        if (App.isBanner(currentApp, launcherActivity)) iconImageView.getLayoutParams().width = launcherActivity.dp(150);

        iconImageView.setOnClickListener(iconPickerView -> {
            iconDrawable = currentApp.loadIcon(packageManager);
            packageName = currentApp.packageName;

            iconFile = Icon.iconFileForPackage(launcherActivity, currentApp.packageName);
            if (iconFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                iconFile.delete();
            }
            launcherActivity.setSelectedImageView(iconImageView);
            ImageLib.showImagePicker(launcherActivity, Settings.PICK_ICON_CODE);
        });

        dialog.findViewById(R.id.info).setVisibility(isWeb ? View.GONE : View.VISIBLE);
        if (isVr) {
            // VR apps MUST launch out, so just hide the option and replace it with another
            // Websites could theoretically support this option, but it is currently way too buggy
            launchModeSection.setVisibility(View.GONE);
            refreshIconButton.setVisibility(View.VISIBLE);
            launchOutButton.setVisibility(View.GONE);

            refreshIconButton.setOnClickListener(view -> Icon.reloadIcon(launcherActivity, currentApp, iconImageView));
        } else {
            launchModeSection.setVisibility(View.VISIBLE);
            refreshIconButton.setVisibility(View.GONE);
            launchModeSwitch.setChecked(launchOut[0]);
            launchOutButton.setVisibility(View.VISIBLE);
            launchOutButton.setOnClickListener((view) -> {
                SettingsManager.setAppLaunchOut(currentApp.packageName, true);
                Launch.launchApp(launcherActivity, currentApp);
                SettingsManager.setAppLaunchOut(currentApp.packageName, launchOut[0]);
            });

            launchModeSwitch.setOnCheckedChangeListener((sw, value) -> {
                if (!launcherActivity.sharedPreferences.getBoolean(Settings.KEY_SEEN_LAUNCH_OUT_POPUP, false) && value) {
                    launchModeSwitch.setChecked(false); // Revert switch
                    AlertDialog subDialog = Dialog.build(launcherActivity, R.layout.dialog_launch_out_info);
                    subDialog.findViewById(R.id.confirm).setOnClickListener(view -> {
                        launcherActivity.sharedPreferenceEditor
                                .putBoolean(Settings.KEY_SEEN_LAUNCH_OUT_POPUP, true);
                        subDialog.dismiss();
                        SettingsManager.setAppLaunchOut(currentApp.packageName, true);
                        launchOut[0] = SettingsManager.getAppLaunchOut(currentApp.packageName);
                        launchModeSwitch.setChecked(true);
                    });
                    subDialog.findViewById(R.id.cancel).setOnClickListener(view -> {
                        subDialog.dismiss(); // Dismiss without setting
                    });
                } else {
                    SettingsManager.setAppLaunchOut(currentApp.packageName, !launchOut[0]);
                    launchOut[0] = SettingsManager.getAppLaunchOut(currentApp.packageName);
                }
            });
        }

        // set name
        String name = SettingsManager.getAppLabel(currentApp);
        final EditText appNameEditText = dialog.findViewById(R.id.appLabel);
        appNameEditText.setText(name);
        dialog.findViewById(R.id.confirm).setOnClickListener(view -> {
            SettingsManager.setAppLabel(currentApp, appNameEditText.getText().toString());
            dialog.dismiss();
            launcherActivity.refresh();
        });
    }


    // Animation

    private void animateOpen(ViewHolder holder) {

        int[] l = new int[2];
        View clip = holder.view.findViewById(R.id.clip);
        clip.getLocationInWindow(l);
        int w = clip.getWidth();
        int h = clip.getHeight();

        View openAnim = launcherActivity.m.findViewById(R.id.openAnim);
        openAnim.setX(l[0]);
        openAnim.setY(l[1]);
        ViewGroup.LayoutParams layoutParams = new FrameLayout.LayoutParams(w, h);
        openAnim.setLayoutParams(layoutParams);

        openAnim.setVisibility(View.VISIBLE);
        openAnim.setClipToOutline(true);

        ImageView animIcon = openAnim.findViewById(R.id.openIcon);
        ImageView animIconBg = openAnim.findViewById(R.id.openIconBg);
        animIcon.setImageDrawable(holder.imageView.getDrawable());
        animIconBg.setImageDrawable(holder.imageView.getDrawable());
        animIconBg.setAlpha(1f);

        View openProgress = launcherActivity.m.findViewById(R.id.openProgress);
        openProgress.setVisibility(View.VISIBLE);
        openProgress.setAlpha(0f);

        ObjectAnimator aX = ObjectAnimator.ofFloat(openAnim, "ScaleX", 100f);
        ObjectAnimator aY = ObjectAnimator.ofFloat(openAnim, "ScaleY", 100f);
        ObjectAnimator aA = ObjectAnimator.ofFloat(animIcon, "Alpha", 0f);
        ObjectAnimator aP = ObjectAnimator.ofFloat(openProgress, "Alpha", 0.8f);
        aX.setDuration(1000);
        aY.setDuration(1000);
        aA.setDuration(500);
        aP.setDuration(500);
        aP.setStartDelay(1000);
        aX.start();
        aY.start();
        aA.start();
        aP.start();
    }
    public static boolean shouldAnimateClose = false;
    public static boolean animateClose(LauncherActivity launcherActivity) {
        View openAnim = launcherActivity.m.findViewById(R.id.openAnim);
        ImageView animIcon = openAnim.findViewById(R.id.openIcon);

        View openProgress = launcherActivity.m.findViewById(R.id.openProgress);
        openProgress.setVisibility(View.INVISIBLE);

        final boolean rv = (openAnim.getVisibility() == View.VISIBLE);
        if (!AppsAdapter.shouldAnimateClose) {
            openAnim.setVisibility(View.INVISIBLE);
            openAnim.setScaleX(1);
            openAnim.setScaleY(1);
            animIcon.setAlpha(1.0f);
            openAnim.setVisibility(View.INVISIBLE);
        } else {
            // This animation assumes the app already animated open, and therefore does not set icon
            ImageView animIconBg = openAnim.findViewById(R.id.openIconBg);

            openAnim.setScaleX(3f);
            openAnim.setScaleY(3f);
            animIcon.setAlpha(1f);

            ObjectAnimator aX = ObjectAnimator.ofFloat(openAnim, "ScaleX", 1f);
            ObjectAnimator aY = ObjectAnimator.ofFloat(openAnim, "ScaleY", 1f);
            ObjectAnimator aA = ObjectAnimator.ofFloat(animIconBg, "Alpha", 0f);

            aX.setDuration(100);
            aY.setDuration(100);
            aA.setDuration(50);
            aA.setStartDelay(50);
            aA.start();
            aX.start();
            aY.start();

            openAnim.postDelayed(() -> openAnim.setVisibility(View.INVISIBLE), 100);
            shouldAnimateClose = false;
        }
        return rv;
    }
}
