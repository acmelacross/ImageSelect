package com.jhworks.library.ui;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.ListPopupWindow;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import com.jhworks.library.Constant;
import com.jhworks.library.R;
import com.jhworks.library.adapter.FolderAdapter;
import com.jhworks.library.adapter.ImageAdapter;
import com.jhworks.library.bean.DataBundle;
import com.jhworks.library.bean.Folder;
import com.jhworks.library.bean.Media;
import com.jhworks.library.bean.MediaSelectConfig;
import com.jhworks.library.decoration.DividerGridItemDecoration;
import com.jhworks.library.load.MediaDataLoader;
import com.jhworks.library.utils.FileUtils;
import com.jhworks.library.utils.ScreenUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jiahui
 *         date 2016/5/18
 */
public class ImageSelectorFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<List<Media>> {

    private static final int REQUEST_STORAGE_WRITE_ACCESS_PERMISSION = 110;
    private static final int REQUEST_CAMERA = 100;
    public static final int REQUEST_IMAGE_VIEW = 120;

    private static final String KEY_TEMP_FILE = "key_temp_file";


    // image result data set
    private ArrayList<String> resultList = new ArrayList<>();
    // folder result data set
    private ArrayList<Folder> mResultFolder = new ArrayList<>();

    private RecyclerView mRecyclerView;
    private Callback mCallback;

    private ImageAdapter mImageAdapter;
    private FolderAdapter mFolderAdapter;

    private ListPopupWindow mFolderPopupWindow;

    private TextView mCategoryText;
    private View mPopupAnchorView;

    private boolean hasFolderGened = false;

