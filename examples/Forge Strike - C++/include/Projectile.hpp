#pragma once

#include <SFML/Graphics.hpp>

namespace forge
{
    class Projectile
    {
    public:
        Projectile(sf::Vector2f position, sf::Vector2f velocity);

        void update(float seconds);
        void draw(sf::RenderTarget& target) const;

        [[nodiscard]] sf::Vector2f position() const;
        [[nodiscard]] bool hasExpired(sf::Vector2f arenaSize) const;

    private:
        sf::Vector2f position_;
        sf::Vector2f velocity_;
        float life_{1.35f};
    };
}
