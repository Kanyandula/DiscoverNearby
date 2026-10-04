package com.kanyandula.discovernearby

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Runs Dispatchers.Main (so viewModelScope) on a test dispatcher; runTest then shares its virtual clock. */
@OptIn(ExperimentalCoroutinesApi::class) // setMain, resetMain
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(StandardTestDispatcher())

    override fun finished(description: Description) = Dispatchers.resetMain()
}
