package de.yoxcu.ytdl;

import android.content.Intent;
import android.os.StrictMode;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class ShareActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_share);
        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();
        moveTaskToBack(true);
        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                handleSendText(intent);
            } else {
                Log.d("debug", "falsetype");
            }
        } else {
            Log.d("debug", "falseaction");
        }
    }

    void handleSendText(Intent intent) {
        String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (sharedText != null) {

            Download dl= new Download(sharedText,"mp3");
            Intent dlint = new Intent(this, DownloadService.class);
            dlint.putExtra("download", dl.toJson());
            dlint.setAction(DownloadService.action_startConvert);
            startService(dlint);
        }
        finish();
    }
}
