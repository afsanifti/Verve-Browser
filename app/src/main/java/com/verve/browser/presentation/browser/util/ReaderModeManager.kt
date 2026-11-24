package com.verve.browser.presentation.browser.util

import android.webkit.WebView
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

internal enum class ReaderFontFamily(
    val id: String,
    val label: String,
    val cssStack: String
) {
    SERIF(
        id = "serif",
        label = "Serif",
        cssStack = """Georgia, "Iowan Old Style", "Palatino Linotype", "Times New Roman", serif"""
    ),
    SANS_SERIF(
        id = "sans",
        label = "Sans",
        cssStack = """-apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif"""
    ),
    MONOSPACE(
        id = "mono",
        label = "Mono",
        cssStack = """ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"""
    );

    companion object {
        fun fromId(id: String?): ReaderFontFamily =
            entries.firstOrNull { it.id == id } ?: SERIF
    }
}

internal data class ReaderPalette(
    val backgroundHex: String,
    val onBackgroundHex: String,
    val secondaryTextHex: String,
    val linkHex: String,
    val cardSurfaceHex: String,
    val dividerHex: String,
    val activeChipBgHex: String,
    val activeChipTextHex: String
) {
    companion object {
        fun fromColorScheme(scheme: ColorScheme): ReaderPalette = ReaderPalette(
            backgroundHex = scheme.background.toCssHex(),
            onBackgroundHex = scheme.onBackground.toCssHex(),
            secondaryTextHex = scheme.onSurfaceVariant.toCssHex(),
            linkHex = scheme.primary.toCssHex(),
            cardSurfaceHex = scheme.surfaceContainerLow.toCssHex(),
            dividerHex = scheme.outlineVariant.toCssHex(),
            activeChipBgHex = scheme.primary.toCssHex(),
            activeChipTextHex = scheme.onPrimary.toCssHex()
        )

        private fun Color.toCssHex(): String =
            String.format("#%06X", 0xFFFFFF and toArgb())
    }
}

internal object ReaderModeManager {

    fun enable(
        webView: WebView,
        palette: ReaderPalette,
        defaultFont: ReaderFontFamily = ReaderFontFamily.SERIF,
        onResult: (Boolean) -> Unit = {}
    ) {
        val script = buildEnableScript(palette, defaultFont)
        webView.evaluateJavascript(script) { rawResult ->
            val activated = rawResult?.trim('"') == "true"
            onResult(activated)
        }
    }

