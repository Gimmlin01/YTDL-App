package de.yoxcu.ytdl;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import javax.net.ssl.SSLException;

/**
 * Created by yoxcu on 21.03.18.
 */

public class DownloadService extends Service {
    public String TAG = "DownloadService";

    private static final int MESSAGE_START_CONVERT = 1;
    private static final int MESSAGE_START_LISTEN = 2;
    private static final int MESSAGE_START_DOWNLOAD = 3;

    private static final int MESSAGE_FINISH_TASK = 11;
    private static final int MESSAGE_STOP_THREAD = 12;


    private static final int MESSAGE_STOP_DOWNLOAD = 21;


    public static final String action_startConvert = "de.yoxcu.ytd.intent.startConvert";
    public static final String action_startListen = "de.yoxcu.ytd.intent.startListen";
    public static final String action_startDownload = "de.yoxcu.ytd.intent.startDownload";
    public static final String action_stopDownload = "de.yoxcu.ytd.intent.stopDownload";

    private Context context;
    private NotificationManagerCompat notificationManager;
    private ServiceHandler mServiceHandler;
    private HandlerThread handlerThread;
    private SharedPreferences prefs;
    private volatile ArrayList<Download> downloads;
    private volatile ArrayList<Thread> threads;
    private volatile Boolean gettingStarted;

