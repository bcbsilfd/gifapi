package com.example.gifapp.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StableIdKeyProvider
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.gifapp.R
import com.example.gifapp.data.FileRepository
import com.example.gifapp.model.Gif
import com.example.gifapp.ui.adapters.GifFavoriteAdapter
import com.example.gifapp.ui.adapters.GifFavoriteLookup
import com.example.gifapp.utils.Constants.GIF_DESC
import com.example.gifapp.utils.Constants.GIF_ID
import com.example.gifapp.utils.Constants.GIF_URI
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val TAG = "GifFavoriteFragment"

class GifFavoriteFragment : Fragment() {

    private lateinit var rv: RecyclerView
    private lateinit var adapter: GifFavoriteAdapter
    private lateinit var repository: FileRepository
    private var gif: Gif? = null
    private var selectedCount: Int = 0

    // Multi selection
    private var tracker: SelectionTracker<Long>? = null

    private lateinit var bottomLayout: ConstraintLayout
    private lateinit var deleteBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var selectedTv: TextView
    private lateinit var selectAllCb: CheckBox
    private var sheetBehavior: BottomSheetBehavior<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null)
            tracker?.onRestoreInstanceState(savedInstanceState)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        tracker?.onSaveInstanceState(outState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_favorite, container, false)

        adapter = GifFavoriteAdapter(listOf())
        adapter.gif.observe(viewLifecycleOwner, { mGif ->
            gif = mGif
            gif?.let {
                val args = Bundle().apply {
                    Log.i(TAG, "--> onCreateView: id[${it.id}]")
                    this.putString(GIF_ID, it.id)
                    Log.i(TAG, "--> onCreateView: description[${it.description}]")
                    this.putString(GIF_DESC, it.description)
                    Log.i(TAG, "--> onCreateView: uri[${it.gifURL}]")
                    this.putString(GIF_URI, it.gifURL)
                }
                if (it.gifURL.isNotEmpty() && adapter.isEditable.value == false) {
                    // If dialog was created dismiss
                    Log.i(TAG, "--> onCreateView: moveToFullScreen")

                    val fragment = GifFullScreenFragment()
                    fragment.arguments = args
                    fragment.show(childFragmentManager, "GifFullScreenFragment")
                }
            }
        })



        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        Log.i(TAG, "--> onViewCreated: ")
        initViews(view)

        CoroutineScope(Dispatchers.Main).launch {
            adapter.isSelected.collect {
                it?.let {
                    if (it) {
                        sheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                }
            }
            adapter.selectedData.collect {
                adapter.updateData(it)
            }

        }

        selectAllCb.setOnCheckedChangeListener { buttonView, isChecked ->
            if (isChecked) {
                Log.i(TAG, "--> onViewCreated: ${adapter.itemCount}")
                for (i in 0..adapter.itemCount){
                    tracker?.select(i.toLong())
                }
            } else {
                for (i in 0..adapter.itemCount){
                    tracker?.deselect(i.toLong())
                }

            }
        }

        repository = FileRepository.getInstance(requireContext())

        CoroutineScope(Dispatchers.Main).launch {
            adapter.selectedCount.collect {
                Log.i(TAG, "--> onViewCreated: selected.count=$it")
                selectedCount = it
                selectedTv.text = "Selected: $it"
                if (it == 0) selectAllCb.isChecked = false
            }
        }
    }

    private fun initViews(view: View) {
        rv = view.findViewById(R.id.favorite_rv)
        rv.layoutManager = GridLayoutManager(requireContext(), 3)
        rv.setHasFixedSize(true)
        rv.adapter = adapter

        // Multi selection
        tracker = SelectionTracker.Builder(
            "selection_to_delete",
            rv,
            StableIdKeyProvider(rv),
            GifFavoriteLookup(rv),
            StorageStrategy.createLongStorage()
        ).withSelectionPredicate(
            SelectionPredicates.createSelectAnything()
        ).build()

        adapter.setTracker(tracker)

        bottomLayout = view.findViewById(R.id.favorite_top_menu)
        deleteBtn = view.findViewById(R.id.favorite_bottom_delete_btn)
        cancelBtn = view.findViewById(R.id.favorite_bottom_cancel_btn)
        selectAllCb = view.findViewById(R.id.favorite_bottom_check)
        selectedTv = view.findViewById(R.id.favorite_bottom_select_tv)
        selectedTv.text = "Selected: $selectedCount"


        deleteBy()

        sheetBehavior = BottomSheetBehavior.from(bottomLayout)
        sheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        sheetBehavior?.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        Log.i(
                            TAG,
                            "--> onStateChanged: State_Hidden"
                        )
                        adapter.clearSelection()
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        Log.i(TAG, "--> onStateChanged: State_Expanded")
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> Log.i(
                        TAG,
                        "--> onStateChanged: State_Collapsed"
                    )
                    BottomSheetBehavior.STATE_DRAGGING -> Log.i(
                        TAG,
                        "--> onStateChanged: State_Dragging"
                    )
                    BottomSheetBehavior.STATE_SETTLING -> Log.i(
                        TAG,
                        "--> onStateChanged: State_Setting"
                    )
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> Log.i(
                        TAG,
                        "--> onStateChanged: State_Half_Expanded"
                    )
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                Log.i(TAG, "--> onSlide: ")
            }

        })

    }

    private fun deleteBy() {
        CoroutineScope(Dispatchers.Main).launch {
            adapter.isSelected.collect {
                it?.let {
                    if (it) {
                        cancelBtn.setOnClickListener {
                            Log.i(TAG, "--> deleteBy: Clicked Cancel")
                            sheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
                        }
                        deleteBtn.setOnClickListener {
                            Log.i(TAG, "--> deleteBy: Clicked Delete")
                            adapter.deleteSelected()
                            sheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
                        }
                    } else {
                        sheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
                    }
                }
            }
        }
    }

    private fun updateFavoriteList() {
        val list = repository.loadFromStorage()
        Log.i(TAG, "--> updateFavoriteList: ${list.size}")
        adapter.updateData(list)
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "--> onPause: ")
        adapter.clearSelection()
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "--> onResume: ")

        updateFavoriteList()
    }
}