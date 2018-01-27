package com.xiandian.openstack.cloud.swiftstorage.fragment;


import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;

import com.woorea.openstack.swift.model.Object;
import com.woorea.openstack.swift.model.Objects;
import com.xiandian.openstack.cloud.swiftstorage.AppState;
import com.xiandian.openstack.cloud.swiftstorage.LoginActivity;
import com.xiandian.openstack.cloud.swiftstorage.MainActivity;
import com.xiandian.openstack.cloud.swiftstorage.R;
import com.xiandian.openstack.cloud.swiftstorage.base.TaskResult;
import com.xiandian.openstack.cloud.swiftstorage.fs.SFile;
import com.xiandian.openstack.cloud.swiftstorage.sdk.service.OpenStackClientService;
import com.xiandian.openstack.cloud.swiftstorage.utils.Constants;
import com.xiandian.openstack.cloud.swiftstorage.utils.FileIconHelper;
import com.xiandian.openstack.cloud.swiftstorage.utils.PromptDialogUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

/**
 * 展示所有文件的Fragment，内部包含一个ListView。
 *
 * @author 云计算应用与开发项目组
 * @since  V1.0
 */
public class CategoryFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener,
        SFileListViewAdapter.ItemClickCallable, SFileEditable {


    //Log 信息标签。
    private String TAG = MainFragment.class.getSimpleName();
    //Context
    private Context context;

    //操作，确定和取消，默认隐藏，有操作是显示
    private LinearLayout fileActionBar;
    //下拉刷新
    private SwipeRefreshLayout fileListSwipe;
    //File List View
    private ListView fileListView;
    //图片工具类
    FileIconHelper fileIconHelper;
    //File List View Adapter
    private SFileListViewAdapter fileListViewAdapter;
    //File data model
    List<SFileData> fileListData = new ArrayList<SFileData>();

    //当前展示的文件列表（注意：太多临时变量不容易维护）
    private List<SFile> swiftFiles = new ArrayList<>();

    //增加分类类别，分别取导航的显示内容
    private String[] mimeTypes;
    //图片地址
    private String[] imgfiles;
    //下载的id
    int downid=0;
    //进度条
    ProgressBar pb;
    //文件大小
    int   fileSize=0;
    //已下载的文件大小
    int   downLoadFileSize=0;
    /**
     * 缺省构造器。
     */
    public CategoryFragment() {
    }

    /**
     * 构造视图。
     *
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //修改(1) 获取参数，分类类型，由MainActivity传入
        mimeTypes = getArguments().getStringArray(Constants.CATEGORY_TYPE);

        context = this.getActivity();
        fileIconHelper = new FileIconHelper(context);
        //Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        //操作按钮（确认和取消），当移动，复制等操作是出现。
        fileActionBar = (LinearLayout) rootView.findViewById(R.id.layout_operation_bar);

        //下拉刷新
        fileListSwipe = (SwipeRefreshLayout) rootView.findViewById(R.id.swipe_refresh_files);
        fileListSwipe.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        fileListSwipe.setOnRefreshListener(this);//增加刷新方法

        //文件列表视图
        fileListView = (ListView) rootView.findViewById(R.id.main_list_root);
        //创适配器
        fileListViewAdapter = new SFileListViewAdapter(context, fileListData, this);
        fileListView.setAdapter(fileListViewAdapter);
        //读取云存储数据，填充视图
        GetOSSObjectsTask getOSSObjectsTask = new GetOSSObjectsTask();
        getOSSObjectsTask.execute();

        return rootView;
    }

    /**
     * File保持的名称含有路径，进行分解，只取文件名称。
     *
     * @param path
     * @return the string
     */
    private String cleanName(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }
    ///////////////////////////////////////////////

    /**
     * 目前APP的状态记录。
     *
     * @return
     */
    private AppState getAppState() {
        return AppState.getInstance();
    }

    /**
     * 服务。
     *
     * @return
     */
    private OpenStackClientService getService() {
        return OpenStackClientService.getInstance();
    }


    /////////////////////获取云存储对象，转换为文件系统，并填充listView的任务/////////////////////<

    /**
     * 获取云存储的对象。
     */
    private class GetOSSObjectsTask extends AsyncTask<String, Object, TaskResult<Objects>> {
        /**
         * 后台线程任务。
         *
         * @param params
         * @return
         */
        @Override
        protected TaskResult<Objects> doInBackground(String... params) {
            try {
                //(6) 通过云存储服务，获得当前容器的对象
                Objects objs = getService().getObjects(getAppState().getSelectedContainer().getName());
                return new TaskResult<Objects>(objs);
            } catch (Exception except) {
                return new TaskResult<Objects>(except);
            }
        }

        /**
         * 任务执行完毕。
         *
         * @param result
         */
        @Override
        protected void onPostExecute(TaskResult<Objects> result) {

            //(7). 如果数据有效
            if (result.isValid()) {
                SFile fs = getAppState().readFromObjects(result.getResult());
                getAppState().setOSSFS(fs);
                //(8) 根据模拟的文件系统填充ListView
                fillListView();
            } else {
                //提示错误，返回登录
                PromptDialogUtil.showErrorDialog(getActivity(),
                        R.string.alert_error_get_objects, result.getException(),
                        new Intent(getActivity(), LoginActivity.class));
            }
        }
    }


    /////////////////////获取云存储对象，转换为文件系统，并填充listView的任务/////////////////////>

    /////////////////////并填充listView的任务/////////////////////<

    /**
     * 填充“所有”的当前目录数据。
     */
    private void fillListView() {
        setFileListData();
        fileListViewAdapter.notifyDataSetChanged();
    }

    /**
     * 根据当前选择目录，转换成ListData。
     */
    private void setFileListData() {
        //显示格式，这里简单统一处理
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        //清空
        fileListData.clear();
        swiftFiles.clear();
        //遍历文件数将所有的文件添加到一个集合中
        findFiles(getAppState().getOSSFS());
        if (swiftFiles != null && swiftFiles.size() > 0) {
            //文件
            for (int i = 0; i < swiftFiles.size(); i++) {
                SFile file = swiftFiles.get(i);
                SFileData fileData = new SFileData();
                String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/openstack/" + cleanName(file.getName());
                //1 Icon 2 name 3 time 4 size 5 folder 6 index 7  checked
                //目前采用默认图标
                if (file.getContentType().contains("image")) {
                    Bitmap bitmap = fileIconHelper.getImageThumbnail(filePath);
                    if (bitmap != null) {
                        fileData.setImage(bitmap);
                    } else {
                        fileData.setImageResource(R.drawable.ic_file_pic);
                    }

                } else if (file.getContentType().contains("video")) {
                    Bitmap bitmap = fileIconHelper.getVideoThumbnail(filePath);
                    if (bitmap != null) {
                        fileData.setImage(bitmap);
                    } else {
                        fileData.setImageResource(R.drawable.ic_file_video);
                    }
                } else if (file.getContentType().contains("audio")) {
                    fileData.setImageResource(R.drawable.ic_file_music);
                } else {
                    fileData.setImageResource(R.drawable.ic_file_doc);
                }
                //其他暂时都认为是文档，区分office\pdf\txt\html作为扩展内容，由学习者实现.
                //如果需要实现图片预览，目前服务器端没有实现，需要下载本地，产生缩略图

                fileData.setFileName(cleanName(file.getName()));
                fileData.setFolder(true);
                //记录对应的信息
                fileData.setIndex(i);
                fileData.setChecked(false);
                Calendar calendar = file.getLastModified();
                fileData.setLastModifiedTime(calendar.getTimeInMillis());
                fileData.setLastModified(dateFormat.format(calendar.getTime()));
                fileData.setFileSize(file.getSize());
                fileData.setFolder(false);
                fileData.setIndex(i);
                fileListData.add(fileData);
            }
        }

        Log.d(TAG, fileListData.toString());
    }

    //递归查找所有的文件，并将其添加到集合中
    private void findFiles(SFile fs) {
        Collection<SFile> sFiles = fs.listFiles();
        //遍历所有的文件
        for (SFile sFile : sFiles) {
            //获取当前文件的类型
            String type = sFile.getSwiftObject().getContentType();
            boolean flag = false;
            //判断当前文件类型是否在所选的类型范围中
            for (int i = 0; i < mimeTypes.length; i++) {
                if (type.contains(mimeTypes[i]) || type.contains(mimeTypes[i].replace("/", "%2F"))) {
                    flag = true;
                    break;
                }
            }
            if (flag) {
                swiftFiles.add(sFile);
            }
        }
        Collection<SFile> subFs = fs.listDirectories();
        if (subFs != null && subFs.size() > 0) {
            for (SFile s : subFs) {
                findFiles(s);
            }
        }
    }


    /////////////////////并填充listView的任务/////////////////////>


    @Override
    public void intoItem(int position) {

    }

    @Override
    public void onRefresh() {
        fileListSwipe.setRefreshing(true);
        // 获取对象，重新获取当前目录对象
        GetOSSObjectsTask getObjectsTask = new GetOSSObjectsTask();
        getObjectsTask.execute();
        //2秒刷新事件
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                fileListSwipe.setRefreshing(false);
            }
        }, 2000);
    }

    @Override
    public void search(String fileName) {

    }

    @Override
    public void share() {

    }

    @Override
    public void selectAll() {

    }

    @Override
    public void unselectAll() {

    }

    @Override
    public void openFile(SFile filePath) {

    }

    @Override
    public void createDir(String filePath) {

    }

    @Override
    public void upload() {

    }

    @Override
    public void download() {

    }

    @Override
    public void takePhoto() {

    }

    @Override
    public void recordvideo() {

    }

    @Override
    public void recordaudio() {

    }

    @Override
    public void rename(String oldFilePath, String newFilePath) {

    }

    @Override
    public void copy(String fromPath, String toPath) {

    }

    @Override
    public void move(String fromPath, String toPath) {

    }

    @Override
    public void recycle(String filePath) {

    }

    @Override
    public void sort() {

    }

    @Override
    public void details(int type, boolean ascend) {

    }

    @Override
    public void refresh() {

    }

    @Override
    public void restroe() {

    }

    @Override
    public void empty() {

    }
}
