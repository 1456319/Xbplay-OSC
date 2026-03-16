package com.studio08.xbgamestream.Helpers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.ProductDetailsResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;
import com.google.common.collect.ImmutableList;
import com.ironsource.mediationsdk.IronSource;
import com.ironsource.mediationsdk.integration.IntegrationHelper;
import com.ironsource.mediationsdk.logger.IronSourceError;
import com.ironsource.mediationsdk.model.Placement;
import com.ironsource.mediationsdk.sdk.RewardedVideoListener;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.StreamWebview;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class PurchaseClient {
    private Context context;
    private BillingClient billingClient;
    EncryptClient encryptClient = null;
    private StreamWebview mSystemWebview;

    private PurchasesUpdatedListener purchasesUpdatedListener = new PurchasesUpdatedListener() {
        @Override
        public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
            Log.d("PurchaseClient", "onPurchasesUpdated: " + billingResult.getResponseCode());

            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (Purchase purchase : purchases) {
                    handlePurchase(purchase);
                }
            } else if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
                // Handle an error caused by a user cancelling the purchase flow.
            } else {
                // Handle any other error codes.
            }
        }
    };

    public PurchaseClient(Context context, StreamWebview webview){
        this.context = context;
        this.encryptClient = new EncryptClient(context);
        this.mSystemWebview = webview;
        initBillingClient();
    }

    // BILLING
    private void initBillingClient(){
        billingClient = BillingClient.newBuilder(context)
                .setListener(purchasesUpdatedListener)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() ==  BillingClient.BillingResponseCode.OK) {
                    // always check if the user has purchased the remove ads feature
                    queryPurchases();
                }
            }
            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.
            }
        });
    }
    public void queryPurchases(){
        billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build(),
                new PurchasesResponseListener() {
                    public void onQueryPurchasesResponse(BillingResult billingResult, List purchases) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                            for (Object purchase : purchases) {
                                handlePurchase((Purchase) purchase);
                            }
                        }
                    }
                }
        );

        // Query for subscriptions
        billingClient.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build(),
                new PurchasesResponseListener() {
                    @Override
                    public void onQueryPurchasesResponse(BillingResult billingResult, List<Purchase> purchases) {
                        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                            for (Purchase purchase : purchases) {
                                handlePurchase(purchase);
                            }
                        }
                    }
                }
        );
    }

    private void handlePurchase(Purchase purchase) {
        Log.e("PurchaseClient", "handlePurchase: getPurchaseState=" + purchase.getPurchaseState());

        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
                    @Override
                    public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                        updateToken(purchase);
                    }
                });
            } else {
                updateToken(purchase);
            }
        } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            Toast.makeText(this.context, "Purchase is Pending...", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateToken(Purchase purchase){
        JSONObject params = new JSONObject();
        try {
            params.put("packageName", this.context.getPackageName());
            params.put("product", purchase.getProducts().get(0)); // always 0th?

            ((Activity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ApiClient.callJavaScript(mSystemWebview, "setIAPTransactionTokens", purchase.getPurchaseToken(), "android", params.toString());
                }
            });

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void purchasesSubscription(String productId){
        Log.d("PurchaseClient", "showing purchase view for SUB id: " + productId);
        // lookup in app purchase with productId name
        QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(
                        ImmutableList.of(
                                QueryProductDetailsParams.Product.newBuilder()
                                        .setProductId("xbplay_group")
                                        .setProductType(BillingClient.ProductType.SUBS)
                                        .build()))
                .build();

        // actually lookup the data
        billingClient.queryProductDetailsAsync(
                queryProductDetailsParams,
                new ProductDetailsResponseListener() {
                    public void onProductDetailsResponse(BillingResult billingResult, List<ProductDetails> productDetailsList) {

                        // loop over all responses
                        for (ProductDetails purchase : productDetailsList) {
                            Log.d("PurchaseClient", "Found a sub product: " + purchase.getProductId());

                            // if we find a productId matching our passed in value
                            if (purchase.getProductId().equals("xbplay_group")){
                                Log.e("PurchaseClient", "Found valid purchase: " + purchase.getSubscriptionOfferDetails());

                                if (purchase.getSubscriptionOfferDetails() == null){
                                    Log.e("PurchaseClient", "Invalid getSubscriptionOfferDetails");
                                    return;
                                }

                                ProductDetails.SubscriptionOfferDetails offer = null;
                                for (int i = 0; i < purchase.getSubscriptionOfferDetails().size(); i++){
                                    ProductDetails.SubscriptionOfferDetails offerDetails = purchase.getSubscriptionOfferDetails().get(i);
                                    if (offerDetails.getBasePlanId().equals(productId)){
                                        offer = offerDetails;
                                    } else {
                                        Log.e("PurchaseClient", "Ignore plan: " + offerDetails.getBasePlanId());
                                    }
                                }

                                if (offer == null){
                                    Log.e("PurchaseClient", "Invalid offerToUse");
                                    return;
                                }

                                ImmutableList<BillingFlowParams.ProductDetailsParams> productDetailsParamsList =
                                        ImmutableList.of(
                                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                                        .setProductDetails(purchase)
                                                        .setOfferToken(offer.getOfferToken())
                                                        .build()
                                        );

                                BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                        .setProductDetailsParamsList(productDetailsParamsList)
                                        .build();

                                // Launch the billing flow
                                BillingResult result = billingClient.launchBillingFlow((Activity) context, billingFlowParams);
                                Log.e("PurchaseClient", "Launch flow result"+ result.getResponseCode() + " " +result.getDebugMessage());
                            } else {
                                Log.e("PurchaseClient", "Ignore purchase: " + purchase.getName());
                            }
                        }
                    }
                }
        );
    }

    public void purchaseProduct(String productId){
        Log.d("PurchaseClient", "showing purchase view for IAP id: " + productId);

        // lookup in app purchase with productId name
        QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(
                        ImmutableList.of(
                                QueryProductDetailsParams.Product.newBuilder()
                                        .setProductId(productId)
                                        .setProductType(BillingClient.ProductType.INAPP)
                                        .build()))
                        .build();

        // actually lookup the data
        billingClient.queryProductDetailsAsync(
                queryProductDetailsParams,
                new ProductDetailsResponseListener() {
                    public void onProductDetailsResponse(BillingResult billingResult, List<ProductDetails> productDetailsList) {
                        // loop over all responses
                        for (ProductDetails purchase : productDetailsList) {

                            // if we find a productId matching our passed in value
                            if (purchase.getProductId().equals(productId)){

                                // create param of this item
                                ImmutableList productDetailsParamsList =
                                        ImmutableList.of(
                                                BillingFlowParams.ProductDetailsParams.newBuilder()
                                                        // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                                                        .setProductDetails(purchase)
                                                        .build()
                                        );

                                BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                                        .setProductDetailsParamsList(productDetailsParamsList)
                                        .build();


                                // Launch the billing flow
                                BillingResult billingResultBuy = billingClient.launchBillingFlow((Activity) context, billingFlowParams);
                        }}
                    }
                }
        );
    }
}
