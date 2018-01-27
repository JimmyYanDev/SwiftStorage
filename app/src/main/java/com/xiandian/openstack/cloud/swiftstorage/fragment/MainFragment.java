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
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.xiandian.openstack.cloud.swiftstorage.utils.FileIconHelper;
import com.xiandian.openstack.cloud.swiftstorage.utils.HttpUtils;
import com.xiandian.openstack.cloud.swiftstorage.utils.PromptDialogUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;

/**
 * 展示所有文件的Fragment，内部包含一个ListView。
 *
 * @author 云计算应用与开发项目组
 * @since  V1.0
 */
public class MainFragment extends Fragment
        implements OnRefreshListener, SFileListViewAdapter.ItemClickCallable, SFileEditable {


    //Log 信息标签。
    private String TAG = MainFragment.class.getSimpleName();
    //Context
    private Context context;
    //图片工具类
    FileIconHelper fileIconHelper;
    //操作，确定和取消，默认隐藏，有操作是显示
    private LinearLayout fileActionBar;
    //确定按钮
    Button btnConfirm;
    //取消按钮
    Button btnCancel;
    //下拉刷新
    private SwipeRefreshLayout fileListSwipe;
    //File List View
    private ListView fileListView;
    //是否是复制
    private boolean iscopy = false;
    //复制的文件名称
    String copyFileName = null;
    //复制到的文件地址
    String copyToFileName = null;
    //复制文件的类型
    String copyFileType = null;
    //是否是移动
    private boolean ismove = false;
    //移动的文件名称
    String moveFileName = null;
    //移动到的位置
    String moveToFileName = null;
    //移动的文件类型
    String moveFileType = null;
    //File List View Adapter
    private SFileListViewAdapter fileListViewAdapter;
    //File data model
    List<SFileData> fileListData = new ArrayList<SFileData>();
    //当前展示的文件夹列表（注意：太多临时变量不容易维护）
    private List<SFile> swiftFolders;
    //当前展示的文件列表（注意：太多临时变量不容易维护）
    private List<SFile> swiftFiles;
    //下载的id
    private int downid = 0;
    //进度条
    private ProgressBar pb;
    //文件大小
    private int fileSize = 0;
    //已下载的文件大小
    private int downLoadFileSize = 0;

    /**
     * 缺省构造器。
     */
    public MainFragment() {

    }

    /**
     * 构造视图。
     *
     * @param inflater:界面XML
     * @param container：
     * @param savedInstanceState
     * @return
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        context = this.getActivity();
        //(1) Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        fileIconHelper = new FileIconHelper(context);
        //(2)操作按钮（确认和取消），当移动，复制等操作是出现。
        fileActionBar = (LinearLayout) rootView.findViewById(R.id.layout_operation_bar);
        btnConfirm = (Button) rootView.findViewById(R.id.btnConfirm);
        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
        btnCancel = (Button) rootView.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fileActionBar.setVisibility(View.GONE);
            }
        });

        //(3) 下拉刷新
        fileListSwipe = (SwipeRefreshLayout) rootView.findViewById(R.id.swipe_refresh_files);
        fileListSwipe.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        fileListSwipe.setOnRefreshListener(this);//增加刷新方法

        //(4) 文件列表视图
        fileListView = (ListView) rootView.findViewById(R.id.main_list_root);
        //创适配器
        fileListViewAdapter = new SFileListViewAdapter(context, fileListData, this);
        fileListView.setAdapter(fileListViewAdapter);
        //(5) 读取云存储数据，填充视图
        GetOSSObjectsTask getOSSObjectsTask = new GetOSSObjectsTask();
        getOSSObjectsTask.execute();

        return rootView;
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
        protected void onPostExecute(TaskResult<Objects> result) {

            //(7). 如果数据有效
            if (result.isValid()) {
                //当前选择目录
                SFile selectedDirectory = getAppState().getSelectedDirectory();
                //转换读取的对象为文件系统
                SFile fs = getAppState().readFromObjects(result.getResult());
                getAppState().setOSSFS(fs);

                //如果当前选择目录存在（如进入子目录）
                if (selectedDirectory != null && selectedDirectory.hasData() && selectedDirectory.getName() != null) {
                    //重新寻找对应的目录，默认路径不变
                    getAppState().setSelectedDirectory(
                            getAppState().findChild(getAppState().getSelectedDirectory().getRoot(), selectedDirectory.getName()));
                } else {
                    //如果空的，设置为最新读取的数值
                    getAppState().setSelectedDirectory(getAppState().getOSSFS());
                }
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
        if (getAppState().getSelectedDirectory() != null) {
            //调用MainActivity改变Toolbar的路径信息
            ((MainActivity) getActivity()).setToolbarTitles(getString(R.string.menu_swiftdisk), getAppState().getSelectedDirectory().getName());
        } else {
            GetOSSObjectsTask getObjectsTask = new GetOSSObjectsTask();
            getObjectsTask.execute();
        }
//        //调用MainActivity改变Toolbar的路径信息
//        ((MainActivity) getActivity()).setToolbarTitles(getString(R.string.menu_swiftdisk), getAppState().getSelectedDirectory().getName());
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

    /**
     * 根据当前选择目录，转换成ListData。
     */
    private void setFileListData() {
        //显示格式，这里简单统一处理
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        //清空

        fileListData.clear();

        //子目录文件夹
        SFile currentFolder = getAppState().getSelectedDirectory();
        if (currentFolder != null) {
            swiftFolders = new ArrayList<SFile>(currentFolder.listDirectories());
            swiftFiles = new ArrayList<SFile>(currentFolder.listFiles());
        }
        if (swiftFolders != null) {
            //文件夹
            for (int i = 0; i < swiftFolders.size(); i++) {
                SFile dir = swiftFolders.get(i);
                SFileData fileData = new SFileData();
                //1 Icon 2 name 3 time 4 size 5 folder 6 index 7  checked
                //默认目录图标
                fileData.setImageResource(R.drawable.ic_file_folder);
                fileData.setFileName(cleanName(dir.getName()));
                fileData.setFolder(true);
                //记录对应的信息
                fileData.setIndex(i);
                fileData.setChecked(false);
                fileData.setFolder(true);
                Calendar calendar = dir.getLastModified();
                //Todo: Why calendar == can be null?
                fileData.setLastModifiedTime(calendar == null ? System.currentTimeMillis() : calendar.getTimeInMillis());
                fileData.setLastModified(calendar == null ? "" : dateFormat.format(calendar.getTime()));
                fileListData.add(fileData);
            }
        }
        if (swiftFiles != null) {
            //文件
            for (int i = 0; i < swiftFiles.size(); i++) {
                SFile file = swiftFiles.get(i);
                SFileData fileData = new SFileData();
                String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/openstack/" + cleanName(file.getName());
                //1 Icon 2 name 3 time 4 size 5 folder 6 index 7  checked
                //目前采用默认图标
                if (file.getContentType().contains("image")) {
                    String fileName = file.getName();
                    // 异步下载图片到本地
                    HttpUtils.downloadFromSwfit(fileName, filePath, fileListViewAdapter, getActivity());
                    Bitmap bitmap = fileIconHelper.getImageThumbnail(filePath);
                    if (bitmap != null) {
                        fileData.setImage(bitmap);
                    } else {
                        fileData.setImageResource(R.drawable.ic_file_pic);
                    }

                } else if (file.getContentType().contains("video")) {
                    String fileName = file.getName();
                    // 异步下载图片到本地
                    HttpUtils.downloadFromSwfit(fileName, filePath, fileListViewAdapter, getActivity());
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


    /////////////////////并填充listView的任务/////////////////////>


    /////////////////////点击条目进入下一级目录/////////////////////<


    /**
     * 进入选择条目，如果是目录进入下一级目录。
     * 如果是文件，传递给Android系统，启动默认支持打开程序开启。
     *
     * @param position
     */
    @Override
    public void intoItem(int position) {

        //选择对应的数据
        SFileData item = fileListData.get(position);
        //是否是目录
        boolean isFolder = item.isFolder();
        //对应的数据Index
        int index = item.getIndex();
        //如何是目录，进入下一级别
        if (isFolder) {
            // 文件夹
            getAppState().setSelectedDirectory(swiftFolders.get(position));
            fillListView();
        } else {

        }
    }

    /////////////////////点击条目进入下一级目录/////////////////////>

    /////////////////////回退操作/////////////////////<

    /**
     * 当主Activity进行回退时，如果不再跟目录，需要回退到上一次目录，返回调用的Activity一个状态，是否回退。
     * 如果是文件，传递给Android系统，启动默认支持打开程序开启。
     *
     * @return false 没有回退，true进行了回退操作
     */
    public boolean onContextBackPress() {
        //如果是跟元素
        if (getAppState().getSelectedDirectory().getParent() == null) {
            return false;
        } else {
            getAppState().setSelectedDirectory(getAppState().getSelectedDirectory().getParent());
            fillListView();
            return true;
        }
    }

    /////////////////////回退操作/////////////////////>

    /////////////////////回退操作/////////////////////>

    /**
     * SwipeRefreshLayout实现了下拉刷新，内部视图是ScrollView、ListView或GridView。
     * 当下拉组件时，调用该方法。
     */
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
    /////////////////////回退操作/////////////////////>

    /**
     * Activity之间调用和回调传递数据使用 startActivityForResult()  setResult() onActivityResult()。
     * 目前上传文件，拍照，上传数据使用外部的Activity。
     *
     * @param requestCode
     * @param resultCode:返回的key
     * @param data：返回的内容
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {

            default:
                break;
        }

    }


    ///////////////可编辑的实现//////////////////

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


    /**
     * 刷新当前视口。
     */
    public void refresh() {
        fillListView();
    }

    @Override
    public void restroe() {

    }

    @Override
    public void empty() {

    }

    /**
     * 获取选择的文件或目录。
     *
     * @return
     */
    private List<SFile> getSelectedFiles() {
        ArrayList<SFile> selected = new ArrayList<SFile>();
        for (SFileData fd : fileListData) {
            if (fd.isChecked()) {
                selected.add(fd.isFolder() ? swiftFolders.get(fd.getIndex()) : swiftFiles.get(fd.getIndex()));
            }
        }
        return null;
    }

    /**
     * 获取选择的文件或目录。
     *
     * @return
     */
    private SFile getFirstSelectedFile() {
        ArrayList<SFile> selected = new ArrayList<SFile>();
        for (SFileData fd : fileListData) {
            if (fd.isChecked() && !fd.isFolder()) {
                return swiftFiles.get(fd.getIndex());
            }
        }
        return null;
    }

    /**
     * 获取选择的第一个文件或目录。
     *
     * @return
     */
    private SFile getFirstSelected() {
        for (SFileData fd : fileListData) {
            if (fd.isChecked()) {
                return fd.isFolder() ? swiftFolders.get(fd.getIndex()) : swiftFiles.get(fd.getIndex());
            }
        }
        return null;
    }


    ///////////////可编辑的实现//////////////////


}
