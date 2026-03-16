package com.studio08.xbgamestream.Helpers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.adcolony.sdk.AdColony;
import com.adcolony.sdk.AdColonyInterstitial;
import com.adcolony.sdk.AdColonyInterstitialListener;
import com.adcolony.sdk.AdColonyReward;
import com.adcolony.sdk.AdColonyRewardListener;
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
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.OnUserEarnedRewardListener;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd;
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback;
import com.ironsource.mediationsdk.IronSource;
import com.ironsource.mediationsdk.integration.IntegrationHelper;
import com.ironsource.mediationsdk.logger.IronSourceError;
import com.ironsource.mediationsdk.model.Placement;
import com.ironsource.mediationsdk.sdk.RewardedVideoListener;
import com.studio08.xbgamestream.BuildConfig;
import com.google.common.collect.ImmutableList;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.Web.ApiClient;

import java.util.Arrays;
import java.util.List;

public class RewardedAdLoader {

    public interface RewardAdListener {
        void onRewardComplete();
    }
    private static int AD_TIMESTAMP_EXTENSION_PERIOD = 15 * 60 * 1000; // 30 min
    private static int FAILED_AD_TIMESTAMP_EXTENSION_PERIOD = 15 * 60 * 1000; // 15 min
    public static int GET_TOKEN_CACHE_DURATION = 24 * 60 * 60 * 1000; // 24 hours

    private RewardedInterstitialAd rewardedInterstitialAdAdmob;
    private Context context;
    private RewardAdListener listener = null;
    private BillingClient billingClient;
    private RewardedAd rewardedVideoAd;
    private int attemptedAdShowCount = 0;
    boolean adIsClosed = false;
    boolean adRewardGranted = false;
    EncryptClient encryptClient = null;

