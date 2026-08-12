#pragma once

#include <SFML/System/Vector2.hpp>

namespace forge::math
{
    inline constexpr float pi = 3.14159265358979323846f;

    [[nodiscard]] float lengthSquared(sf::Vector2f value);
    [[nodiscard]] float length(sf::Vector2f value);
    [[nodiscard]] float distanceSquared(sf::Vector2f first, sf::Vector2f second);
    [[nodiscard]] sf::Vector2f normalised(sf::Vector2f value, sf::Vector2f fallback = {1.f, 0.f});
}
