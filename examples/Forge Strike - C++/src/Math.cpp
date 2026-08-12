#include "Math.hpp"

#include <cmath>

namespace forge::math
{
    float lengthSquared(sf::Vector2f value)
    {
        return value.x * value.x + value.y * value.y;
    }

    float length(sf::Vector2f value)
    {
        return std::sqrt(lengthSquared(value));
    }

    float distanceSquared(sf::Vector2f first, sf::Vector2f second)
    {
        return lengthSquared(first - second);
    }

    sf::Vector2f normalised(sf::Vector2f value, sf::Vector2f fallback)
    {
        const float magnitude = length(value);
        return magnitude > 0.0001f ? value / magnitude : fallback;
    }
}
