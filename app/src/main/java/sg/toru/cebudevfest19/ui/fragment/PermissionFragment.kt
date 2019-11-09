package sg.toru.cebudevfest19.ui.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.Navigation

import sg.toru.cebudevfest19.R

/**
 * A simple [Fragment] subclass.
 */
class PermissionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_permission, container, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(hasPermissions()){
            // Go To Camera.
            Navigation.findNavController(requireActivity(), R.id.fragment_container)
                .navigate(R.id.action_permission_to_camera)
        }
        else{
            requestPermissions(permissions, requestCodeForPermission)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == requestCodeForPermission && permissions[0] == Manifest.permission.CAMERA){
            if(grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED){
                // Go To Camera.
                Navigation.findNavController(requireActivity(), R.id.fragment_container)
                    .navigate(R.id.action_permission_to_camera)
            }
            else{
                Toast.makeText(context!!, "You have to grant permission to use Camera.", Toast.LENGTH_SHORT).show()
                activity?.finish()
            }
        }
    }

    private fun hasPermissions():Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context!!, it) == PackageManager.PERMISSION_GRANTED
        }
    }

}

private val requestCodeForPermission = 0x20
private val permissions = arrayOf(Manifest.permission.CAMERA)