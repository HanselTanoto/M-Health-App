package com.example.m_health.ui.home

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.m_health.databinding.CardAddBpUserBinding
import com.example.m_health.databinding.FragmentHomeBinding
import com.example.m_health.model.FirestoreDataViewModel
import com.example.m_health.model.adapter.BPRecordAdapter
import com.example.m_health.model.data.BPRecord
import com.example.m_health.model.data.BPUser
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max


class HomeFragment : Fragment(), OnItemSelectedListener {

    private var _binding: FragmentHomeBinding? = null
    private var _addBPUserCard: CardAddBpUserBinding? = null
    private val binding get() = _binding!!      // This property is only valid between onCreateView and onDestroyView.
    private val addBPUserCard get() = _addBPUserCard!!

    private lateinit var safeContext: Context
    private lateinit var firestoreDataViewModel: FirestoreDataViewModel

    // UI Components
    private lateinit var userNameHeaderText: TextView
    private lateinit var bpUserSelector: Spinner
    private lateinit var addBPUserButton: Button
    private lateinit var bpRecordChart: LineChart
    private lateinit var latestSysValue: TextView
    private lateinit var latestDiaValue: TextView
    private lateinit var latestPulValue: TextView
    private lateinit var bpRecordRecyclerView: RecyclerView
    private lateinit var bpRecordEmptyMessage: TextView

    private lateinit var bpUserList: MutableList<BPUser>
    private lateinit var bpUserNameList: Array<String>
    private lateinit var bpUserSelected: BPUser
    private lateinit var bpUserListAdapter: ArrayAdapter<Any?>
    private lateinit var bpRecordAdapter: BPRecordAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        safeContext = context
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root
        _addBPUserCard = CardAddBpUserBinding.inflate(inflater, container, false)

        firestoreDataViewModel = ViewModelProvider(this)[FirestoreDataViewModel::class.java]

        // UI Binding
        userNameHeaderText      = binding.homeHeaderText2
        bpUserSelector          = binding.userSelector
        addBPUserButton         = binding.addUserButton
        bpRecordChart           = binding.chart
        latestSysValue          = binding.latestSysValue
        latestDiaValue          = binding.latestDiaValue
        latestPulValue          = binding.latestPulValue
        bpRecordRecyclerView    = binding.bpLogRecyclerView
        bpRecordEmptyMessage    = binding.emptyLogMessage

        // Header Setup
        firestoreDataViewModel.authUser.observe(viewLifecycleOwner) {
            if (it != null) {
                val name = it.name
                userNameHeaderText.text = name.substring(0, name.indexOf("(")).trim()
            }
        }

        // User Selector Setup
        bpUserList = mutableListOf()
        bpUserSelector.onItemSelectedListener = this
        firestoreDataViewModel.bpUserList.observe(viewLifecycleOwner) { item ->
            bpUserList = item
            bpUserNameList = bpUserList.map { it.name }.toTypedArray()
            Log.d(TAG, "User Count: ${bpUserNameList.size}")
            bpUserListAdapter = ArrayAdapter<Any?>(
                safeContext,
                android.R.layout.simple_spinner_item,
                bpUserNameList
            )
            bpUserListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            bpUserSelector.adapter = bpUserListAdapter
        }

        // BP Record Recycler View Setup
        bpRecordRecyclerView.layoutManager = LinearLayoutManager(safeContext)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        addBPUserButton.setOnClickListener {
            addNewBPUser()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        firestoreDataViewModel.getBPUserData()
        bpUserListAdapter.notifyDataSetChanged()
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        bpUserSelected = bpUserList[position]
        if (bpUserSelected.records.isNotEmpty()) {
            bpRecordEmptyMessage.visibility = View.GONE
        } else {
            bpRecordEmptyMessage.visibility = View.VISIBLE
        }
        bpRecordAdapter = BPRecordAdapter(safeContext, bpUserSelected.records.asReversed(), bpUserSelected)
        bpRecordRecyclerView.adapter = bpRecordAdapter
        setupChart()
        setLatestRecordData()
    }

    override fun onNothingSelected(p0: AdapterView<*>?) {
    }

