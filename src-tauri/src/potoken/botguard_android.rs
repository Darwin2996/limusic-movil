//! Android fallback while rusty_v8's Android arm64 binary is unavailable.
//!
//! Direct stream clients do not need PoTokens. BotGuard clients fail closed and let the
//! orchestrator continue through its normal fallback order.

#[derive(Debug, thiserror::Error)]
pub enum Error {
    #[error("BotGuard is not available in this Android build")]
    Transient(String),
    #[error("BotGuard is not available in this Android build")]
    Fatal(String),
}

pub struct Minter;

pub struct Bootstrap {
    pub minter: Minter,
    pub session_token: String,
    pub lifetime_secs: u64,
}

impl Minter {
    pub async fn spawn(_user_agent: String, _session_ident: String) -> Result<Bootstrap, Error> {
        Err(Error::Transient("BotGuard is not available on Android".into()))
    }

    pub async fn mint(&self, _ident: &str) -> Result<String, Error> {
        Err(Error::Transient("BotGuard is not available on Android".into()))
    }
}
