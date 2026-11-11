package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.lang.api.run.RunOptionsPage;

/// The C++ run configuration page.
///
/// A C++ program has no runtime to configure: it is built, then started as a
/// plain executable. So the shared runtime options are really build options,
/// and this page says so. It adds no controls of its own (yet), which is why it
/// only overrides the labels.
public final class CppRunOptionsPage implements RunOptionsPage
{
    @Override
    public String title()
    {
        return "C++";
    }

    @Override
    public String runtimeOptionsLabel()
    {
        return "Build options";
    }

    @Override
    public String runtimeOptionsHint()
    {
        return "Added when the program is built: compiler flags for g++, such as -O2, or -D options for CMake.";
    }
}
