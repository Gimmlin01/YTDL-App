package de.yoxcu.ytdl;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static String TAG = "MainActivity";
    private static int UPDATE_LIMIT = 20;

    private Context context;
    private List<Download> downloadList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearLayoutManager mLayoutManager;
    private DownloadAdapter mAdapter;
    private boolean readyToLoad = true;
    private SharedPreferences prefs;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;
        prefs = getSharedPreferences("de.yoxcu.ytdl", MODE_PRIVATE);
        setContentView(R.layout.activity_main);
        perm();
        initChannels();
        checkForUpdates();

        RecyclerView recyclerView = findViewById(R.id.my_recycler_view);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                downloadList.clear();
                updateMovieData(UPDATE_LIMIT,0);
            }
        });
        mAdapter = new DownloadAdapter(downloadList, new DownloadAdapter.CustomItemClickListener() {
            @Override
            public void onItemClick(View v, int position) {
                Download dl = downloadList.get(position);
                Intent intent = new Intent(MainActivity.this, DownloadService.class);
                intent.putExtra("download", dl.toJson());
                intent.setAction(DownloadService.action_startDownload);
                startService(intent);
                // do what ever you want to do with it
            }
        });
        mLayoutManager = new LinearLayoutManager(getApplicationContext());

        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setHasFixedSize(true);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dy > 0) //check for scroll down
                {

                    int pastVisiblesItems, visibleItemCount, totalItemCount;
                    visibleItemCount = mLayoutManager.getChildCount();
                    totalItemCount = mLayoutManager.getItemCount();
                    pastVisiblesItems = mLayoutManager.findFirstVisibleItemPosition();


                    if ((visibleItemCount + pastVisiblesItems) >= totalItemCount) {
                        recyclerView.stopScroll();
                        if (readyToLoad) {
                            readyToLoad = false;
                            Log.v("...", "last element of recyclerview !");
                            updateMovieData(UPDATE_LIMIT, totalItemCount);
                        }
                    }
                }
            }
        });

        recyclerView.setAdapter(mAdapter);

    }

    @Override
    public void onResume() {
        super.onResume();
        downloadList.clear();
        updateMovieData(UPDATE_LIMIT, 0);
    }


    public void perm() {
        if (Build.VERSION.SDK_INT >= 23) {

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                    AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
                    alertBuilder.setCancelable(true);
                    alertBuilder.setTitle("YTDL benötigt die Berechtigung für den Speicher");
                    alertBuilder.setMessage("YTDL soll ja die heruntergeladen Lieder speichern!");
                    alertBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
                        }
                    });
                    AlertDialog alert = alertBuilder.create();
                    alert.show();
                } else {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 0);
                }
            }
        }
    }

    public void initChannels() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel("default",
                "Download Progress",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("Download Progress");
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        ;
        nm.createNotificationChannel(channel);
    }

    private void updateMovieData(final int limit, final int offset) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            swipeRefreshLayout.setRefreshing(true);
                        }
                    });
                    String json = HttpHandler.getFromServer(String.format("status?limit=%s&offset=%s", limit, offset), context);
                    ArrayList<Download> downloads = Download.dlListFromJson(json);
                    if (downloads != null) {
                        downloadList.addAll(downloads);
                    }
                } catch (IOException e) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, getString(R.string.warning_Timeout), Toast.LENGTH_SHORT).show();
                        }
                    });
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            swipeRefreshLayout.setRefreshing(false);
                            mAdapter.notifyDataSetChanged();
                            readyToLoad = true;
                        }
                    });
                }

            }
        });
        thread.start();
    }

    public void checkForUpdates(){
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG,"Checking for Updates");
                    String json = HttpHandler.get("update", "").getJson();
                    final Double newestVersion = Double.parseDouble(json);
                    if (newestVersion>Double.parseDouble(BuildConfig.VERSION_NAME)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(context);
                                alertBuilder.setCancelable(true);
                                alertBuilder.setTitle("Neues Update");
                                alertBuilder.setMessage("Es ist ein neues Update verfügbar!");
                                alertBuilder.setPositiveButton("Herunerladen", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Download dl = new Download("Dummy", "mp3");
                                        dl.setDl_id(0);
                                        dl.setDownloadUrl(String.format("update/%s",newestVersion));
                                        dl.setStatus(100.0);
                                        dl.setFilename("YTDL-v"+String.valueOf(newestVersion)+".apk");
                                        Intent intent = new Intent(MainActivity.this, DownloadService.class);
                                        intent.putExtra("download", dl.toJson());
                                        intent.setAction(DownloadService.action_startDownload);
                                        startService(intent);
                                    }
                                });
                                AlertDialog alert = alertBuilder.create();
                                alert.show();
                            }
                        });

                    }else{
                        Log.d(TAG,"up to date");
                    }
                } catch (IOException e) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(context, getString(R.string.warning_Timeout), Toast.LENGTH_SHORT).show();
                        }
                    });
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                    }
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
        thread.start();
    }

    public void onDownloadButtonClick(View view) {

        EditText editText = findViewById(R.id.editText);
        String url = editText.getText().toString();
        if (HttpHandler.urlIsValid(url)) {
            Download dl = new Download(url, "mp3");
            Intent intent = new Intent(this, DownloadService.class);
            intent.putExtra("download", dl.toJson());
            intent.setAction(DownloadService.action_startConvert);
            startService(intent);
        } else {
            Toast.makeText(this, "Gültige URL eingeben!", Toast.LENGTH_SHORT).show();
        }
    }


}
