package com.xiandian.openstack.cloud.swiftstorage;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TestActivity extends Activity {

    private Button btn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        btn = (Button) findViewById(R.id.button);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION);
                String directory = AppState.getInstance().getOpenStackLocalPath();
                String fileName = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".mp3";
                File fileOutPath = new File(directory);
                if (!fileOutPath.exists()) {
                    fileOutPath.mkdirs();
                }
                fileOutPath = new File(directory, fileName);
                Uri uri = Uri.fromFile(fileOutPath);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                startActivityForResult(intent, 1);
            }
        });
    }
}
