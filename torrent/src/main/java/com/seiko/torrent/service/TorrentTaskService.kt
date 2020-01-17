package com.seiko.torrent.service

import android.app.IntentService
import android.content.Context
import android.content.Intent
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.ToastUtils
import com.seiko.common.eventbus.EventBusScope
import com.seiko.core.data.Result
import com.seiko.torrent.model.AddTorrentParams
import com.seiko.torrent.model.PostEvent
import com.seiko.torrent.model.toTask
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

class TorrentTaskService : IntentService("TorrentTaskService") {

    companion object {

        private const val ACTION_ADD_TORRENT = "ACTION_ADD_TORRENT"
        private const val ACTION_DEL_TORRENT = "ACTION_DEL_TORRENT"
        private const val ACTION_SHUT_DOWN = "ACTION_SHUT_DOWN"

        private const val EXTRA_ADD_TORRENT_PARAMS = "EXTRA_ADD_TORRENT_PARAMS"
        private const val EXTRA_DEL_HASH = "EXTRA_DEL_HASH"
        private const val EXTRA_WITH_FILE = "EXTRA_WITH_FILE"

        /**
         * 添加种子任务
         */
        @JvmStatic
        fun addTorrent(context: Context, params: AddTorrentParams) {
            val intent = Intent(context, TorrentTaskService::class.java)
            intent.action = ACTION_ADD_TORRENT
            intent.putExtra(EXTRA_ADD_TORRENT_PARAMS, params)
            context.startService(intent)
        }

        /**
         * 删除种子任务
         */
        @JvmStatic
        fun delTorrent(context: Context, hash: String, withFile: Boolean) {
            val intent = Intent(context, TorrentTaskService::class.java)
            intent.action = ACTION_DEL_TORRENT
            intent.putExtra(EXTRA_DEL_HASH, hash)
            intent.putExtra(EXTRA_WITH_FILE, withFile)
            context.startService(intent)
        }

        /**
         * 关闭服务
         */
        @JvmStatic
        fun shutDown(context: Context) {
            val intent = Intent(context, TorrentTaskService::class.java)
            intent.action = ACTION_SHUT_DOWN
            context.startService(intent)
        }
    }


    override fun onHandleIntent(intent: Intent?) {
        if (intent == null) return
        when(intent.action) {
            ACTION_ADD_TORRENT -> {
                if (intent.hasExtra(EXTRA_ADD_TORRENT_PARAMS)) {
                    val params: AddTorrentParams = intent.getParcelableExtra(EXTRA_ADD_TORRENT_PARAMS)!!
                    addTorrent(params)
                }
            }
            ACTION_DEL_TORRENT -> {
                if (intent.hasExtra(EXTRA_DEL_HASH)) {
                    val hash = intent.getStringExtra(EXTRA_DEL_HASH)!!
                    val withFile = intent.getBooleanExtra(EXTRA_WITH_FILE, true)
                    delTorrent(hash, withFile)
                }
            }
            ACTION_SHUT_DOWN -> {
                shutDown()
            }
        }
    }

    private val downloader: Downloader by inject()

    /**
     * 添加 种子任务
     */
    private fun addTorrent(params: AddTorrentParams) = GlobalScope.launch {
        val task = params.toTask()
        when(val result = downloader.start(task, params.fromMagnet)) {
            is Result.Success -> {
                EventBusScope.getDefault().post(PostEvent.TorrentAdded(task))
            }
            is Result.Error -> {
                LogUtils.w(result.exception.message)
                ToastUtils.showShort(result.exception.message)
            }
        }
    }

    /**
     * 删除 种子任务
     */
    private fun delTorrent(hash: String, withFile: Boolean) {
        downloader.deleteTorrent(hash, withFile)
        EventBusScope.getDefault().post(PostEvent.TorrentRemoved(hash))
    }

    /**
     * 关闭种子下载引擎
     */
    private fun shutDown() {
        downloader.release()
    }
}