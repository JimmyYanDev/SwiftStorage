package com.xiandian.openstack.cloud.swiftstorage.fragment;


import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.woorea.openstack.swift.model.Object;
import com.woorea.openstack.swift.model.ObjectDownload;
import com.woorea.openstack.swift.model.Objects;
import com.xiandian.openstack.cloud.swiftstorage.AppState;
import com.xiandian.openstack.cloud.swiftstorage.LoginActivity;
import com.xiandian.openstack.cloud.swiftstorage.R;
import com.xiandian.openstack.cloud.swiftstorage.base.TaskResult;
import com.xiandian.openstack.cloud.swiftstorage.fs.OSSFileSystem;
import com.xiandian.openstack.cloud.swiftstorage.fs.SFile;
import com.xiandian.openstack.cloud.swiftstorage.sdk.service.OpenStackClientService;
import com.xiandian.openstack.cloud.swiftstorage.utils.Constants;
import com.xiandian.openstack.cloud.swiftstorage.utils.FileIconHelper;
import com.xiandian.openstack.cloud.swiftstorage.utils.PromptDialogUtil;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

public class ImggridviewFragment extends ImgFragment
        implements SwipeRefreshLayout.OnRefreshListener, SFileListViewAdapter.ItemClickCallable, SFileEditable {
    //Log 信息标签。
    private String TAG = MainFragment.class.getSimpleName();
    //图片工具类
    FileIconHelper fileIconHelper;
    //Context
    private Context context;
    //图片数组
    Bitmap[] img;
    //图片文字数组
    String[] imgtxt;
    //增加分类类别，分别取导航的显示内容
    private String[] mimeTypes;
    //图片地址
    ArrayList<String> imglist=new ArrayList<String>();
    //File data model
    List<SFileData> fileListData = new ArrayList<SFileData>();
    //当前展示的文件列表（注意：太多临时变量不容易维护）
    private List<SFile> swiftFiles = new ArrayList<>();
    //下拉刷新
    private SwipeRefreshLayout fileListSwipe;
    private PictureAdapter adapter;
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
        context = this.getActivity();
        mimeTypes = getArguments().getStringArray(Constants.CATEGORY_TYPE);
        //(1) Inflate the layout for this fragment
        fileIconHelper=new FileIconHelper(context);
        View rootView = inflater.inflate(R.layout.img_videogridview, container, false);
        //下拉刷新
        fileListSwipe = (SwipeRefreshLayout) rootView.findViewById(R.id.swipe_refresh_files2);
        fileListSwipe.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        fileListSwipe.setOnRefreshListener(this);//增加刷新方法
        GridView gridView=(GridView)rootView.findViewById(R.id.img_video);
        context = getActivity();
        fileIconHelper=new FileIconHelper(context);
//        img=new Bitmap[swiftFiles.size()];
//        imgtxt= new String[swiftFiles.size()];
//
//        for(int i=0;i<fileListData.size();i++){
//                img[i]=fileListData.get(i).getImage();
//        }
//        //获取GridView组件//定义并初始化保存图片的数组
//        for (int i=0;i<fileListData.size();i++){
//            imgtxt[i]=fileListData.get(i).getFileName();
//        }
        //定义并初始化保存说明文字的数组

        adapter = new PictureAdapter(fileListData, getActivity());
//        sFileListViewAdapter = new SFileListViewAdapter(getActivity(), fileListData, this);
        gridView.setAdapter(adapter);//将适配器与gridView关联
        gridView.setOnItemClickListener(new ItemClickListener());

        GetOSSObjectsTask getOSSObjectsTask = new GetOSSObjectsTask();
        getOSSObjectsTask.execute();

        return rootView;
    }
    class  ItemClickListener implements AdapterView.OnItemClickListener {

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            openFile(swiftFiles.get(position));
        }
    }

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
        adapter.notifyDataSetChanged();
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
    public void restroe() {

    }

    @Override
    public void intoItem(int position) {
    //选择对应的数据
        SFileData item = fileListData.get(position);

    }

    @Override
    public void onRefresh() {
        fileListSwipe.setRefreshing(true);
        GetOSSObjectsTask getOSSObjectsTask = new GetOSSObjectsTask();
        getOSSObjectsTask.execute();
        //2秒刷新事件
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                fileListSwipe.setRefreshing(false);
            }
        }, 2000);
    }

    //自定义适配器
    class PictureAdapter extends BaseAdapter {
        private LayoutInflater inflater;
        List<SFileData> datas;

        public PictureAdapter(List<SFileData> datas, Context context) {
            super();
            inflater = LayoutInflater.from(context);
            this.datas = datas;
        }


        @Override
        public int getCount()
        {
            return datas.size();
        }

        @Override
        public SFileData getItem(int position)
        {
            return datas.get(position);
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            ViewHolder viewHolder;
            if (convertView == null)
            {
                convertView = inflater.inflate(R.layout.grad_item, null);
                viewHolder = new ViewHolder();
                viewHolder.title = (TextView) convertView.findViewById(R.id.img_name);
                viewHolder.image = (ImageView) convertView.findViewById(R.id.img_bitmap);
                convertView.setTag(viewHolder);
            } else
            {
                viewHolder = (ViewHolder) convertView.getTag();
            }
            viewHolder.title.setText(getItem(position).getFileName());
            if(getItem(position).getImage()!=null){
                viewHolder.image.setImageBitmap(getItem(position).getImage());
            }else {
                viewHolder.image.setImageResource(getItem(position).getImageResource());
            }
            return convertView;
        }

    }
    class ViewHolder
    {
        public TextView title;
        public ImageView image;
    }


}
