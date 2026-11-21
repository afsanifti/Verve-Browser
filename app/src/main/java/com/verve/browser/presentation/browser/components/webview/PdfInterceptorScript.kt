package com.verve.browser.presentation.browser.components.webview

internal object PdfInterceptorScript {

    val SCRIPT: String = """
        (() => {
            const DOWNLOAD_ACTION_REGEX = /(admitcard|download|export|receipt|invoice|challan|payslip|transcript|certificate|marksheet|voucher|statement|attachment)/i;
            const DOWNLOAD_LABEL_REGEX = /(download|export|save\s+pdf|print\s+pdf|get\s+pdf)/i;
            const FILE_EXT_REGEX = /\.(pdf|zip|rar|7z|csv|xlsx|xls|doc|docx|txt|apk|tar|gz)(\?|#|$)/i;
            const SENSITIVE_FIELD_REGEX = /(password|passwd|pwd|pin|otp|cvv|cardnumber|secret)/i;
            const blobUrlToName = window.__verveBlobUrlToName || (window.__verveBlobUrlToName = new Map());
            const nativeFetch = typeof window.fetch === 'function' ? window.fetch.bind(window) : null;
            let lastSubmitter = null;
            let lastSubmitterTime = 0;

            const normalizeDownloadName = (rawName, fallbackExt) => {
                let cleaned = String(rawName || 'download')
                    .split('/').pop()
                    .split('\\').pop()
                    .replace(/\.(php|aspx|jsp|cgi|do)\.(pdf|zip|csv|xlsx|xls|doc|docx|txt)$/i, '.$2');
                if (fallbackExt && !cleaned.toLowerCase().endsWith('.' + fallbackExt.toLowerCase())) {
                    cleaned += '.' + fallbackExt;
                }
                return cleaned || 'download';
            };

            const extractFileName = (disposition, fallbackUrl, defaultExt) => {
                if (disposition) {
                    const utf8Match = disposition.match(/filename\*\s*=\s*(?:UTF-8''|utf-8'')([^;\r\n]+)/i);
                    if (utf8Match && utf8Match[1]) {
                        try {
                            return normalizeDownloadName(decodeURIComponent(utf8Match[1].trim().replace(/^["']|["']$/g, '')), defaultExt);
                        } catch (_) {}
                    }
                    const stdMatch = disposition.match(/filename\s*=\s*["']?([^"';\r\n]+)["']?/i);
                    if (stdMatch && stdMatch[1]) {
                        return normalizeDownloadName(stdMatch[1].trim(), defaultExt);
                    }
                }
                if (fallbackUrl && !fallbackUrl.startsWith('blob:') && !fallbackUrl.startsWith('data:')) {
                    try {
                        const parsed = new URL(fallbackUrl, window.location.href);
                        const lastSeg = parsed.pathname.split('/').filter(Boolean).pop();
                        if (lastSeg) {
                            return normalizeDownloadName(lastSeg, defaultExt);
                        }
                    } catch (_) {}
                }
                return defaultExt ? ('document.' + defaultExt) : 'download';
            };

            const resolveSameOriginHttpsUrl = (rawAction) => {
                try {
                    const resolved = new URL(rawAction || window.location.href, window.location.href);
                    if (window.location.protocol === 'https:' &&
                        resolved.protocol === 'http:' &&
                        resolved.hostname === window.location.hostname
                    ) {
                        resolved.protocol = 'https:';
                    }
                    return resolved.href;
                } catch (_) {
                    return rawAction || window.location.href;
                }
            };

            const hasPdfMagicBytes = async (blob) => {
                if (!blob || blob.size < 5) return false;
                try {
                    const scanLimit = Math.min(blob.size, 1024);
                    const headerBuffer = await blob.slice(0, scanLimit).arrayBuffer();
                    const bytes = new Uint8Array(headerBuffer);
                    for (let i = 0; i <= bytes.length - 5; i++) {
                        if (bytes[i] === 0x25 &&     // %
                            bytes[i + 1] === 0x50 && // P
                            bytes[i + 2] === 0x44 && // D
                            bytes[i + 3] === 0x46 && // F
                            bytes[i + 4] === 0x2D    // -
                        ) {
                            return true;
                        }
                    }
                    return false;
                } catch (_) {
                    return false;
                }
            };

            const classifyDownloadResponse = async (contentType, disposition, blob) => {
                const ct = (contentType || '').toLowerCase();
                const cd = (disposition || '').toLowerCase();
                const isAttachment = cd.includes('attachment');

                if (ct.includes('application/pdf') || ct.includes('application/x-pdf') || cd.includes('.pdf') || await hasPdfMagicBytes(blob)) {
                    return { isDownload: true, isPdf: true };
                }
                if (ct.includes('text/html') || ct.includes('application/xhtml+xml')) {
                    return { isDownload: false, isPdf: false };
                }
                if (isAttachment ||
                    ct.includes('application/octet-stream') ||
                    ct.includes('application/zip') ||
                    ct.includes('application/vnd.') ||
                    ct.includes('application/msword') ||
                    ct.includes('text/csv')
                ) {
                    return { isDownload: true, isPdf: false };
                }
                return { isDownload: false, isPdf: false };
            };

            const sendBlobToNative = (blob, fileName, mimeType, sourceUrl, isPdf) => {
                if (!window.VervePdfBridge || !blob) return;
                const resolvedSource = sourceUrl || window.location.href;
                const reader = new FileReader();
                reader.onloadend = () => {
                    if (typeof reader.result !== 'string') return;
                    if (isPdf) {
                        const cleanPdfName = normalizeDownloadName(fileName, 'pdf');
                        if (window.VervePdfBridge.onBase64PdfWithSourceReceived) {
                            window.VervePdfBridge.onBase64PdfWithSourceReceived(reader.result, cleanPdfName, resolvedSource);
                        } else {
                            window.VervePdfBridge.onBase64PdfReceived(reader.result, cleanPdfName);
                        }
                    } else if (window.VervePdfBridge.onBase64FileReceived) {
                        window.VervePdfBridge.onBase64FileReceived(
                            reader.result,
                            normalizeDownloadName(fileName, null),
                            mimeType || blob.type || 'application/octet-stream',
                            resolvedSource
                        );
                    }
                };
                reader.readAsDataURL(blob);
            };

            const containsSensitiveInputs = (form) => {
                if (!form || !form.elements) return true;
                for (let i = 0; i < form.elements.length; i++) {
                    const el = form.elements[i];
                    const type = (el.type || '').toLowerCase();
                    if (type === 'password') return true;
                    const fieldId = [el.name, el.id, el.autocomplete].join(' ');
                    if (SENSITIVE_FIELD_REGEX.test(fieldId)) return true;
                }
                return false;
            };

            const shouldInterceptPostForm = (form, submitter) => {
                if (!form || form.tagName !== 'FORM' || !window.VervePdfBridge || !nativeFetch) return false;
                if (containsSensitiveInputs(form)) return false;

                const rawMethod = (submitter && submitter.getAttribute('formmethod')) ||
                    form.getAttribute('method') ||
                    form.method ||
                    'GET';
                if (rawMethod.toUpperCase() !== 'POST') return false;

                const actionAttr = (submitter && submitter.getAttribute('formaction')) ||
                    form.getAttribute('action') ||
                    '';
                const targetAttr = ((submitter && submitter.getAttribute('formtarget')) ||
                    form.getAttribute('target') ||
                    '').toLowerCase();
                const submitterLabel = submitter
                    ? [
                        submitter.innerText,
                        submitter.textContent,
                        submitter.value,
                        submitter.name,
                        submitter.id,
                        submitter.getAttribute('aria-label')
                    ].join(' ')
                    : '';

                return targetAttr === '_blank' ||
                    form.hasAttribute('download') ||
                    (submitter && submitter.hasAttribute && submitter.hasAttribute('download')) ||
                    FILE_EXT_REGEX.test(actionAttr) ||
                    DOWNLOAD_ACTION_REGEX.test(actionAttr) ||
                    DOWNLOAD_LABEL_REGEX.test(submitterLabel);
            };

            const executeInterceptedPostForm = (form, submitter, fallbackNativeSubmit) => {
                const rawAction = (submitter && submitter.getAttribute('formaction')) ||
                    form.getAttribute('action') ||
                    window.location.href;
                const actionUrl = resolveSameOriginHttpsUrl(rawAction);

                const formData = new FormData(form);
                if (submitter && submitter.name && !formData.has(submitter.name)) {
                    formData.append(submitter.name, submitter.value || '');
                }

                const encType = ((submitter && submitter.getAttribute('formenctype')) ||
                    form.getAttribute('enctype') ||
                    form.enctype ||
                    '').toLowerCase();
                const requestBody = encType === 'multipart/form-data'
                    ? formData
                    : new URLSearchParams(formData);

                nativeFetch(actionUrl, {
                    method: 'POST',
                    body: requestBody,
                    credentials: 'include',
                    referrer: window.location.href,
                    redirect: 'follow',
                    headers: {
                        'Accept': 'application/pdf,application/octet-stream,text/html;q=0.9,*/*;q=0.8'
                    }
                }).then(async (response) => {
                    const contentType = response.headers.get('content-type') || '';
                    const disposition = response.headers.get('content-disposition') || '';
                    const resolvedUrl = response.url || actionUrl;
                    const blob = await response.blob();
                    const classification = await classifyDownloadResponse(contentType, disposition, blob);

                    if (classification.isDownload && response.ok && blob.size > 0) {
                        const fileName = extractFileName(disposition, resolvedUrl, classification.isPdf ? 'pdf' : null);
                        sendBlobToNative(blob, fileName, contentType, resolvedUrl, classification.isPdf);
                    } else if (window.VervePdfBridge && window.VervePdfBridge.onPostDownloadError) {
                        const htmlText = await blob.slice(0, 1024).text();
                        const alertMatch = htmlText.match(/alert\s*\(\s*['"]([^'"]+)['"]\s*\)/i);
                        const snippet = (alertMatch && alertMatch[1])
                            ? ('ALERT:' + alertMatch[1])
                            : htmlText.replace(/<[^>]*>/g, ' ').trim();
                        window.VervePdfBridge.onPostDownloadError(
                            extractFileName(disposition, resolvedUrl, 'pdf'),
                            resolvedUrl,
                            response.status,
                            contentType,
                            snippet
                        );
                    }
                }).catch(() => {
                    fallbackNativeSubmit();
                });
            };

            if (!window.__vervePdfProtoPatched) {
                window.__vervePdfProtoPatched = true;

                const origFormSubmit = HTMLFormElement.prototype.submit;
                HTMLFormElement.prototype.submit = function() {
                    const submitter = (Date.now() - lastSubmitterTime < 1500) ? lastSubmitter : null;
                    if (shouldInterceptPostForm(this, submitter)) {
                        executeInterceptedPostForm(this, submitter, () => origFormSubmit.call(this));
                        return;
                    }
                    return origFormSubmit.apply(this, arguments);
                };

                if (nativeFetch) {
                    window.fetch = function(input, init) {
                        const promise = nativeFetch(input, init);
                        promise.then(async (response) => {
                            try {
                                if (!response || !response.ok || !window.VervePdfBridge) return;
                                const contentType = response.headers.get('content-type') || '';
                                const disposition = response.headers.get('content-disposition') || '';
                                const cloned = response.clone();
                                const blob = await cloned.blob();
                                const classification = await classifyDownloadResponse(contentType, disposition, blob);
                                if (classification.isDownload && blob.size > 0) {
                                    const reqUrl = typeof input === 'string' ? input : (input && input.url) || response.url;
                                    const fileName = extractFileName(disposition, reqUrl, classification.isPdf ? 'pdf' : null);
                                    sendBlobToNative(blob, fileName, contentType, reqUrl, classification.isPdf);
                                }
                            } catch (_) {}
                        }).catch(() => {});
                        return promise;
                    };
                }

                const origXhrOpen = XMLHttpRequest.prototype.open;
                const origXhrSend = XMLHttpRequest.prototype.send;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this.__verveUrl = url;
                    return origXhrOpen.apply(this, arguments);
                };
                XMLHttpRequest.prototype.send = function() {
                    this.addEventListener('load', function() {
                        try {
                            if (!window.VervePdfBridge || this.status < 200 || this.status >= 300) return;
                            const contentType = this.getResponseHeader('content-type') || '';
                            const disposition = this.getResponseHeader('content-disposition') || '';

                            let blob = null;
                            if (this.response instanceof Blob) {
                                blob = this.response;
                            } else if (this.response instanceof ArrayBuffer) {
                                blob = new Blob([this.response], { type: contentType || 'application/octet-stream' });
                            } else if ((!this.responseType || this.responseType === 'text') &&
                                typeof this.responseText === 'string' &&
                                this.responseText.slice(0, 1024).includes('%PDF-')
                            ) {
                                blob = new Blob([this.responseText], { type: 'application/pdf' });
                            }

                            if (blob && blob.size > 0) {
                                classifyDownloadResponse(contentType, disposition, blob).then((classification) => {
                                    if (classification.isDownload) {
                                        const sourceUrl = this.responseURL || this.__verveUrl;
                                        const fileName = extractFileName(disposition, sourceUrl, classification.isPdf ? 'pdf' : null);
                                        sendBlobToNative(blob, fileName, contentType, sourceUrl, classification.isPdf);
                                    }
                                });
                            }
                        } catch (_) {}
                    });
                    return origXhrSend.apply(this, arguments);
                };

                const origCreateObjectURL = URL.createObjectURL;
                if (typeof origCreateObjectURL === 'function') {
                    URL.createObjectURL = function(obj) {
                        const url = origCreateObjectURL.apply(this, arguments);
                        try {
                            if (obj instanceof Blob && obj.size > 0 && window.VervePdfBridge) {
                                hasPdfMagicBytes(obj).then((isMagicPdf) => {
                                    if (obj.type === 'application/pdf' || isMagicPdf) {
                                        blobUrlToName.set(url, 'document.pdf');
                                        sendBlobToNative(obj, 'document.pdf', 'application/pdf', window.location.href, true);
                                    }
                                });
                            }
                        } catch (_) {}
                        return url;
                    };
                }

                const origAnchorClick = HTMLAnchorElement.prototype.click;
                HTMLAnchorElement.prototype.click = function() {
                    try {
                        const href = this.href || '';
                        const downloadName = this.getAttribute('download') || '';
                        if (window.VervePdfBridge && nativeFetch && (href.startsWith('blob:') || href.startsWith('data:'))) {
                            const resolvedName = downloadName || blobUrlToName.get(href) || 'download';
                            nativeFetch(href)
                                .then(r => r.blob())
                                .then(async (blob) => {
                                    if (blob.size > 0) {
                                        const isPdf = blob.type === 'application/pdf' || await hasPdfMagicBytes(blob);
                                        sendBlobToNative(blob, resolvedName, blob.type, window.location.href, isPdf);
                                    }
                                })
                                .catch(() => {});
                            return;
                        }
                    } catch (_) {}
                    return origAnchorClick.apply(this, arguments);
                };

                window.addEventListener('click', (event) => {
                    const target = event.target && event.target.closest
                        ? event.target.closest('button, input[type="submit"], input[type="button"], input[type="image"], a, [onclick], [role="button"]')
                        : null;
                    if (!target) return;

                    if (target.tagName === 'A' && target.href && nativeFetch &&
                        (target.href.startsWith('blob:') || target.href.startsWith('data:'))
                    ) {
                        event.preventDefault();
                        const name = target.getAttribute('download') || blobUrlToName.get(target.href) || 'download';
                        nativeFetch(target.href)
                            .then(r => r.blob())
                            .then(async (blob) => {
                                if (blob.size > 0) {
                                    const isPdf = blob.type === 'application/pdf' || await hasPdfMagicBytes(blob);
                                    sendBlobToNative(blob, name, blob.type, window.location.href, isPdf);
                                }
                            })
                            .catch(() => {});
                        return;
                    }

                    lastSubmitter = target;
                    lastSubmitterTime = Date.now();
                }, true);

                window.addEventListener('submit', (event) => {
                    if (event.defaultPrevented) return;
                    const form = event.target;
                    const submitter = event.submitter || ((Date.now() - lastSubmitterTime < 1500) ? lastSubmitter : null);
                    if (!shouldInterceptPostForm(form, submitter)) return;

                    event.preventDefault();
                    event.stopImmediatePropagation();
                    executeInterceptedPostForm(form, submitter, () => {
                        origFormSubmit.call(form);
                    });
                }, false);
            }
        })();
    """.trimIndent()
}