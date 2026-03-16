package com.studio08.xbgamestream.ui.builder;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class ControllerBuilderViewModel extends ViewModel {

    private MutableLiveData<String> mText;

    public ControllerBuilderViewModel() {
        mText = new MutableLiveData<>();
        mText.setValue("This is gallery fragment");
    }

    public LiveData<String> getText() {
        return mText;
    }
}