    fun setFontFamily(webView: WebView, fontFamily: ReaderFontFamily) {
        val escapedStack = fontFamily.cssStack.replace("'", "\\'")
        val script = """
            (function() {
                const root = document.getElementById('__verve_reader_root');
                if (!root) return;
                root.style.setProperty('--verve-reader-font', '$escapedStack');
                root.querySelectorAll('[data-reader-font]').forEach(function(btn) {
                    btn.classList.toggle('is-active', btn.getAttribute('data-reader-font') === '${fontFamily.id}');
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    fun disable(webView: WebView) {
        val script = """
            (function() {
                const root = document.getElementById('__verve_reader_root');
                if (root) root.remove();

                const styleTag = document.getElementById('__verve_reader_style');
                if (styleTag) styleTag.remove();

                if (Array.isArray(window.__verveDisabledSheets)) {
                    window.__verveDisabledSheets.forEach(function(entry) {
                        try { entry.sheet.disabled = entry.wasDisabled; } catch (e) {}
                    });
                    window.__verveDisabledSheets = null;
                }

                Array.from(document.body.children).forEach(function(el) {
                    if (el.dataset && el.dataset.verveOrigDisplay !== undefined) {
                        el.style.display = el.dataset.verveOrigDisplay;
                        delete el.dataset.verveOrigDisplay;
                    }
                });

                if (typeof window.__verveOrigBodyStyle === 'string') {
                    document.body.setAttribute('style', window.__verveOrigBodyStyle);
                    delete window.__verveOrigBodyStyle;
                }

                if (typeof window.__verveOrigScrollY === 'number') {
                    window.scrollTo(0, window.__verveOrigScrollY);
                    delete window.__verveOrigScrollY;
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun buildEnableScript(
        palette: ReaderPalette,
        defaultFont: ReaderFontFamily
    ): String {
        val serifStack = ReaderFontFamily.SERIF.cssStack.replace("'", "\\'")
        val sansStack = ReaderFontFamily.SANS_SERIF.cssStack.replace("'", "\\'")
        val monoStack = ReaderFontFamily.MONOSPACE.cssStack.replace("'", "\\'")

        return """
            (function() {
                if (document.getElementById('__verve_reader_root')) return "true";

                const NEGATIVE_RE = /nav|footer|sidebar|comment|menu|banner|promo|related|share|social|popup|modal|cookie|newsletter|subscribe|widget|breadcrumb|author-bio|tags/i;
                const POSITIVE_RE = /article|body|content|entry|main|post|story|text|blog/i;

                function textLength(el) {
                    return (el.innerText || el.textContent || '').trim().length;
                }

                function linkDensity(el) {
                    const total = textLength(el);
                    if (total === 0) return 1;
                    let linkChars = 0;
                    el.querySelectorAll('a').forEach(function(a) {
                        linkChars += (a.innerText || '').trim().length;
                    });
                    return linkChars / total;
                }

                function findBestArticleNode() {
                    const directSelectors = [
                        'article',
                        '[role="main"] article',
                        'main article',
                        '.post-content',
                        '.article-content',
                        '.article-body',
                        '.entry-content',
                        '.story-body',
                        '[itemprop="articleBody"]',
                        'main',
                        '[role="main"]'
                    ];

                    let bestNode = null;
                    let bestScore = 0;

                    for (let i = 0; i < directSelectors.length; i++) {
                        const nodes = document.querySelectorAll(directSelectors[i]);
                        nodes.forEach(function(node) {
                            const len = textLength(node);
                            if (len < 280) return;
                            const density = linkDensity(node);
                            if (density > 0.45) return;
                            const pCount = node.querySelectorAll('p').length;
                            const score = len * (1 - density) + (pCount * 80);
                            if (score > bestScore) {
                                bestScore = score;
                                bestNode = node;
                            }
                        });
                        if (bestNode && bestScore > 900) break;
                    }

                    if (!bestNode) {
                        document.querySelectorAll('div, section').forEach(function(node) {
                            const idClass = (node.id || '') + ' ' + (typeof node.className === 'string' ? node.className : '');
                            if (NEGATIVE_RE.test(idClass) && !POSITIVE_RE.test(idClass)) return;
                            const len = textLength(node);
                            if (len < 320) return;
                            const density = linkDensity(node);
                            if (density > 0.4) return;
                            const pCount = node.querySelectorAll('p').length;
                            let score = len * (1 - density) + (pCount * 90);
                            if (POSITIVE_RE.test(idClass)) score *= 1.35;
                            if (score > bestScore) {
                                bestScore = score;
                                bestNode = node;
                            }
                        });
                    }

                    return bestNode;
                }

                const articleNode = findBestArticleNode();
                if (!articleNode) return "false";

                const clone = articleNode.cloneNode(true);

                const junkSelectors = [
                    'script', 'style', 'noscript', 'iframe', 'form', 'button', 'input',
                    'select', 'textarea', 'nav', 'footer', 'aside',
                    '[aria-hidden="true"]', '[role="navigation"]', '[role="banner"]',
                    '[role="complementary"]', '[role="dialog"]'
                ];
                clone.querySelectorAll(junkSelectors.join(',')).forEach(function(el) {
                    el.remove();
                });

                clone.querySelectorAll('*').forEach(function(el) {
                    const idClass = (el.id || '') + ' ' + (typeof el.className === 'string' ? el.className : '');
                    if (NEGATIVE_RE.test(idClass) && !POSITIVE_RE.test(idClass) && textLength(el) < 400) {
                        el.remove();
                        return;
                    }

                    if (el.tagName === 'IMG') {
                        const lazySrc = el.getAttribute('data-src') ||
                            el.getAttribute('data-lazy-src') ||
                            el.getAttribute('data-original');
                        if (lazySrc && (!el.getAttribute('src') || el.getAttribute('src').startsWith('data:'))) {
                            el.setAttribute('src', lazySrc);
                        }
                        if ((el.width && el.width < 32) || (el.height && el.height < 32)) {
                            el.remove();
                            return;
                        }
                    }

                    el.removeAttribute('style');
                    el.removeAttribute('class');
                    el.removeAttribute('width');
                    el.removeAttribute('height');
                });

                const h1 = clone.querySelector('h1') || document.querySelector('h1');
                const titleText = (h1 ? h1.innerText : document.title || '').trim();
                if (h1 && clone.contains(h1)) {
                    h1.remove();
                }

                const authorMeta = document.querySelector('meta[name="author"], meta[property="article:author"]');
                const authorText = authorMeta ? (authorMeta.getAttribute('content') || '').trim() : '';
                const words = (clone.innerText || '').trim().split(/\s+/).filter(Boolean).length;
                const readMinutes = Math.max(1, Math.round(words / 220));
                const domain = location.hostname.replace(/^www\./, '');
                const metaParts = [domain, readMinutes + ' min read'];
                if (authorText) metaParts.splice(1, 0, authorText);

                const FONT_STACKS = {
                    serif: '$serifStack',
                    sans: '$sansStack',
                    mono: '$monoStack'
                };

                let savedPrefs = { font: '${defaultFont.id}', scale: 1.0 };
                try {
                    const raw = localStorage.getItem('__verve_reader_prefs');
                    if (raw) {
                        const parsed = JSON.parse(raw);
                        if (parsed && FONT_STACKS[parsed.font]) savedPrefs.font = parsed.font;
                        if (parsed && typeof parsed.scale === 'number') {
                            savedPrefs.scale = Math.min(1.45, Math.max(0.85, parsed.scale));
                        }
                    }
                } catch (e) {}

                function savePrefs() {
                    try {
                        localStorage.setItem('__verve_reader_prefs', JSON.stringify(savedPrefs));
                    } catch (e) {}
                }

                window.__verveOrigScrollY = window.scrollY || 0;
                window.__verveOrigBodyStyle = document.body.getAttribute('style') || '';
                window.__verveDisabledSheets = [];
                Array.from(document.styleSheets).forEach(function(sheet) {
                    try {
                        window.__verveDisabledSheets.push({ sheet: sheet, wasDisabled: sheet.disabled });
                        sheet.disabled = true;
                    } catch (e) {}
                });

                Array.from(document.body.children).forEach(function(el) {
                    el.dataset.verveOrigDisplay = el.style.display || '';
                    el.style.display = 'none';
                });

                const styleEl = document.createElement('style');
                styleEl.id = '__verve_reader_style';
                styleEl.textContent = `
                    html, body {
                        margin: 0 !important;
                        padding: 0 !important;
                        background: ${palette.backgroundHex} !important;
                        color: ${palette.onBackgroundHex} !important;
                        -webkit-text-size-adjust: 100%;
                    }
                    #__verve_reader_root {
                        --verve-reader-font: ${defaultFont.cssStack};
                        --verve-reader-scale: 1;
                        max-width: 68ch;
                        margin: 0 auto;
                        padding: 28px 22px 120px;
                        font-family: var(--verve-reader-font);
                        font-size: calc(1.125rem * var(--verve-reader-scale));
                        line-height: 1.72;
                        word-wrap: break-word;
                        overflow-wrap: break-word;
                    }
                    #__verve_reader_root header {
                        margin-bottom: 24px;
                        padding-bottom: 18px;
                        border-bottom: 1px solid ${palette.dividerHex};
                    }
                    #__verve_reader_root h1 {
                        font-size: calc(1.75rem * var(--verve-reader-scale));
                        line-height: 1.28;
                        font-weight: 700;
                        margin: 0 0 12px;
                        letter-spacing: -0.015em;
                    }
                    #__verve_reader_meta {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                        font-size: 0.875rem;
                        line-height: 1.4;
                        color: ${palette.secondaryTextHex};
                        margin-bottom: 14px;
                    }
                    #__verve_reader_controls {
                        display: flex;
                        align-items: center;
                        justify-content: space-between;
                        gap: 8px;
                        flex-wrap: wrap;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                    }
                    #__verve_reader_controls .verve-pill-group {
                        display: inline-flex;
                        align-items: center;
                        gap: 4px;
                        padding: 4px;
                        border-radius: 999px;
                        background: ${palette.cardSurfaceHex};
                        border: 1px solid ${palette.dividerHex};
                    }
                    #__verve_reader_controls button {
                        appearance: none;
                        border: none;
                        background: transparent;
                        color: ${palette.secondaryTextHex};
                        font-size: 0.8125rem;
                        font-weight: 600;
                        padding: 6px 12px;
                        border-radius: 999px;
                        cursor: pointer;
                        line-height: 1;
                    }
                    #__verve_reader_controls button[data-reader-font="serif"] {
                        font-family: Georgia, serif;
                    }
                    #__verve_reader_controls button[data-reader-font="mono"] {
                        font-family: ui-monospace, Menlo, Consolas, monospace;
                    }
                    #__verve_reader_controls button.is-active {
                        background: ${palette.activeChipBgHex};
                        color: ${palette.activeChipTextHex};
                    }
                    #__verve_reader_root h2, #__verve_reader_root h3, #__verve_reader_root h4 {
                        line-height: 1.35;
                        margin-top: 1.6em;
                        margin-bottom: 0.5em;
                    }
                    #__verve_reader_root p {
                        margin: 0 0 1.15em;
                    }
                    #__verve_reader_root a {
                        color: ${palette.linkHex};
                        text-decoration: underline;
                        text-underline-offset: 3px;
                    }
                    #__verve_reader_root img, #__verve_reader_root figure {
                        max-width: 100%;
                        height: auto;
                        border-radius: 14px;
                        margin: 1.25em 0;
                        display: block;
                    }
                    #__verve_reader_root blockquote {
                        margin: 1.25em 0;
                        padding: 12px 16px;
                        border-left: 3px solid ${palette.linkHex};
                        background: ${palette.cardSurfaceHex};
                        border-radius: 0 12px 12px 0;
                        color: ${palette.secondaryTextHex};
                    }
                    #__verve_reader_root pre, #__verve_reader_root code {
                        font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
                        font-size: 0.9em;
                        background: ${palette.cardSurfaceHex};
                        border-radius: 8px;
                    }
                    #__verve_reader_root pre {
                        padding: 14px;
                        overflow-x: auto;
                    }
                    #__verve_reader_root code {
                        padding: 2px 6px;
                    }
                    #__verve_reader_root ul, #__verve_reader_root ol {
                        padding-left: 1.4em;
                        margin: 0 0 1.2em;
                    }
                    #__verve_reader_root li {
                        margin-bottom: 0.45em;
                    }
                    #__verve_reader_root table {
                        width: 100%;
                        border-collapse: collapse;
                        margin: 1.25em 0;
                        font-size: 0.95rem;
                    }
                    #__verve_reader_root th, #__verve_reader_root td {
                        border: 1px solid ${palette.dividerHex};
                        padding: 8px 10px;
                        text-align: left;
                    }
                `;
                document.head.appendChild(styleEl);

                const readerRoot = document.createElement('article');
                readerRoot.id = '__verve_reader_root';
                readerRoot.style.setProperty('--verve-reader-font', FONT_STACKS[savedPrefs.font]);
                readerRoot.style.setProperty('--verve-reader-scale', String(savedPrefs.scale));

                const header = document.createElement('header');
                const h1El = document.createElement('h1');
                h1El.textContent = titleText;

                const metaEl = document.createElement('div');
                metaEl.id = '__verve_reader_meta';
                metaEl.textContent = metaParts.join(' · ');

                const controlsEl = document.createElement('div');
                controlsEl.id = '__verve_reader_controls';

                const fontGroup = document.createElement('div');
                fontGroup.className = 'verve-pill-group';

                const fontOptions = [
                    { id: 'serif', label: 'Serif' },
                    { id: 'sans', label: 'Sans' },
                    { id: 'mono', label: 'Mono' }
                ];

                fontOptions.forEach(function(opt) {
                    const btn = document.createElement('button');
                    btn.type = 'button';
                    btn.textContent = opt.label;
                    btn.setAttribute('data-reader-font', opt.id);
                    if (savedPrefs.font === opt.id) btn.classList.add('is-active');
                    btn.addEventListener('click', function() {
                        savedPrefs.font = opt.id;
                        readerRoot.style.setProperty('--verve-reader-font', FONT_STACKS[opt.id]);
                        fontGroup.querySelectorAll('button').forEach(function(b) {
                            b.classList.toggle('is-active', b === btn);
                        });
                        savePrefs();
                    });
                    fontGroup.appendChild(btn);
                });

                const sizeGroup = document.createElement('div');
                sizeGroup.className = 'verve-pill-group';

                const decBtn = document.createElement('button');
                decBtn.type = 'button';
                decBtn.textContent = 'A−';
                decBtn.setAttribute('aria-label', 'Decrease font size');
                decBtn.addEventListener('click', function() {
                    savedPrefs.scale = Math.max(0.85, Number((savedPrefs.scale - 0.08).toFixed(2)));
                    readerRoot.style.setProperty('--verve-reader-scale', String(savedPrefs.scale));
                    savePrefs();
                });

                const incBtn = document.createElement('button');
                incBtn.type = 'button';
                incBtn.textContent = 'A+';
                incBtn.setAttribute('aria-label', 'Increase font size');
                incBtn.addEventListener('click', function() {
                    savedPrefs.scale = Math.min(1.45, Number((savedPrefs.scale + 0.08).toFixed(2)));
                    readerRoot.style.setProperty('--verve-reader-scale', String(savedPrefs.scale));
                    savePrefs();
                });

                sizeGroup.appendChild(decBtn);
                sizeGroup.appendChild(incBtn);

                controlsEl.appendChild(fontGroup);
                controlsEl.appendChild(sizeGroup);

                header.appendChild(h1El);
                header.appendChild(metaEl);
                header.appendChild(controlsEl);

                readerRoot.appendChild(header);
                readerRoot.appendChild(clone);
                document.body.appendChild(readerRoot);
                window.scrollTo(0, 0);

                return "true";
            })();
        """.trimIndent()
    }
}