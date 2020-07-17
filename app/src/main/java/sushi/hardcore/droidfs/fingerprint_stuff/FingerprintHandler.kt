package sushi.hardcore.droidfs.fingerprint_stuff

import android.content.Context
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.M)
class FingerprintHandler(private val context: Context) : FingerprintManager.AuthenticationCallback(){
    private lateinit var cancellationSignal: CancellationSignal
    private lateinit var onTouched: (resultCode: onTouchedResultCodes) -> Unit

    fun startAuth(fingerprintManager: FingerprintManager, cryptoObject: FingerprintManager.CryptoObject, onTouched: (resultCode: onTouchedResultCodes) -> Unit){
        cancellationSignal = CancellationSignal()
        this.onTouched = onTouched
        fingerprintManager.authenticate(cryptoObject, cancellationSignal, 0, this, null)
    }

    override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
        onTouched(onTouchedResultCodes.SUCCEED)
    }

    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
        onTouched(onTouchedResultCodes.ERROR)
    }

    override fun onAuthenticationFailed() {
        onTouched(onTouchedResultCodes.FAILED)
    }
}