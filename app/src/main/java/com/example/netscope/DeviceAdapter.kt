package com.example.netscope

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DeviceAdapter(private val devices: List<NetworkDevice>) :
    RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvIp: TextView = view.findViewById(R.id.tvIp)
        val tvHostname: TextView = view.findViewById(R.id.tvHostname)
        val openPortsContainer: ViewGroup = view.findViewById(R.id.openPortsContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.tvIp.text = device.ip
        holder.tvHostname.text = device.hostname

        // Clear previous ports
        holder.openPortsContainer.removeAllViews()

        if (device.openPorts.isEmpty()) {
            val view = LayoutInflater.from(holder.itemView.context).inflate(
                R.layout.item_port, holder.openPortsContainer, false
            )
            val portText = view.findViewById<TextView>(R.id.tvPort)
            val serviceText = view.findViewById<TextView>(R.id.tvService)
            portText.text = "—"
            serviceText.text = "No open ports detected"
            holder.openPortsContainer.addView(view)
        } else {
            for ((port, service) in device.openPorts) {
                val view = LayoutInflater.from(holder.itemView.context).inflate(
                    R.layout.item_port, holder.openPortsContainer, false
                )
                val portText = view.findViewById<TextView>(R.id.tvPort)
                val serviceText = view.findViewById<TextView>(R.id.tvService)
                portText.text = "Port $port"
                serviceText.text = service
                holder.openPortsContainer.addView(view)
            }
        }
    }

    override fun getItemCount() = devices.size
}
