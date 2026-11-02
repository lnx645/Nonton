package id.nonton.app.core

import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ContentNotSupportedException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.search.SearchExtractor
import java.io.IOException
import java.net.SocketTimeoutException

fun Throwable.userMessage(): String = when (this) {
    is ReCaptchaException ->
        "Layanan meminta verifikasi (bot/captcha). Coba lagi nanti atau pilih layanan lain."
    is AgeRestrictedContentException ->
        "Konten ini dibatasi usia dan tidak dapat diputar tanpa login."
    is ContentNotAvailableException ->
        "Konten tidak tersedia, dihapus, atau diblokir di wilayah ini."
    is ContentNotSupportedException ->
        "Jenis konten ini belum didukung."
    is SearchExtractor.NothingFoundException ->
        "Tidak ada hasil untuk pencarian ini."
    is SocketTimeoutException ->
        "Koneksi terlalu lama. Periksa jaringan lalu coba lagi."
    is IOException ->
        "Tidak dapat terhubung ke layanan. Periksa koneksi internetmu."
    is ExtractionException ->
        message?.takeIf { it.isNotBlank() } ?: "Gagal mengambil data dari layanan."
    else ->
        message?.takeIf { it.isNotBlank() } ?: "Terjadi kesalahan yang tidak terduga."
}
