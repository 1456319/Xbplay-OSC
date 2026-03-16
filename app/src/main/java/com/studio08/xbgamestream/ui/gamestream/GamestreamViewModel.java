package com.studio08.xbgamestream.ui.gamestream;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class GamestreamViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public GamestreamViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is gallery fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}