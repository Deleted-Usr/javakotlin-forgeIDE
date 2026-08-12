#pragma once

#include <SFML/Graphics.hpp>

namespace forge
{
    class ForgeCore
    {
    public:
        static constexpr float radius = 62.f;

        explicit ForgeCore(sf::Vector2f position);

        void draw(sf::RenderTarget& target, float elapsed) const;
        void damage(float amount);
        void reset();

        [[nodiscard]] sf::Vector2f position() const;
        [[nodiscard]] float health() const;
        [[nodiscard]] bool isDead() const;

    private:
        sf::Vector2f position_;
        float health_{100.f};
    };
}
