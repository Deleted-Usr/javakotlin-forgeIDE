#pragma once

#include <SFML/Graphics.hpp>

namespace forge
{
    class ParticleSystem;

    class Player
    {
    public:
        static constexpr float radius = 18.f;

        explicit Player(sf::Vector2f spawnPosition);

        void update(float seconds, sf::Vector2f input, bool dashRequested,
                    sf::Vector2f arenaSize, ParticleSystem& particles);
        void draw(sf::RenderTarget& target, sf::Vector2f aimDirection, float weaponReadiness) const;
        void reset();
        void damage(float amount);
        void heal(float amount);
        void applyRecoil(sf::Vector2f direction);

        [[nodiscard]] sf::Vector2f position() const;
        [[nodiscard]] float health() const;
        [[nodiscard]] float dashReadiness() const;
        [[nodiscard]] bool isDead() const;

    private:
        sf::Vector2f spawnPosition_;
        sf::Vector2f position_;
        sf::Vector2f velocity_;
        sf::Vector2f lastMoveDirection_{1.f, 0.f};
        float health_{100.f};
        float dashCooldown_{};
        float dashTime_{};
        float trailTimer_{};
    };
}
