use adblock::engine::Engine;
use adblock::lists::{FilterSet, ParseOptions};
use adblock::request::Request;
use jni::objects::{JClass, JString};
use jni::sys::jboolean;
use jni::JNIEnv;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::RwLock;

struct SafeEngine(Engine);

unsafe impl Send for SafeEngine {}
unsafe impl Sync for SafeEngine {}

static ENGINE: RwLock<Option<SafeEngine>> = RwLock::new(None);

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

        let mut filter_set = FilterSet::new(true);
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
            let url_str: String = match env.get_string(&url) {
                Ok(s) => s.into(),
                Err(_) => {
                    let _ = env.exception_clear();
                    return Ok(0);
                }
            };

            let source_str: String = match env.get_string(&source_url) {
                Ok(s) => s.into(),
                Err(_) => {
                    let _ = env.exception_clear();
                    return Ok(0);
                }
            };

            let type_str: String = match env.get_string(&resource_type) {
                Ok(s) => s.into(),
                Err(_) => {
                    let _ = env.exception_clear();
                    return Ok(0);
                }
            };

            if let Ok(guard) = ENGINE.read() {
                if let Some(ref safe_engine) = *guard {
                    let request = match Request::new(&url_str, &source_str, &type_str, "GET") {
                        Ok(req) => req,
                        Err(_) => return Ok(0),
                    };
                    let blocker_result = safe_engine.0.check_network_request(&request);
                    let is_blocked = blocker_result.filter.is_some() && blocker_result.exception.is_none();
                    return Ok(if is_blocked { 1 } else { 0 });
                }
            }
            Ok(0)
        });

        frame_result.unwrap_or(0)
    }));

    result.unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_adblock_engine_matching_and_exception() {
        let rules = vec![
            "||doubleclick.net^",
            "@@||doubleclick.net/allowed_ad.js",
        ];

        let mut filter_set = FilterSet::new(true);
        filter_set.add_filter_list(rules.join("\n"), ParseOptions::default());
        let engine = Engine::new_with_filter_set(filter_set);

        let req_blocked = Request::new("https://doubleclick.net/ad.js", "https://example.com", "script", "GET").unwrap();
        let res_blocked = engine.check_network_request(&req_blocked);
        assert!(res_blocked.filter.is_some() && res_blocked.exception.is_none());

        let req_allowed = Request::new("https://doubleclick.net/allowed_ad.js", "https://example.com", "script", "GET").unwrap();
        let res_allowed = engine.check_network_request(&req_allowed);
        assert!(res_allowed.exception.is_some());

        let req_clean = Request::new("https://example.com/logo.png", "https://example.com", "image", "GET").unwrap();
        let res_clean = engine.check_network_request(&req_clean);
        assert!(res_clean.filter.is_none());
    }
}