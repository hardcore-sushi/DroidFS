package sushi.hardcore.droidfs.fingerprint_stuff

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import sushi.hardcore.droidfs.R

class FingerprintFragment(val volume_path: String, val action_description: String, val callbackOnDismiss: () -> Unit) : DialogFragment() {
    lateinit var image_fingerprint: ImageView
    lateinit var text_instruction: TextView
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_fingerprint, container, false)
        val text_volume = view.findViewById<TextView>(R.id.text_volume)
        text_volume.text = volume_path
        image_fingerprint = view.findViewById(R.id.image_fingerprint)
        val text_action_description = view.findViewById<TextView>(R.id.text_action_description)
        text_action_description.text = action_description
        text_instruction = view.findViewById(R.id.text_instruction)
        return view
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        callbackOnDismiss()
    }
}