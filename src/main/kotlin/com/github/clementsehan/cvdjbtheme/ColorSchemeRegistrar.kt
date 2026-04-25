package com.github.clementsehan.cvdjbtheme

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.util.JDOMUtil

/**
 * Ensures the "Pro-Deutan Dark" editor color scheme is registered and applied.
 *
 * Two problems are solved here:
 *
 * 1. The <editorColorScheme> EP is not reachable from com.intellij.modules.platform
 *    in the 2025.2 module split, so we register the scheme programmatically as a
 *    fallback in case the EP (now guarded by com.intellij.modules.lang) didn't load it.
 *
 * 2. EditorColorsManager.loadState() (which restores the saved global scheme) runs
 *    during service initialisation, before the editorColorScheme EP has registered
 *    our scheme. When it can't find "Pro-Deutan Dark" it silently falls back to the
 *    IDE default. We detect that mismatch in appFrameCreated and restore the correct
 *    scheme before any editor opens.
 */
internal class ColorSchemeRegistrar : AppLifecycleListener {

    override fun appFrameCreated(commandLineArgs: List<String>) {
        val manager = EditorColorsManager.getInstance()

        // Fallback registration if the editorColorScheme EP didn't load the scheme.
        if (manager.getScheme(SCHEME_NAME) == null) {
            val stream = ColorSchemeRegistrar::class.java.getResourceAsStream(SCHEME_RESOURCE)
            if (stream == null) {
                thisLogger().error("CVD Theme: color scheme resource not found at $SCHEME_RESOURCE")
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
                thisLogger().error("CVD Theme: failed to register color scheme '$SCHEME_NAME'", e)
                return
            }
        }

        // If our theme is active but EditorColorsManager.loadState() fell back to a
        // different scheme (because the scheme wasn't registered yet at that point),
        // restore the correct scheme now.
        @Suppress("DEPRECATION")
        val isOurThemeActive = LafManager.getInstance().currentLookAndFeel?.name == THEME_NAME
        if (isOurThemeActive && manager.globalScheme.name != SCHEME_NAME) {
            manager.getScheme(SCHEME_NAME)?.let { manager.setGlobalScheme(it) }
        }
    }

    private companion object {
        const val THEME_NAME = "Pro-Deutan Dark"
        const val SCHEME_NAME = "Pro-Deutan Dark"
        const val SCHEME_RESOURCE = "/themes/pro-deutan-dark.icls"
    }
}
