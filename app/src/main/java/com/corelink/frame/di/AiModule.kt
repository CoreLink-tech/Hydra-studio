package com.corelink.frame.di

import com.corelink.frame.ai.runtime.InferenceRuntime
import com.corelink.frame.ai.runtime.onnx.OnnxInferenceRuntime
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindInferenceRuntime(impl: OnnxInferenceRuntime): InferenceRuntime
}
