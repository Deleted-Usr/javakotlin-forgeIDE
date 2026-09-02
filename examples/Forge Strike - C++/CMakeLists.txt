cmake_minimum_required(VERSION 3.20)
project(ForgeStrike LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

find_package(SFML 3 COMPONENTS Graphics Window System REQUIRED)

add_executable(ForgeStrike
    include/Game.hpp
    include/Player.hpp
    include/Enemy.hpp
    include/Projectile.hpp
    include/EnergyPickup.hpp
    include/ForgeCore.hpp
    include/ParticleSystem.hpp
    include/Math.hpp
    include/Palette.hpp
    src/main.cpp
    src/Game.cpp
    src/Player.cpp
    src/Enemy.cpp
    src/Projectile.cpp
    src/EnergyPickup.cpp
    src/ForgeCore.cpp
    src/ParticleSystem.cpp
    src/Math.cpp
)

target_include_directories(ForgeStrike PRIVATE include)
target_link_libraries(ForgeStrike PRIVATE SFML::Graphics SFML::Window SFML::System)

if(MSVC)
    target_compile_options(ForgeStrike PRIVATE /W4 /permissive-)
else()
    target_compile_options(ForgeStrike PRIVATE -Wall -Wextra -Wpedantic)
endif()
