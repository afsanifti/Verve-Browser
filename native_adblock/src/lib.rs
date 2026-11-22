use adblock::engine::Engine;
use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use adblock::resources::Resource;
use jni::objects::{JByteArray, JClass, JObject, JObjectArray, JString};
use jni::sys::{jboolean, jbyteArray, jobjectArray};
use jni::JNIEnv;
use serde::Serialize;
use std::collections::HashSet;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;
use std::sync::RwLock;

struct SafeEngine(Engine);

unsafe impl Send for SafeEngine {}
unsafe impl Sync for SafeEngine {}

static ENGINE: RwLock<Option<SafeEngine>> = RwLock::new(None);

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct CosmeticResourcesPayload {
    hide_selectors: Vec<String>,
    exceptions: Vec<String>,
    injected_script: String,
    generichide: bool,
}

fn attach_resources(engine: &mut Engine, resources_json: &str) {
    if resources_json.trim().is_empty() {
        return;
    }
    if let Ok(resources) = serde_json::from_str::<Vec<Resource>>(resources_json) {
        engine.use_resources(resources);
    }
}

fn read_string_array(env: &mut JNIEnv, array: &JObjectArray) -> Vec<String> {
    if array.is_null() {
        return Vec::new();
    }
    let len = match env.get_array_length(array) {
        Ok(l) if l > 0 => l as usize,
        _ => return Vec::new(),
    };
    let mut out = Vec::with_capacity(len.min(512));
    for idx in 0..len.min(512) {
        if let Ok(obj) = env.get_object_array_element(array, idx as i32) {
            if !obj.is_null() {
                let jstr = JString::from(obj);
                let rust_str_res = env.get_string(&jstr);
                match rust_str_res {
                    Ok(rust_str) => {
                        let s: String = rust_str.into();
                        if !s.is_empty() {
                            out.push(s);
                        }
                    }
                    Err(_) => {
                        let _ = env.exception_clear();
                    }
                }
            }
        } else {
            let _ = env.exception_clear();
        }
    }
    out
}

