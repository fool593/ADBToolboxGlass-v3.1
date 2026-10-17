package com.example.adbtoolbox.common

/**
 * 多线程下载器（expect）。
 *
 * 功能：
 * - 多线程分块下载（Range 分段，每块一个连接并行写 .part 分片）；
 * - 断点续传：元数据文件（destPath + ".dlm"）记录 URL/总大小/每分片已写字节；
 *   再次调用同 dest 时自动从断点继续（跳过已完成字节）；
 * - 服务端不支持 Range 时自动退化为单线程整包下载；
 * - 完成后合并分片、校验总大小、清理临时文件。
 *
 * 通过 [download] 的 onProgress 回报 (已下载字节, 总字节)，供 UI 显示进度。
 */
expect object Downloader {
    suspend fun download(
        url: String,
        destPath: String,
        threads: Int = 4,
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> }
    ): DownloadResult
}

/** 下载结果。ok=false 时 message 为失败原因（含断点信息，可直接再调 resume）。 */
data class DownloadResult(val ok: Boolean, val message: String, val total: Long = 0L)