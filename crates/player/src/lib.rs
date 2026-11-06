#[cfg(target_os = "android")]
mod android;
#[cfg(target_os = "android")]
pub use android::*;

#[cfg(not(target_os = "android"))]
#[path = "desktop.rs"]
mod desktop;
#[cfg(not(target_os = "android"))]
pub use desktop::*;