    private fun addNewBPUser() {
        val addBPUserDialog = AlertDialog.Builder(safeContext)
        val addBPUserLayout = addBPUserCard.root
        if (addBPUserLayout.parent != null) {
            (addBPUserLayout.parent as ViewGroup).removeView(addBPUserLayout)
        }
        addBPUserDialog.setView(addBPUserLayout)
        addBPUserDialog.setTitle("Add New User")
        addBPUserDialog.setPositiveButton("Confirm") { dialog, _ ->
            val name = addBPUserCard.nameInput.text.toString()
            val weight = addBPUserCard.weightInput.text.toString()
            val height = addBPUserCard.heightInput.text.toString()
            val datePicker = addBPUserCard.birthdateInput
            val calendar = Calendar.getInstance()
            calendar.set(datePicker.year, datePicker.month, datePicker.dayOfMonth)
            val birthdate = Timestamp(calendar.time)
            if (name.isNotEmpty() && weight.isNotEmpty() && height.isNotEmpty()) {
                Log.i(TAG, "Adding $name as new user...")
                firestoreDataViewModel.setBPUserData(name, weight.toInt(), height.toInt(), birthdate)
                Snackbar.make(requireView(), "New user added successfully", Snackbar.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Snackbar.make(requireView(), "Please fill out all fields", Snackbar.LENGTH_SHORT).show()
            }
        }
        addBPUserDialog.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
        addBPUserDialog.show()
    }

    private fun setupChart() {
        val records = bpUserSelected.records
        if (records.isEmpty()) {
            bpRecordChart.clear()
            return
        }
        val chartData = getChartData(records)
        bpRecordChart.data = LineData(chartData[0], chartData[1], chartData[2])
        bpRecordChart.description.isEnabled = false
        val xAxis = bpRecordChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(getChartLabel(records))
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true
        xAxis.axisMinimum = 0f
        xAxis.labelRotationAngle = 90f
        xAxis.yOffset = 15f
        xAxis.textSize = 12f
        val leftYAxis = bpRecordChart.axisLeft
        leftYAxis.xOffset = 15f
        leftYAxis.textSize = 12f
        val rightYAxis = bpRecordChart.axisRight
        rightYAxis.xOffset = 15f
        rightYAxis.textSize = 12f
        bpRecordChart.legend.isEnabled = false
        bpRecordChart.setVisibleXRangeMaximum(7f)
        bpRecordChart.moveViewToX(max(chartData[0].entryCount - 8f, 0f))
        bpRecordChart.setPinchZoom(true)
        bpRecordChart.setTouchEnabled(true)
        bpRecordChart.isDragEnabled = true
        bpRecordChart.animateXY(1500,1500)
        bpRecordChart.invalidate()
    }

    private fun getChartData(records: MutableList<BPRecord>) : List<LineDataSet> {
        val sysValueSet = arrayListOf<Entry>()
        val diaValueSet = arrayListOf<Entry>()
        val pulValueSet = arrayListOf<Entry>()
        for (i in 0..<records.size) {
            sysValueSet.add(Entry(i.toFloat(), records[i].sys.toFloat()))
            diaValueSet.add(Entry(i.toFloat(), records[i].dia.toFloat()))
            pulValueSet.add(Entry(i.toFloat(), records[i].pul.toFloat()))
        }
        val sysDataSet = LineDataSet(sysValueSet, "SYS")
        val diaDataSet = LineDataSet(diaValueSet, "DIA")
        val pulDataSet = LineDataSet(pulValueSet, "PUL")

        sysDataSet.color = Color.BLUE
        sysDataSet.setCircleColor(Color.BLUE)
        sysDataSet.setDrawFilled(true)
        sysDataSet.fillColor = Color.BLUE
        sysDataSet.fillAlpha = 50

        diaDataSet.color = Color.GREEN
        diaDataSet.setCircleColor(Color.GREEN)
        diaDataSet.setDrawFilled(true)
        diaDataSet.fillColor = Color.GREEN
        diaDataSet.fillAlpha = 50

        pulDataSet.color = Color.RED
        pulDataSet.setCircleColor(Color.RED)
        pulDataSet.setDrawFilled(true)
        pulDataSet.fillColor = Color.RED
        pulDataSet.fillAlpha = 50

        return listOf(sysDataSet, diaDataSet, pulDataSet)
    }

    private fun getChartLabel(records: MutableList<BPRecord>) : List<String> {
        val xAxis = arrayListOf<String>()
        for (i in 0..<records.size) {
            xAxis.add(formatFirebaseTimestamp(records[i].time))
        }
        return xAxis
    }

    private fun formatFirebaseTimestamp(timestamp: Timestamp) : String {
        val dateFormat = SimpleDateFormat("MMM dd, yyyy - HH:mm:ss", Locale.getDefault())
        val date = Date(timestamp.seconds * 1000 + timestamp.nanoseconds / 1000000)
        return dateFormat.format(date)
    }

    private fun setLatestRecordData() {
        val bpRecord = bpUserSelected.records
        if (bpRecord.isEmpty()) {
            latestSysValue.text = "-"
            latestDiaValue.text = "-"
            latestPulValue.text = "-"
        } else {
            latestSysValue.text = bpRecord.last().sys.toString()
            latestDiaValue.text = bpRecord.last().dia.toString()
            latestPulValue.text = bpRecord.last().pul.toString()
        }
    }

    companion object {
        private const val TAG = "Home"
    }
}