package com.example.model

sealed class RemoteInputEvent {
    abstract fun toJson(): String

    data class MouseMove(val xRatio: Float, val yRatio: Float) : RemoteInputEvent() {
        override fun toJson(): String =
            """{"type":"mouse_move","x":${"%.4f".format(xRatio.coerceIn(0f, 1f))},"y":${"%.4f".format(yRatio.coerceIn(0f, 1f))}}"""
    }

    data class MouseClick(val button: String, val down: Boolean) : RemoteInputEvent() {
        override fun toJson(): String =
            """{"type":"mouse_click","button":"$button","down":$down}"""
    }

    data class MouseScroll(val deltaX: Float, val deltaY: Float) : RemoteInputEvent() {
        override fun toJson(): String =
            """{"type":"mouse_scroll","dx":${"%.2f".format(deltaX)},"dy":${"%.2f".format(deltaY)}}"""
    }

    data class KeyEvent(val key: String, val keyCode: Int, val isDown: Boolean) : RemoteInputEvent() {
        override fun toJson(): String =
            """{"type":"key","key":"$key","code":$keyCode,"down":$isDown}"""
    }

    data class QualityChange(val preset: String) : RemoteInputEvent() {
        override fun toJson(): String =
            """{"type":"set_quality","preset":"$preset"}"""
    }

    data class Heartbeat(val timestamp: Long = System.currentTimeMillis()) : RemoteInputEvent() {
        override fun toJson(): String =
            """{"type":"ping","t":$timestamp}"""
    }
}
