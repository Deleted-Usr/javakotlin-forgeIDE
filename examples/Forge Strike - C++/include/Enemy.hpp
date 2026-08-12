#pragma once

#include <SFML/Graphics.hpp>

namespace forge
{
    class Enemy
    {
    public:
        Enemy(sf::Vector2f position, float radius, float health, float phase);

        void update(float seconds, sf::Vector2f playerPosition, sf::Vector2f corePosition, int wave);
        void draw(sf::RenderTarget& target, sf::Vector2f playerPosition) const;
        void damage(float amount);

        [[nodiscard]] sf::Vector2f position() const;
        [[nodiscard]] float radius() const;
        [[nodiscard]] bool isDead() const;

    private:
        sf::Vector2f position_;
        sf::Vector2f velocity_;
        float radius_{};
        float health_{};
        float phase_{};
    };
}
