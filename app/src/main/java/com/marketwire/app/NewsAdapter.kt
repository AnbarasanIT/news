package com.marketwire.app

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class NewsAdapter(private var items: List<NewsItem>) :
    RecyclerView.Adapter<NewsAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateline: TextView = itemView.findViewById(R.id.dateline)
        val headline: TextView = itemView.findViewById(R.id.headline)
        val excerpt: TextView = itemView.findViewById(R.id.excerpt)
        val readMore: TextView = itemView.findViewById(R.id.readMore)
    }

    fun updateItems(newItems: List<NewsItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.dateline.text = "${item.source.uppercase()}  ·  ${item.publishedIst}"
        holder.headline.text = item.title
        holder.excerpt.text = item.summary

        val openLink = View.OnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
            it.context.startActivity(intent)
        }
        holder.headline.setOnClickListener(openLink)
        holder.readMore.setOnClickListener(openLink)
    }

    override fun getItemCount(): Int = items.size
}
