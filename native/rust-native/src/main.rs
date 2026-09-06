// A Rust port of the Forge IDE Windows Launcher

// This attribute is the equivalent of writing WinMain instead of main in C++:
// it marks the executable as a GUI application, so Windows does not open a
// console window behind it. Everything else still starts at `fn main()`.
#![windows_subsystem = "windows"]

use std::ffi::{c_char, c_void, CString, OsStr};
use std::os::windows::ffi::OsStrExt;
use std::path::{Path, PathBuf};
use std::process::ExitCode;
use std::ptr;

use jni_sys::{jint, jobjectArray, jvalue, JNIEnv, JavaVM, JavaVMInitArgs, JavaVMOption, JNI_OK};
use windows_sys::Win32::System::LibraryLoader::{GetProcAddress, LoadLibraryW};
use windows_sys::Win32::UI::WindowsAndMessaging::{MessageBoxW, MB_ICONERROR};

/// jni-sys only ships constants up to 1.8, so this one is spelled out. It is
/// the same value as JNI_VERSION_10 in jni.h.
const JNI_VERSION_10: jint = 0x000A_0000;

/// The Rust spelling of `typedef jint(JNICALL* CreateJavaVM_t)(JavaVM**, void**, void**)`.
///
/// `extern "system"` is the calling convention JNICALL expands to on Windows.
type CreateJavaVmFn = unsafe extern "system" fn(
    pvm: *mut *mut JavaVM,
    penv: *mut *mut c_void,
    args: *mut c_void,
) -> jint;

/// Win32 wants NUL-terminated UTF-16, which is what the `L"..."` prefix gives
/// in C++. Rust strings are UTF-8, so they have to be converted.
///
/// The returned Vec must stay alive for as long as the pointer to it is in
/// use — hence every caller below binds it to a variable first.
fn to_wide(text: &str) -> Vec<u16>
{
    OsStr::new(text)
        .encode_wide()
        .chain(std::iter::once(0)) // the terminating NUL
        .collect()
}

fn show_error(message: &str)
{
    let text = to_wide(message);
    let caption = to_wide("Error");

    unsafe
    {
        MessageBoxW(ptr::null_mut(), text.as_ptr(), caption.as_ptr(), MB_ICONERROR);
    }
}

/// `getExecutablePath` in the original. std does the GetModuleFileNameW call,
///  including the buffer sizing that MAX_BUFFER was there to handle.
fn executable_dir() -> Result<PathBuf, String>
{
    let exe = std::env::current_exe()
        .map_err(|_| "failed to get executable path".to_string())?;

    // `parent()` returns None only for a path with no directory part.
    exe.parent()
        .map(Path::to_path_buf)
        .ok_or_else(|| "Failed to get executable path".to_string())
}

fn build_class_path(root_dir: &Path) -> Result<String, String>
{
    let app_jar = root_dir.join("app").join("ForgeIDE.jar");
    let libs_dir = root_dir.join("libs");

    if !app_jar.is_file()
    {
        return Err(format!("App JAR is missing: {}", app_jar.display()));
    }
    if !libs_dir.is_dir()
    {
        return Err(format!("Libraries Directory is missing: {}", libs_dir.display()));
    }

    let mut libs: Vec<PathBuf> = Vec::new();

    let entries = std::fs::read_dir(&libs_dir)
        .map_err(|e| format!("Could not read {}.\n{e}", libs_dir.display()))?;

    for entry in entries
    {
        // read_dir yields Results, because a single entry can fail to be read
        // even after the directory opened successfully.
        let entry = entry.map_err(|e| format!("Could not read {}.\n{e}", libs_dir.display()))?;
        let path = entry.path();

        if path.is_file() && path.extension() == Some(OsStr::new("jar"))
        {
            libs.push(path);
        }
    }

    libs.sort();

    let mut class_path = app_jar.display().to_string();

    // Append each library to the classpath string
    let separator = if cfg!(windows) { ";" } else { ":" };
    for lib in &libs
    {
        class_path.push_str(separator);
        class_path.push_str(&lib.display().to_string());
    }

    Ok(class_path)
}

