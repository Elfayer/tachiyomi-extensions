package eu.kanade.tachiyomi.extension.fr.scanfr

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import java.text.SimpleDateFormat
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class ScanFR : ParsedHttpSource() {

    override val name = "ScanFR"

    override val baseUrl = "https://www.scan-fr.co"

    override val lang = "fr"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/filterList?page=$page&cat=&alpha=&sortBy=views&asc=false&author=&tag=", headers)
    }

    override fun popularMangaSelector() = "div.media"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.title = element.select("a.chart-title strong").text().trim()
        manga.setUrlWithoutDomain(element.select("a.chart-title").attr("abs:href"))
        manga.thumbnail_url = element.select("img").attr("abs:src")
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET(baseUrl, headers)
    }

    override fun latestUpdatesSelector() = ".mangalist .manga-item"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select(".manga-heading a").text().trim()
        setUrlWithoutDomain(element.select(".manga-heading a").attr("abs:href"))
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response, query)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val uri = Uri.parse("$baseUrl/search").buildUpon()
            .appendQueryParameter("query", query)
        return GET(uri.toString(), headers)
    }

    private fun searchMangaParse(response: Response, query: String): MangasPage {
        val jsonData = response.body()!!.string()
        val jsonObject = JSONObject(jsonData)
        val jsonArray = jsonObject.getJSONArray("suggestions")
        val mangas = ArrayList<SManga>()

        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)

            mangas.add(SManga.create().apply {
                title = obj.getString("value")
                setUrlWithoutDomain("$baseUrl/manga/${obj.getString("data")}")
            })
        }

        return MangasPage(mangas, false)
    }

    override fun searchMangaSelector() = throw Exception("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw Exception("Not used")

    override fun searchMangaNextPageSelector(): String? = null

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = document.select("img.img-responsive").attr("abs:src")
        manga.description = document.select(".well p").text()
        manga.author = document.select("a[href*=author]").text()
        val glist = document.select("a[href*=category]").map { it.text() }
        manga.genre = glist.joinToString(", ")
        manga.status = when (document.select("span.label").text()) {
            "En cours" -> SManga.ONGOING
            "Complete" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        return manga
    }

    // Chapters

    override fun chapterListSelector() = "ul.chapters888 li"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.name = element.select("h5").text().trim()
        chapter.setUrlWithoutDomain(element.select("a").attr("href"))
        chapter.date_upload = parseDate(element.select("div.date-chapter-title-rtl").text())
        return chapter
    }

    private fun parseDate(date: String): Long {
        if (date.isEmpty()) {
            return 0L
        }
        return SimpleDateFormat("dd MMM. yyyy", Locale.US).parse(date).time
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val pages = mutableListOf<Page>()

        document.select("#all .img-responsive").forEachIndexed { i, img ->
            pages.add(Page(i, "", img.attr("abs:data-src")))
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = throw Exception("Not used")

    override fun getFilterList() = FilterList()
}
