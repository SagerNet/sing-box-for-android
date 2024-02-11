package io.nekohasekai.sfa.vendor

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import io.nekohasekai.sfa.Application
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.ktx.errorDialogBuilder
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object Vendor : VendorInterface {

    private const val TAG = "Vendor"
    override fun checkUpdateAvailable(): Boolean {
        return true
    }

    override fun checkUpdate(activity: Activity, byUser: Boolean) {
        val appUpdateManager = AppUpdateManagerFactory.create(activity)
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            when (appUpdateInfo.updateAvailability()) {
                UpdateAvailability.UPDATE_NOT_AVAILABLE -> {
                    Log.d(TAG, "checkUpdate: not available")
                    if (byUser) activity.showNoUpdatesDialog()
                }

                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                    Log.d(TAG, "checkUpdate: in progress, status: ${appUpdateInfo.installStatus()}")
                    when (appUpdateInfo.installStatus()) {
                        InstallStatus.DOWNLOADED -> {
                            appUpdateManager.completeUpdate()
                        }
                    }
                }

                UpdateAvailability.UPDATE_AVAILABLE -> {
                    Log.d(TAG, "checkUpdate: available")
                    if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
                        appUpdateManager.startUpdateFlow(
                            appUpdateInfo,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
                        )
                    } else if (appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                        appUpdateManager.startUpdateFlow(
                            appUpdateInfo,
                            activity,
                            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                        )
                    }
                }

                UpdateAvailability.UNKNOWN -> {
                    if (byUser) activity.showNoUpdatesDialog()
                }
            }
        }
        appUpdateInfoTask.addOnFailureListener {
            Log.e(TAG, "checkUpdate: ", it)
        }
        appUpdateManager.registerListener { state ->
            if (state.installStatus() == InstallStatus.DOWNLOADED) {
                appUpdateManager.completeUpdate()
            }
        }
    }

    private lateinit var billingClient: BillingClient
    override fun initializeBillingClient(activity: Activity) {
        billingClient =
            BillingClient.newBuilder(Application.application).setListener { result, purchases ->
                onPurchasesUpdated(activity, result, purchases)
            }.enablePendingPurchases().build()
    }

    private fun requireConnection(callback: (String?) -> Unit) {
        when (billingClient.connectionState) {
            BillingClient.ConnectionState.CONNECTED -> callback(null)
            else -> {
                billingClient.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                            callback(null)
                        } else {
                            callback(result.toString())
                        }
                    }

                    override fun onBillingServiceDisconnected() {
                    }
                })
            }
        }
    }

    override fun startSponsor(activity: Activity, fallback: () -> Unit) {
        val dialog = ProgressDialog(activity)
        dialog.setMessage(activity.getString(R.string.loading))
        dialog.show()
        requireConnection {
            if (it != null) {
                activity.errorDialogBuilder(it).show()
                return@requireConnection
            }
            GlobalScope.launch(Dispatchers.IO) {
                acknowledgeSponsor()
                startSponsor0(activity, dialog, fallback)
            }
        }
    }

    private suspend fun startSponsor0(
        activity: Activity,
        dialog: ProgressDialog,
        fallback: () -> Unit,
    ) {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder().setProductId("sponsor_circle_1")
                    .setProductType(BillingClient.ProductType.SUBS).build(),
                QueryProductDetailsParams.Product.newBuilder().setProductId("sponsor_circle_10")
                    .setProductType(BillingClient.ProductType.SUBS).build(),
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId("sponsor_circle_100")
                    .setProductType(BillingClient.ProductType.SUBS).build(),
            )
        ).build()
        val (result, products) = billingClient.queryProductDetails(params)
        withContext(Dispatchers.Main) {
            dialog.dismiss()
        }
        if (result.responseCode != BillingClient.BillingResponseCode.OK || products.isNullOrEmpty()) {
            withContext(Dispatchers.Main) {
                activity.errorDialogBuilder(result.toString()).show()
            }
            return
        }
        val selecting = products.sortedBy { it.productId.substringAfterLast("_").toInt() }
        val selected = AtomicInteger(0)
        withContext(Dispatchers.Main) {
            MaterialAlertDialogBuilder(activity).setTitle(R.string.sponsor_play)
                .setSingleChoiceItems(selecting.map { it.title.removeSuffix(" (sing-box)") }
                    .toMutableList().also {
                        it.add(activity.getString(R.string.other_methods))
                    }.toTypedArray(), 0
                ) { _, which ->
                    selected.set(which)
                }.setNeutralButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_start) { _, _ ->
                    if (selected.get() == selecting.size) {
                        fallback()
                        return@setPositiveButton
                    }
                    startSponsor1(activity, selecting[selected.get()])
                }.show()
        }
    }

    private fun startSponsor1(activity: Activity, product: ProductDetails) {
        val paramsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product)
                .setOfferToken(product.subscriptionOfferDetails!![0].offerToken).build()
        )
        val flowParams =
            BillingFlowParams.newBuilder().setProductDetailsParamsList(paramsList).build()
        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            activity.errorDialogBuilder(result.toString()).show()
        }
    }

    private fun Context.showNoUpdatesDialog() {
        MaterialAlertDialogBuilder(this).setTitle(io.nekohasekai.sfa.R.string.check_update)
            .setMessage(R.string.no_updates_available).setPositiveButton(R.string.ok, null).show()
    }

    private fun onPurchasesUpdated(
        activity: Activity, result: BillingResult, purchases: List<Purchase>?
    ) {
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases.isNullOrEmpty()) {
            return
        }
        requireConnection {
            if (it != null) GlobalScope.launch(Dispatchers.Main) {
                val dialog = ProgressDialog(activity)
                dialog.setMessage(activity.getString(R.string.loading))
                dialog.show()
                val errorMessage = acknowledgeSponsor0(purchases)
                dialog.dismiss()
                if (errorMessage != null) {
                    activity.errorDialogBuilder(errorMessage).show()
                }
            }
        }
    }

    private suspend fun acknowledgeSponsor() {
        val result = billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS)
                .build()
        )
        if (result.purchasesList.isNotEmpty()) {
            acknowledgeSponsor0(result.purchasesList)
        }
    }

    private suspend fun acknowledgeSponsor0(purchases: List<Purchase>): String? = coroutineScope {
        val deferred = mutableListOf<Deferred<String?>>()
        for (purchase in purchases) {
            deferred += async(Dispatchers.IO) {
                suspendCoroutine { continuation ->
                    billingClient.acknowledgePurchase(
                        AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken).build()
                    ) {
                        if (it.responseCode == BillingClient.BillingResponseCode.OK) {
                            continuation.resume(null)
                        } else {
                            continuation.resume(it.toString())
                        }
                    }
                }
            }
        }
        deferred.awaitAll().filterNotNull().firstOrNull()
    }

}