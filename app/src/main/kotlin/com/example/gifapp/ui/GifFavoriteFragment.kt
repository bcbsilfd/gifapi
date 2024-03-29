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
import androidx.fragment.app.FragmentManager
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
    private var fullScreenFragment: GifFullScreenFragment? = null
    private var fmCallbackInstance: FragmentManager.FragmentLifecycleCallbacks? = null

    // Multi selection
    private var tracker: SelectionTracker<Long>? = null

    private lateinit var bottomLayout: ConstraintLayout
    private lateinit var deleteBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var selectedTv: TextView
    private lateinit var selectAllCb: CheckBox
    private var sheetBehavior: BottomSheetBehavior<*>? = null
    private var sheetCallback: BottomSheetBehavior.BottomSheetCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null)
            tracker?.onRestoreInstanceState(savedInstanceState)
        if (fullScreenFragment == null) fullScreenFragment = GifFullScreenFragment()
        repository = FileRepository.getInstance(requireContext())
    }

    override fun onStart() {
        super.onStart()

        // Add bottom dialog fragment lifecycle callbacks
        addDialogStatusListener()
        // Add bottom sheet callbacks
        addBottomSheetCallbacks()
        //Init list from database
        adapter.updateData(repository.loadFromDatabase())
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

        adapter = GifFavoriteAdapter(emptyList())
        adapter.gif.observe(viewLifecycleOwner, { mGif ->
            gif = mGif
            gif?.let {
                Log.i(TAG, "--> onCreateView: gif.id=${it.id}")
                Log.i(TAG, "--> onCreateView: gif.desc=${it.description}")
                Log.i(TAG, "--> onCreateView: gif.uri=${it.gifURL}")
                // here
                val args = Bundle().apply {
                    Log.i(TAG, "--> onCreateView: id[${it.id}]")
                    putString(GIF_ID, it.id)
                    Log.i(TAG, "--> onCreateView: description[${it.description}]")
                    putString(GIF_DESC, it.description)
                    Log.i(TAG, "--> onCreateView: uri[${it.gifURL}]")
                    putString(GIF_URI, it.gifURL)
                }
                if (adapter.isEditable.value == false) {
                    // If dialog was created dismiss
                    Log.i(TAG, "--> onCreateView: moveToFullScreen")

                    fullScreenFragment?.arguments = args
                    fullScreenFragment?.show(childFragmentManager, "GifFullScreenFragment")
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
                for (i in 0..adapter.itemCount) {
                    tracker?.select(i.toLong())
                }
            } else {
                for (i in 0..adapter.itemCount) {
                    tracker?.deselect(i.toLong())
                }

            }
        }


        CoroutineScope(Dispatchers.Main).launch {
            adapter.selectedCount.collect {
                Log.i(TAG, "--> onViewCreated: selected.count=$it")
                selectedCount = it
                selectedTv.text = "Selected: $it"
                if (it == 0) selectAllCb.isChecked = false
            }
        }
    }

    private fun addDialogStatusListener() {
        fmCallbackInstance = object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
                super.onFragmentStopped(fm, f)
                if (f == fullScreenFragment) {
                    Log.i(TAG, "onFragmentStopped: FullScreenDialog")
                    adapter.updateData(repository.getFavoriteList())
                }
            }
        }
        fmCallbackInstance?.let {
            childFragmentManager.registerFragmentLifecycleCallbacks(
                it, true
            )
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

    }

    private fun addBottomSheetCallbacks() {
        sheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
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

        }
        sheetCallback?.let {
            sheetBehavior?.addBottomSheetCallback(it)
        }
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

//        updateFavoriteList()
        adapter.updateData(repository.loadFromDatabase())
    }

    override fun onStop() {
        super.onStop()

        // Clear bottom dialog callbacks
        fmCallbackInstance?.let {
            childFragmentManager.unregisterFragmentLifecycleCallbacks(
                it
            )
        }

        // Clear bottom sheet callbacks
        sheetCallback?.let {
            sheetBehavior?.removeBottomSheetCallback(it)
        }
    }

    override fun onDestroy() {
        // Clear bottom fragment
        fullScreenFragment = null
        super.onDestroy()
    }
}