#[no_mangle]
pub extern "system" fn Java_com_verve_browser_core_adblock_NativeAdBlocker_initEngine(
    mut env: JNIEnv,
    _class: JClass,
    rules: JString,
) -> jboolean {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if rules.is_null() {
            return 0;
        }
        let rules_str: String = match env.get_string(&rules) {
            Ok(s) => s.into(),
            Err(_) => {
                let _ = env.exception_clear();
                return 0;
            }
        };

        let mut filter_set = FilterSet::new(false);
        filter_set.add_filter_list(rules_str, ParseOptions::default());
        let engine = Engine::new_with_filter_set(filter_set);

        if let Ok(mut guard) = ENGINE.write() {
            *guard = Some(SafeEngine(engine));
            1
        } else {
            0
        }
    }));
    result.unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_verve_browser_core_adblock_NativeAdBlocker_initEngineFromLists(
    mut env: JNIEnv,
    _class: JClass,
    rules: JString,
    resources_json: JString,
) -> jbyteArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if rules.is_null() {
            return ptr::null_mut();
        }
        let rules_str: String = match env.get_string(&rules) {
            Ok(s) => s.into(),
            Err(_) => {
                let _ = env.exception_clear();
                return ptr::null_mut();
            }
        };
        let resources_str: String = if resources_json.is_null() {
            String::new()
        } else {
            match env.get_string(&resources_json) {
                Ok(s) => s.into(),
                Err(_) => {
                    let _ = env.exception_clear();
                    String::new()
                }
            }
        };

        let mut filter_set = FilterSet::new(false);
        filter_set.add_filter_list(rules_str, ParseOptions::default());
        let mut engine = Engine::new_with_filter_set(filter_set);
        attach_resources(&mut engine, &resources_str);

        let serialized: Vec<u8> = engine.serialize();

        if let Ok(mut guard) = ENGINE.write() {
            *guard = Some(SafeEngine(engine));
        } else {
            return ptr::null_mut();
        }

        if serialized.is_empty() {
            return ptr::null_mut();
        }

        match env.byte_array_from_slice(&serialized) {
            Ok(arr) => arr.into_raw(),
            Err(_) => {
                let _ = env.exception_clear();
                ptr::null_mut()
            }
        }
    }));
    result.unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_verve_browser_core_adblock_NativeAdBlocker_loadSerializedEngine(
    mut env: JNIEnv,
    _class: JClass,
    serialized_data: JByteArray,
    resources_json: JString,
) -> jboolean {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if serialized_data.is_null() {
            return 0;
        }
        let bytes = match env.convert_byte_array(&serialized_data) {
            Ok(b) if !b.is_empty() => b,
            _ => {
                let _ = env.exception_clear();
                return 0;
            }
        };
        let resources_str: String = if resources_json.is_null() {
            String::new()
        } else {
            match env.get_string(&resources_json) {
                Ok(s) => s.into(),
                Err(_) => {
                    let _ = env.exception_clear();
                    String::new()
                }
            }
        };

        let mut engine = Engine::default();
        if engine.deserialize(&bytes).is_err() {
            return 0;
        }
        attach_resources(&mut engine, &resources_str);

        if let Ok(mut guard) = ENGINE.write() {
            *guard = Some(SafeEngine(engine));
            1
        } else {
            0
        }
    }));
    result.unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_verve_browser_core_adblock_NativeAdBlocker_shouldBlock(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
    source_url: JString,
    resource_type: JString,
) -> jboolean {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if url.is_null() || source_url.is_null() || resource_type.is_null() {
            return 0;
        }
        let frame_result: Result<jboolean, jni::errors::Error> = env.with_local_frame(16, |env| {
            let url_str: String = env.get_string(&url)?.into();
            let source_str: String = env.get_string(&source_url)?.into();
            let type_str: String = env.get_string(&resource_type)?.into();

            if let Ok(guard) = ENGINE.read() {
                if let Some(ref safe_engine) = *guard {
                    let request = match Request::new(&url_str, &source_str, &type_str, "GET") {
                        Ok(req) => req,
                        Err(_) => return Ok(0),
                    };
                    let blocker_result = safe_engine.0.check_network_request(&request);
                    let is_blocked =
                        blocker_result.filter.is_some() && blocker_result.exception.is_none();
                    return Ok(if is_blocked { 1 } else { 0 });
                }
            }
            Ok(0)
        });
        frame_result.unwrap_or(0)
    }));
    result.unwrap_or(0)
}

