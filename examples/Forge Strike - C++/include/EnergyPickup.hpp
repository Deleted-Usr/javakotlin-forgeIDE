#pragma once

#include <SFML/Graphics.hpp>

namespace forge
{
    class EnergyPickup
    {
    public:
        EnergyPickup(sf::Vector2f position, float phase);

        void update(float seconds, sf::Vector2f playerPosition);
        void draw(sf::RenderTarget& target) const;

        [[nodiscard]] sf::Vector2f position() const;
        [[nodiscard]] bool touches(sf::Vector2f playerPosition) const;

    private:
        sf::Vector2f position_;
        float phase_{};
    };
}
