/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.transition

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.StrictFragment
import androidx.lifecycle.Lifecycle
import androidx.transition.test.R
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A fragment that has transitions that can be tracked. */
open class TransitionFragment(@LayoutRes contentLayoutId: Int = R.layout.strict_view_fragment) :
    StrictViewFragment(contentLayoutId) {
    val enterTransition = TrackingVisibility()
    val reenterTransition = TrackingVisibility()
    var exitTransition = TrackingVisibility()
    val returnTransition = TrackingVisibility()
    val sharedElementEnter = TrackingTransition()
    val sharedElementReturn = TrackingTransition()
    var startTransitionCountDownLatch = CountDownLatch(1)
    var endTransitionCountDownLatch = CountDownLatch(1)

    @Suppress("LeakingThis") val listener = TestTransitionFragmentListener(this)

    init {
        @Suppress("LeakingThis") setEnterTransition(enterTransition)
        @Suppress("LeakingThis") setReenterTransition(reenterTransition)
        @Suppress("LeakingThis") setExitTransition(exitTransition)
        @Suppress("LeakingThis") setReturnTransition(returnTransition)
        sharedElementEnterTransition = sharedElementEnter
        sharedElementReturnTransition = sharedElementReturn
        enterTransition.addListener(listener)
        sharedElementEnter.addListener(listener)
        reenterTransition.addListener(listener)
        exitTransition.addListener(listener)
        returnTransition.addListener(listener)
        sharedElementReturn.addListener(listener)
    }

    internal fun waitForTransition() {
        assertWithMessage("Timed out waiting for onTransitionEnd")
            .that(endTransitionCountDownLatch.await(1, TimeUnit.SECONDS))
            .isTrue()
        assertThat(listener.lifecycleInTransitionEnd).isNotEqualTo(Lifecycle.State.DESTROYED)
        endTransitionCountDownLatch = CountDownLatch(1)
    }

    internal fun waitForNoTransition() {
        assertThat(startTransitionCountDownLatch.await(250, TimeUnit.MILLISECONDS)).isFalse()
    }
}

open class StrictViewFragment(@LayoutRes contentLayoutId: Int = R.layout.strict_view_fragment) :
    StrictFragment(contentLayoutId) {

    internal var onCreateViewCalled: Boolean = false
    internal var onViewCreatedCalled: Boolean = false
    internal var onDestroyViewCalled: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        checkGetActivity()
        checkState("onCreateView", State.CREATED)
        assertWithMessage("Fragment should not have a view when calling onCreateView")
            .that(view)
            .isNull()
        return super.onCreateView(inflater, container, savedInstanceState).also {
            onCreateViewCalled = true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        checkGetActivity()
        checkState("onViewCreated", State.CREATED)
        onViewCreatedCalled = true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        assertWithMessage("getView returned null in onDestroyView").that(view).isNotNull()
        checkGetActivity()
        checkState("onDestroyView", State.CREATED)
        onDestroyViewCalled = true
    }

    override fun onDestroy() {
        if (onCreateViewCalled) {
            assertWithMessage("onDestroyView should be called before on Destroy")
                .that(onDestroyViewCalled)
                .isTrue()
        }
        super.onDestroy()
    }
}

open class TestTransitionFragmentListener(val fragment: TransitionFragment) :
    Transition.TransitionListener {

    lateinit var lifecycleInTransitionEnd: Lifecycle.State

    override fun onTransitionEnd(transition: Transition) {
        lifecycleInTransitionEnd =
            if (fragment.view == null) {
                Lifecycle.State.DESTROYED
            } else {
                fragment.viewLifecycleOwner.lifecycle.currentState
            }
        fragment.endTransitionCountDownLatch.countDown()
        fragment.startTransitionCountDownLatch = CountDownLatch(1)
    }

    override fun onTransitionResume(transition: Transition) {}

    override fun onTransitionPause(transition: Transition) {}

    override fun onTransitionCancel(transition: Transition) {}

    override fun onTransitionStart(transition: Transition) {
        fragment.startTransitionCountDownLatch.countDown()
    }
}
