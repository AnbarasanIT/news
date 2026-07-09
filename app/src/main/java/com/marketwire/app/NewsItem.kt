package com.marketwire.app

data class NewsItem(
    val title: String,
    val link: String,
    val source: String,
    val publishedIst: String,
    val summary: String,
    val sortMillis: Long
)

data class Snapshot(
    val items: List<NewsItem>,
    val marketStatus: String,   // "open" or "closed"
    val generatedAt: String,
    val error: String?
)
