package com.studio08.xbgamestream.Helpers;

import android.content.Context;
import android.util.Log;
import com.android.billingclient.api.*;
import com.google.common.collect.ImmutableList;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IAPPricesManager implements PurchasesUpdatedListener {
    private static final String TAG = "IAPPricesManager";
    private BillingClient billingClient;
    private final Map<String, String> productPrices = new HashMap<>();
    private final String iapProductId = "remove_ads";
    private final String subscriptionProductId = "xbplay_group";
    private final EncryptClient encryptClient;
    private boolean iapFetchComplete = false;
    private boolean subsFetchComplete = false;

    public IAPPricesManager(Context context) {
        this.encryptClient = new EncryptClient(context);
        billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        startConnection();
    }

    private void startConnection() {
        Log.d(TAG, "startConnection");

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "onBillingSetupFinished");
                    fetchSubscriptions();
                    fetchIAPs();
                } else {
                    Log.e(TAG, "Billing setup failed: " + billingResult.getDebugMessage());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                Log.e(TAG, "Billing service disconnected");
            }
        });
    }

    public void fetchSubscriptions() {
        Log.d(TAG, "fetchSubscriptions");
        QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(
                        ImmutableList.of(
                                QueryProductDetailsParams.Product.newBuilder()
                                        .setProductId(subscriptionProductId)
                                        .setProductType(BillingClient.ProductType.SUBS)
                                        .build()
                        )
                )
                .build();

        billingClient.queryProductDetailsAsync(queryProductDetailsParams, new ProductDetailsResponseListener() {
            public void onProductDetailsResponse(BillingResult billingResult, List<ProductDetails> productDetailsList) {
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || productDetailsList == null) {
                    Log.e(TAG, "Failed to fetch subscriptions: " + billingResult.getDebugMessage());
                    subsFetchComplete = true;
                    checkAndSavePrices();
                    return;
                }

                for (ProductDetails purchase : productDetailsList) {
                    if (purchase.getProductId().equals(subscriptionProductId)) {
                        Log.d(TAG, "Found valid subscription: " + purchase.getSubscriptionOfferDetails());

                        if (purchase.getSubscriptionOfferDetails() == null) {
                            Log.e(TAG, "Invalid getSubscriptionOfferDetails");
                            subsFetchComplete = true;
                            checkAndSavePrices();
                            return;
                        }

                        for (ProductDetails.SubscriptionOfferDetails offerDetails : purchase.getSubscriptionOfferDetails()) {
                            ProductDetails.PricingPhase pricingPhase = offerDetails.getPricingPhases().getPricingPhaseList().get(0);
                            String basePlanId = offerDetails.getBasePlanId();
                            String priceWithDollarSymbol = pricingPhase.getFormattedPrice();
                            String currency = !pricingPhase.getPriceCurrencyCode().isEmpty() ? " (" + pricingPhase.getPriceCurrencyCode() + ")" : "";
                            String priceDescription = pricingPhase.getBillingPeriod().equals("P1M") ? " / month" : " / year";

                            productPrices.put(basePlanId, priceWithDollarSymbol + priceDescription + currency);
                        }
                    } else {
                        Log.e(TAG, "Ignore product: " + purchase.getName());
                    }
                }
                subsFetchComplete = true;
                checkAndSavePrices();
            }
        });
    }

    public void fetchIAPs() {
        Log.d(TAG, "fetchRemoveAdsIAPs");

        QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(
                        ImmutableList.of(
                                QueryProductDetailsParams.Product.newBuilder()
                                        .setProductId(iapProductId)
                                        .setProductType(BillingClient.ProductType.INAPP)
                                        .build()
                        )
                )
                .build();

        billingClient.queryProductDetailsAsync(queryProductDetailsParams, new ProductDetailsResponseListener() {
            @Override
            public void onProductDetailsResponse(BillingResult billingResult, List<ProductDetails> productDetailsList) {
                if (billingResult.getResponseCode() != BillingClient.BillingResponseCode.OK || productDetailsList == null) {
                    Log.e(TAG, "Failed to fetch IAPs: " + billingResult.getDebugMessage());
                    iapFetchComplete = true;
                    checkAndSavePrices();
                    return;
                }

                for (ProductDetails productDetails : productDetailsList) {
                    if (productDetails.getProductId().equals(iapProductId)) {
                        Log.d(TAG, "Found valid IAP: " + productDetails.getProductId());

                        ProductDetails.OneTimePurchaseOfferDetails offerDetails = productDetails.getOneTimePurchaseOfferDetails();
                        if (offerDetails != null) {
                            String formattedPrice = offerDetails.getFormattedPrice();
                            String currencyCode = offerDetails.getPriceCurrencyCode();
                            String currency = !currencyCode.isEmpty() ? " (" + currencyCode + ")" : "";

                            productPrices.put(productDetails.getProductId(), formattedPrice + " once" + currency);
                        } else {
                            Log.e(TAG, "No price details available for IAP: " + productDetails.getProductId());
                        }
                    } else {
                        Log.e(TAG, "Ignore product: " + productDetails.getProductId());
                    }
                }
                iapFetchComplete = true;
                checkAndSavePrices();
            }
        });
    }

    // Method to check if both fetches are complete and then save the productPrices map to EncryptClient
    private void checkAndSavePrices() {
        if (iapFetchComplete && subsFetchComplete) {
            Log.d(TAG, "All products fetched. Saving to EncryptClient.");
            encryptClient.saveJSONObject("productPriceData", new JSONObject(productPrices));
            Log.d(TAG, "Saved productPrices: " + new JSONObject(productPrices));
        }
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        // ignore
    }
}
