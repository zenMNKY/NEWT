package io.github.artcraftandroid.procraft.canvas

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONException
import org.json.JSONObject

/** A key press: the key's name as the shared shortcuts have it ("z", "[", "space", "left", "f5"), and the Ctrl (command), Shift and Alt keys held. */
data class Combo(val key: String, val ctrl: Boolean = false, val shift: Boolean = false, val alt: Boolean = false) {
    /** Written for keeping: "ctrl+shift+z". */
    fun text(): String = buildList {
        if (ctrl) add("ctrl")
        if (shift) add("shift")
        if (alt) add("alt")
        add(key)
    }.joinToString("+")

    /** Written for showing: "Ctrl+Shift+Z". */
    fun label(): String = buildList {
        if (ctrl) add("Ctrl")
        if (shift) add("Shift")
        if (alt) add("Alt")
        add(keyLabel(key))
    }.joinToString("+")

    companion object {
        /** A press as [text] writes it; null if it is not one. */
        fun parse(text: String): Combo? {
            val parts = text.split("+")
            val key = parts.lastOrNull()?.takeIf { it.isNotEmpty() } ?: return null
            val mods = parts.dropLast(1)
            if (mods.any { it != "ctrl" && it != "shift" && it != "alt" }) return null
            return Combo(key, "ctrl" in mods, "shift" in mods, "alt" in mods)
        }

        private fun keyLabel(key: String): String = when (key) {
            "space" -> "Space"
            "delete" -> "Delete"
            "backspace" -> "Backspace"
            "left" -> "Left"
            "right" -> "Right"
            "up" -> "Up"
            "down" -> "Down"
            else -> if (key.length == 1) key.uppercase() else key.replaceFirstChar { it.uppercase() }
        }
    }
}

/**
 * The hotkeys of brushes: a press that chooses a brush, by the brush's key in the library
 * (`quickBrushKey`). Kept apart from the other settings, in [prefs]. (The keys of the app's own
 * shortcuts are rebound by the Keyboard shortcuts plugin.)
 */
class Hotkeys(private val prefs: SharedPreferences) {
    /** The presses that choose brushes, by the brush's key in the library. */
    private val brushes = HashMap<Long, Combo>()

    /** Counts every change, for the screen to look again. */
    var changes by mutableIntStateOf(0)
        private set

    init {
        for ((id, text) in prefs.all) {
            if (text !is String || !id.startsWith(BRUSH_PREFIX)) continue
            val key = id.removePrefix(BRUSH_PREFIX).toLongOrNull() ?: continue
            brushes[key] = Combo.parse(text) ?: continue
        }
    }

    /** The presses that choose brushes, by brush key. */
    fun brushBindings(): Map<Long, Combo> {
        changes.let { }
        return brushes.toMap()
    }

    /** The brush (its key) [press] chooses, if any. */
    fun brushFor(press: Combo): Long? = brushes.entries.firstOrNull { it.value == press }?.key

    private fun removeBrush(key: Long) {
        brushes.remove(key)
        prefs.edit().remove(BRUSH_PREFIX + key).apply()
    }

    /**
     * Sets the press that chooses the brush [key] ([combo] null: none). The same press given to
     * another brush is taken from it: whether there was one is returned.
     */
    fun assignBrush(key: Long, combo: Combo?): Boolean {
        var took = false
        if (combo != null) {
            for ((other, c) in brushes.toMap()) {
                if (other != key && c == combo) {
                    removeBrush(other)
                    took = true
                }
            }
        }
        if (combo == null) {
            removeBrush(key)
        } else {
            brushes[key] = combo
            prefs.edit().putString(BRUSH_PREFIX + key, combo.text()).apply()
        }
        changes++
        return took
    }

    /** The brushes' presses as a text to keep in a file ([importJson] reads it back). */
    fun exportJson(): String {
        val chosen = JSONObject()
        for ((key, c) in brushes) chosen.put(key.toString(), c.text())
        return JSONObject().put("app", "procraft-hotkeys").put("version", 2).put("brushes", chosen).toString(2)
    }

    /** Takes in what [exportJson] wrote: false if [text] is not that. */
    fun importJson(text: String): Boolean {
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return false
        }
        val chosen = root.optJSONObject("brushes") ?: return false
        for (key in brushes.keys.toList()) removeBrush(key)
        for (name in chosen.keys()) {
            val key = name.toLongOrNull() ?: continue
            val combo = Combo.parse(chosen.optString(name)) ?: continue
            brushes[key] = combo
            prefs.edit().putString(BRUSH_PREFIX + key, combo.text()).apply()
        }
        changes++
        return true
    }

    private companion object {
        /** What a brush's kept press is named under, before its key. */
        const val BRUSH_PREFIX = "brush:"
    }
}