#[no_mangle]
pub extern "system" fn Java_com_verve_browser_core_adblock_NativeAdBlocker_evaluateRequest(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
    source_url: JString,
    resource_type: JString,
    method: JString,
) -> jobjectArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if url.is_null() || source_url.is_null() || resource_type.is_null() {
            return ptr::null_mut();
        }
        let frame_result: Result<jobjectArray, jni::errors::Error> =
            env.with_local_frame(24, |env| {
                let url_str: String = env.get_string(&url)?.into();
                let source_str: String = env.get_string(&source_url)?.into();
                let type_str: String = env.get_string(&resource_type)?.into();
                let method_str: String = if method.is_null() {
                    "GET".to_string()
                } else {
                    env.get_string(&method)?.into()
                };

                let mut decision = "ALLOW";
                let mut redirect_uri = String::new();
                let mut rewritten_url = String::new();

                if let Ok(guard) = ENGINE.read() {
                    if let Some(ref safe_engine) = *guard {
                        if let Ok(request) =
                            Request::new(&url_str, &source_str, &type_str, &method_str)
                        {
                            let res = safe_engine.0.check_network_request(&request);
                            if res.exception.is_none() {
                                if let Some(ref redirect) = res.redirect {
                                    decision = "REDIRECT";
                                    redirect_uri = redirect.clone();
                                } else if res.filter.is_some() {
                                    decision = "BLOCK";
                                } else if let Some(ref rewritten) = res.rewritten_url {
                                    if !rewritten.is_empty() && rewritten != &url_str {
                                        decision = "REWRITE";
                                        rewritten_url = rewritten.clone();
                                    }
                                }
                            } else if let Some(ref rewritten) = res.rewritten_url {
                                if !rewritten.is_empty() && rewritten != &url_str {
                                    decision = "REWRITE";
                                    rewritten_url = rewritten.clone();
                                }
                            }
                        }
                    }
                }

                let out_array = env.new_object_array(3, "java/lang/String", JObject::null())?;
                let j_decision = env.new_string(decision)?;
                let j_redirect = env.new_string(&redirect_uri)?;
                let j_rewritten = env.new_string(&rewritten_url)?;
                env.set_object_array_element(&out_array, 0, j_decision)?;
                env.set_object_array_element(&out_array, 1, j_redirect)?;
                env.set_object_array_element(&out_array, 2, j_rewritten)?;
                Ok(out_array.into_raw())
            });

        frame_result.unwrap_or(ptr::null_mut())
    }));
    result.unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_verve_browser_core_adblock_NativeAdBlocker_getCosmeticResources(
    mut env: JNIEnv,
    _class: JClass,
    url: JString,
) -> jni::sys::jstring {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if url.is_null() {
            return ptr::null_mut();
        }
        let url_str: String = match env.get_string(&url) {
            Ok(s) => s.into(),
            Err(_) => {
                let _ = env.exception_clear();
                return ptr::null_mut();
            }
        };

        let json_opt = if let Ok(guard) = ENGINE.read() {
            guard.as_ref().map(|safe_engine| {
                let res = safe_engine.0.url_cosmetic_resources(&url_str);
                let payload = CosmeticResourcesPayload {
                    hide_selectors: res.hide_selectors.into_iter().collect(),
                    exceptions: res.exceptions.into_iter().collect(),
                    injected_script: res.injected_script,
                    generichide: res.generichide,
                };
                serde_json::to_string(&payload).unwrap_or_default()
            })
        } else {
            None
        };

        match json_opt {
            Some(json) if !json.is_empty() => match env.new_string(&json) {
                Ok(s) => s.into_raw(),
                Err(_) => {
                    let _ = env.exception_clear();
                    ptr::null_mut()
                }
            },
            _ => ptr::null_mut(),
        }
    }));
    result.unwrap_or(ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_verve_browser_core_adblock_NativeAdBlocker_getHiddenClassIdSelectors(
    mut env: JNIEnv,
    _class: JClass,
    classes: JObjectArray,
    ids: JObjectArray,
    exceptions: JObjectArray,
) -> jobjectArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let frame_result: Result<jobjectArray, jni::errors::Error> =
            env.with_local_frame(64, |env| {
                let class_list = read_string_array(env, &classes);
                let id_list = read_string_array(env, &ids);
                let exception_set: HashSet<String> =
                    read_string_array(env, &exceptions).into_iter().collect();

                if class_list.is_empty() && id_list.is_empty() {
                    return Ok(env
                        .new_object_array(0, "java/lang/String", JObject::null())?
                        .into_raw());
                }

                let selectors: Vec<String> = if let Ok(guard) = ENGINE.read() {
                    if let Some(ref safe_engine) = *guard {
                        safe_engine.0.hidden_class_id_selectors(
                            &class_list,
                            &id_list,
                            &exception_set,
                        )
                    } else {
                        Vec::new()
                    }
                } else {
                    Vec::new()
                };

                let out_array = env.new_object_array(
                    selectors.len() as i32,
                    "java/lang/String",
                    JObject::null(),
                )?;
                for (i, sel) in selectors.iter().enumerate() {
                    let j_sel = env.new_string(sel)?;
                    env.set_object_array_element(&out_array, i as i32, j_sel)?;
                }
                Ok(out_array.into_raw())
            });

        frame_result.unwrap_or(ptr::null_mut())
    }));
    result.unwrap_or(ptr::null_mut())
}

#[cfg(test)]
mod baseline_suite {
    use super::*;
    use adblock::resources::{MimeType, ResourceType};

    fn build_test_engine(rules: &[&str], resources: Vec<Resource>) -> Engine {
        let mut filter_set = FilterSet::new(false);
        filter_set.add_filter_list(rules.join("\n"), ParseOptions::default());
        let mut engine = Engine::new_with_filter_set(filter_set);
        if !resources.is_empty() {
            engine.use_resources(resources);
        }
        engine
    }

