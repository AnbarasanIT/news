package com.marketwire.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.view.View
import android.widget.TextView
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : AppCompatActivity() {

    private val REFRESH_MS = 60_000L

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var ticker: TextView
    private lateinit var itemCount: TextView
    private lateinit var lastUpdated: TextView
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var emptyText: TextView

    private val adapter = NewsAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        ticker = findViewById(R.id.ticker)
        itemCount = findViewById(R.id.itemCount)
        lastUpdated = findViewById(R.id.lastUpdated)
        statusDot = findViewById(R.id.statusDot)
        statusText = findViewById(R.id.statusText)
        emptyText = findViewById(R.id.emptyText)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Marquee needs this to actually scroll
        ticker.isSelected = true

        swipeRefresh.setOnRefreshListener { refreshOnce() }

        lifecycleScope.launch {
            while (isActive) {
                refreshOnce()
                delay(REFRESH_MS)
            }
        }
    }

    private fun refreshOnce() {
        lifecycleScope.launch {
            swipeRefresh.isRefreshing = true
            try {
                val snapshot = FeedFetcher.fetchSnapshot()
                render(snapshot)
            } catch (ex: Exception) {
                emptyText.visibility = View.VISIBLE
                emptyText.text = "Couldn't reach the wire: ${ex.message}"
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun render(snapshot: Snapshot) {
        adapter.updateItems(snapshot.items)
        emptyText.visibility = if (snapshot.items.isEmpty()) View.VISIBLE else View.GONE
        emptyText.text = if (snapshot.error != null && snapshot.items.isEmpty())
            "No news yet — some feeds had trouble: ${snapshot.error}"
        else
            "No news items published today yet."

        itemCount.text = "${snapshot.items.size} item${if (snapshot.items.size == 1) "" else "s"} today"
        lastUpdated.text = "last updated ${snapshot.generatedAt}"

        if (snapshot.marketStatus == "open") {
            statusDot.background = ContextCompat.getDrawable(this, R.drawable.dot_green)
            statusText.text = "NSE open"
        } else {
            statusDot.background = ContextCompat.getDrawable(this, R.drawable.dot_red)
            statusText.text = "NSE closed"
        }

        ticker.text = if (snapshot.items.isEmpty())
            "No headlines yet today"
        else
            snapshot.items.joinToString("     ●     ") { it.title }
    }
}
