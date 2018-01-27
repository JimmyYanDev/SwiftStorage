package com.xiandian.openstack.cloud.swiftstorage.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.widget.BaseAdapter;

import com.woorea.openstack.swift.model.Object;
import com.woorea.openstack.swift.model.ObjectDownload;
import com.xiandian.openstack.cloud.swiftstorage.AppState;
import com.xiandian.openstack.cloud.swiftstorage.LoginActivity;
import com.xiandian.openstack.cloud.swiftstorage.R;
import com.xiandian.openstack.cloud.swiftstorage.base.TaskResult;
import com.xiandian.openstack.cloud.swiftstorage.sdk.service.OpenStackClientService;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Created by michealyan on 2018/1/27.
 */

public class HttpUtils {

    /**
     * 异步下载图片到本地
     * @param fileName swift文件路径
     * @param filePath 本地文件路径
     * @param fileListViewAdapter
     * @param activity
     */
    public static void downloadFromSwfit(final String fileName, final String filePath, final BaseAdapter fileListViewAdapter, final Activity activity) {
        new AsyncTask<String, Object, TaskResult<java.lang.Object>>() {

            @Override
            protected TaskResult<java.lang.Object> doInBackground(String... strings) {
                try {
                    File tempFile = new File(filePath);
                    if (!tempFile.exists()) {
                        tempFile.createNewFile();
                    }
                    ObjectDownload objectDownload = OpenStackClientService.getInstance().downloadObject(AppState.getInstance().getSelectedContainer().getName(), fileName);
                    BufferedInputStream bis = new BufferedInputStream(objectDownload.getInputStream());
                    FileOutputStream fos = new FileOutputStream(tempFile);
                    byte[] buffer = new byte[4096];
                    int hasRead = 0;
                    while ((hasRead = bis.read(buffer)) > 0) {
                        fos.write(buffer, 0, hasRead);
                    }
                    fos.flush();
                    fos.close();
                    bis.close();
                    return new TaskResult<java.lang.Object>(null);
                } catch (Exception except) {
                    return new TaskResult<java.lang.Object>(except);
                }
            }

            @Override
            protected void onPostExecute(TaskResult<java.lang.Object> result) {
                if (result.isValid()) {
                    fileListViewAdapter.notifyDataSetChanged();

                } else {
                    //提示错误，返回登录
                    PromptDialogUtil.showErrorDialog(activity,
                            R.string.alert_error_get_objects, result.getException(),
                            new Intent(activity, LoginActivity.class));
                }
            }
        }.execute();
    }
}
