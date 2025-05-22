package com.example.hama.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 애플리케이션 전역 의존성 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    // 전역 의존성 설정 (필요시 추가)
}