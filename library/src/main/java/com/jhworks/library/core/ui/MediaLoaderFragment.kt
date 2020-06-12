package com.jhworks.library.core.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import com.jhworks.library.core.MediaConstant.DEFAULT_IMAGE_SIZE
import com.jhworks.library.core.MediaConstant.DEFAULT_IMAGE_SPAN_COUNT
import com.jhworks.library.core.MediaConstant.KEY_MEDIA_SELECT_CONFIG
import com.jhworks.library.core.MediaLoader
import com.jhworks.library.core.vo.FolderVo
import com.jhworks.library.core.vo.MediaConfigVo
import com.jhworks.library.core.vo.MediaVo
import com.jhworks.library.core.vo.SelectMode

/**
 *
 * @author jackson
 * @version 1.0
 * @date 2020/6/11 11:06
 */
abstract class MediaLoaderFragment : Fragment(), LoaderManager.LoaderCallbacks<MutableList<MediaVo>> {
    protected var mMediaConfig: MediaConfigVo? = null


    // folder result data set
    protected val mResultFolder = arrayListOf<FolderVo>()
    protected val mAllMediaList = arrayListOf<MediaVo>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mMediaConfig = arguments?.getParcelable(KEY_MEDIA_SELECT_CONFIG)
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<MutableList<MediaVo>> {
        return MediaLoader(activity!!, id, args, mMediaConfig)
    }

    override fun onLoadFinished(loader: Loader<MutableList<MediaVo>>, data: MutableList<MediaVo>?) {
        if (isOpenCameraOnly()) return
        data ?: return
        if (loader is MediaLoader) {
            if (mResultFolder.isNotEmpty()) mResultFolder.clear()
            mResultFolder.addAll(loader.getResultFolder())
            if (mAllMediaList.isNotEmpty()) mAllMediaList.clear()
            mAllMediaList.addAll(data)
            // 更新ui
            onLoadFinishedUpdateUi(data)
        }
    }

    override fun onLoaderReset(loader: Loader<MutableList<MediaVo>>) {

    }

    protected fun isOpenCameraOnly(): Boolean {
        return mMediaConfig != null && mMediaConfig!!.openCameraOnly
    }

    protected fun selectMode(): Int {
        return if (mMediaConfig == null) SelectMode.MODE_MULTI else mMediaConfig!!.selectMode
    }

    protected fun selectImageCount(): Int {
        return if (mMediaConfig == null) DEFAULT_IMAGE_SIZE else mMediaConfig!!.maxCount
    }

    protected fun imageSpanCount(): Int {
        return if (mMediaConfig == null) DEFAULT_IMAGE_SPAN_COUNT else mMediaConfig!!.imageSpanCount
    }

    protected fun showCamera(): Boolean {
        return mMediaConfig != null && mMediaConfig!!.isShowCamera
    }

    abstract fun onLoadFinishedUpdateUi(data: MutableList<MediaVo>?)
}