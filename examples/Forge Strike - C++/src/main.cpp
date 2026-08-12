#include "Game.hpp"

#include <exception>
#include <iostream>

int main()
{
    try
    {
        forge::Game game;
        game.run();
    }
    catch (const std::exception& error)
    {
        std::cerr << "Forge Strike could not start: " << error.what() << '\n';
        return 1;
    }
}
