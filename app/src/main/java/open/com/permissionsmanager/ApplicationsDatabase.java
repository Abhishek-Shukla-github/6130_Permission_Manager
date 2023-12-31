package open.com.permissionsmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;


public class ApplicationsDatabase {
    private final static int TASK_RETURN_A_COPY = 1;
    private static final int TASK_REPLACE = 2;
    public static final String SHARED_PREF_KEY_TEMPORARILY_IGNORED_APPS = "IGNORED_APPS";
    public static final String SHARED_PREF_KEY_DUMMY = "DUMMY";
    public static final String AOSP_APPS_PREFIX = "com.android.";
    private List<AndroidApplication> applications = new ArrayList<>();
    private Context context;
    private SharedPreferences permissionsManagerSharedPreferences;
    private static ApplicationsDatabase applicationsDatabase;
    private List<ApplicationDatabaseChangeListener> applicationDatabaseChangeListeners;
    private boolean scanInProgress = false;

    private ApplicationsDatabase(Context context) {
        this.context = context;
        permissionsManagerSharedPreferences = MainUtils.getSharedPreferences(context);
        applicationDatabaseChangeListeners = new ArrayList<>(3);
        updateApplicationsDatabaseAsync();
    }

    private void updateApplicationsDatabaseAsync() {
        scanInProgress = true;
        new Thread() {
            @Override
            public void run() {
                updateApplicationsDatabase();
            }
        }.start();
    }

    public boolean isScanInProgress() {
        return scanInProgress;
    }

    public synchronized static ApplicationsDatabase getApplicationsDatabase(Context context) {
        if (applicationsDatabase == null)
            applicationsDatabase = new ApplicationsDatabase(context);
        return applicationsDatabase;
    }

    private synchronized List<AndroidApplication> performSynchronizedTask(int task, List<AndroidApplication> newApplicationsList) {
        switch (task) {
            case TASK_RETURN_A_COPY:
                return new ArrayList<>(applications);
            case TASK_REPLACE:
                return applications = newApplicationsList;
        }
        throw new RuntimeException("No task with id " + task + " found");
    }

    public List<AndroidApplication> getACopyOfApplications() {
        return performSynchronizedTask(TASK_RETURN_A_COPY, null);
    }

    public void updateApplicationsDatabase() {
        scanInProgress = true;
        ArrayList<AndroidApplication> newApplicationsList = new ArrayList<>();
        PackageManager pm = context.getPackageManager();
        AndroidApplication androidApplication;
        List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        Set<String> ignoredPermissionsForAllApps = getIgnoredPermissionsForAllApps();
        Set<String> temporarilyIgnoredApps = getIgnoredAppsList();

        for (ApplicationInfo applicationInfo : packages) {
            String packageName = applicationInfo.packageName;
            //System.out.println("Processing App: " + packageName + " (System App: " + ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) + ")");

            if (!applicationInfo.enabled || (applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || packageName.startsWith(AOSP_APPS_PREFIX)) {
                //System.out.println("Skipped App: " + packageName);
                continue;  // Skip system apps and AOSP apps
            }

            // Rest of the code for user-installed apps
            //System.out.println("User Installed App: " + packageName);

            try {
                androidApplication = createAndroidApplication(pm, applicationInfo, temporarilyIgnoredApps, ignoredPermissionsForAllApps);
                if (androidApplication.getWarnablePermissions().size() == 0) {
                    System.out.println("App with no warnable permissions: " + packageName);
                    continue;
                }
                newApplicationsList.add(androidApplication);
            } catch (PackageManager.NameNotFoundException e) {
                System.out.println("Name not found for package " + packageName + ", skipping it");
            }
        }
        performSynchronizedTask(TASK_REPLACE, newApplicationsList);
        scanInProgress = false;
        MainUtils.updateLastScanTime(context);
        for (ApplicationDatabaseChangeListener applicationDatabaseChangeListener : applicationDatabaseChangeListeners)
            applicationDatabaseChangeListener.applicationsDatabaseUpdated(performSynchronizedTask(TASK_RETURN_A_COPY, applications));
    }

    @NonNull
    private AndroidApplication createAndroidApplication(PackageManager pm, ApplicationInfo applicationInfo, Set<String> temporarilyIgnoredApps, Set<String> ignoredPermissionsForAllApps) throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo;
        packageInfo = pm.getPackageInfo(applicationInfo.packageName, PackageManager.GET_PERMISSIONS);
        List<String> nonwarnablePermission = new ArrayList<>();
        List<String> warnablePermissions = new ArrayList<>(3);
        Set<String> appSpecificIgnoreList;

        int dangerousThreshold = 1;

        if (packageInfo.requestedPermissions != null) {
            appSpecificIgnoreList = getAppSpecificIgnoreList(applicationInfo.packageName);
            for (String permission : packageInfo.requestedPermissions) {
                if (pm.checkPermission(permission, packageInfo.packageName) == PackageManager.PERMISSION_GRANTED) {
                    if (isDangerous(permission, pm) && !ignoredPermissionsForAllApps.contains(permission) && !appSpecificIgnoreList.contains(permission))
                        warnablePermissions.add(permission);
                    else
                        nonwarnablePermission.add(permission);
                }
            }
        }
        System.out.println(applicationInfo.packageName);
        // Check if the app is WhatsApp and set a specific threshold
        if (applicationInfo.packageName.equals("com.whatsapp")) {
            dangerousThreshold = 10; // Set your specific threshold for WhatsApp
        } else {
            // For other apps, set a random threshold between 1 to 5
            Random random = new Random();
            dangerousThreshold = random.nextInt(5) + 1;
        }

        return new AndroidApplication.Builder(packageInfo.packageName)
                .withName(getApplicationName(pm, applicationInfo))
                .withNonWarnablePermissions(nonwarnablePermission)
                .withWarnablePermissions(warnablePermissions)
                .withIcon(pm.getApplicationIcon(packageInfo.packageName))
                .withIgnoredTemporarily(temporarilyIgnoredApps.contains(packageInfo.packageName))
                .withDangerousThreshold(dangerousThreshold)
                .build();
    }

