package com.safepulse.ui.onboarding

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.toSize

/**
 * Component bounds registry for tutorial spotlight targeting
 */
object TutorialTargetRegistry {
    private val _componentBounds = mutableStateMapOf<String, Rect>()
    val componentBounds: Map<String, Rect> get() = _componentBounds
    
    var activeTargetId by mutableStateOf<String?>(null)

    fun registerComponent(id: String, bounds: Rect) {
        _componentBounds[id] = bounds
    }
    
    fun unregisterComponent(id: String) {
        _componentBounds.remove(id)
    }
    
    fun clear() {
        _componentBounds.clear()
    }
}

/**
 * Modifier that registers a component as a tutorial target
 * @param id Unique identifier for this component in the tutorial
 */
fun Modifier.tutorialTarget(id: String): Modifier = composed {
    val density = LocalDensity.current
    
    DisposableEffect(id) {
        onDispose {
            TutorialTargetRegistry.unregisterComponent(id)
        }
    }
    
    this.onGloballyPositioned { coordinates ->
        val position = coordinates.positionInRoot()
        val size = coordinates.size.toSize()
        
        val bounds = Rect(
            left = position.x,
            top = position.y,
            right = position.x + size.width,
            bottom = position.y + size.height
        )
        
        TutorialTargetRegistry.registerComponent(id, bounds)
    }
}