    private PurchasesUpdatedListener purchasesUpdatedListener = new PurchasesUpdatedListener() {
        @Override
        public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
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

    public RewardedAdLoader(Context context){
        this.context = context;
        initAd();
        initBillingClient();
        this.encryptClient = new EncryptClient(context);
    }

    public void resume(){
    }
    public void setCallbackListener(RewardAdListener listener){
        this.listener = listener;
    }

    public void showRewardedAdDialog(){
        Log.e("HERE", "Starting show ad attempt!");

        if (!shouldShowAd(context)){
            Log.e("HERE", "Not showing ad due to policy");
            if (listener != null) {
                listener.onRewardComplete();
            }
            return;
        }

        Log.e("HERE", "Showing ad dialog box");

        boolean allowTrialExtension = true;

        AlertDialog.Builder adDialog = new AlertDialog.Builder(context)
            .setTitle("Free Trial Ended")
            .setCancelable(true)
            .setPositiveButton("Purchase", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    Log.d("HERE", "Purchase");
                    buyAdRemoval();
                }
            })
            .setNegativeButton("Exit App", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    ((Activity) context).finishAffinity();
                }
            });

        if (allowTrialExtension){
            adDialog.setMessage("Thanks for trying out the app! If it worked for you, please purchase the full version. Still not sure? You can extend the trial time by watching an ad.");
            adDialog.setNeutralButton("Extend Trial", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    int currentExtensions = 0;
                    try {
                        currentExtensions = Integer.parseInt(encryptClient.getValue("free_trial_extensions"));
                    } catch (Exception e){}

                    try {
                        encryptClient.saveValue("free_trial_extensions", String.valueOf((currentExtensions + 1)));
                    } catch (Exception e){}


                    showAnyAvailableAd();
                }
            });
        } else {
            adDialog.setMessage("Thanks for trying out the app! If it worked for you, please purchase the full version to continue use.");
        }

        adDialog.show();
    }

    // is static so it can be accessed in widget class without rewardedAdLoader class
    public static boolean shouldShowAd(Context context){
        if(BuildConfig.BUILD_TYPE.equals("debug")){
            Log.e("HERE", "Not showing ad on debug build");
            return false;
        }

        // if already showed an ad
        if(!isAdTimestampExpired(context)){
            Log.e("HERE", "Not showing ad due to already showed");
            return false;
        }

        // if the user bought the ad free version
        if(getPurchaseItem(context)){
            Log.e("HERE", "Not showing ad due to user purchase");
            return false;
        }
        return true;
    }

    private void showAnyAvailableAd(){
        // check if mediated ironsource ad is available
        boolean available = IronSource.isRewardedVideoAvailable();
        if(available){
            Log.e("HERE", "Attempting to show ironsource ad");

            // reset vars
            adRewardGranted = false;
            adIsClosed = false;

            IronSource.showRewardedVideo();
        } else {
            Log.e("HERE", "CANT show ironsource ad");
            updateAdTimestamp(FAILED_AD_TIMESTAMP_EXTENSION_PERIOD, this.context);
            if(listener != null){
                listener.onRewardComplete();
                Toast.makeText(context, "Can't load ad. Giving you 15 minutes of free use anyway :)", Toast.LENGTH_SHORT).show();
            }
        }

// ADMOB STUFF DONT USE FOR NOW UNTIL THEY STOP BULLYING ME
//        if(rewardedVideoAd != null){
//            rewardedVideoAd.show((Activity) context, new OnUserEarnedRewardListener() {
//                @Override
//                public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
//                    updateAdTimestamp();
//                    listener.onRewardComplete();
//                }
//            });
//
//        } else if(rewardedInterstitialAdAdmob != null) {
//            rewardedInterstitialAdAdmob.show((Activity) context, new OnUserEarnedRewardListener(){
//                @Override
//                public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
//                    updateAdTimestamp();
//                    listener.onRewardComplete();
//                }
//            });
//        } else {
////            Log.d("HERE", "Not showing admob AD due to null rewardedInterstitialAd");
////            if(!showAdcolonyAd()) {
//                listener.onRewardComplete();
//                Toast.makeText(context, "Can't load ad...", Toast.LENGTH_SHORT).show();
////            } else {
////                Log.d("HERE", "Am showing adcolony AD!");
////            }
//        }
    }
    private void initAd(){

        // setup ironsource ad listeners
        IronSource.setRewardedVideoListener(new RewardedVideoListener() {
            @Override
            public void onRewardedVideoAdOpened() {
                Log.e("HERE", "onRewardedVideoAdOpened");
            }

            @Override
            public void onRewardedVideoAdClosed() {
                Log.e("HERE", "onRewardedVideoAdClosed");
                if(attemptedAdShowCount > 3){
                    attemptedAdShowCount = 0;
                    listener.onRewardComplete();
                } else {
                    adIsClosed = true;
                    grantRewardIfClosedAndCompleted();
                }
            }

            @Override
            public void onRewardedVideoAvailabilityChanged(boolean b) {
                Log.e("HERE", "onRewardedVideoAvailabilityChanged");

            }

            @Override
            public void onRewardedVideoAdStarted() {
                Log.e("HERE", "onRewardedVideoAdStarted");
                attemptedAdShowCount++;
            }

            @Override
            public void onRewardedVideoAdEnded() {
                Log.e("HERE", "onRewardedVideoAdEnded");

            }

            @Override
            public void onRewardedVideoAdRewarded(Placement placement) {
                adRewardGranted = true;
                grantRewardIfClosedAndCompleted();
            }

            @Override
            public void onRewardedVideoAdShowFailed(IronSourceError ironSourceError) {
                if(listener != null){
                    listener.onRewardComplete();
                }
                Log.e("HERE", "onRewardedVideoAdShowFailed");
            }

            @Override
            public void onRewardedVideoAdClicked(Placement placement) {
                Log.e("HERE", "onRewardedVideoAdClicked");
            }
        });
        IronSource.init((Activity) context, BuildConfig.IRONSOURCE_APP_KEY, IronSource.AD_UNIT.REWARDED_VIDEO);
        IronSource.setConsent(true);

        if(BuildConfig.BUILD_TYPE.equals("debug")) { // extra ad logging for debug mode
            IntegrationHelper.validateIntegration((Activity) context);
        }


        // Admob
//        MobileAds.initialize(context, new OnInitializationCompleteListener() {
//            @Override
//            public void onInitializationComplete(InitializationStatus initializationStatus) {
//                //loadRewardedInterstitial();
//                //loadRewardedVideoAd();
//            }
//        });

    }

    private void grantRewardIfClosedAndCompleted(){
        if(adIsClosed && adRewardGranted) {
            updateAdTimestamp(AD_TIMESTAMP_EXTENSION_PERIOD, this.context);
            if(listener != null){
                listener.onRewardComplete();
            }
            Log.e("HERE", "onRewardedVideoAdRewarded");
            attemptedAdShowCount = 0;
            adIsClosed = false;
            adRewardGranted = false;
        } else {
            Log.e("HERE", "Not granting reward. Closed:" + adIsClosed + " Granted:" + adRewardGranted);
        }
    }

    // for admob
    private void loadRewardedVideoAd(){
        String AdUnitId = (BuildConfig.BUILD_TYPE.equals("debug")) ? "ca-app-pub-5718214549980942/6015423364" : BuildConfig.ADMOB_UNIT_ID_REWARDED_INTERSTITIAL_1;

        AdRequest adRequest = new AdRequest.Builder().build();
        RewardedAd.load(context, AdUnitId, adRequest, new RewardedAdLoadCallback() {
            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                // Handle the error.
                Log.d("HERE", loadAdError.toString());
                rewardedVideoAd = null;
            }

            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                rewardedVideoAd = rewardedAd;
                Log.d("HERE", "Ad was loaded.");
                rewardedVideoAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override
                    public void onAdClicked() {
                        // Called when a click is recorded for an ad.
                        Log.d("HERE", "Ad was clicked.");
                    }

                    @Override
                    public void onAdDismissedFullScreenContent() {
                        // Called when ad is dismissed.
                        // Set the ad reference to null so you don't show the ad a second time.
                        Log.d("HERE", "Ad dismissed fullscreen content.");
                        rewardedVideoAd = null;
                        loadRewardedVideoAd();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(AdError adError) {
                        // Called when ad fails to show.
                        Log.e("HERE", "Ad failed to show fullscreen content.");
                        rewardedVideoAd = null;
                    }

                    @Override
                    public void onAdImpression() {
                        // Called when an impression is recorded for an ad.
                        Log.d("HERE", "Ad recorded an impression.");
                    }

                    @Override
                    public void onAdShowedFullScreenContent() {
                        // Called when ad is shown.
                        Log.d("HERE", "Ad showed fullscreen content.");
                    }
                });
            }
        });
    }
    private void loadRewardedInterstitial(){

        String AdUnitId = (BuildConfig.BUILD_TYPE.equals("debug")) ? "ca-app-pub-3940256099942544/5354046379" : BuildConfig.ADMOB_UNIT_ID_REWARDED_INTERSTITIAL_1;

        RewardedInterstitialAd.load(context, AdUnitId,
                new AdRequest.Builder().build(),  new RewardedInterstitialAdLoadCallback() {
                    @Override
                    public void onAdLoaded(RewardedInterstitialAd ad) {
                        Log.d("HERE", "Ad was loaded.");
                        rewardedInterstitialAdAdmob = ad;
                        rewardedInterstitialAdAdmob.setFullScreenContentCallback(new FullScreenContentCallback() {
                            @Override
                            public void onAdClicked() {
                                // Called when a click is recorded for an ad.
                                Log.e("HERE", "Ad was clicked.");
                            }

                            @Override
                            public void onAdDismissedFullScreenContent() {
                                // Called when ad is dismissed.
                                // Set the ad reference to null so you don't show the ad a second time.
                                Log.e("HERE", "Ad dismissed fullscreen content.");
                                rewardedInterstitialAdAdmob = null;
                                loadRewardedInterstitial();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(AdError adError) {
                                // Called when ad fails to show.
                                Log.e("HERE", "Ad failed to show fullscreen content.");
                                rewardedInterstitialAdAdmob = null;
                            }

                            @Override
                            public void onAdImpression() {
                                // Called when an impression is recorded for an ad.
                                Log.e("HERE", "Ad recorded an impression.");
                            }

                            @Override
                            public void onAdShowedFullScreenContent() {
                                // Called when ad is shown.
                                Log.e("HERE", "Ad showed fullscreen content.");
                            }
                        });
                    }
                    @Override
                    public void onAdFailedToLoad(LoadAdError loadAdError) {
                        Log.e("HERE", loadAdError.toString());
                        rewardedInterstitialAdAdmob = null;
                    }
                });
    }

    // ad helpers
    public static void updateAdTimestamp(int expireTime, Context ctx){
        SharedPreferences freqPrefs = ctx.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        SharedPreferences.Editor editor = freqPrefs.edit();
        editor.putLong("nextAdShowtime", System.currentTimeMillis()+(expireTime));
        editor.apply();
    }
    private static boolean isAdTimestampExpired(Context context){
        SharedPreferences freqPrefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        long adTimestamp = freqPrefs.getLong("nextAdShowtime", 0);
        long currentTimestamp = System.currentTimeMillis();

        Log.e("HERE", "Ad Timestamp: " + adTimestamp + " Current Timestamp: " + currentTimestamp);
        // if our ad timestamp is in the past then show new ad and update ad timestamp
        if (adTimestamp == 0){
            Log.e("HERE", "Ad Timestamp not expired due to first usage.");
            updateAdTimestamp(RewardedAdLoader.AD_TIMESTAMP_EXTENSION_PERIOD, context);
            return false;
        } else if(adTimestamp < currentTimestamp){
            Log.e("HERE", "Ad timestamp expired. Should show new ad");
            return true;
        }

        Log.e("HERE", "Ad timestamp still in future. Don't show new ad. Min Remaining: " +  ((adTimestamp - currentTimestamp) / (60 * 1000)));
        return false;
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
                            boolean activeAndroidPurchase = false;

                            for (Object purchase : purchases) {
                                boolean validPurchase = handlePurchase((Purchase) purchase);
                                if (validPurchase){
                                    activeAndroidPurchase = true;
                                }
                            }

                            // if user hasn't bought anything unset bought flag
                            if(!activeAndroidPurchase){
                                checkCrossRestoreStatus(); // this will call setPurchaseItem(false) if no external purchases
                            }
                        }
                    }
                }
        );
    }
    public static boolean getPurchaseItem(Context context){
        SharedPreferences freqPrefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        return freqPrefs.getBoolean("boughtAdRemoval", false);
    }

    // this is updated in the getToken() request on successful response
    public static boolean shouldCheckNewToken(Context context){
        SharedPreferences freqPrefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);

        long getTokenTimestamp = freqPrefs.getLong("nextMakeGetTokenRequest", 0);
        long currentTimestamp = System.currentTimeMillis();

        if (getTokenTimestamp == 0){
            Log.i("shouldCheckNewToken", "0");
            return true;
        } else if(getTokenTimestamp < currentTimestamp){
            Log.i("shouldCheckNewToken", getTokenTimestamp + "/" + currentTimestamp);
            return true;
        } else {
            return false;
        }
    }

    // will automatically revoke access if no valid tokens
    private void checkCrossRestoreStatus(){
        boolean checkToken = RewardedAdLoader.shouldCheckNewToken(this.context);
        if (!checkToken){
            Log.e("Purchase", "Not revoking access yet. Token response cached");
        } else {
            ApiClient apiClient = new ApiClient(context);
            apiClient.getToken(); // will update purchase status on failure
        }
    }


    public static void setPurchaseItem(boolean isPurchased, Context context){
        SharedPreferences freqPrefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        SharedPreferences.Editor editor = freqPrefs.edit();
        editor.putBoolean("boughtAdRemoval", isPurchased);
        editor.apply();
    }

    private boolean handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                                .setPurchaseToken(purchase.getPurchaseToken())
                                .build();
                billingClient.acknowledgePurchase(acknowledgePurchaseParams, new AcknowledgePurchaseResponseListener() {
                    @Override
                    public void onAcknowledgePurchaseResponse(@NonNull BillingResult billingResult) {
                        if (purchase.getProducts().get(0).equals("remove_ads")){
                            setPurchaseItem(true, context);
                            sendToken(purchase);
                        }
                    }
                });
                return true;
            } else {
                if (purchase.getProducts().get(0).equals("remove_ads")){
                    setPurchaseItem(true, context);
                    sendToken(purchase);
                    return true;
                } else {
                    return false;
                }
            }
        } else {
            return false;
        }
    }
    private void sendToken(Purchase purchase){
        String previousPurchaseToken = encryptClient.getValue("purchaseToken");

        try {
            if (previousPurchaseToken.equals(purchase.getPurchaseToken())){
                Log.e("Purchase", "Purchase token unchanged. Don't resend");
                return;
            }
        } catch (Exception e){
            e.printStackTrace();
        }

        ApiClient apiClient = new ApiClient(context);
        apiClient.sendToken(purchase);
    }

    public void buyAdRemoval(){

        // lookup in app purchase with name remove_ads
        QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(
                        ImmutableList.of(
                                QueryProductDetailsParams.Product.newBuilder()
                                        .setProductId("remove_ads")
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

                            // if we find a remove_ads product
                            if (purchase.getProductId().equals("remove_ads")){

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
