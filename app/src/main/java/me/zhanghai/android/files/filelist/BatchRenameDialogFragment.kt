/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.BatchRenameDialogBinding
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.util.DebouncedRunnable
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.setOnEditorConfirmActionListener
import me.zhanghai.android.files.util.show

class BatchRenameDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()
    private lateinit var _binding: BatchRenameDialogBinding
    private val binding: BatchRenameDialogBinding
        get() = _binding
    private var plans: List<Plan> = emptyList()

    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.file_batch_rename_title)
            .apply {
                _binding = BatchRenameDialogBinding.inflate(context.layoutInflater)
                binding.numberingPaddingSpinner.adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_item,
                    PADDING_ENTRIES
                ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                binding.findEdit.setOnEditorConfirmActionListener { onOk() }
                val onTextChanged = DebouncedRunnable(
                    Handler(Looper.getMainLooper()), PREVIEW_UPDATE_DELAY_MILLIS
                ) { updatePreview() }
                binding.findEdit.addTextChangedListener(onTextChanged.createTextWatcher())
                binding.replaceEdit.addTextChangedListener(onTextChanged.createTextWatcher())
                binding.numberingStartEdit.addTextChangedListener(onTextChanged.createTextWatcher())
                binding.includeExtensionCheckbox.setOnCheckedChangeListener { _, _ ->
                    updatePreview()
                }
                binding.numberingCheckbox.setOnCheckedChangeListener { _, checked ->
                    binding.numberingLayout.isVisible = checked
                    updatePreview()
                }
                setView(binding.root)
            }
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .apply {
                window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
                setOnShowListener {
                    updatePreview()
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onOk() }
                }
            }

    private val find: String
        get() = binding.findEdit.text.toString()

    private val replace: String
        get() = binding.replaceEdit.text.toString()

    private val includeExtension: Boolean
        get() = binding.includeExtensionCheckbox.isChecked

    private val isNumberingEnabled: Boolean
        get() = binding.numberingCheckbox.isChecked

    private val numberingStart: Int
        get() = binding.numberingStartEdit.text.toString().toIntOrNull()
            ?: DEFAULT_NUMBERING_START

    private val paddingWidth: Int
        get() = (binding.numberingPaddingSpinner.selectedItemPosition + 1)
            .coerceIn(1, MAX_PADDING_WIDTH)

    private fun onOk() {
        // The preview update may be pending because it is debounced.
        updatePreview()
        val renames = plans.filter { it.needsRename && !it.hasProblem }
            .map { plan -> plan.file.path to plan.newName }
        if (renames.isEmpty()) {
            return
        }
        dismiss()
        listener.onBatchRename(renames)
    }

    private fun updatePreview() {
        plans = computePlans()
        val separator = getString(R.string.file_batch_rename_name_separator)
        val renamedPlans = plans.filter { it.needsRename }
        binding.previewText.text = if (renamedPlans.isEmpty()) {
            getString(R.string.file_batch_rename_no_change)
        } else {
            renamedPlans.joinToString("\n") { plan ->
                plan.file.name + separator + plan.newName
            }
        }
        val problemPlans = plans.filter { it.needsRename && it.hasProblem }
        binding.warningText.isVisible = problemPlans.isNotEmpty()
        if (problemPlans.isNotEmpty()) {
            binding.warningText.text = getString(R.string.file_batch_rename_conflict_title) + "\n" +
                problemPlans.joinToString("\n") { plan ->
                    plan.file.name + separator + plan.newName + ": " + problemMessage(plan)
                }
        }
        dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
            plans.any { it.needsRename && !it.hasProblem }
    }

    private fun problemMessage(plan: Plan): String {
        return when {
            !plan.isValid -> getString(R.string.file_batch_rename_conflict_invalid, plan.newName)
            plan.isExternalConflict ->
                getString(R.string.file_batch_rename_conflict_exists, plan.newName)
            else -> getString(R.string.file_batch_rename_conflict_duplicate, plan.newName)
        }
    }

    private fun computePlans(): List<Plan> {
        val plans = args.files.mapIndexed { index, file ->
            var newName = file.computeNewName(find, replace, includeExtension)
            if (isNumberingEnabled) {
                newName = computeNumberedName(newName, numberingStart, paddingWidth, index)
            }
            Plan(file, newName)
        }
        val nameCountByParentAndName = mutableMapOf<Pair<Path?, String>, Int>()
        for (plan in plans) {
            if (!plan.needsRename) {
                continue
            }
            val key = plan.file.path.parent to plan.newName
            nameCountByParentAndName[key] = (nameCountByParentAndName[key] ?: 0) + 1
        }
        for (plan in plans) {
            val key = plan.file.path.parent to plan.newName
            plan.isInternalConflict = (nameCountByParentAndName[key] ?: 0) > 1
            plan.isExternalConflict = plan.newName != plan.file.name &&
                args.directoryFileNames.contains(plan.newName)
        }
        return plans
    }

    private fun DebouncedRunnable.createTextWatcher(): TextWatcher = object : TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence?, start: Int, count: Int, after: Int
        ) { }

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            invoke()
        }

        override fun afterTextChanged(s: Editable?) { }
    }

    companion object {
        private const val PREVIEW_UPDATE_DELAY_MILLIS = 100L

        private const val DEFAULT_NUMBERING_START = 1

        private val PADDING_ENTRIES = arrayOf("1", "01", "001", "0001", "00001", "000001")

        private val MAX_PADDING_WIDTH = PADDING_ENTRIES.size

        fun show(files: FileItemSet, directoryFileNames: Set<String>, fragment: Fragment) {
            BatchRenameDialogFragment().putArgs(Args(files, directoryFileNames)).show(fragment)
        }
    }

    @Parcelize
    class Args(val files: FileItemSet, val directoryFileNames: Set<String>) : ParcelableArgs

    interface Listener {
        fun onBatchRename(renames: List<Pair<Path, String>>)
    }

    private class Plan(
        val file: FileItem,
        val newName: String
    ) {
        var isInternalConflict = false

        var isExternalConflict = false

        val isValid: Boolean
            get() = newName.asFileNameOrNull() != null

        val hasProblem: Boolean
            get() = !isValid || isInternalConflict || isExternalConflict

        val needsRename: Boolean
            get() = newName != file.name
    }
}