    private File mTmpFile;
    private Context mContext;
    private ArrayList<Media> mAllMediaList = new ArrayList<>();
    private MediaSelectConfig mMediaSelectConfig;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mCallback = (Callback) getActivity();
        } catch (ClassCastException e) {
            throw new ClassCastException("The Activity must implement ImageSelectorFragment.Callback interface...");
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        if (arguments != null) {
            mMediaSelectConfig = arguments.getParcelable(Constant.KEY_MEDIA_SELECT_CONFIG);
        }
        int mode = selectMode();

        if (mode == MediaSelectConfig.MODE_MULTI && mMediaSelectConfig != null
                && mMediaSelectConfig.getOriginData() != null) {
            resultList = mMediaSelectConfig.getOriginData();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mis_fragment_multi_image, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (isOpenCameraOnly()) {
            showCameraAction();
            return;
        }
        mContext = getContext();

        final int mode = selectMode();
        int imageSpanCount = mMediaSelectConfig == null ?
                Constant.DEFAULT_IMAGE_SPAN_COUNT : mMediaSelectConfig.getImageSpanCount();
        mImageAdapter = new ImageAdapter(getActivity(), showCamera(), imageSpanCount);
        mImageAdapter.showSelectIndicator(mode == MediaSelectConfig.MODE_MULTI);

        mPopupAnchorView = view.findViewById(R.id.footer);

        mCategoryText = view.findViewById(R.id.category_btn);
        mCategoryText.setText(R.string.mis_folder_all);
        mCategoryText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (mFolderPopupWindow == null) {
                    createPopupFolderList();
                }

                if (mFolderPopupWindow.isShowing()) {
                    mFolderPopupWindow.dismiss();
                } else {
                    mFolderPopupWindow.show();
                    int index = mFolderAdapter.getSelectIndex();
                    index = index == 0 ? index : index - 1;
                    if (null != mFolderPopupWindow && mFolderPopupWindow.getListView() != null)
                        mFolderPopupWindow.getListView().setSelection(index);
                }
            }
        });

        mRecyclerView = view.findViewById(R.id.grid);
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), imageSpanCount);
        mRecyclerView.setLayoutManager(layoutManager);
        mRecyclerView.addItemDecoration(new DividerGridItemDecoration(mContext, R.drawable.divider));
        mRecyclerView.setAdapter(mImageAdapter);
        mImageAdapter.setOnItemClickListener(new ImageAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(Media media, int position) {
                if (mImageAdapter.isShowCamera()) {
                    if (position == 0) {
                        showCameraAction();
                    } else {
                        openImageActivity(position - 1, mode, media.path);
                    }
                } else {
                    openImageActivity(position - 1, mode, media.path);
                }
            }

            @Override
            public void onCheckClick(Media media, int position) {
                if (mImageAdapter.isShowCamera()) {
                    if (position == 0) {
                        showCameraAction();
                    } else {
                        selectImageFromGrid(media, mode);
                    }
                } else {
                    selectImageFromGrid(media, mode);
                }
            }
        });


        mFolderAdapter = new FolderAdapter(getActivity());
    }

    private void openImageActivity(int position, int mode, String path) {
        if (mode == MediaSelectConfig.MODE_MULTI) {
            Intent intent = new Intent(mContext, ImageActivity.class);
            if (mAllMediaList != null) {

                for (String imagePath : resultList) {
                    if (TextUtils.isEmpty(imagePath))
                        continue;
                    for (Media media : mAllMediaList) {
                        if (imagePath.equals(media.path)) {
                            media.isSelect = true;
                        }
                    }
                }
                DataBundle.get().setMediaList(mAllMediaList);
            }
            intent.putExtra(Constant.KEY_EXTRA_CURRENT_POSITION, position);
            intent.putExtra(Constant.KEY_EXTRA_SELECT_COUNT, selectImageCount());
            startActivityForResult(intent, REQUEST_IMAGE_VIEW);
        } else if (mode == MediaSelectConfig.MODE_SINGLE) {
            if (mCallback != null) {
                mCallback.onSingleImageSelected(path);
            }
        }
    }

    /**
     * Create popup ListView
     */

    private void createPopupFolderList() {
        Point point = ScreenUtils.getScreenSize(getActivity());
        int width = point.x;
        int height = (int) (point.y * (4.5f / 8.0f));
        mFolderPopupWindow = new ListPopupWindow(getActivity());
        mFolderPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        mFolderPopupWindow.setAdapter(mFolderAdapter);
        mFolderPopupWindow.setContentWidth(width);
        mFolderPopupWindow.setWidth(width);
        mFolderPopupWindow.setHeight(height);
        mFolderPopupWindow.setAnchorView(mPopupAnchorView);
        mFolderPopupWindow.setModal(true);
        mFolderPopupWindow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

                mFolderAdapter.setSelectIndex(i);
                final int index = i;
                final AdapterView v = adapterView;

                /* new Handler() */
                mRecyclerView.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mFolderPopupWindow.dismiss();

                        if (index == 0) {
                            getLoaderManager().restartLoader(R.id.loader_all_media_store_data, null,
                                    ImageSelectorFragment.this);
                            mCategoryText.setText(R.string.mis_folder_all);
                            mImageAdapter.setShowCamera(showCamera());
                        } else {
                            Folder folder = (Folder) v.getAdapter().getItem(index);
                            if (null != folder) {
                                if (mAllMediaList.size() > 0)
                                    mAllMediaList.clear();
                                mAllMediaList.addAll(folder.mediaStoreList);
                                mImageAdapter.setData(folder.mediaStoreList);
                                mCategoryText.setText(folder.name);
                                if (resultList != null && resultList.size() > 0) {
                                    mImageAdapter.setDefaultSelected(resultList);
                                }
                            }
                            mImageAdapter.setShowCamera(false);
                        }

                        mRecyclerView.smoothScrollToPosition(0);
                    }
                }, 100);

            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(KEY_TEMP_FILE, mTmpFile);
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        if (savedInstanceState != null) {
            mTmpFile = (File) savedInstanceState.getSerializable(KEY_TEMP_FILE);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // load image data
        getLoaderManager().initLoader(R.id.loader_all_media_store_data, null, this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CAMERA) {
            if (resultCode == Activity.RESULT_OK) {
                if (mTmpFile != null) {
                    if (mCallback != null) {
                        mCallback.onCameraShot(mTmpFile);
                    }
                }
            } else {
                // delete tmp file
                while (mTmpFile != null && mTmpFile.exists()) {
                    boolean success = mTmpFile.delete();
                    if (success) {
                        mTmpFile = null;
                    }
                }
                if (isShowCamera()) {
                    getActivity().finish();
                }
            }
        } else if (requestCode == REQUEST_IMAGE_VIEW) {
            if (resultCode == Activity.RESULT_OK) {
                if (data != null) {
                    resultList = data.getStringArrayListExtra(Constant.KEY_EXTRA_IMAGE_SELECT_LIST);
                    if (resultList != null && resultList.size() > 0) {
                        mImageAdapter.setDefaultSelected(resultList);
                        if (mCallback != null) {
                            mCallback.onImageSelectList(resultList);
                        }
                    }
                }
            }
        }
    }

    private boolean isShowCamera() {
        return mMediaSelectConfig != null && mMediaSelectConfig.isShowCamera();
    }

    private boolean isOpenCameraOnly() {
        return mMediaSelectConfig != null && mMediaSelectConfig.isOpenCameraOnly();
    }

    private boolean isSingleMode() {
        return mMediaSelectConfig != null
                && mMediaSelectConfig.getSelectMode() == MediaSelectConfig.MODE_SINGLE;
    }

    private boolean isMultiMode() {
        return mMediaSelectConfig != null
                && mMediaSelectConfig.getSelectMode() == MediaSelectConfig.MODE_MULTI;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        if (mFolderPopupWindow != null) {
            if (mFolderPopupWindow.isShowing()) {
                mFolderPopupWindow.dismiss();
            }
        }
        super.onConfigurationChanged(newConfig);
    }

    /**
     * Open camera
     */
    private void showCameraAction() {
        if (ContextCompat.checkSelfPermission(getContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    getString(R.string.mis_permission_rationale_write_storage), REQUEST_STORAGE_WRITE_ACCESS_PERMISSION);
        } else {

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                try {
                    mTmpFile = FileUtils.createTmpFile(getActivity());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (mTmpFile != null && mTmpFile.exists()) {
                    // // FIXME: 2017/4/11 修复android N 打开相机崩溃
                    if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(mTmpFile));
                    } else {
                        ContentValues contentValues = new ContentValues(1);
                        contentValues.put(MediaStore.Images.Media.DATA, mTmpFile.getAbsolutePath());
                        Uri uri = getContext().getContentResolver().insert(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
                        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
                    }
                    startActivityForResult(intent, REQUEST_CAMERA);
                } else {
                    Toast.makeText(getActivity(), R.string.mis_error_image_not_exist, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getActivity(), R.string.mis_msg_no_camera, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestPermission(final String permission, String rationale, final int requestCode) {
        if (shouldShowRequestPermissionRationale(permission)) {
            new AlertDialog.Builder(getContext())
                    .setTitle(R.string.mis_permission_dialog_title)
                    .setMessage(rationale)
                    .setPositiveButton(R.string.mis_permission_dialog_ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            requestPermissions(new String[]{permission}, requestCode);
                        }
                    }).setNegativeButton(R.string.mis_permission_dialog_cancel, null).create().show();
        } else {
            requestPermissions(new String[]{permission}, requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_STORAGE_WRITE_ACCESS_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showCameraAction();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    /**
     * notify callback
     *
     * @param media image data
     */
    private void selectImageFromGrid(Media media, int mode) {
        if (media != null) {
            if (mode == MediaSelectConfig.MODE_MULTI) {
                if (resultList.contains(media.path)) {
                    resultList.remove(media.path);
                    if (mCallback != null) {
                        mCallback.onImageUnselected(media.path);
                    }
                } else {
                    if (selectImageCount() == resultList.size()) {
                        Toast.makeText(getActivity(), R.string.mis_msg_amount_limit, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    resultList.add(media.path);
                    if (mCallback != null) {
                        mCallback.onImageSelected(media.path);
                    }
                }
                mImageAdapter.select(media);
            } else if (mode == MediaSelectConfig.MODE_SINGLE) {
                if (mCallback != null) {
                    mCallback.onSingleImageSelected(media.path);
                }
            }
        }
    }

    private boolean showCamera() {
        return mMediaSelectConfig == null || mMediaSelectConfig.isShowCamera();
    }

    private int selectMode() {
        return mMediaSelectConfig == null ? MediaSelectConfig.MODE_MULTI : mMediaSelectConfig.getSelectMode();
    }

    private int selectImageCount() {
        return mMediaSelectConfig == null ? Constant.DEFAULT_IMAGE_SIZE
                : mMediaSelectConfig.getMaxCount();
    }

    @Override
    public Loader<List<Media>> onCreateLoader(int id, Bundle args) {
        return new MediaDataLoader(getActivity(), id, args, mMediaSelectConfig);
    }

    @Override
    public void onLoadFinished(Loader<List<Media>> loader, List<Media> data) {
        if (isOpenCameraOnly())
            return;
        if (loader instanceof MediaDataLoader) {
            MediaDataLoader mediaDataLoader = (MediaDataLoader) loader;
            if (mResultFolder.size() > 0)
                mResultFolder.clear();
            mResultFolder.addAll(mediaDataLoader.getResultFolder());
            if (!hasFolderGened) {
                mFolderAdapter.setData(mResultFolder);
                hasFolderGened = true;
            }
        }

        if (data == null)
            return;
        if (mAllMediaList.size() > 0)
            mAllMediaList.clear();
        mAllMediaList.addAll(data);
        mImageAdapter.setData(data);
        if (resultList != null && resultList.size() > 0) {
            mImageAdapter.setDefaultSelected(resultList);
        }
    }

    @Override
    public void onLoaderReset(Loader<List<Media>> loader) {
        // Do nothing.
    }

    /**
     * Callback for host activity
     */
    public interface Callback {
        void onSingleImageSelected(String path);

        void onImageSelected(String path);

        void onImageUnselected(String path);

        void onCameraShot(File imageFile);

        void onImageSelectList(ArrayList<String> imageList);
    }
}