/// The body of the old WinMain. Every failure returns a message rather than
/// popping up a dialog itself, so there is exactly one place that shows one.
fn run() -> Result<(), String>
{
    // Locate the JVM DLL. As in C++, this is relative to the working
    // directory rather than to the executable.
    let dll_path = to_wide("runtime\\bin\\server\\jvm.dll");

    let jvm_dll = unsafe { LoadLibraryW(dll_path.as_ptr()) };
    if jvm_dll.is_null()
    {
        return Err("Failed to load JVM.".to_string());
    }

    // Get the address of JNI_CreateJavaVM. The `\0` is written out because
    // this is a plain byte string, not a Rust string literal — Rust strings
    // are not NUL-terminated.
    let procedure = unsafe { GetProcAddress(jvm_dll, b"JNI_CreateJavaVM\0".as_ptr()) };

    let Some(procedure) = procedure else
    {
        return Err("Failed to find JNI_CreateJavaVM in the JVM.".to_string());
    };

    // GetProcAddress hands back an untyped function pointer, so this is the
    // reinterpret_cast the C++ did. It is unsafe because nothing checks that
    // the real function matches CreateJavaVmFn.
    let create_java_vm: CreateJavaVmFn = unsafe { std::mem::transmute(procedure) };

    let root_dir = executable_dir()?;
    let class_path = build_class_path(&root_dir)?;

    // Set up JVM arguments (.vmoptions).
    //
    // These three bindings matter: JavaVMOption stores raw pointers into them,
    // so if a CString were created inline it would be dropped at the end of
    // that statement and the JVM would read freed memory. Naming them keeps
    // them alive until the end of this function.
    let class_path_option = CString::new(format!("-Djava.class.path={class_path}"))
        .map_err(|_| "The class path contains a Nul byte.".to_string())?;
    let heap_option = CString::new("-Xmx512m").unwrap();
    let native_access_option = CString::new("--enable-native-access=ALL-UNNAMED").unwrap();

    let mut options = [
        JavaVMOption {
            optionString: class_path_option.as_ptr() as *mut c_char,
            extraInfo: ptr::null_mut(),
        },
        JavaVMOption {
            optionString: heap_option.as_ptr() as *mut c_char,
            extraInfo: ptr::null_mut(),
        },
        JavaVMOption {
            optionString: native_access_option.as_ptr() as *mut c_char,
            extraInfo: ptr::null_mut(),
        },
    ];

    let mut init_args = JavaVMInitArgs {
        version: JNI_VERSION_10,
        nOptions: options.len() as jint,
        options: options.as_mut_ptr(),
        ignoreUnrecognized: 1, // JNI_TRUE
    };

    let mut jvm: *mut JavaVM = ptr::null_mut(); // Pointer to the JVM
    let mut env: *mut JNIEnv = ptr::null_mut(); // Pointer to the native interface functions

    // Start the JVM. A non-zero return is a failure, as in the original.
    let status = unsafe
    {
        create_java_vm(
            &mut jvm,
            &mut env as *mut *mut JNIEnv as *mut *mut c_void,
            &mut init_args as *mut JavaVMInitArgs as *mut c_void,
        )
    };

    if status != JNI_OK
    {
        return Err("Failed to initialise JVM.".to_string());
    }

    // Find and invoke the main class.
    //
    // In C++, this is written as env->FindClass(...). In Rust the JNI function table
    // is just a struct of function pointers, so the call is spelled out:
    // `**env` is the table, the field is an Option that has to be unwrapped,
    // and `env` is passed back in as the first argument.
    unsafe
    {
        let find_class = (**env).FindClass.unwrap();
        let main_class = find_class(env, b"com/willclay/forgeide/Main\0".as_ptr() as *const c_char);

        if !main_class.is_null()
        {
            let get_static_method_id = (**env).GetStaticMethodID.unwrap();
            let main_method = get_static_method_id(
                env,
                main_class,
                b"main\0".as_ptr() as *const c_char,
                b"([Ljava/lang/String;)V\0".as_ptr() as *const c_char,
            );

            if !main_method.is_null()
            {
                let new_object_array = (**env).NewObjectArray.unwrap();
                let string_class = find_class(env, b"java/lang/String\0".as_ptr() as *const c_char);
                let args: jobjectArray = new_object_array(env, 0, string_class, ptr::null_mut());

                // CallStaticVoidMethod is variadic in C. The `A` variant takes
                // the arguments as an array instead, which is far easier to
                // call from Rust and does exactly the same thing.
                let call_static_void_method = (**env).CallStaticVoidMethodA.unwrap();
                let call_args = [jvalue { l: args }];

                call_static_void_method(env, main_class, main_method, call_args.as_ptr());
            }
        }

        // Safely destroy the JVM. This blocks until the Swing thread finishes.
        let destroy_java_vm = (**jvm).DestroyJavaVM.unwrap();
        destroy_java_vm(jvm);
    }

    Ok(())
}

fn main() -> ExitCode
{
    match run()
    {
        Ok(()) => ExitCode::SUCCESS,
        Err(message) => {
            show_error(&message);
            ExitCode::FAILURE
        }
    }
}