package sg.toru.cebudevfest19.ui.fragment

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

import sg.toru.cebudevfest19.R

class ResultFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_result, container, false)
        initResult(view)
        return view
    }

    private fun initResult(view:View){
        val arg = arguments
        val resultText = if(arg?.containsKey(KEY) == true){
            arg.getString(KEY)
        }
        else{
            "No Scanned Result!"
        }
        val result = view.findViewById<TextView>(R.id.txt_result)
        result.text = resultText
        val image = when(resultText){
            "Jollibee"-> R.drawable.jollibee
            "Bulalo"-> R.drawable.bulalo
            "Lechon"-> R.drawable.lechon
            else-> R.drawable.gdg
        }

        val foodImage = view.findViewById<ImageView>(R.id.img_food)
        foodImage.setImageResource(image)
    }

    companion object{
        private val TAG = "ResultFragment"
        private const val KEY = "RESULT_KEY"
        fun show(fragmentManager: FragmentManager, result:String) {
            val scanResultFragment = ResultFragment()

            scanResultFragment.arguments = Bundle().apply {
                putString(KEY, result)
            }
            scanResultFragment.show(fragmentManager, TAG)
        }

        fun dismiss(fragmentManager: FragmentManager) {
            (fragmentManager.findFragmentByTag(TAG) as ResultFragment?)?.dismiss()
        }
    }
}