    #[test]
    fn phase2_a_network_filtering_resource_types_and_party() {
        let rules = [
            "||doubleclick.net^",
            "||mc.yandex.ru^",
            "||advmaker.net^$image",
            "||hotjar.com^$script",
            "||tracker.example.com/pixel.gif^$image,third-party",
            "||ads.example.com/ad.css^$stylesheet",
            "||sentry.io^$xmlhttprequest",
            "||adfont.example.com^$font",
            "||advideo.example.com^$media",
        ];
        let engine = build_test_engine(&rules, Vec::new());

        // Known ad & tracking domains
        let ad_req = Request::new("https://stats.g.doubleclick.net/dc.js", "https://news.example.org", "script", "GET").unwrap();
        assert!(engine.check_network_request(&ad_req).filter.is_some());

        let tracker_req = Request::new("https://mc.yandex.ru/metrika/tag.js", "https://news.example.org", "script", "GET").unwrap();
        assert!(engine.check_network_request(&tracker_req).filter.is_some());

        // First-party vs third-party enforcement
        let first_party_pixel = Request::new("https://tracker.example.com/pixel.gif", "https://tracker.example.com/home", "image", "GET").unwrap();
        assert!(engine.check_network_request(&first_party_pixel).filter.is_none(), "First-party pixel must not match $third-party rule");

        let third_party_pixel = Request::new("https://tracker.example.com/pixel.gif", "https://publisher.org/article", "image", "GET").unwrap();
        assert!(engine.check_network_request(&third_party_pixel).filter.is_some(), "Third-party pixel must be blocked");

        // Image, Script, Stylesheet, XHR, Font, Media
        for (url, rtype) in [
            ("https://advmaker.net/banner468x60.png", "image"),
            ("https://static.hotjar.com/c/hotjar-123.js", "script"),
            ("https://ads.example.com/ad.css", "stylesheet"),
            ("https://o123.ingest.sentry.io/api/1/envelope/", "xmlhttprequest"),
            ("https://adfont.example.com/promo.woff2", "font"),
            ("https://advideo.example.com/preroll.mp4", "media"),
        ] {
            let req = Request::new(url, "https://publisher.org", rtype, "GET").unwrap();
            assert!(engine.check_network_request(&req).filter.is_some(), "Expected block for {url} ({rtype})");
        }
    }

    #[test]
    fn phase2_b_filter_rule_behavior_exceptions_precedence_and_malformed() {
        let rules = [
            "||example-ads.com^",
            "@@||example-ads.com/essential-sdk.js$script,domain=trusted.org",
            "||cdn.example.com/app.js$domain=other.org",
            "###invalid[[[[malformed_selector",
            "||malformed$$$$option",
        ];
        let engine = build_test_engine(&rules, Vec::new());

        // Exception rule applies on trusted.org
        let allowed = Request::new("https://example-ads.com/essential-sdk.js", "https://trusted.org/app", "script", "GET").unwrap();
        let res_allowed = engine.check_network_request(&allowed);
        assert!(res_allowed.exception.is_some(), "Exception rule must override block on trusted.org");

        // Block still applies on untrusted.org
        let blocked = Request::new("https://example-ads.com/essential-sdk.js", "https://untrusted.org/app", "script", "GET").unwrap();
        let res_blocked = engine.check_network_request(&blocked);
        assert!(res_blocked.filter.is_some() && res_blocked.exception.is_none());

        // Domain-scoped rule does not leak to other domains
        let other_domain = Request::new("https://cdn.example.com/app.js", "https://trusted.org", "script", "GET").unwrap();
        assert!(engine.check_network_request(&other_domain).filter.is_none());
    }

    #[test]
    fn phase2_c_cosmetic_filtering_generic_domain_exceptions_and_procedural() {
        let rules = [
            "##.ad-banner-468x60",
            "###ad-container",
            "adblock-tester.com##.test-banner-box",
            "adblock-tester.com#@#.ad-banner-468x60",
            "example.com##div.feed-item:has(.sponsored-badge)",
        ];
        let engine = build_test_engine(&rules, Vec::new());

        // Domain-specific selectors & exceptions
        let tester_cosmetics = engine.url_cosmetic_resources("https://adblock-tester.com/");
        assert!(tester_cosmetics.hide_selectors.contains(".test-banner-box"));
        assert!(tester_cosmetics.exceptions.contains(".ad-banner-468x60"));

        // Generic class/id selector lookup honoring exceptions
        let hidden = engine.hidden_class_id_selectors(
            &["ad-banner-468x60".to_string(), "article-body".to_string()],
            &["ad-container".to_string()],
            &tester_cosmetics.exceptions,
        );
        assert!(hidden.contains(&"#ad-container".to_string()));
        assert!(!hidden.contains(&".ad-banner-468x60".to_string()), "Exception must prevent generic hiding");

        // Procedural selector availability
        let proc_cosmetics = engine.url_cosmetic_resources("https://example.com/news");
        assert!(
            proc_cosmetics.hide_selectors.iter().any(|s| s.contains(":has(")),
            "Procedural cosmetic selector must be returned for matching domain"
        );
    }

