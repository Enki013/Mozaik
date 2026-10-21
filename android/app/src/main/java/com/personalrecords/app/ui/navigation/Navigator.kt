package com.personalrecords.app.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Handles navigation events (forward, tab switch, reselect, and back) by mutating [NavigationState].
 */
class Navigator(val state: NavigationState) {
    private val _reselectEvents = MutableSharedFlow<NavKey>(extraBufferCapacity = 1)
    val reselectEvents = _reselectEvents.asSharedFlow()

    fun navigate(route: NavKey) {
        if (route in state.backStacks.keys) {
            // Top-level tab switch
            state.topLevelRoute = route
        } else {
            // Detail destination: add to current active tab's backstack
            state.backStacks[state.topLevelRoute]?.add(route)
        }
    }

    fun onReselect(route: NavKey) {
        val currentStack = state.backStacks[route]
        if (currentStack != null && currentStack.size > 1) {
            // Pop back to root destination of this top-level tab
            while (currentStack.size > 1) {
                currentStack.removeLastOrNull()
            }
        } else {
            _reselectEvents.tryEmit(route)
        }
    }

    fun goBack(): Boolean {
        val currentStack = state.backStacks[state.topLevelRoute] ?: return false
        val currentRoute = currentStack.lastOrNull() ?: return false

        return if (currentRoute == state.topLevelRoute) {
            if (state.topLevelRoute != state.startRoute) {
                state.topLevelRoute = state.startRoute
                true
            } else {
                false // Top-level startRoute: let Activity handle back / exit app
            }
        } else {
            currentStack.removeLastOrNull()
            true
        }
    }

    fun currentRoute(): NavKey {
        return state.backStacks[state.topLevelRoute]?.lastOrNull() ?: state.topLevelRoute
    }
}