    @NonNull
    private Set<String> getAppSpecificIgnoreList(String packageName) {
        return permissionsManagerSharedPreferences.getStringSet(packageName, new HashSet<String>(0));
    }

    private boolean isDangerous(String permission, PackageManager pm) throws PackageManager.NameNotFoundException {
        return pm.getPermissionInfo(permission, PackageManager.GET_META_DATA).protectionLevel == PermissionInfo.PROTECTION_DANGEROUS;
    }

    private String getApplicationName(PackageManager pm, ApplicationInfo applicationInfo) {
        try {
            return pm.getApplicationLabel(applicationInfo).toString();
        } catch (Exception e) {
            System.out.println("This application has no name hence using its package name" + applicationInfo.packageName);
            return applicationInfo.packageName;
        }
    }


    public Set<String> getIgnoredPermissionsForAllApps() {
        return permissionsManagerSharedPreferences.getStringSet(context.getString(R.string.allowed_permissions), new HashSet<String>(0));
    }

    public void ignorePermissionForAllApps(String permission) {
        Set<String> ignoredPermissionsForAllApps = getIgnoredPermissionsForAllApps();
        ignoredPermissionsForAllApps.add(permission);
        permissionsManagerSharedPreferences
                .edit()
                .putInt(SHARED_PREF_KEY_DUMMY, new Random().nextInt())
                .putStringSet(context.getString(R.string.allowed_permissions), ignoredPermissionsForAllApps)
                .apply();
        updateApplicationsDatabase();
    }

    public void unignorePermissionForAllApps(String permission) {//TODO: refactor this block as this looks quite similar to above
        Set<String> ignoredPermissionsForAllApps = getIgnoredPermissionsForAllApps();
        ignoredPermissionsForAllApps.remove(permission);
        permissionsManagerSharedPreferences
                .edit()
                .putInt(SHARED_PREF_KEY_DUMMY, new Random().nextInt())
                .putStringSet(context.getString(R.string.allowed_permissions), ignoredPermissionsForAllApps)
                .apply();
        updateApplicationsDatabase();
    }

    public void ignorePermissionForSpecificApp(String packageName, String permission) {
        int indexOfApp = applications.indexOf(new AndroidApplication(packageName));
        if (indexOfApp == -1)
            return;
        AndroidApplication application = applications.get(indexOfApp);
        List<String> warnablePermissions = application.getWarnablePermissions();
        if (!warnablePermissions.contains(permission))
            return;
        warnablePermissions.remove(permission);
        application.getNonwarnablePermissions().add(permission);


        Set<String> ignoredPermissionsForGivenApp = getAppSpecificIgnoreList(packageName);
        ignoredPermissionsForGivenApp.add(permission);

        permissionsManagerSharedPreferences
                .edit()
                .putInt(SHARED_PREF_KEY_DUMMY, new Random().nextInt())
                .putStringSet(packageName, ignoredPermissionsForGivenApp)
                .apply();

        for (ApplicationDatabaseChangeListener applicationDatabaseChangeListener : applicationDatabaseChangeListeners)
            applicationDatabaseChangeListener.applicationPermissionsUpdated(application);
    }

