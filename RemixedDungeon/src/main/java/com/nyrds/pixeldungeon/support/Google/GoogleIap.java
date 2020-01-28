package com.nyrds.pixeldungeon.support.Google;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchaseHistoryRecord;
import com.android.billingclient.api.PurchaseHistoryResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.nyrds.pixeldungeon.ml.EventCollector;
import com.nyrds.pixeldungeon.ml.R;
import com.nyrds.pixeldungeon.support.IPurchasesUpdated;
import com.watabou.noosa.Game;
import com.watabou.pixeldungeon.Preferences;
import com.watabou.pixeldungeon.utils.GLog;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

public class GoogleIap implements PurchasesUpdatedListener, PurchaseHistoryResponseListener, ConsumeResponseListener {

    private final Map<String, Purchase> mPurchases = new HashMap<>();
    private BillingClient mBillingClient;
    private boolean mIsServiceConnected;
    private IPurchasesUpdated mPurchasesUpdatedListener;
    private Map<String, SkuDetails> mSkuDetails = new HashMap<>();
    private boolean mIsServiceConnecting;

    private ConcurrentLinkedQueue<Runnable> mRequests = new ConcurrentLinkedQueue<>();

    public GoogleIap(Context context, IPurchasesUpdated purchasesUpdatedListener) {
        mBillingClient = BillingClient.newBuilder(context)
                .enablePendingPurchases()
                .setListener(this)
                .build();
        mPurchasesUpdatedListener = purchasesUpdatedListener;
    }

    public void querySkuList(final List<String> skuList) {
        Runnable queryRequest = () -> {
            SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
            params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP);
            mBillingClient.querySkuDetailsAsync(params.build(),
                    (billingResult, list) -> {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                                && list != null) {
                            mSkuDetails = new HashMap<>();
                            for (SkuDetails skuDetails : list) {
                                mSkuDetails.put(skuDetails.getSku().toLowerCase(Locale.ROOT), skuDetails);
                            }
                        }
                    });
        };
        executeServiceRequest(queryRequest);
    }

    public void doPurchase(final String skuId) {
        Runnable purchaseFlowRequest = () -> {
            BillingFlowParams purchaseParams = BillingFlowParams.newBuilder()
                    .setSkuDetails(mSkuDetails.get(skuId))
                    .build();
            mBillingClient.launchBillingFlow(Game.instance(), purchaseParams);
        };

        executeServiceRequest(purchaseFlowRequest);
    }

    private void handlePurchase(Purchase purchase) {
        if (!verifySignature(purchase.getOriginalJson(), purchase.getSignature())) {
            EventCollector.logException("bad signature");
            return;
        }
        //GLog.w("purchase: %s",purchase.toString());
        //mBillingClient.consumeAsync(purchase.getPurchaseToken(),this);
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {

            // Acknowledge the purchase if it hasn't already been acknowledged.
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams =
                        AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                mBillingClient.acknowledgePurchase(acknowledgePurchaseParams, billingResult -> {
                    EventCollector.logEvent("billing_result",billingResult.toString());
                });
            }

            mPurchases.put(purchase.getSku().toLowerCase(Locale.ROOT), purchase);

            String purchaseData = purchase.getOriginalJson();
            if( !Preferences.INSTANCE.getBoolean(purchaseData,false) ) {
                EventCollector.logEvent("iap_data", purchaseData);
                Preferences.INSTANCE.put(purchaseData,true);
            }
        }

    }

    public void queryPurchases() {
        Runnable queryToExecute = () -> {
            Purchase.PurchasesResult result = mBillingClient.queryPurchases(BillingClient.SkuType.INAPP);
            onPurchasesUpdated(result.getBillingResult(), result.getPurchasesList());

            //mBillingClient.queryPurchaseHistoryAsync(BillingClient.SkuType.INAPP, GoogleIap.this);
        };

        executeServiceRequest(queryToExecute);
    }

    private void startServiceConnection() {
        if (mIsServiceConnecting) {
            return;
        }

        mIsServiceConnecting = true;
        mBillingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {

                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    mIsServiceConnected = true;
                    for (Runnable runnable : mRequests) {
                        getExecutor().execute(runnable);
                    }
                } else {
                    EventCollector.logException("google play billing:" + billingResult.getDebugMessage());
                }
                mIsServiceConnecting = false;
            }

            @Override
            public void onBillingServiceDisconnected() {
                mIsServiceConnected = false;
                mIsServiceConnecting = false;
            }
        });
    }

    private void executeServiceRequest(final Runnable runnable) {
        Game.instance().runOnUiThread(() -> {
            if (isServiceConnected()) {
                getExecutor().execute(runnable);
            } else {
                mRequests.add(runnable);
                startServiceConnection();
            }
        });

    }

    private boolean verifySignature(String signedData, String signature) {
        try {
            return GoogleIapCheck.verifyPurchase(Game.getVar(R.string.iapKey), signedData, signature);
        } catch (IOException e) {
            EventCollector.logException(e, "GoogleIap.verifySignature");
            return false;
        }
    }

    public boolean checkPurchase(String item) {
        return mPurchases.containsKey(item.toLowerCase(Locale.ROOT));
    }

    public String getSkuPrice(String sku) {
        String skuLowerCase = sku.toLowerCase(Locale.ROOT);
        if (mSkuDetails.containsKey(skuLowerCase)) {
            return mSkuDetails.get(skuLowerCase).getPrice();
        } else {
            EventCollector.logException(sku);
            return "N/A";
        }
    }

    private Executor getExecutor() {
        return Game.instance().executor;
    }

    public boolean isServiceConnected() {
        return mIsServiceConnected && !mIsServiceConnecting;
    }


    @Override
    public void onConsumeResponse(BillingResult billingResult, String s) {
        GLog.w("consumed: %d %s", billingResult.getDebugMessage(), s);
    }

    @Override
    public void onPurchaseHistoryResponse(BillingResult billingResult, List<PurchaseHistoryRecord> list) {
        if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            EventCollector.logException("queryPurchasesHistory" + billingResult.getDebugMessage());
        }

    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, @Nullable List<Purchase> list) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK
                && list != null) {
            for (Purchase purchase : list) {
                handlePurchase(purchase);
            }
            mPurchasesUpdatedListener.onPurchasesUpdated();
        } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle an error caused by a user cancelling the purchase flow.
        } else {
            // Handle any other error codes.
        }
    }
}