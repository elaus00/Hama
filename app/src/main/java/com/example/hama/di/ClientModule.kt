package com.example.hama.di

import com.example.hama.client.repository.McpClientRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 클라이언트 관련 의존성 모듈
 */
@Module
@InstallIn(SingletonComponent::class)
object ClientModule {
    
    @Provides
    @Singleton
    fun provideMcpClientRepository(): McpClientRepository {
        return McpClientRepository()
    }
}