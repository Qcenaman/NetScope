package com.example.wifiscanner

import android.net.wifi.ScanResult
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class WifiAdapter(
    private val onConnectClick: (ScanResult) -> Unit
) : RecyclerView.Adapter<WifiAdapter.WifiViewHolder>() {

    private var networks = emptyList<ScanResult>()

    inner class WifiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvSsid: TextView = view.findViewById(R.id.tvSsid)
        val tvBssid: TextView = view.findViewById(R.id.tvBssid)
        val tvLevel: TextView = view.findViewById(R.id.tvLevel)
        val tvSecurity: TextView = view.findViewById(R.id.tvSecurity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_wifi, parent, false)
        return WifiViewHolder(view)
    }

    override fun onBindViewHolder(holder: WifiViewHolder, position: Int) {
        val result = networks[position]
        holder.tvSsid.text = result.SSID
        holder.tvBssid.text = result.BSSID
        holder.tvLevel.text = "${result.level} dBm"
        holder.tvSecurity.text = getSecurityType(result)

        // Color code by signal strength
        val level = result.level
        holder.tvSsid.setTextColor(
            when {
                level > -60 -> holder.tvSsid.context.getColor(android.R.color.holo_green_dark)
                level > -70 -> holder.tvSsid.context.getColor(android.R.color.holo_orange_dark)
                else -> holder.tvSsid.context.getColor(android.R.color.holo_red_dark)
            }
        )

        holder.itemView.setOnClickListener { onConnectClick(result) }
    }

    override fun getItemCount() = networks.size

    private fun getSecurityType(result: ScanResult): String {
        return when {
            result.capabilities.contains("WPA") -> "🔒 WPA/WPA2"
            result.capabilities.contains("WEP") -> "🔒 WEP"
            result.capabilities.isEmpty() -> "🔓 Open"
            else -> "🔒 Unknown"
        }
    }

    fun updateList(newNetworks: List<ScanResult>) {
        networks = newNetworks.sortedByDescending { it.level }
        notifyDataSetChanged()
    }
}