    #[test]
    fn phase2_d_scriptlets_and_redirect_surrogates() {
        let noop_js = Resource {
            name: "noopjs".to_string(),
            aliases: vec!["noop.js".to_string()],
            kind: ResourceType::Mime(MimeType::ApplicationJavascript),
            content: base64_encode("(function(){})();"),
            dependencies: vec![],
            permission: Default::default(),
        };
        let set_const = Resource {
            name: "set-constant.js".to_string(),
            aliases: vec!["set.js".to_string()],
            kind: ResourceType::Template,
            content: base64_encode("window['{{1}}'] = {{2}};"),
            dependencies: vec![],
            permission: Default::default(),
        };
        let rules = [
            "||google-analytics.com/analytics.js$script,redirect=noopjs",
            "example.com##+js(set-constant, adblockEnabled, true)",
        ];
        let engine = build_test_engine(&rules, vec![noop_js, set_const]);

        let ga_req = Request::new("https://www.google-analytics.com/analytics.js", "https://example.com", "script", "GET").unwrap();
        let res = engine.check_network_request(&ga_req);
        assert!(res.redirect.is_some(), "Redirect surrogate data URI must be returned when ResourceStorage is populated");
        assert!(res.redirect.unwrap().starts_with("data:application/javascript;base64,"));

        let cosmetics = engine.url_cosmetic_resources("https://example.com/");
        assert!(cosmetics.injected_script.contains("adblockEnabled"), "Scriptlet must be compiled into injected_script");
    }

    #[test]
    fn phase2_e_snapshot_serialization_roundtrip_and_compatibility() {
        let rules = [
            "||doubleclick.net^",
            "@@||accounts.google.com^",
            "@@||oauth.example.com^",
        ];
        let engine = build_test_engine(&rules, Vec::new());
        let bytes = engine.serialize();
        assert!(!bytes.is_empty());

        let mut restored = Engine::default();
        restored.deserialize(&bytes).expect("Serialized snapshot must deserialize cleanly");

        // Auth & legitimate CDN requests must pass untouched
        for (url, page, rtype) in [
            ("https://accounts.google.com/o/oauth2/auth", "https://myapp.example.com/login", "sub_frame"),
            ("https://cdn.jsdelivr.net/npm/react@18/umd/react.production.min.js", "https://myapp.example.com", "script"),
            ("https://api.myapp.example.com/v1/user/profile", "https://myapp.example.com/dashboard", "xmlhttprequest"),
        ] {
            let req = Request::new(url, page, rtype, "GET").unwrap();
            let res = restored.check_network_request(&req);
            assert!(res.filter.is_none() || res.exception.is_some(), "Legitimate request broken: {url}");
        }
    }

    fn base64_encode(input: &str) -> String {
        const TABLE: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        let bytes = input.as_bytes();
        let mut out = String::new();
        for chunk in bytes.chunks(3) {
            let b0 = chunk[0] as u32;
            let b1 = if chunk.len() > 1 { chunk[1] as u32 } else { 0 };
            let b2 = if chunk.len() > 2 { chunk[2] as u32 } else { 0 };
            let triple = (b0 << 16) | (b1 << 8) | b2;
            out.push(TABLE[((triple >> 18) & 0x3F) as usize] as char);
            out.push(TABLE[((triple >> 12) & 0x3F) as usize] as char);
            out.push(if chunk.len() > 1 { TABLE[((triple >> 6) & 0x3F) as usize] as char } else { '=' });
            out.push(if chunk.len() > 2 { TABLE[(triple & 0x3F) as usize] as char } else { '=' });
        }
        out
    }
}