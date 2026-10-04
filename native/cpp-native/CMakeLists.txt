cmake_minimum_required(VERSION 4.3)
project(cpp_native)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}")

# forge.rc embeds the application icon; CMake compiles .rc files with windres/rc automatically.
add_executable(ForgeIDE WIN32 main.cpp forge.rc)

find_package(JNI REQUIRED)
target_include_directories(ForgeIDE PRIVATE ${JNI_INCLUDE_DIRS})

# Link the standard C++ libraries statically, but leave the Windows runtime dynamic
target_link_options(ForgeIDE PRIVATE -static-libgcc -static-libstdc++)
