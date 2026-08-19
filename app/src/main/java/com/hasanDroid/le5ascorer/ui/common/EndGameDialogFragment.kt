package com.hasanDroid.le5ascorer.ui.common

import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import com.hasanDroid.le5ascorer.R
import com.hasanDroid.le5ascorer.util.ImageUtil
import kotlin.random.Random

/**
 * Modal end-game dialog.
 *
 * Contract:
 * - Input: loser names (already computed by the engine/viewmodel)
 * - Output: invokes [onShowRoundScores] when CTA is tapped.
 *
 * Notes:
 * - Not cancelable via back press or outside touch.
 * - Shows a fun, light teasing sentence picked randomly.
 * - Allows capturing loser's photo via camera.
 */
class EndGameDialogFragment : DialogFragment() {

    var onShowRoundScores: (() -> Unit)? = null
    var onPhotoSaved: ((String) -> Unit)? = null

    private var matchId: Long = 0

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCameraIntent()
        } else {
            Toast.makeText(
                requireContext(),
                R.string.camera_permission_required,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val bitmap = result.data?.getParcelableExtra<Bitmap>("data")
            if (bitmap != null) {
                val imagePath = ImageUtil.saveBitmapToFile(requireContext(), bitmap, matchId)
                if (imagePath != null) {
                    // Close dialog and navigate to scores
                    dismissAllowingStateLoss()
                    onPhotoSaved?.invoke(imagePath)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
        matchId = arguments?.getLong(ARG_MATCH_ID, 0) ?: 0
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext(), R.style.Theme_Le5aScorer_TransparentFullscreenDialog)
        val view = layoutInflater.inflate(R.layout.dialog_end_game, null)

        val loserNames = requireArguments().getStringArrayList(ARG_LOSER_NAMES).orEmpty()

        view.findViewById<TextView>(R.id.textLoser).text =
            loserNames.joinToString(", ").ifBlank { getString(R.string.player_default) }

        view.findViewById<TextView>(R.id.textTease).text = pickTeaseLine()

        view.findViewById<View>(R.id.buttonCapturePhoto).setOnClickListener {
            launchCamera()
        }

        view.findViewById<View>(R.id.buttonShowRoundScores).setOnClickListener {
            dismissAllowingStateLoss()
            onShowRoundScores?.invoke()
        }

        dialog.setContentView(view)
        dialog.setCanceledOnTouchOutside(false)

        dialog.setOnShowListener {
            val card = view.findViewById<View>(R.id.card)
            card.alpha = 0f
            card.scaleX = 0.9f
            card.scaleY = 0.9f
            card.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(220)
                .setInterpolator(android.view.animation.OvershootInterpolator(0.9f))
                .start()
        }

        dialog.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.decorView.setPadding(0, 0, 0, 0)
        }

        return dialog
    }

    private fun pickTeaseLine(): String {
        val lines = resources.getStringArray(R.array.end_game_tease_lines)
        return lines[Random.nextInt(lines.size)]
    }

     private fun launchCamera() {
         if (ContextCompat.checkSelfPermission(
             requireContext(),
             android.Manifest.permission.CAMERA
         ) == android.content.pm.PackageManager.PERMISSION_GRANTED
         ) {
             // Permission already granted
             launchCameraIntent()
         } else {
             // Request permission
             permissionLauncher.launch(android.Manifest.permission.CAMERA)
         }
     }

     private fun launchCameraIntent() {
         val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
         cameraLauncher.launch(takePictureIntent)
     }

     companion object {
         private const val ARG_LOSER_NAMES = "loserNames"
         private const val ARG_MATCH_ID = "matchId"

         fun newInstance(loserNames: List<String>, matchId: Long = 0): EndGameDialogFragment {
             return EndGameDialogFragment().apply {
                 arguments = bundleOf(
                     ARG_LOSER_NAMES to ArrayList(loserNames),
                     ARG_MATCH_ID to matchId
                 )
             }
         }
     }
 }