    public void unignorePermissionForSpecificApp(String packageName, String permission) {//TODO: refactor this block as this looks quite similar to above
        int indexOfApp = applications.indexOf(new AndroidApplication(packageName));
        if (indexOfApp == -1)
            return;
        AndroidApplication application = applications.get(indexOfApp);
        List<String> warnablePermissions = application.getWarnablePermissions();
        if (warnablePermissions.contains(permission))
            return;
        warnablePermissions.add(permission);
        application.getNonwarnablePermissions().remove(permission);

        Set<String> ignoredPermissionsForGivenApp = getAppSpecificIgnoreList(packageName);
        ignoredPermissionsForGivenApp.remove(permission);

        permissionsManagerSharedPreferences
                .edit()
                .putInt(SHARED_PREF_KEY_DUMMY, new Random().nextInt())
                .putStringSet(packageName, ignoredPermissionsForGivenApp)
                .apply();

        for (ApplicationDatabaseChangeListener applicationDatabaseChangeListener : applicationDatabaseChangeListeners)
            applicationDatabaseChangeListener.applicationPermissionsUpdated(application);
    }


    public void addApplicationDatabaseChangeListener(ApplicationDatabaseChangeListener applicationDatabaseChangeListener) {
        applicationDatabaseChangeListeners.add(applicationDatabaseChangeListener);
    }

    public void removeApplicationDatabaseChangeListener(ApplicationDatabaseChangeListener applicationDatabaseChangeListener) {
        applicationDatabaseChangeListeners.remove(applicationDatabaseChangeListener);
    }

    public AndroidApplication getApplication(String packageName) {
        if (packageName == null)
            return null;
        int indexOfApplication = applications.indexOf(new AndroidApplication(packageName));
        if (indexOfApplication == -1)
            return null;
        return applications.get(indexOfApplication);
    }

    public void addAppToIgnoreList(AndroidApplication androidApplication) {
        if (!applications.contains(androidApplication))
            return;
        Set<String> ignoredApps = permissionsManagerSharedPreferences.getStringSet(SHARED_PREF_KEY_TEMPORARILY_IGNORED_APPS, new HashSet<String>(1));
        ignoredApps.add(androidApplication.getPackageName());
        permissionsManagerSharedPreferences.edit()
                .putInt(SHARED_PREF_KEY_DUMMY, new Random().nextInt())
                .putStringSet(SHARED_PREF_KEY_TEMPORARILY_IGNORED_APPS, ignoredApps)
                .apply();
        //android 19 has issue saving hashset, so we have to save something random with it
        AndroidApplication ignoredApplication = createACopyOfAndroidApplicationButIgnoredFlag(true, androidApplication);
        applications.remove(ignoredApplication);
        applications.add(ignoredApplication);
        for(ApplicationDatabaseChangeListener applicationDatabaseChangeListener : applicationDatabaseChangeListeners)
            applicationDatabaseChangeListener.applicationAddedToIgnoreList(ignoredApplication);
    }

    private AndroidApplication createACopyOfAndroidApplicationButIgnoredFlag(boolean ignored, AndroidApplication androidApplication) {
        return new AndroidApplication.Builder(androidApplication.getPackageName())
                .withIgnoredTemporarily(ignored)
                .withIcon(androidApplication.getIcon())
                .withName(androidApplication.getName())
                .withNonWarnablePermissions(androidApplication.getNonwarnablePermissions())
                .withWarnablePermissions(androidApplication.getWarnablePermissions())
                .build();
    }

    public void removeAppFromIgnoreList(AndroidApplication androidApplication) {
        if (!applications.contains(androidApplication))
            return;
        Set<String> ignored_apps = permissionsManagerSharedPreferences.getStringSet(SHARED_PREF_KEY_TEMPORARILY_IGNORED_APPS, new HashSet<String>(1));
        ignored_apps.remove(androidApplication.getPackageName());
        permissionsManagerSharedPreferences.edit()
                .putInt(SHARED_PREF_KEY_DUMMY, new Random().nextInt())
                .putStringSet(SHARED_PREF_KEY_TEMPORARILY_IGNORED_APPS, ignored_apps)
                .apply();
        AndroidApplication unignoredApplication = createACopyOfAndroidApplicationButIgnoredFlag(false, androidApplication);
        applications.remove(unignoredApplication);
        applications.add(unignoredApplication);
        for(ApplicationDatabaseChangeListener applicationDatabaseChangeListener : applicationDatabaseChangeListeners)
            applicationDatabaseChangeListener.applicationRemovedFromIgnoredList(unignoredApplication);
    }

    public Set<String> getIgnoredAppsList(){
        return permissionsManagerSharedPreferences.getStringSet(SHARED_PREF_KEY_TEMPORARILY_IGNORED_APPS, new HashSet<String>(0));
    }
}