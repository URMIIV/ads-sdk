package com.example.adssdk

interface AdListener {
    fun onAdLoaded() {}
    fun onAdFailed() {}
    fun onAdClicked() {}
    fun onAdClosed() {}
}