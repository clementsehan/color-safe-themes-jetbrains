package com.github.clementsehan.cvdjbtheme

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.util.JDOMUtil

/**
 * Ensures all CVD editor color schemes are registered and applied.
 *
 * Two problems are solved here:
 *
 * 1. The <editorColorScheme> EP is not reachable from com.intellij.modules.platform
 *    in the 2025.2 module split, so we register schemes programmatically as a
 *    fallback in case the EP (now guarded by com.intellij.modules.lang) didn't load them.
 *
 * 2. EditorColorsManager.loadState() (which restores the saved global scheme) runs
 *    during service initialisation, before the editorColorScheme EP has registered
 *    our schemes. When it can't find the scheme it silently falls back to the IDE
 *    default. We detect that mismatch in appFrameCreated and restore the correct
 *    scheme before any editor opens.
 */
internal class ColorSchemeRegistrar : AppLifecycleListener {

    override fun appFrameCreated(commandLineArgs: List<String>) {
        val manager = EditorColorsManager.getInstance()

        SCHEMES.forEach { (schemeName, resource) ->
            ensureSchemeRegistered(manager, schemeName, resource)
        }

        @Suppress("DEPRECATION")
        val activeLafName = LafManager.getInstance().currentLookAndFeel?.name
        SCHEMES.keys.find { it == activeLafName }?.let { schemeName ->
            if (manager.globalScheme.name != schemeName) {
                manager.getScheme(schemeName)?.let { manager.setGlobalScheme(it) }
            }
        }
    }

    private fun ensureSchemeRegistered(
        manager: EditorColorsManager,
        schemeName: String,
        resource: String,
    ) {
        if (manager.getScheme(schemeName) != null) return
        val stream = ColorSchemeRegistrar::class.java.getResourceAsStream(resource)
        if (stream == null) {
            thisLogger().error("CVD Theme: color scheme resource not found at $resource")
            return
        }
        runCatching {
            stream.use {
                val element = JDOMUtil.load(it)
                val parent = manager.getScheme("Darcula") ?: manager.globalScheme
                val scheme = EditorColorsSchemeImpl(parent)
                scheme.readExternal(element)
                manager.addColorScheme(scheme)
            }
        }.onFailure { e ->
            thisLogger().error("CVD Theme: failed to register color scheme '$schemeName'", e)
        }
    }

    private companion object {
        val SCHEMES = mapOf(
            "Pro-Deutan Dark" to "/themes/pro-deutan-dark.icls",
            "Tritan Dark" to "/themes/tritan-dark.icls",
        )
    }
}
