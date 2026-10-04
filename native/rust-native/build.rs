use std::io;

fn main() -> io::Result<()> {
    // Only run this build script if compiling for Windows
    if std::env::var_os("CARGO_CFG_WINDOWS").is_some() {
        let mut res = winresource::WindowsResource::new();
        // Path can be absolute, or relative to the crate root
        res.set_icon("forge.ico");
        res.compile()?;
    }
    Ok(())
}
