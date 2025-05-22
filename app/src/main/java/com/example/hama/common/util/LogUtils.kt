package com.example.hama.common.util

import android.util.Log

/**
 * 로깅 관련 유틸리티 클래스
 */
object LogUtils {
    private const val DEFAULT_TAG = "Hama"
    
    /**
     * 디버그 로그
     */
    fun d(tag: String = DEFAULT_TAG, message: String) {
        Log.d(tag, message)
    }
    
    /**
     * 정보 로그
     */
    fun i(tag: String = DEFAULT_TAG, message: String) {
        Log.i(tag, message)
    }
    
    /**
     * 경고 로그
     */
    fun w(tag: String = DEFAULT_TAG, message: String) {
        Log.w(tag, message)
    }
    
    /**
     * 에러 로그
     */
    fun e(tag: String = DEFAULT_TAG, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
        } else {
            Log.e(tag, message)
        }
    }
    
    /**
     * 상세 로그
     */
    fun v(tag: String = DEFAULT_TAG, message: String) {
        Log.v(tag, message)
    }
}