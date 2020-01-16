package com.seiko.torrent.vm

import android.net.Uri
import android.webkit.URLUtil
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.LogUtils
import com.seiko.common.BaseViewModel
import com.seiko.core.domain.torrent.GetTorrentTempWithContentUseCase
import com.seiko.core.domain.torrent.GetTorrentTempWithNetUseCase
import com.seiko.core.data.Result
import com.seiko.torrent.constants.*
import com.seiko.torrent.extensions.find
import com.seiko.torrent.extensions.getLeaves
import com.seiko.torrent.extensions.toFileTree
import com.seiko.torrent.model.AddTorrentParams
import com.seiko.torrent.model.filetree.BencodeFileTree
import com.seiko.torrent.service.Downloader
import com.seiko.torrent.ui.add.State
import com.seiko.download.torrent.model.MagnetInfo
import com.seiko.download.torrent.model.TorrentMetaInfo
import com.seiko.torrent.extensions.isMagnet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.libtorrent4j.Priority
import java.io.File

class AddTorrentViewModel(
    private val downloader: Downloader,
    private val torrentDownloadDir: File,
    private val getTorrentTempWithContentUseCase: GetTorrentTempWithContentUseCase,
    private val getTorrentTempWithNetUseCase: GetTorrentTempWithNetUseCase
) : BaseViewModel() {

    /**
     * 当前状态
     */
    private val _state = MutableLiveData<Result<Int>>()
    val state: LiveData<Result<Int>> = _state

    /**
     * 磁力信息
     */
    private val _magnetInfo = MutableLiveData<MagnetInfo>()
    val magnetInfo: LiveData<MagnetInfo> = _magnetInfo

    /**
     * 种子信息
     */
    private val _torrentMetaInfo = MutableLiveData<TorrentMetaInfo>()
    val torrentMetaInfo: LiveData<TorrentMetaInfo> = _torrentMetaInfo

    /**
     * 文件树
     */
    private val _fileTree = MutableLiveData<BencodeFileTree>()
    val fileTree: LiveData<BencodeFileTree> = _fileTree

    /**
     * 种子下载路径
     */
    private val _downloadDir = MutableLiveData<File>()
    val downloadDir: LiveData<File> = _downloadDir

    // 自动下载
    var autoStart = true
    // 顺序下载
    var isSequentialDownload = false
    // 自定义名称
    var customName = ""

    private var source = ""
    private var fromMagnet = false

    fun loadData() {
        _downloadDir.value = torrentDownloadDir
    }

    fun decodeUri(uri: Uri) = viewModelScope.launch {
        val path = uri.toString()
        when {
            path.isMagnet() -> {
                fromMagnet = true
                source = uri.toString()
                _magnetInfo.value = downloader.fetchMagnet(source) { info ->
                    updateState(State.FETCHING_MAGNET_COMPLETED)
                    updateTorrentInfo(info)
                }
                updateState(State.FETCHING_MAGNET)
            }
            URLUtil.isContentUrl(path) -> {
                updateState(State.FETCHING_HTTP)
                delay(50)
                when(val result = getTorrentTempWithNetUseCase.invoke(source)) {
                    is Result.Success -> {
                        source = result.data
                        updateTorrentInfo(TorrentMetaInfo(source))
                    }
                    is Result.Error -> Result.Error(result.exception)
                }
                updateState(State.FETCHING_HTTP_COMPLETED)
            }
            URLUtil.isFileUrl(path) -> {
                updateState(State.DECODE_TORRENT_FILE)
                delay(50)
                source = uri.path!!
                updateTorrentInfo(TorrentMetaInfo(source))
                updateState(State.DECODE_TORRENT_COMPLETED)
            }
            URLUtil.isContentUrl(path) -> {
                updateState(State.DECODE_TORRENT_FILE)
                delay(50)
                when(val result = getTorrentTempWithContentUseCase.invoke(uri)) {
                    is Result.Success -> {
                        source = result.data
                        updateTorrentInfo(TorrentMetaInfo(source))
                    }
                    is Result.Error -> Result.Error(result.exception)
                }
                updateState(State.DECODE_TORRENT_COMPLETED)
            }
            else -> {
                Result.Error(IllegalArgumentException("Unknown link/path type: ${uri.scheme}"))
                return@launch
            }
        }
    }

    private fun updateState(state: Int) {
        _state.value = Result.Success(state)
    }

    private fun updateTorrentInfo(info: TorrentMetaInfo) {
        _torrentMetaInfo.value = info

        val fileTree = info.fileList.toFileTree()

        val priorities = magnetInfo.value?.filePriorities
        if (priorities.isNullOrEmpty()) {
            fileTree.select(true)
        } else {
            val size = priorities.size.coerceAtMost(info.fileCount)
            for (i in 0 until size) {
                if (priorities[i] == Priority.IGNORE) {
                    continue
                }
                val file = fileTree.find(i) ?: continue
                file.select(true)
            }
        }
        _fileTree.value = fileTree
    }

    /**
     * 创建种子任务
     */
    fun buildTorrentTask(): Result<AddTorrentParams> {

        // 种子解析完成，有信息
        val info = torrentMetaInfo.value ?: return Result.Error(Exception("种子尚未解析完成"))

        // 有有效的存储名称
        val name = if (customName.isNotEmpty()) customName else info.torrentName
        if (name.isEmpty()) {
            return Result.Error(Exception("没有有效的存储名称"))
        }

        // 种子文件数 > 0
        if (info.fileCount == 0) {
            return Result.Error(Exception("种子文件数为0"))
        }

        // 获取需要下载的文件Index
        val fileTree = fileTree.value ?: return Result.Error(Exception("没有有效的文件目录"))
        val selectedIndexes = fileTree.getLeaves().filter { it.isSelected }.map { it.index }
        if (selectedIndexes.isEmpty()) {
            return Result.Error(Exception("没有选中的下载文件"))
        }

        //
        val priorities: List<Priority> = if (info.fileCount == selectedIndexes.size) {
            MutableList(info.fileCount) { Priority.DEFAULT }
        } else {
            MutableList(info.fileCount) { i ->
                if (selectedIndexes.contains(i)) {
                    Priority.DEFAULT
                } else {
                    Priority.IGNORE
                }
            }
        }

        val params = AddTorrentParams(
            source = source,
            fromMagnet = fromMagnet,
            sha1hash = info.sha1Hash,
            name = info.torrentName,
            filePriorities = priorities,
            pathToDownload = _downloadDir.value!!.absolutePath,
            sequentialDownload = isSequentialDownload,
            addPaused = !autoStart)
        return Result.Success(params)
    }

    override fun onCleared() {
        val hash = _magnetInfo.value?.sha1hash
        if (hash != null) {
            downloader.cancelFetchMagnet(hash)
        }
        super.onCleared()
    }

}