    // Handler that receives messages from the thread
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.d(TAG, "getting Message: "+msg.what);
            Download dl = (Download) msg.obj;
            Thread thread=null;
            switch (msg.what) {
                case MESSAGE_START_DOWNLOAD: {
                    if (dl.getDl_id() >= 0 && dl.getStatus() >= 100) {
                        thread = getDownloadThread(dl);
                    } else {
                        Log.d(TAG, "Download not started");
                    }
                    break;
                }
                case MESSAGE_START_CONVERT: {
                    if (dl.getDl_id() == -1 && dl.getUrl() != null) {
                        thread = getConvertThread(dl);
                    } else {
                        Log.d(TAG, "Convert not started");
                    }
                    break;
                }
                case MESSAGE_START_LISTEN: {
                    if (dl.getDl_id() >= 0) {
                        thread = getListenThread(dl);
                    } else {
                        Log.d(TAG, "Listen not started");
                    }
                    break;
                }case MESSAGE_FINISH_TASK: {
                    Log.d(TAG, "Task finished");
                    downloads.remove(dl);
                    return;
                }
                case MESSAGE_STOP_DOWNLOAD: {
                    Log.d(TAG, "stopping Download");
                    for (Thread t : threads) {
                        if (t.getId() == dl.getThreadId()) {
                            t.interrupt();
                            try {
                                t.join();
                                Log.d(TAG, "stopped");
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            notificationManager.cancel(dl.getNot_id());
                            break;
                        }
                    }
                    for (Download dl1 :downloads){
                        if (dl1.getDl_id()==dl.getDl_id()){
                            downloads.remove(dl1);
                            break;
                        }
                    }
                    return;
                }
                default: {
                    Log.d(TAG, "Unknown Message");
                    return;
                }
            }
            if (downloads.contains(dl)) {
                Log.d(TAG, "Download already in in Progress");
                return;
            }
            if (HttpHandler.haveNetworkConnection(context)) {
                if (thread!=null) {
                    thread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    thread.start();
                    threads.add(thread);
                    dl.setThreadId(thread.getId());
                    downloads.add(dl);
                }
            } else {
                handleError(dl, getString(R.string.warning_NoInternet));
                showToast("Kein Internet");
                tryStopService(dl.getStartId());
            }

        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        // We don't provide binding, so return null
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Download Service Created");
        context = this;
        downloads = new ArrayList<>();
        threads = new ArrayList<>();
        notificationManager = NotificationManagerCompat.from(context);
        prefs = getSharedPreferences("de.yoxcu.ytdl", MODE_PRIVATE);
        handlerThread = new HandlerThread("DownloadHandler");
        handlerThread.setPriority(Process.THREAD_PRIORITY_BACKGROUND);
        handlerThread.start();
        // Get the HandlerThread's Looper and use it for our Handler
        Looper mServiceLooper = handlerThread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public void onDestroy() {
        for (Thread t : threads) {
            t.interrupt();
            try {
                t.join(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        for (Download d : downloads){
            handleError(d,"Download von System abgebrochen");
        }
        handlerThread.quit();
        Log.d(TAG, "Download Service Destroyed");
    }

    @Override
    public void onTaskRemoved(Intent rootintent){
        Log.d(TAG,"about to get killed");

        for (Thread t : threads) {
            t.interrupt();
            try {
                t.join(5000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        for (Download d : downloads){
            Log.d(TAG,d.getFilename());
            handleError(d,"Download Abgebrochen");
        }
        handlerThread.quit();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        gettingStarted=true;
        Log.d(TAG, "onStartCommand Called");
        if (intent != null && !intent.getBooleanExtra("used", false)) {
            String json = intent.getStringExtra("download");
            intent.putExtra("used", true);
            if (json != null) {
                Download dl = Download.fromJson(json);
                dl.setStartId(startId);
                Message msg = mServiceHandler.obtainMessage();
                msg.obj = dl;
                if (intent.getAction() != null) {
                    switch (intent.getAction()) {
                        case action_startConvert: {
                            showToast("Vorbereitung gestartet");
                            msg.what = MESSAGE_START_CONVERT;
                            break;
                        }
                        case action_startDownload: {
                            showToast("Download gestartet");
                            msg.what = MESSAGE_START_DOWNLOAD;
                            break;
                        }
                        case action_startListen: {
                            msg.what = MESSAGE_START_LISTEN;
                            break;
                        }
                        case action_stopDownload: {
                            msg.what = MESSAGE_STOP_DOWNLOAD;
                            break;
                        }
                        default: {
                            Log.d(TAG, "Unknown Action");
                        }
                    }
                }
                Log.d(TAG, "Sending Message: "+msg.what);
                mServiceHandler.sendMessage(msg);
                gettingStarted=false;
                return START_STICKY;
            } else {
                Log.d(TAG, "Kein song Json");
                tryStopService(startId);
            }
        } else {
            Log.d(TAG, "Empty or Used Intent");
            tryStopService(startId);
        }
        gettingStarted=false;
        return START_STICKY;
    }


    private void tryStopService(int id) {
        Log.d(TAG, "Trying to stop service");
        Boolean hasStartMsg = mServiceHandler.hasMessages(MESSAGE_START_CONVERT)||mServiceHandler.hasMessages(MESSAGE_START_DOWNLOAD)||mServiceHandler.hasMessages(MESSAGE_START_LISTEN);
        if (threads.size() == 0&& !gettingStarted && !hasStartMsg) {
            stopSelf();
            Log.d(TAG, "Service stopped");
        } else {
            Log.d(TAG, "Still downloads in queue:");
            for (Download dl :downloads){
                Log.d(TAG,dl.toJson());
            }

            Log.d(TAG, "Still threads running:");
            for (Thread t :threads){
                Log.d(TAG,String.valueOf(t.getId()));
            }
        }
    }


    public void restartService(Download dl, String action) {
        Intent intent = new Intent(DownloadService.this, DownloadService.class);
        intent.putExtra("download", dl.toJson());
        intent.setAction(action);
        startService(intent);
    }

    public Thread getConvertThread(final Download dl) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {

                    Intent delintent = new Intent(context, context.getClass());
                    delintent.putExtra("download", dl.toJson());
                    delintent.setAction(action_stopDownload);
                    PendingIntent deleteIntent = PendingIntent.getService(context, 0, delintent, PendingIntent.FLAG_UPDATE_CURRENT);
                    Notification n = new NotificationCompat.Builder(DownloadService.this, "default")
                            .setContentTitle(getString(R.string.notification_Title_Convert))
                            .setSmallIcon(R.drawable.download_icon_anim)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setProgress(100, 0, true)
                            .setContentText("verbinde...")
                            .setDeleteIntent(deleteIntent)
                            .build();
                    notificationManager.notify(dl.getNot_id(), n);
                    String responseJson = HttpHandler.postToServer("downloads", dl.toServerJson(), context);
                    dl.updateFromJson(responseJson);
                    sendMsg(MESSAGE_FINISH_TASK,dl);
                    //restartService(dl, action_startListen);
                    sendMsg(MESSAGE_START_LISTEN,dl);
                } catch (HttpHandler.HttpResponseException e) {
                    handleError(dl, getString(R.string.warning_HttpException));
                    e.printStackTrace();
                } catch (IOException e) {
                    handleError(dl, "Server Offline");
                    e.printStackTrace();
                } catch (Exception e) {
                    handleError(dl, "Unbekannter Fehler");
                    e.printStackTrace();
                }finally {
                    finishThread(dl.getThreadId());
                }
            }
        });
        return thread;
    }

    public Thread getListenThread(final Download dl) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d("ConvertService", String.format("Listening to id: %d", dl.getDl_id()));

                    Intent delintent = new Intent(context, context.getClass());
                    delintent.putExtra("download", dl.toJson());
                    delintent.setAction(action_stopDownload);
                    PendingIntent deleteIntent = PendingIntent.getService(context, 0, delintent, PendingIntent.FLAG_UPDATE_CURRENT);
                    double prog = 0;
                    while (prog < 100 && !Thread.currentThread().isInterrupted()) {
                        try {
                            String responseJson = HttpHandler.getFromServer("status/" + dl.getDl_id(), context);
                            dl.updateFromJson(responseJson);
                            prog = dl.getStatus();
                            Log.d("ConvertService", String.valueOf(prog));
                            Notification n = new NotificationCompat.Builder(DownloadService.this, "default")
                                    .setContentTitle(getString(R.string.notification_Title_Convert))
                                    .setContentText(String.format("%.2f%s", prog, "%"))
                                    .setProgress(100, (int) prog, false)
                                    .setSmallIcon(R.drawable.download_icon_anim)
                                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                                    .setDeleteIntent(deleteIntent)
                                    .build();
                            notificationManager.notify(dl.getNot_id(), n);
                            if (prog < 0 || prog >= 100) {
                                break;
                            }
                        } catch (SSLException e) {
                            handleError(dl, "Es konnte keine Sichere verbindung hergestellt werden.");
                            e.printStackTrace();
                            break;
                        } catch (HttpHandler.HttpResponseException e) {
                            handleError(dl, getString(R.string.warning_HttpException));
                            e.printStackTrace();
                            break;
                        }catch (IOException e) {
                            Log.d("ConvertService", "IOException");
                            e.printStackTrace();
                            if (!HttpHandler.haveNetworkConnection(context)) {
                                Notification n = new NotificationCompat.Builder(DownloadService.this, "default")
                                        .setContentTitle(getString(R.string.notification_Title_Convert))
                                        .setContentText("Kein Internet")
                                        .setProgress(0, 0, true)
                                        .setSmallIcon(R.drawable.ic_download_frame00)
                                        .setPriority(NotificationCompat.PRIORITY_HIGH).build();
                                notificationManager.notify(dl.getNot_id(), n);
                                int errors = 0;
                                while (!HttpHandler.haveNetworkConnection(context)) {
                                    Thread.sleep(1000);
                                    Log.d("ConvertService", String.valueOf(errors));
                                    errors++;
                                    if (errors >= 10) throw e;
                                }
                            } else {
                                throw e;
                            }
                        } catch (Exception e) {
                            handleError(dl, "Unbekannter Fehler");
                            e.printStackTrace();
                        } finally {
                            Thread.sleep(250);
                        }
                    }

                    sendMsg(MESSAGE_FINISH_TASK,dl);
                    if (dl.getStatus() == 100) {
                        //restartService(dl, action_startDownload);
                        sendMsg(MESSAGE_START_DOWNLOAD,dl);
                    } else {
                        handleError(dl,"Convertierungs Fehler");
                        Log.d("ConvertService", "Status not 100%");
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }  catch (IOException e) {
                    handleError(dl, getString(R.string.warning_Timeout));
                    e.printStackTrace();
                } catch (Exception e) {
                    Log.d("ConvertService", "Fehler Aussen");
                    e.printStackTrace();
                }finally{
                    finishThread(dl.getThreadId());
                }
            }
        });
        return thread;
    }

    public Thread getDownloadThread(final Download dl) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Intent delintent = new Intent(context, context.getClass());
                    delintent.putExtra("download", dl.toJson());
                    delintent.setAction(action_stopDownload);
                    PendingIntent deleteIntent = PendingIntent.getService(context, 0, delintent, PendingIntent.FLAG_UPDATE_CURRENT);
                    Log.d(TAG, "Download gestartet");
                    String url = getString(R.string.api_server_adress) +"/"+ dl.getDownloadUrl();
                    Log.d(TAG,url);
                    HttpURLConnection connection = (HttpURLConnection) ((new URL(url).openConnection()));
                    File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    dl.setFile(new File(path, dl.getFilename()));
                    long downloaded = 0;
                    if (dl.getFile().exists()) {
                        downloaded = dl.getFile().length();
                        connection.setRequestProperty("Range", "bytes=" + downloaded + "-");
                    }

                    connection.setRequestMethod("GET");
                    connection.setReadTimeout(HttpHandler.TIMEOUT);

                    String header = "Basic " + new String(android.util.Base64.encode(HttpHandler.getToken(context).getAuth().getBytes(), android.util.Base64.NO_WRAP));
                    connection.addRequestProperty("Authorization", header);
                    int responseCode = connection.getResponseCode();
                    if (responseCode == 200||responseCode ==206) {
                        int maxLength = connection.getContentLength();
                        Log.d("Zu downloaden", String.valueOf(maxLength));
                        Log.d("schon runtergeladen", String.valueOf(downloaded));
                        InputStream in = connection.getInputStream();
                        FileOutputStream fos = (downloaded == 0) ? new FileOutputStream(dl.getFile()) : new FileOutputStream(dl.getFile(), true);
                        BufferedOutputStream bout = new BufferedOutputStream(fos, 1024);
                        byte[] data = new byte[1024];
                        int x = 0;
                        double olddownloaded = downloaded;
                        double oldprog = dl.getProg();
                        double actprog = 0;
                        Log.d("oldprog", String.valueOf(oldprog));
                        while (!Thread.currentThread().isInterrupted() && (x = in.read(data, 0, 1024)) >= 0) {
                            fos.write(data, 0, x);
                            downloaded += x;

                            double prog = oldprog + (100.0 - oldprog) * (1.0 * downloaded - olddownloaded) / maxLength;
                            dl.setProg(prog);
                            if (prog < 100 && (prog - actprog) > 0.1) {
                                Notification n = new NotificationCompat.Builder(DownloadService.this, "default")
                                        .setContentTitle(getShortFileName(dl.getFilename()))
                                        .setContentText(String.format("%.2f%s", prog, "%"))
                                        .setProgress(100, (int) prog, false)
                                        .setSmallIcon(R.drawable.download_icon_anim)
                                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                                        .setDeleteIntent(deleteIntent)
                                        .build();
                                notificationManager.notify(dl.getNot_id(), n);
                                actprog = prog;
                            }
                        }
                        Thread.sleep(200);
                        Log.d(TAG, "finished Downloading");
                        Intent intent = new Intent();
                        intent.setAction(android.content.Intent.ACTION_VIEW);
                        Uri fileUri = FileProvider.getUriForFile(DownloadService.this,
                                BuildConfig.APPLICATION_ID + ".provider",
                                dl.getFile());
                        String mimeType= MimeTypeMap.getFileExtensionFromUrl(dl.getFile().getAbsolutePath());
                        Log.d(TAG,mimeType);
                        intent.setData(fileUri);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        PendingIntent pendingIntent = PendingIntent.getActivity(DownloadService.this, 0, intent, 0);
                        Notification n = new NotificationCompat.Builder(DownloadService.this, "default")
                                .setContentTitle(getShortFileName(dl.getFilename()))
                                .setSmallIcon(R.drawable.ic_download_frame00)
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setContentText("Download Fertiggestellt")
                                .setContentIntent(pendingIntent)
                                .setProgress(0, 0, false).build();
                        notificationManager.notify(dl.getNot_id(), n);
                        showToast(dl.getFilename() + " fertiggestellt!");
                        sendMsg(MESSAGE_FINISH_TASK,dl);
                    } else {
                        throw new HttpHandler.HttpResponseException("Unhandled Response Code: " + responseCode, responseCode);
                    }

                } catch (HttpHandler.HttpResponseException e) {
                    handleError(dl, getString(R.string.warning_HttpException));
                    e.printStackTrace();
                } catch (SSLException e) {
                    handleError(dl, "Es konnte keine Sichere verbindung hergestellt werden.");
                    e.printStackTrace();
                } catch (IOException e) {
                    Log.d(TAG, "IOException");
                    e.printStackTrace();
                    if (!HttpHandler.haveNetworkConnection(context)) {
                        Notification n = new NotificationCompat.Builder(DownloadService.this, "default")
                                .setContentTitle(getShortFileName(dl.getFilename()))
                                .setContentText("Kein Internet")
                                .setProgress(0, 0, true)
                                .setSmallIcon(R.drawable.ic_download_frame00)
                                .setPriority(NotificationCompat.PRIORITY_HIGH).build();
                        notificationManager.notify(dl.getNot_id(), n);
                        int errors = 0;
                        while (!HttpHandler.haveNetworkConnection(context)) {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e1) {
                                e1.printStackTrace();
                                break;
                            }
                            Log.d(TAG, String.valueOf(errors));
                            errors++;
                            if (errors >= 10) {
                                handleError(dl, "Download unterbrochen");
                                break;
                            }
                        }
                        if (errors < 10) {
                            sendMsg(MESSAGE_FINISH_TASK,dl);
                            //restartService(dl, action_startDownload);
                            sendMsg(MESSAGE_START_DOWNLOAD,dl);
                        }
                    } else {
                        handleError(dl, "Kein Internet");
                    }
                } catch (InterruptedException e) {
                    Log.d(TAG,"Interrupted");
                    e.printStackTrace();
                } catch (Exception e) {
                    handleError(dl, "Unbekannter Fehler");
                    e.printStackTrace();
                } finally {
                    finishThread(dl.getThreadId());
                }
            }
        });
        return thread;
    }

    public void sendMsg(int what,Download dl) {
        Message msg = mServiceHandler.obtainMessage();
        msg.obj = dl;
        msg.what = what;
        mServiceHandler.sendMessage(msg);
    }

    public void finishThread(long id){
            Log.d(TAG, "finishing Thread");
            for (Thread t : threads) {
                if (t.getId() == id) {
                    threads.remove(t);
                    Log.d(TAG,"Thread finished");
                    break;
                }
            }
            tryStopService(0);
    }


    public String getShortFileName(String s) {
        if (s.length() > 20) {
            return s.substring(0, 20) + s.substring(s.length() - 4);
        }
        return s;
    }

    public void handleError(Download dl, String text) {
        handleError(dl, text, false);
    }

    public void handleError(Download dl, String text, Boolean autoCancel) {
        sendMsg(MESSAGE_FINISH_TASK,dl);
        Intent delintent = new Intent(context, context.getClass());
        delintent.putExtra("download", dl.toJson());
        delintent.setAction(action_stopDownload);
        PendingIntent deleteIntent = PendingIntent.getService(context, 0, delintent, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent intent = new Intent(context, context.getClass());
        intent.putExtra("download", dl.toJson());
        if (dl.getDl_id() < 0) {
            intent.setAction(action_startConvert);
        } else if (dl.getStatus()==null||dl.getStatus() < 100) {
            intent.setAction(action_startListen);
        } else if (dl.getStatus()==null||dl.getProg() < 100) {
            intent.setAction(action_startDownload);
        }
        PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context, "default");
        File file = dl.getFile();
        String filename = "Download Unterbrochen";
        if (file != null) {
            filename = file.getName();
        }
        mBuilder.setContentTitle(filename)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_download_frame00)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDeleteIntent(deleteIntent)
                .setAutoCancel(autoCancel);
        notificationManager.notify(dl.getNot_id(), mBuilder.build());

        showToast("Download unterbrochen");
    }


    public void showToast(final String Text) {

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, Text, Toast.LENGTH_SHORT).show();
            }
        });
    }

}

