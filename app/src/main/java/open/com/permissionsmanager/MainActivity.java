package open.com.permissionsmanager;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.GridView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.biometric.BiometricPrompt;
import androidx.biometric.BiometricManager;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ApplicationDatabaseChangeListener {
    public static final String APPLICATION_PACKAGE_NAME = "APPLICATION_PACKAGE_NAME";
    private ApplicationsDatabase applicationsDatabase;
    private List<AndroidApplication> warnableApplications, ignoredApplications;
    private GridView listOfApplications_gridView, ignorelistOfApplications_gridView;
    private AppCompatTextView warnableAppsToggle;
    private AppCompatTextView ignoredAppsToggle;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        Executor executor = ContextCompat.getMainExecutor(this);
        applicationsDatabase = ApplicationsDatabase.getApplicationsDatabase(this);
        applicationsDatabase.addApplicationDatabaseChangeListener(this);
        setupListViewsAndToggles();
        scanApplications();
        showSpinner();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MainUtils.setAlarm(this);
        if(MainUtils.areScanResultsOlderThan5Mins(this)){
            scanApplications();
            showSpinner();
        }
    }

    private void scanApplications() {
        if(applicationsDatabase.isScanInProgress())
            return;
        new Thread(){
            @Override
            public void run() {
                applicationsDatabase.updateApplicationsDatabase();
            }
        }.start();
    }

    private void setupListViewsAndToggles() {
        listOfApplications_gridView = (GridView) findViewById(R.id.grid_apps);
        ignorelistOfApplications_gridView = (GridView) findViewById(R.id.grid_ignored_apps);
        warnableAppsToggle = (AppCompatTextView) findViewById(R.id.warnable_apps_toggle);
        ignoredAppsToggle = (AppCompatTextView) findViewById(R.id.ignored_apps_toggle);

        warnableAppsToggle.setOnClickListener(getToggleClickListener(listOfApplications_gridView, warnableAppsToggle));
        ignoredAppsToggle.setOnClickListener(getToggleClickListener(ignorelistOfApplications_gridView, ignoredAppsToggle));

        AdapterView.OnItemClickListener onAppClick = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intentToShowApplicationDetails = new Intent(MainActivity.this, ApplicationDetails.class);
                intentToShowApplicationDetails.putExtra(APPLICATION_PACKAGE_NAME, ((PermissionsApplicationsArrayAdapter) parent.getAdapter()).getItem(position).getPackageName());
                startActivity(intentToShowApplicationDetails);
            }
        };

        listOfApplications_gridView.setOnItemClickListener(onAppClick);

        listOfApplications_gridView.setOnItemLongClickListener(getAppLongClickListener(true));

        listOfApplications_gridView.setAdapter(new PermissionsApplicationsArrayAdapter(MainActivity.this, R.layout.application_info_row));

        ignorelistOfApplications_gridView.setOnItemClickListener(onAppClick);

        ignorelistOfApplications_gridView.setOnItemLongClickListener(getAppLongClickListener(false));
        ignorelistOfApplications_gridView.setAdapter(new PermissionsApplicationsArrayAdapter(MainActivity.this, R.layout.application_info_row));
    }
    @NonNull
    private View.OnClickListener getToggleClickListener(final GridView gridView, final AppCompatTextView appCompatTextView) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleList(gridView, appCompatTextView);
            }
        };
    }
    @NonNull
    private AdapterView.OnItemLongClickListener getAppLongClickListener(final boolean isWarnableAppsList) {
        return new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(final AdapterView<?> parent, View view, final int position, long id) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(isWarnableAppsList ? R.string.add_to_ignore_list : R.string.stop_ignoring)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if(isWarnableAppsList)
                                    applicationsDatabase.addAppToIgnoreList(((PermissionsApplicationsArrayAdapter)parent.getAdapter()).getItem(position));
                                else
                                    applicationsDatabase.removeAppFromIgnoreList(((PermissionsApplicationsArrayAdapter)parent.getAdapter()).getItem(position));
                            }
                        })
                        .setNegativeButton("No", null)
                        .show();
                return true;
            }
        };
    }

    private void toggleList(GridView gridView, AppCompatTextView toggle) {
        if(gridView.getVisibility() == View.VISIBLE){
            gridView.setVisibility(View.GONE);
            toggle.setCompoundDrawablesWithIntrinsicBounds(null, null, ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_keyboard_arrow_down_24dp), null);
        }
        else{
            gridView.setVisibility(View.VISIBLE);
            toggle.setCompoundDrawablesWithIntrinsicBounds(null, null, ContextCompat.getDrawable(MainActivity.this, R.drawable.ic_keyboard_arrow_up_24dp), null);
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity_menu, menu);
        menu.findItem(R.id.refresh).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                updateApplicationsList();
                return true;
            }
        });
        return super.onCreateOptionsMenu(menu);
    }

    private void updateApplicationsList() {
        showSpinner();
        new AsyncTask<Void, Void, Void>(){

            @Override
            protected Void doInBackground(Void... params) {
                applicationsDatabase.updateApplicationsDatabase();
                return null;
            }
        }.execute();
    }
    private void showSpinner() {
        findViewById(R.id.progressbar).setVisibility(View.VISIBLE);
        warnableAppsToggle.setVisibility(View.GONE);
        ignoredAppsToggle.setVisibility(View.GONE);
        listOfApplications_gridView.setVisibility(View.GONE);
        ignorelistOfApplications_gridView.setVisibility(View.GONE);
    }

    private void hideSpinner() {
        findViewById(R.id.progressbar).setVisibility(View.GONE);
        warnableAppsToggle.setVisibility(View.VISIBLE);
        ignoredAppsToggle.setVisibility(View.VISIBLE);
        listOfApplications_gridView.setVisibility(View.VISIBLE);
        ignorelistOfApplications_gridView.setVisibility(View.VISIBLE);


    }

    @Override
    protected void onDestroy() {
        applicationsDatabase.removeApplicationDatabaseChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void applicationPermissionsUpdated(final AndroidApplication androidApplication) {
        final PermissionsApplicationsArrayAdapter warnableApplicationsListAdapter = (PermissionsApplicationsArrayAdapter) listOfApplications_gridView.getAdapter();
        final PermissionsApplicationsArrayAdapter ignoredApplicationsListAdapter = (PermissionsApplicationsArrayAdapter) ignorelistOfApplications_gridView.getAdapter();
        if(warnableApplicationsListAdapter == null && ignoredApplicationsListAdapter == null)
            return;
        if(androidApplication.isIgnoredTemporarily())
            updateAdapterWithNewApplication(androidApplication, ignoredApplicationsListAdapter);
        else
            updateAdapterWithNewApplication(androidApplication, warnableApplicationsListAdapter);
    }
    private void updateAdapterWithNewApplication(final AndroidApplication androidApplication, final PermissionsApplicationsArrayAdapter listAdapter) {
        final int indexOfApplication = listAdapter.getPosition(androidApplication);
        if(indexOfApplication == -1)
            return;
        warnableApplications.remove(indexOfApplication);
        warnableApplications.add(indexOfApplication, androidApplication);
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                listAdapter.replaceItemAt(indexOfApplication, androidApplication);
                listAdapter.notifyDataSetChanged();
            }
        });
    }

    private void updateView() {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showSpinner();
                PermissionsApplicationsArrayAdapter warnableAppsAdapter = (PermissionsApplicationsArrayAdapter) listOfApplications_gridView.getAdapter();
                PermissionsApplicationsArrayAdapter ignoredAppsAdapter = (PermissionsApplicationsArrayAdapter) ignorelistOfApplications_gridView.getAdapter();
                updateListWithApplications(warnableAppsAdapter, warnableApplications);
                updateListWithApplications(ignoredAppsAdapter, ignoredApplications);

                hideSpinner();
            }
        });
    }
    private void updateListWithApplications(PermissionsApplicationsArrayAdapter adapter, List<AndroidApplication> androidApplications) {
        adapter.addAllApplications(androidApplications);
        adapter.notifyDataSetChanged();
    }
    @Override
    public void applicationsDatabaseUpdated(List<AndroidApplication> androidApplications) {
        PermissionsApplicationsArrayAdapter warnableAppsListAdapter = (PermissionsApplicationsArrayAdapter) listOfApplications_gridView.getAdapter();
        PermissionsApplicationsArrayAdapter ignoredAppsListAdapter = (PermissionsApplicationsArrayAdapter) listOfApplications_gridView.getAdapter();
        if(warnableAppsListAdapter == null && ignoredAppsListAdapter == null)
            return;
        MainUtils.sort(androidApplications);
        warnableApplications = new ArrayList<>();
        ignoredApplications = new ArrayList<>();
        for(AndroidApplication application: androidApplications){
            if(application.isIgnoredTemporarily())
                ignoredApplications.add(application);
            else
                warnableApplications.add(application);
        }
        updateView();
    }
    @Override
    public void applicationAddedToIgnoreList(final AndroidApplication application) {
        warnableApplications.remove(application);
        ignoredApplications.add(application);
        updateView();
    }
    @Override
    public void applicationRemovedFromIgnoredList(final AndroidApplication application) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                warnableApplications.add(application);
                ignoredApplications.remove(application);
                updateView();
            }
        });
    }
}
